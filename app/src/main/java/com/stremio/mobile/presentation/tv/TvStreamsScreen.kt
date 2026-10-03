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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.stremio.mobile.BuildConfig
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.presentation.tv.theme.TvColors
import kotlinx.coroutines.launch

@Composable
internal fun TvStreamsScreen(
    state: TvStreamSelectionUiState,
    focusMemory: MutableMap<String, TvStreamFocusMemory>,
    onBack: () -> Unit,
    onSelectProvider: (String?) -> Unit,
    onSelectStream: (String) -> Unit,
    onFocusChanged: (String, TvStreamFocusMemory) -> Unit,
    onEnableValidationMedia: () -> Unit = {},
    onArmIncompatibleValidation: () -> Unit = {},
    onArmHoldAfterFirstVisual: () -> Unit = {},
    onArmMpvRequestedEngine: () -> Unit = {},
    onSmartPlayFixture: () -> Unit = {},
) {
    val target = state.target ?: return
    val targetKey = target.semanticTargetKey
    val saved = focusMemory[targetKey] ?: TvStreamFocusMemory()
    val listState = rememberLazyListState(saved.firstVisibleIndex, saved.firstVisibleOffset)
    val scope = rememberCoroutineScope()
    val backRequester = remember(targetKey) { FocusRequester() }
    val validationRequester = remember(targetKey) { FocusRequester() }
    val incompatibleRequester = remember(targetKey) { FocusRequester() }
    val holdRequester = remember { FocusRequester() }
    val mpvRequester = remember(targetKey) { FocusRequester() }
    val smartFixtureRequester = remember(targetKey) { FocusRequester() }
    val providerRequesters = remember(targetKey) { mutableMapOf<String, FocusRequester>() }
    val streamRequesters = remember(targetKey) { mutableMapOf<String, FocusRequester>() }
    val visible = state.visibleOptions
    var focusedKey by remember(targetKey) { mutableStateOf(saved.semanticKey) }
    var didRestoreFocus by remember(targetKey) { mutableStateOf(false) }
    val providers = state.providers.filter { it.readyStreamCount > 0 }
    val showFilter = providers.size > 1
    val filterIds = listOf<String?>(null) + providers.map { it.identity }
    val debugActions = if (BuildConfig.DEBUG) {
        listOf(
            TvStreamsDebugAction(validationRequester, "Use bundled validation media (A → B → C)", onEnableValidationMedia),
            TvStreamsDebugAction(incompatibleRequester, "DEBUG: force hardware-decoding recreation on B", onArmIncompatibleValidation),
            TvStreamsDebugAction(holdRequester, "DEBUG: pause next fixture after first visual", onArmHoldAfterFirstVisual),
            TvStreamsDebugAction(mpvRequester, "DEBUG: request MPV for fixture B", onArmMpvRequestedEngine),
            TvStreamsDebugAction(smartFixtureRequester, "DEBUG: Smart Fallback A → B → C fixture", onSmartPlayFixture),
        )
    } else {
        emptyList()
    }
    val focusPolicy = tvStreamsFocusPolicy(
        debugActionsRendered = debugActions.isNotEmpty(),
        providerFilterRendered = showFilter,
        streamRendered = visible.isNotEmpty(),
    )
    val selectedProviderRequester = if (showFilter) {
        providerRequesters.getOrPut(state.selectedProvider ?: "all") { FocusRequester() }
    } else {
        null
    }
    val firstStreamRequester = visible.firstOrNull()?.let { option ->
        streamRequesters.getOrPut(option.semanticKey) { FocusRequester() }
    }
    val lastDebugRequester = debugActions.lastOrNull()?.requester

    fun requesterFor(target: TvStreamsFocusTarget): FocusRequester = when (target) {
        TvStreamsFocusTarget.Back -> backRequester
        TvStreamsFocusTarget.FirstDebugAction -> debugActions.firstOrNull()?.requester ?: backRequester
        TvStreamsFocusTarget.LastDebugAction -> lastDebugRequester ?: backRequester
        TvStreamsFocusTarget.ProviderFilter -> selectedProviderRequester ?: backRequester
        TvStreamsFocusTarget.FirstStream -> firstStreamRequester ?: backRequester
    }

    LaunchedEffect(targetKey, visible.map { it.semanticKey }) {
        val savedKey = saved.semanticKey
        if (visible.isNotEmpty()) {
            if (!didRestoreFocus) {
                didRestoreFocus = true
                if (savedKey != null) {
                    val index = restoredStreamIndex(visible, savedKey, saved.fallbackIndex) ?: return@LaunchedEffect
                    listState.scrollToItem(index)
                    kotlinx.coroutines.yield()
                    val key = visible[index].semanticKey
                    focusedKey = key
                    streamRequesters.getOrPut(key) { FocusRequester() }.requestFocus()
                } else {
                    val index = restoredStreamIndex(visible, null, saved.fallbackIndex) ?: return@LaunchedEffect
                    listState.scrollToItem(index)
                    kotlinx.coroutines.yield()
                    val key = visible[index].semanticKey
                    focusedKey = key
                    streamRequesters.getOrPut(key) { FocusRequester() }.requestFocus()
                }
            } else if (focusedKey != null && visible.none { it.semanticKey == focusedKey }) {
                val fallback = restoredStreamIndex(visible, focusedKey, saved.fallbackIndex) ?: return@LaunchedEffect
                listState.scrollToItem(fallback)
                kotlinx.coroutines.yield()
                val key = visible[fallback].semanticKey
                focusedKey = key
                streamRequesters.getOrPut(key) { FocusRequester() }.requestFocus()
            }
        }
    }
    LaunchedEffect(targetKey, state.selectedProvider, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, focusedKey) {
        onFocusChanged(
            targetKey,
            TvStreamFocusMemory(
                semanticKey = focusedKey,
                fallbackIndex = focusedKey?.let { key -> visible.indexOfFirst { it.semanticKey == key }.takeIf { it >= 0 } } ?: saved.fallbackIndex,
                firstVisibleIndex = listState.firstVisibleItemIndex,
                firstVisibleOffset = listState.firstVisibleItemScrollOffset,
            ),
        )
    }
    LaunchedEffect(targetKey) {
        kotlinx.coroutines.yield()
        runCatching { backRequester.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(TvColors.background).padding(horizontal = 56.dp, vertical = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.focusRequester(backRequester)
                    .focusProperties { down = requesterFor(focusPolicy.backDown) },
            ) { Text("Back") }
            Column(Modifier.weight(1f).padding(start = 18.dp)) {
                Text("Choose Source", style = MaterialTheme.typography.headlineSmall, color = TvColors.primaryText)
                Text(
                    buildString {
                        append(target.contentName)
                        target.episodeLabel?.let { append("  ·  ").append(it) }
                        target.releaseDate?.let { append("  ·  ").append(it) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvColors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        debugActions.forEachIndexed { index, action ->
            val upRequester = debugActions.getOrNull(index - 1)?.requester ?: backRequester
            val downRequester = debugActions.getOrNull(index + 1)?.requester
                ?: requesterFor(focusPolicy.lastDebugActionDown)
            OutlinedButton(
                onClick = action.onClick,
                modifier = Modifier.focusRequester(action.requester)
                    .padding(top = if (index == 0) 8.dp else 4.dp)
                    .focusProperties { up = upRequester; down = downRequester },
            ) {
                Text(action.label)
            }
        }

        if (showFilter) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                filterIds.forEachIndexed { index, id ->
                    val label = if (id == null) "All" else providers.firstOrNull { it.identity == id }?.title ?: "Addon"
                    val requester = providerRequesters.getOrPut(id ?: "all") { FocusRequester() }
                    val upRequester = if (index == 0) requesterFor(focusPolicy.providerFilterUp)
                        else providerRequesters.getOrPut(filterIds[index - 1] ?: "all") { FocusRequester() }
                    val active = state.selectedProvider == id
                    val buttonModifier = Modifier.focusRequester(requester).focusProperties {
                        up = upRequester
                        left = if (index == 0) backRequester else providerRequesters.getOrPut(filterIds[index - 1] ?: "all") { FocusRequester() }
                        right = providerRequesters.getOrPut(filterIds.getOrNull(index + 1) ?: id ?: "all") { FocusRequester() }
                        down = streamRequesters[focusedKey] ?: backRequester
                    }.onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && visible.isNotEmpty()) {
                            val restoreIndex = restoredStreamIndex(visible, focusedKey, saved.fallbackIndex) ?: 0
                            val key = visible[restoreIndex].semanticKey
                            scope.launch {
                                listState.animateScrollToItem(restoreIndex)
                                kotlinx.coroutines.yield()
                                streamRequesters.getOrPut(key) { FocusRequester() }.requestFocus()
                            }
                            true
                        } else false
                    }
                    if (active) Button(onClick = { onSelectProvider(id) }, modifier = buttonModifier) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else OutlinedButton(onClick = { onSelectProvider(id) }, modifier = buttonModifier) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (visible.isNotEmpty()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                itemsIndexed(visible, key = { _, option -> option.semanticKey }) { index, option ->
                    val requester = streamRequesters.getOrPut(option.semanticKey) { FocusRequester() }
                    val upRequester = if (index > 0) streamRequesters.getOrPut(visible[index - 1].semanticKey) { FocusRequester() }
                        else requesterFor(focusPolicy.firstStreamUp)
                    val downRequester = visible.getOrNull(index + 1)?.let {
                        streamRequesters.getOrPut(it.semanticKey) { FocusRequester() }
                    } ?: requester
                    TvSourceRow(
                        option = option,
                        selected = state.selectedStreamKey == option.semanticKey,
                        recommended = state.recommendedStreamKey == option.semanticKey,
                        modifier = Modifier
                            .focusRequester(requester)
                            .focusProperties { up = upRequester; down = downRequester }
                            .onFocusChanged { focus ->
                                if (focus.isFocused) {
                                    focusedKey = option.semanticKey
                                    onFocusChanged(targetKey, TvStreamFocusMemory(
                                        semanticKey = option.semanticKey,
                                        fallbackIndex = index,
                                        firstVisibleIndex = listState.firstVisibleItemIndex,
                                        firstVisibleOffset = listState.firstVisibleItemScrollOffset,
                                    ))
                                }
                            }
                            .onKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (event.key) {
                                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                        onSelectStream(option.semanticKey)
                                        true
                                    }
                                    Key.DirectionUp, Key.DirectionDown -> {
                                        val nextIndex = if (event.key == Key.DirectionUp) index - 1 else index + 1
                                        if (nextIndex !in visible.indices) {
                                            if (event.key == Key.DirectionUp && index == 0) false else true
                                        } else {
                                            val nextKey = visible[nextIndex].semanticKey
                                            scope.launch {
                                                listState.animateScrollToItem(nextIndex)
                                                kotlinx.coroutines.yield()
                                                streamRequesters.getOrPut(nextKey) { FocusRequester() }.requestFocus()
                                            }
                                            true
                                        }
                                    }
                                    else -> false
                                }
                            },
                    )
                }
            }
        } else if (state.requestError != null || state.allProvidersFailed) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
                Text("Sources could not be loaded. Press Back to return.", color = TvColors.secondaryText, style = MaterialTheme.typography.titleMedium)
            }
        } else if (state.isLoading || state.pendingProviders > 0) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
                Text("Finding sources…", color = TvColors.secondaryText, style = MaterialTheme.typography.titleMedium)
            }
        } else if (state.allProvidersComplete || state.providers.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
                Text("No sources found from your installed addons.", color = TvColors.secondaryText, style = MaterialTheme.typography.titleMedium)
            }
        }
        if (state.pendingProviders > 0 && visible.isNotEmpty()) {
            Text("Finding more sources…", color = TvColors.secondaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class TvStreamsDebugAction(
    val requester: FocusRequester,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
private fun TvSourceRow(option: StreamOption, selected: Boolean, recommended: Boolean, modifier: Modifier = Modifier) {
    var focused by remember(option.semanticKey) { mutableStateOf(false) }
    val metadata = remember(option) { parseStreamVideoMetadata(option) }
    val title = safeStreamPresentationText(option.name)
    val technical = listOfNotNull(
        metadata.quality.takeUnless { it == "unknown" }?.uppercase(),
        metadata.release.toPresentationLabel(),
        metadata.codec.toPresentationLabel(),
        metadata.hdr.toPresentationLabel(),
    ).distinct().joinToString("  ·  ")
    val audio = listOfNotNull(
        metadata.audioFeatures.takeIf { it.isNotEmpty() }?.joinToString(" "),
        metadata.audioCodec,
        metadata.channels,
    ).distinct().joinToString("  ·  ")
    val details = listOfNotNull(
        audio.takeIf(String::isNotBlank),
        metadata.languages.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Audio "),
        metadata.subtitleLanguages.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Subs "),
        metadata.sizeLabel,
        metadata.bitrateMbps?.let { "${if (metadata.bitrateCalculatedFromSizeAndDuration) "~" else ""}%.2f Mbps".format(java.util.Locale.ROOT, it) },
        metadata.age,
    ).joinToString("  ·  ")
    val sourceLabel = listOfNotNull(
        option.addonTitle.takeIf(String::isNotBlank),
        metadata.releaseLabel,
        option.seeds?.let { "$it seeds" },
        option.origin?.takeIf(String::isNotBlank)?.let(::safeStreamPresentationText),
    ).joinToString(" · ")
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .fillMaxWidth()
            .background(TvColors.surface, shape)
            .border(
                BorderStroke(if (focused) 2.dp else if (selected) 1.dp else 1.dp, when {
                    focused -> TvColors.focus
                    selected -> TvColors.accent
                    else -> TvColors.divider
                }),
                shape,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                technical.takeIf(String::isNotBlank)?.let { Text(it, color = TvColors.accent, style = MaterialTheme.typography.labelLarge) }
                Text(
                    title,
                    Modifier.weight(1f).padding(start = if (technical.isBlank()) 0.dp else 10.dp),
                    color = TvColors.primaryText,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (details.isNotBlank()) Text(details, color = TvColors.secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sourceLabel, color = TvColors.secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) {
            Spacer(Modifier.width(14.dp))
            Text("✓ Selected", color = TvColors.accent, style = MaterialTheme.typography.labelLarge)
        }
        if (recommended) {
            Spacer(Modifier.width(14.dp))
            Text("Recommended", color = TvColors.accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun ReleaseKind.toPresentationLabel(): String? = when (this) {
    ReleaseKind.Remux -> "REMUX"
    ReleaseKind.WebDl -> "WEB-DL"
    ReleaseKind.WebRip -> "WEBRip"
    ReleaseKind.BluRay -> "BluRay"
    ReleaseKind.Unknown -> null
}

private fun VideoCodec.toPresentationLabel(): String? = when (this) {
    VideoCodec.Avc -> "AVC"
    VideoCodec.Hevc -> "HEVC"
    VideoCodec.Av1 -> "AV1"
    VideoCodec.Vp9 -> "VP9"
    VideoCodec.Unknown -> null
}

private fun VideoHdr.toPresentationLabel(): String? = when (this) {
    VideoHdr.DolbyVision -> "DV"
    VideoHdr.Hdr10Plus -> "HDR10+"
    VideoHdr.Hdr10 -> "HDR10"
    VideoHdr.Hlg -> "HLG"
    VideoHdr.Unknown -> null
}
