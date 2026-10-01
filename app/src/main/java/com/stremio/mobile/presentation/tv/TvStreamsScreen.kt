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
) {
    val target = state.target ?: return
    val targetKey = target.semanticTargetKey
    val saved = focusMemory[targetKey] ?: TvStreamFocusMemory()
    val listState = rememberLazyListState(saved.firstVisibleIndex, saved.firstVisibleOffset)
    val scope = rememberCoroutineScope()
    val backRequester = remember(targetKey) { FocusRequester() }
    val validationRequester = remember(targetKey) { FocusRequester() }
    val incompatibleRequester = remember(targetKey) { FocusRequester() }
    val providerRequesters = remember(targetKey) { mutableMapOf<String, FocusRequester>() }
    val streamRequesters = remember(targetKey) { mutableMapOf<String, FocusRequester>() }
    val visible = state.visibleOptions
    var focusedKey by remember(targetKey) { mutableStateOf(saved.semanticKey) }
    var didRestoreFocus by remember(targetKey) { mutableStateOf(false) }
    val providers = state.providers.filter { it.readyStreamCount > 0 }
    val showFilter = providers.size > 1
    val filterIds = listOf<String?>(null) + providers.map { it.identity }

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
                modifier = Modifier.focusRequester(backRequester),
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

        if (BuildConfig.DEBUG) {
            OutlinedButton(
                onClick = onEnableValidationMedia,
                modifier = Modifier.focusRequester(validationRequester).padding(top = 8.dp)
                    .focusProperties { up = backRequester; down = incompatibleRequester },
            ) {
                Text("Use bundled validation media (A → B → C)")
            }
            val holdRequester = remember { FocusRequester() }
            OutlinedButton(
                onClick = onArmIncompatibleValidation,
                modifier = Modifier.focusRequester(incompatibleRequester).padding(top = 4.dp)
                    .focusProperties { up = validationRequester; down = holdRequester },
            ) {
                Text("DEBUG: force hardware-decoding recreation on B")
            }
            OutlinedButton(
                onClick = onArmHoldAfterFirstVisual,
                modifier = Modifier.focusRequester(holdRequester).padding(top = 4.dp)
                    .focusProperties { up = incompatibleRequester; down = providerRequesters.getOrPut("all") { FocusRequester() } },
            ) {
                Text("DEBUG: pause next fixture after first visual")
            }
        }

        if (showFilter) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                filterIds.forEachIndexed { index, id ->
                    val label = if (id == null) "All" else providers.firstOrNull { it.identity == id }?.title ?: "Addon"
                    val requester = providerRequesters.getOrPut(id ?: "all") { FocusRequester() }
                    val active = state.selectedProvider == id
                    val buttonModifier = Modifier.focusRequester(requester).focusProperties {
                        up = if (index == 0) incompatibleRequester else providerRequesters.getOrPut(filterIds[index - 1] ?: "all") { FocusRequester() }
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
                        else if (showFilter) providerRequesters.getOrPut(state.selectedProvider ?: "all") { FocusRequester() } else backRequester
                    val downRequester = visible.getOrNull(index + 1)?.let {
                        streamRequesters.getOrPut(it.semanticKey) { FocusRequester() }
                    } ?: requester
                    TvSourceRow(
                        option = option,
                        selected = state.selectedStreamKey == option.semanticKey,
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
                                            if (event.key == Key.DirectionUp && index == 0) {
                                                if (showFilter) providerRequesters[state.selectedProvider ?: "all"]?.requestFocus()
                                                    ?: backRequester.requestFocus()
                                            }
                                            true
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

@Composable
private fun TvSourceRow(option: StreamOption, selected: Boolean, modifier: Modifier = Modifier) {
    var focused by remember(option.semanticKey) { mutableStateOf(false) }
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
                option.quality?.let { Text(it.uppercase(), color = TvColors.accent, style = MaterialTheme.typography.labelLarge) }
                Text(
                    option.name,
                    Modifier.weight(1f).padding(start = if (option.quality == null) 0.dp else 10.dp),
                    color = TvColors.primaryText,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                listOfNotNull(option.addonTitle, option.size, option.seeds?.let { "$it seeds" }, option.origin)
                    .joinToString("  ·  "),
                color = TvColors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            option.cleanDescription?.takeIf(String::isNotBlank)?.let {
                Text(it, color = TvColors.secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected) {
            Spacer(Modifier.width(14.dp))
            Text("✓ Selected", color = TvColors.accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}
