package com.stremio.mobile.presentation.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.EpisodeOption
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvDimens
import kotlinx.coroutines.yield
import kotlinx.coroutines.launch

@Composable
internal fun TvEpisodeBrowserContent(
    item: CatalogItem,
    state: TvDetailsUiState,
    browser: TvEpisodeBrowserUiState,
    focusRestoreVideoId: String?,
    focusRestoreId: Int,
    libraryRequester: FocusRequester,
    backRequester: FocusRequester,
    onLibraryAction: () -> Unit,
    onEpisodeActivate: (EpisodeOption) -> Unit,
    onEpisodeSources: (EpisodeOption) -> Unit,
    onBack: () -> Unit,
) {
    val initialSeason = browser.defaultSeason ?: browser.seasons.first().season
    var selectedSeason by remember(item.id) { mutableLongStateOf(initialSeason) }
    var focusedSeason by remember(item.id) { mutableLongStateOf(initialSeason) }
    var focusRegion by remember(item.id) { mutableStateOf(EpisodeFocusRegion.Actions) }
    var fallbackIndex by remember(item.id) { mutableIntStateOf(0) }
    val rememberedVideoBySeason = remember(item.id) { mutableStateMapOf<Long, String>() }
    val rememberedIndexBySeason = remember(item.id) { mutableStateMapOf<Long, Int>() }
    val scrollBySeason = remember(item.id) { mutableStateMapOf<Long, Pair<Int, Int>>() }
    val actionRequesters = listOf(libraryRequester, backRequester)
    val seasonRequesters = remember(item.id) { mutableMapOf<Long, FocusRequester>() }
    val episodeRequesters = remember(item.id) { mutableMapOf<String, FocusRequester>() }
    val sourceRequesters = remember(item.id) { mutableMapOf<String, FocusRequester>() }
    var focusedEpisodeId by remember(item.id) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var pendingEpisodeFocus by remember(item.id) { mutableStateOf(false) }
    var pendingSemanticRestore by remember(item.id) { mutableStateOf<String?>(null) }
    val season = browser.seasons.firstOrNull { it.season == selectedSeason }
    val visibleEpisodes = season?.episodes.orEmpty()
    val focusEpisodeAt: (Int) -> Unit = { index ->
        if (index in visibleEpisodes.indices) {
            val requester = episodeRequesters.getOrPut(visibleEpisodes[index].videoId) { FocusRequester() }
            scope.launch {
                listState.animateScrollToItem(index)
                yield()
                requester.requestFocus()
            }
        }
    }
    val moveDownFromActions: () -> Unit = {
        if (browser.seasons.size > 1) seasonRequesters[selectedSeason]?.requestFocus()
        else pendingEpisodeFocus = true
    }

    LaunchedEffect(browser.seasons.map { it.season }) {
        if (browser.seasons.none { it.season == selectedSeason }) {
            selectedSeason = browser.seasons.firstOrNull()?.season ?: return@LaunchedEffect
        }
    }

    LaunchedEffect(focusRestoreId) {
        val videoId = focusRestoreVideoId ?: return@LaunchedEffect
        val target = browser.episodes.firstOrNull { it.videoId == videoId } ?: return@LaunchedEffect
        val season = target.seriesInfo?.season ?: return@LaunchedEffect
        selectedSeason = season
        val targetEpisodes = browser.episodesFor(season)
        val index = targetEpisodes.indexOfFirst { it.videoId == videoId }
        if (index >= 0) {
            rememberedVideoBySeason[season] = videoId
            rememberedIndexBySeason[season] = index
            pendingSemanticRestore = videoId
            focusRegion = EpisodeFocusRegion.Episodes
        }
    }

    LaunchedEffect(selectedSeason, visibleEpisodes.map { it.videoId }, pendingEpisodeFocus, focusRegion, pendingSemanticRestore) {
        val offset = scrollBySeason[selectedSeason]
        if (offset != null) listState.scrollToItem(offset.first, offset.second)
        if ((pendingEpisodeFocus || focusRegion == EpisodeFocusRegion.Episodes) && visibleEpisodes.isNotEmpty()) {
            val rememberedId = pendingSemanticRestore
                ?: rememberedVideoBySeason[selectedSeason]
                ?: browser.currentVideoId?.takeIf { id -> visibleEpisodes.any { it.videoId == id } }
                ?: browser.continueWatchingVideoId?.takeIf { id -> visibleEpisodes.any { it.videoId == id } }
            val targetIndex = restoredEpisodeIndex(
                visibleEpisodes,
                rememberedId,
                rememberedIndexBySeason[selectedSeason] ?: fallbackIndex,
            ) ?: 0
            val target = visibleEpisodes[targetIndex]
            rememberedVideoBySeason[selectedSeason] = target.videoId
            rememberedIndexBySeason[selectedSeason] = targetIndex
            fallbackIndex = targetIndex
            val targetIsVisible = listState.layoutInfo.visibleItemsInfo.any { it.key == target.videoId }
            if (offset == null || !targetIsVisible) listState.scrollToItem(targetIndex)
            yield()
            episodeRequesters.getOrPut(target.videoId) { FocusRequester() }.requestFocus()
            focusRegion = EpisodeFocusRegion.Episodes
            pendingEpisodeFocus = false
            pendingSemanticRestore = null
        } else if (offset == null) {
            val entryId = browser.currentVideoId?.takeIf { id -> visibleEpisodes.any { it.videoId == id } }
                ?: browser.continueWatchingVideoId?.takeIf { id -> visibleEpisodes.any { it.videoId == id } }
            val entryIndex = visibleEpisodes.indexOfFirst { it.videoId == entryId }
            if (entryIndex > 0) listState.scrollToItem(entryIndex)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(start = 72.dp, end = 72.dp, top = 24.dp, bottom = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (!item.logo.isNullOrBlank()) {
                    AsyncImage(
                        model = item.logo,
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.width(300.dp).height(48.dp),
                    )
                }
                Text(
                    item.name,
                    style = if (item.logo.isNullOrBlank()) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                    color = if (item.logo.isNullOrBlank()) TvColors.primaryText else TvColors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val metadata = listOfNotNull(
                    item.releaseInfo?.takeIf(String::isNotBlank),
                    item.imdbRating?.takeIf(String::isNotBlank)?.let { "IMDb $it" },
                ).joinToString("  •  ")
                if (metadata.isNotBlank()) {
                    Text(metadata, color = TvColors.secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                state.details?.description?.takeIf(String::isNotBlank)?.let { summary ->
                    Text(
                        summary,
                        Modifier.padding(top = 5.dp),
                        color = TvColors.primaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onLibraryAction,
                    enabled = !state.isLibraryActionLoading,
                    modifier = Modifier.focusRequester(actionRequesters[0]).focusProperties {
                        left = actionRequesters[1]
                        right = actionRequesters[1]
                        up = actionRequesters[0]
                        down = seasonRequesters[selectedSeason] ?: episodeRequesters[focusTargetId(browser, selectedSeason, rememberedVideoBySeason, rememberedIndexBySeason)] ?: actionRequesters[0]
                    }.onFocusChanged { if (it.isFocused) focusRegion = EpisodeFocusRegion.Actions }
                        .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            moveDownFromActions()
                            true
                        } else false
                    },
                ) { Text(if (state.isInLibrary) "✓  In Library" else "+  Add to Library") }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.focusRequester(actionRequesters[1]).focusProperties {
                        left = actionRequesters[0]
                        right = actionRequesters[0]
                        up = actionRequesters[1]
                        down = actionRequesters[0]
                    }.onFocusChanged { if (it.isFocused) focusRegion = EpisodeFocusRegion.Actions }
                        .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            moveDownFromActions()
                            true
                        } else false
                    },
                ) { Text("Back") }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (browser.seasons.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                browser.seasons.forEachIndexed { index, option ->
                    val requester = seasonRequesters.getOrPut(option.season) { FocusRequester() }
                    val selected = option.season == selectedSeason
                    val focused = option.season == focusedSeason && focusRegion == EpisodeFocusRegion.Seasons
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) TvColors.focusSoft else TvColors.surface)
                            .border(
                                width = if (selected || focused) 2.dp else 1.dp,
                                color = if (focused) TvColors.focus else if (selected) TvColors.accent else TvColors.divider,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .focusRequester(requester)
                            .focusProperties {
                                left = browser.seasons.getOrNull(index - 1)?.let { seasonRequesters.getOrPut(it.season) { FocusRequester() } } ?: requester
                                right = browser.seasons.getOrNull(index + 1)?.let { seasonRequesters.getOrPut(it.season) { FocusRequester() } } ?: requester
                                up = actionRequesters[0]
                                down = episodeRequesters[focusTargetId(browser, selectedSeason, rememberedVideoBySeason, rememberedIndexBySeason)] ?: requester
                            }
                            .onFocusChanged { if (it.isFocused) { focusedSeason = option.season; focusRegion = EpisodeFocusRegion.Seasons } }
                            .onKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> {
                                    selectedSeason = option.season
                                    pendingEpisodeFocus = true
                                    focusRegion = EpisodeFocusRegion.Episodes
                                    true
                                }
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    selectedSeason = option.season
                                    pendingEpisodeFocus = true
                                    focusRegion = EpisodeFocusRegion.Episodes
                                    true
                                }
                                    Key.DirectionUp -> { actionRequesters[0].requestFocus(); true }
                                    else -> false
                                }
                            }
                            .focusable()
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(option.label, color = TvColors.primaryText, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            itemsIndexed(visibleEpisodes, key = { _, episode -> episode.videoId }) { index, episode ->
                val requester = episodeRequesters.getOrPut(episode.videoId) { FocusRequester() }
                val sourcesRequester = sourceRequesters.getOrPut(episode.videoId) { FocusRequester() }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TvEpisodeRow(
                    episode = episode,
                    isCurrent = episode.videoId == browser.currentVideoId,
                    isContinue = episode.videoId == browser.continueWatchingVideoId,
                    modifier = Modifier.weight(1f)
                        .focusRequester(requester)
                        .focusProperties {
                            up = if (index > 0) episodeRequesters.getOrPut(visibleEpisodes[index - 1].videoId) { FocusRequester() }
                            else seasonRequesters[selectedSeason] ?: actionRequesters[0]
                            down = if (index < visibleEpisodes.lastIndex) episodeRequesters.getOrPut(visibleEpisodes[index + 1].videoId) { FocusRequester() } else requester
                            right = sourcesRequester
                        }
                        .onFocusChanged { focus ->
                            if (focus.isFocused) {
                                focusedEpisodeId = episode.videoId
                                focusRegion = EpisodeFocusRegion.Episodes
                                fallbackIndex = index
                                rememberedVideoBySeason[selectedSeason] = episode.videoId
                                rememberedIndexBySeason[selectedSeason] = index
                                scrollBySeason[selectedSeason] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                            }
                        }
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.DirectionRight -> { sourcesRequester.requestFocus(); true }
                                Key.DirectionUp -> if (index == 0) {
                                    (seasonRequesters[selectedSeason] ?: actionRequesters[0]).requestFocus(); true
                                } else {
                                    focusEpisodeAt(index - 1); true
                                }
                                Key.DirectionDown -> if (index < visibleEpisodes.lastIndex) {
                                    focusEpisodeAt(index + 1); true
                                } else true
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    if (!episode.upcoming) onEpisodeActivate(episode)
                                    true
                                }
                                else -> false
                            }
                        },
                )
                if (focusedEpisodeId == episode.videoId && !episode.upcoming) {
                    Button(
                        onClick = { onEpisodeSources(episode) },
                        modifier = Modifier.focusRequester(sourcesRequester)
                            .focusProperties { left = requester; right = requester; up = requester; down = requester },
                    ) { Text("Sources") }
                }
                }
            }
        }
    }
}

private enum class EpisodeFocusRegion { Actions, Seasons, Episodes }

private fun focusTargetId(
    browser: TvEpisodeBrowserUiState,
    season: Long,
    remembered: Map<Long, String>,
    indices: Map<Long, Int>,
): String? {
    val episodes = browser.episodesFor(season)
    return remembered[season]?.takeIf { id -> episodes.any { it.videoId == id } }
        ?: browser.currentVideoId?.takeIf { id -> episodes.any { it.videoId == id } }
        ?: browser.continueWatchingVideoId?.takeIf { id -> episodes.any { it.videoId == id } }
        ?: episodes.getOrNull(indices[season] ?: 0)?.videoId
}

@Composable
private fun TvEpisodeRow(
    episode: EpisodeOption,
    isCurrent: Boolean,
    isContinue: Boolean,
    modifier: Modifier = Modifier,
) {
    var focused by remember(episode.videoId) { mutableStateOf(false) }
    val series = episode.seriesInfo
    val number = series?.let { "S${it.season} E${it.episode}" }
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .fillMaxWidth()
            .background(
                if (focused) TvColors.surface else TvColors.surface.copy(alpha = 0.68f),
                RoundedCornerShape(10.dp),
            )
            .border(
                BorderStroke(if (focused) TvDimens.focusBorder else 1.dp, if (focused) TvColors.focus else Color.Transparent),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 130.dp, height = 73.dp)
                .clip(RoundedCornerShape(7.dp)).background(TvColors.artworkPanel),
            contentAlignment = Alignment.Center,
        ) {
            if (!episode.thumbnail.isNullOrBlank()) {
                AsyncImage(episode.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("EP", color = TvColors.disabled, style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(number, episode.title.takeIf(String::isNotBlank)).joinToString(" · ").ifBlank { "Episode" },
                    modifier = Modifier.weight(1f, fill = false),
                    color = TvColors.primaryText,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val statuses = buildList {
                    if (episode.upcoming) add("Upcoming")
                    if (episode.watched) add("✓ Watched")
                    if (isCurrent) add("Current")
                    if (isContinue) add("Continue")
                }
                if (statuses.isNotEmpty()) Text("  ${statuses.joinToString(" · ")}", color = TvColors.secondaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            episode.releaseDate?.let { Text(it, color = TvColors.secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            episode.overview?.takeIf(String::isNotBlank)?.let {
                Text(it, color = TvColors.secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val fraction = if (episode.watched) null else coreEpisodeProgressFraction(episode.progress)
                ?.takeIf { it > 0f && it < 1f }
            if (fraction != null) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(0.42f).height(3.dp),
                    color = TvColors.focus,
                    trackColor = TvColors.divider,
                )
            }
        }
    }
}
