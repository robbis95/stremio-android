package com.stremio.mobile.presentation.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.presentation.tv.components.TvPosterCard
import com.stremio.mobile.presentation.tv.focus.TvDiscoverFocusMemory
import com.stremio.mobile.presentation.tv.focus.TvFocusRegistry
import com.stremio.mobile.presentation.tv.focus.contentFocusKey
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvDimens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val DISCOVER_COLUMNS = 5

@Composable
internal fun TvDiscoverScreen(
    state: TvDiscoverUiState,
    memory: TvDiscoverFocusMemory,
    isActive: Boolean,
    restoreFocusRequestId: Int,
    navFocusRequester: FocusRequester,
    onSelectFilter: (ResourceRequest) -> Unit,
    onOpenDetails: (CatalogItem) -> Unit,
    onLoadNextPage: (String) -> Unit,
) {
    val items = state.shelf.items
    val itemKeys = remember(items) { items.map { contentFocusKey(it.type, it.id) } }
    val registry = remember { TvFocusRegistry() }
    val filterRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val gridState = rememberLazyGridState(memory.firstVisibleIndex, memory.firstVisibleOffset)
    val scope = rememberCoroutineScope()
    var focusedKey by remember { mutableStateOf<String?>(null) }

    fun filterRequester(groupKey: String, optionKey: String): FocusRequester =
        filterRequesters.getOrPut("$groupKey|$optionKey") { FocusRequester() }

    fun focusFilter(groupIndex: Int, optionIndex: Int) {
        val group = state.filterGroups.getOrNull(groupIndex) ?: return
        val option = group.options.getOrNull(optionIndex) ?: return
        memory.focusRegion = "filter"
        memory.filterGroupKey = group.key
        memory.filterOptionKey = option.key
        memory.filterOptionIndex = optionIndex
        filterRequester(group.key, option.key).requestFocus()
    }

    fun focusItem(index: Int) {
        val targetIndex = index.coerceIn(0, items.lastIndex.coerceAtLeast(0))
        val targetKey = itemKeys.getOrNull(targetIndex) ?: return
        scope.launch {
            val visible = gridState.layoutInfo.visibleItemsInfo.any { it.key == targetKey }
            if (!visible) gridState.scrollToItem(targetIndex)
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.key == targetKey } }.first { it }
            registry.requester("tv:discover:$targetKey").requestFocus()
        }
    }

    fun restoreContentOrFilter() {
        val previous = memory.contentKey
        val restoredKey = resolveDiscoverFocusKey(itemKeys, previous, memory.indexHint)
        if (memory.focusRegion == "content" && previous != null && restoredKey != null) {
            focusItem(itemKeys.indexOf(restoredKey))
            return
        }

        val rememberedGroupIndex = state.filterGroups.indexOfFirst { it.key == memory.filterGroupKey }
        val rememberedGroup = state.filterGroups.getOrNull(rememberedGroupIndex)
        val rememberedOptionIndex = rememberedGroup?.options?.indexOfFirst { it.key == memory.filterOptionKey } ?: -1
        if (rememberedOptionIndex >= 0) {
            focusFilter(rememberedGroupIndex, rememberedOptionIndex)
            return
        }
        val selectedGroup = state.filterGroups.indexOfFirst { group -> group.options.any { it.request == state.selectedRequest && it.selected } }
        val selectedOption = state.filterGroups.getOrNull(selectedGroup)?.options?.indexOfFirst {
            it.request == state.selectedRequest && it.selected
        } ?: -1
        if (selectedGroup >= 0 && selectedOption >= 0) {
            focusFilter(selectedGroup, selectedOption)
            return
        }
        if (state.filterGroups.isNotEmpty()) {
            focusFilter(0, 0)
        } else if (items.isNotEmpty()) {
            focusItem(0)
        }
    }

    fun focusGridFromFilter(optionIndex: Int) {
        if (items.isEmpty()) return
        val column = optionIndex.coerceIn(0, DISCOVER_COLUMNS - 1)
        focusItem(column.coerceAtMost(items.lastIndex))
    }

    SideEffect {
        registry.retain(itemKeys.map { "tv:discover:$it" }.toSet())
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                memory.firstVisibleIndex = index
                memory.firstVisibleOffset = offset
            }
    }

    LaunchedEffect(isActive, restoreFocusRequestId) {
        if (isActive && restoreFocusRequestId > 0) restoreContentOrFilter()
    }

    val selectionIdentity = state.selectedRequest?.toString()
    LaunchedEffect(selectionIdentity) {
        if (memory.paginationSelectionKey != selectionIdentity) {
            memory.paginationSelectionKey = selectionIdentity
            memory.lastPaginationTriggerIndex = -1
        }
    }

    val pageIdentity = state.nextPageRequest?.toString()
    LaunchedEffect(isActive, pageIdentity, selectionIdentity, itemKeys) {
        val identity = pageIdentity ?: return@LaunchedEffect
        if (!isActive || itemKeys.isEmpty() || state.resolvedRequest != state.selectedRequest) return@LaunchedEffect
        snapshotFlow {
            itemKeys.indexOf(memory.contentKey) to itemKeys.size
        }.distinctUntilChanged().collect { (focusedIndex, itemCount) ->
            val threshold = (itemCount - DISCOVER_COLUMNS).coerceAtLeast(0)
            if (focusedIndex >= threshold && focusedIndex > memory.lastPaginationTriggerIndex) {
                memory.lastPaginationTriggerIndex = focusedIndex
                onLoadNextPage(identity)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = TvDimens.safeHorizontal, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(state.title, style = MaterialTheme.typography.titleLarge, color = TvColors.primaryText, maxLines = 1)

        state.filterGroups.forEachIndexed { groupIndex, group ->
            Column {
                Text(
                    group.label,
                    color = TvColors.secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    group.options.forEachIndexed { optionIndex, option ->
                        val requester = filterRequester(group.key, option.key)
                        val left = group.options.getOrNull(optionIndex - 1)?.let { filterRequester(group.key, it.key) } ?: requester
                        val right = group.options.getOrNull(optionIndex + 1)?.let { filterRequester(group.key, it.key) } ?: requester
                        val upperGroup = state.filterGroups.getOrNull(groupIndex - 1)
                        val lowerGroup = state.filterGroups.getOrNull(groupIndex + 1)
                        val up = upperGroup?.let { upper ->
                            val target = optionIndex.coerceAtMost(upper.options.lastIndex)
                            upper.options.getOrNull(target)?.let { filterRequester(upper.key, it.key) }
                        } ?: if (groupIndex == 0) navFocusRequester else requester
                        val down = lowerGroup?.let { lower ->
                            val target = optionIndex.coerceAtMost(lower.options.lastIndex)
                            lower.options.getOrNull(target)?.let { filterRequester(lower.key, it.key) }
                        } ?: requester
                        Button(
                            onClick = {
                                memory.focusRegion = "filter"
                                memory.filterGroupKey = group.key
                                memory.filterOptionKey = option.key
                                memory.filterOptionIndex = optionIndex
                                onSelectFilter(option.request)
                            },
                            modifier = Modifier.height(34.dp)
                                .focusRequester(requester)
                                .focusProperties {
                                    canFocus = isActive
                                    this.left = left
                                    this.right = right
                                    this.up = up
                                    this.down = if (lowerGroup == null) {
                                        itemKeys.firstOrNull()?.let { registry.requester("tv:discover:$it") } ?: requester
                                    } else down
                                }
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && lowerGroup == null) {
                                        focusGridFromFilter(optionIndex)
                                        items.isNotEmpty()
                                    } else false
                                },
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(TvDimens.controlRadius)),
                            colors = ButtonDefaults.colors(
                                containerColor = if (option.selected) TvColors.focusSoft else TvColors.surface,
                                contentColor = if (option.selected) TvColors.focus else TvColors.primaryText,
                                focusedContainerColor = TvColors.focus,
                                focusedContentColor = TvColors.elevatedSurface,
                            ),
                        ) {
                            Text(option.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (items.isEmpty()) {
                when {
                    state.shelf.error != null -> Text(
                        state.shelf.error,
                        Modifier.align(Alignment.TopStart).padding(top = 14.dp),
                        color = TvColors.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.shelf.isLoading -> Row(
                        Modifier.align(Alignment.TopStart).padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = TvColors.accent,
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Loading catalog…", color = TvColors.secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(DISCOVER_COLUMNS),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(items, key = { index, _ -> itemKeys[index] }, contentType = { _, _ -> "discover-poster" }) { index, item ->
                        val key = itemKeys[index]
                        val requester = registry.requester("tv:discover:$key")
                        val row = index / DISCOVER_COLUMNS
                        val col = index % DISCOVER_COLUMNS
                        val leftIndex = if (col > 0) index - 1 else index
                        val rightIndex = if (col < DISCOVER_COLUMNS - 1 && index + 1 < items.size && (index + 1) / DISCOVER_COLUMNS == row) index + 1 else index
                        val left = registry.requester("tv:discover:${itemKeys[leftIndex]}")
                        val right = registry.requester("tv:discover:${itemKeys[rightIndex]}")
                        TvPosterCard(
                            item = item,
                            isFocused = focusedKey == key,
                            enabled = isActive,
                            requester = requester,
                            left = left,
                            right = right,
                            onFocus = {
                                focusedKey = key
                                memory.focusRegion = "content"
                                memory.contentKey = key
                                memory.indexHint = index
                            },
                            onFocusLost = { if (focusedKey == key) focusedKey = null },
                            onUp = {
                                if (row > 0) {
                                    discoverGridVerticalTarget(index, items.size, DISCOVER_COLUMNS, -1)?.let(::focusItem)
                                } else if (state.filterGroups.isNotEmpty()) {
                                    val groupIndex = state.filterGroups.lastIndex
                                    val group = state.filterGroups[groupIndex]
                                    focusFilter(groupIndex, col.coerceAtMost(group.options.lastIndex))
                                } else navFocusRequester.requestFocus()
                            },
                            onDown = {
                                discoverGridVerticalTarget(index, items.size, DISCOVER_COLUMNS, 1)?.let(::focusItem)
                            },
                            onActivate = {
                                memory.contentKey = key
                                memory.indexHint = index
                                memory.focusRegion = "content"
                                onOpenDetails(item)
                            },
                        )
                    }
                }
                if (state.shelf.isLoading) {
                    Text(
                        "Loading more…",
                        Modifier.align(Alignment.TopEnd).background(TvColors.background.copy(alpha = 0.92f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                        color = TvColors.secondaryText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (state.shelf.error != null) {
                    Text(
                        state.shelf.error,
                        Modifier.align(Alignment.BottomStart).background(TvColors.background.copy(alpha = 0.94f), RoundedCornerShape(8.dp)).padding(6.dp),
                        color = TvColors.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
