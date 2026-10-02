package com.stremio.mobile.presentation.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.stremio.mobile.data.model.MetaDetails
import com.stremio.mobile.data.model.EpisodeOption
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvDimens

@Composable
internal fun TvDetailsScreen(
    state: TvDetailsUiState,
    episodeFocusRestoreVideoId: String?,
    episodeFocusRestoreId: Int,
    onLibraryAction: () -> Unit,
    onPlay: () -> Unit,
    onChooseSource: () -> Unit,
    onEpisodeActivate: (EpisodeOption) -> Unit,
    onEpisodeSources: (EpisodeOption) -> Unit,
    onBack: () -> Unit,
) {
    val details = state.details
    val item = details?.item
    val hasSourceAction = item != null && details?.isLoading == false && state.episodeBrowser == null
    val mode = item?.let(::classifyDetailsArtwork) ?: TvDetailsArtworkMode.TextOnly
    val libraryRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val sourceRequester = remember { FocusRequester() }
    val backRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    LaunchedEffect(hasSourceAction, item?.id, state.episodeBrowser != null) {
        runCatching {
            if (hasSourceAction) playRequester.requestFocus() else libraryRequester.requestFocus()
        }
            .onFailure { runCatching { backRequester.requestFocus() } }
    }

    Box(Modifier.fillMaxSize().background(TvColors.background)) {
        when (mode) {
            TvDetailsArtworkMode.RichBackdrop, TvDetailsArtworkMode.LandscapeArtwork -> {
                val artUrl = if (mode == TvDetailsArtworkMode.RichBackdrop) item?.background else item?.poster
                AsyncImage(
                    model = artUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0f to TvColors.background,
                            0.34f to TvColors.background.copy(alpha = 0.98f),
                            0.56f to TvColors.background.copy(alpha = 0.84f),
                            0.76f to TvColors.background.copy(alpha = 0.35f),
                            1f to TvColors.background.copy(alpha = 0.04f),
                        ),
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to TvColors.background.copy(alpha = 0.10f),
                            0.64f to TvColors.background.copy(alpha = 0f),
                            1f to TvColors.background.copy(alpha = 0.54f),
                        ),
                    ),
                )
            }
            TvDetailsArtworkMode.ContainedPoster -> {
                Box(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(330.dp)
                        .padding(end = 66.dp, top = 36.dp, bottom = 36.dp)
                        .background(TvColors.artworkPanel, RoundedCornerShape(TvDimens.cardRadius)),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = item?.poster,
                        contentDescription = item?.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                    )
                }
            }
            TvDetailsArtworkMode.TextOnly -> Unit
        }

        if (item != null && state.episodeBrowser != null) {
            TvEpisodeBrowserContent(
                item = item,
                state = state,
                browser = state.episodeBrowser,
                focusRestoreVideoId = episodeFocusRestoreVideoId,
                focusRestoreId = episodeFocusRestoreId,
                libraryRequester = libraryRequester,
                backRequester = backRequester,
                onLibraryAction = onLibraryAction,
                onEpisodeActivate = onEpisodeActivate,
                onEpisodeSources = onEpisodeSources,
                onBack = onBack,
            )
        } else Column(
            Modifier.fillMaxSize().verticalScroll(scrollState)
                .padding(start = 72.dp, end = 72.dp, top = 38.dp, bottom = 30.dp)
                .fillMaxWidth(0.66f),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (item != null && !item.logo.isNullOrBlank()) {
                AsyncImage(
                    model = item.logo,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.width(380.dp).height(68.dp),
                )
            }
            // Keep the factual title as a fallback while an optional logo loads or fails.
            Text(
                text = item?.name ?: "Details",
                style = if (item?.logo.isNullOrBlank()) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleMedium,
                color = if (item?.logo.isNullOrBlank()) TvColors.primaryText else TvColors.secondaryText,
                maxLines = if (item?.logo.isNullOrBlank()) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )

            val metadata = details?.let(::detailsMetadataLine).orEmpty()
            if (metadata.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(metadata, color = TvColors.secondaryText, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (!details?.description.isNullOrBlank()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = details.description.orEmpty(),
                    color = TvColors.primaryText,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasSourceAction) {
                    Button(
                        onClick = onPlay,
                        modifier = Modifier.focusRequester(playRequester)
                            .focusProperties { left = backRequester; right = sourceRequester; up = playRequester; down = playRequester },
                    ) { Text("Play") }
                    Button(
                        onClick = onChooseSource,
                        modifier = Modifier.focusRequester(sourceRequester)
                            .focusProperties { left = playRequester; right = libraryRequester; up = sourceRequester; down = sourceRequester },
                    ) { Text("Choose Source") }
                }
                Button(
                    onClick = onLibraryAction,
                    enabled = !state.isLibraryActionLoading,
                    modifier = Modifier.focusRequester(libraryRequester)
                        .focusProperties { left = if (hasSourceAction) sourceRequester else backRequester; right = backRequester; up = libraryRequester; down = libraryRequester },
                ) {
                    Text(if (state.isInLibrary) "✓  In Library" else "+  Add to Library")
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.focusRequester(backRequester)
                        .focusProperties { left = libraryRequester; right = if (hasSourceAction) playRequester else libraryRequester; up = backRequester; down = backRequester },
                ) {
                    Text("Back")
                }
                if (state.isLibraryActionLoading) {
                    Text("Updating…", color = TvColors.secondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (details?.isLoading == true) {
                Spacer(Modifier.height(10.dp))
                Text("Loading additional details…", color = TvColors.secondaryText, style = MaterialTheme.typography.bodySmall)
            } else if (!details?.error.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Additional details could not be loaded.",
                    color = TvColors.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            val genres = details?.genres.orEmpty()
            if (genres.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(genres.distinct().joinToString("  ·  "), color = TvColors.secondaryText,
                    style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val credits = buildList {
                details?.director.orEmpty().takeIf { it.isNotEmpty() }?.let { add("Director: ${it.joinToString(", ")}") }
                details?.cast.orEmpty().takeIf { it.isNotEmpty() }?.let { add("Cast: ${it.joinToString(", ")}") }
            }
            credits.forEach { credit ->
                Spacer(Modifier.height(8.dp))
                Text(credit, color = TvColors.secondaryText, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
