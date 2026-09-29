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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.presentation.tv.components.TvPosterCard
import com.stremio.mobile.presentation.tv.focus.TvFocusRegistry
import com.stremio.mobile.presentation.tv.focus.TvLibraryFocusMemory
import com.stremio.mobile.presentation.tv.focus.contentFocusKey
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvDimens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val LIBRARY_COLUMNS = 5

@Composable
internal fun TvLibraryScreen(
    state: TvLibraryUiState,
    memory: TvLibraryFocusMemory,
    isActive: Boolean,
    restoreFocusRequestId: Int,
    navFocusRequester: FocusRequester,
    onSelectFilter: (com.stremio.core.models.LibraryWithFilters.LibraryRequest) -> Unit,
    onOpenDetails: (CatalogItem) -> Unit,
    onLoadNextPage: (String) -> Unit,
) {
    val items = state.items
    val itemKeys = remember(items) { items.map { contentFocusKey(it.type, it.id) } }
    val latestItemKeys by rememberUpdatedState(itemKeys)
    val registry = remember { TvFocusRegistry() }
    val filterRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val gridState = rememberLazyGridState(memory.firstVisibleIndex, memory.firstVisibleOffset)
    val scope = rememberCoroutineScope()
    var focusedKey by remember { mutableStateOf<String?>(null) }

    fun filterRequester(groupKey: String, optionKey: String) =
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
            if (gridState.layoutInfo.visibleItemsInfo.none { it.key == targetKey }) {
                gridState.scrollToItem(targetIndex)
            }
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.key == targetKey } }.first { it }
            registry.requester("tv:library:$targetKey").requestFocus()
        }
    }

    fun restoreFocus() {
        val restoredKey = resolveLibraryFocusKey(itemKeys, memory.contentKey, memory.indexHint)
        if (memory.focusRegion == "content" && restoredKey != null) {
            focusItem(itemKeys.indexOf(restoredKey))
            return
        }
        val groupIndex = state.filterGroups.indexOfFirst { it.key == memory.filterGroupKey }
        val group = state.filterGroups.getOrNull(groupIndex)
        val optionIndex = group?.options?.indexOfFirst { it.key == memory.filterOptionKey } ?: -1
        if (groupIndex >= 0 && optionIndex >= 0) {
            focusFilter(groupIndex, optionIndex)
            return
        }
        val selectedGroup = state.filterGroups.indexOfFirst { group -> group.options.any { it.selected } }
        val selectedOption = state.filterGroups.getOrNull(selectedGroup)?.options?.indexOfFirst { it.selected } ?: -1
        if (selectedGroup >= 0 && selectedOption >= 0) {
            focusFilter(selectedGroup, selectedOption)
            return
        }
        when {
            state.filterGroups.isNotEmpty() -> focusFilter(0, 0)
            itemKeys.isNotEmpty() -> focusItem(0)
            else -> navFocusRequester.requestFocus()
        }
    }

    fun focusGridFromFilter(optionIndex: Int) {
        if (items.isEmpty()) return
        val column = optionIndex.coerceIn(0, LIBRARY_COLUMNS - 1)
        focusItem(column.coerceAtMost(items.lastIndex))
    }

    SideEffect {
        registry.retain(itemKeys.map { "tv:library:$it" }.toSet())
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
        if (isActive) restoreFocus()
    }

    // If a sync removes the focused item, deterministically move to the nearest surviving index.
    LaunchedEffect(isActive, itemKeys, memory.contentKey, memory.indexHint) {
        if (!isActive || memory.focusRegion != "content" || memory.contentKey == null) return@LaunchedEffect
        if (memory.contentKey !in itemKeys) {
            val fallback = resolveLibraryFocusKey(itemKeys, memory.contentKey, memory.indexHint)
            if (fallback != null) focusItem(itemKeys.indexOf(fallback))
            else {
                val selectedGroup = state.filterGroups.indexOfFirst { group -> group.options.any { it.selected } }
                val selectedOption = state.filterGroups.getOrNull(selectedGroup)?.options?.indexOfFirst { it.selected } ?: -1
                if (selectedGroup >= 0 && selectedOption >= 0) focusFilter(selectedGroup, selectedOption)
                else if (state.filterGroups.isNotEmpty()) focusFilter(0, 0)
                else navFocusRequester.requestFocus()
            }
        }
    }

    val selectionKey = state.logicalSelection?.let { "${it.type.orEmpty()}|${it.sort.name}" }
    LaunchedEffect(selectionKey) {
        if (memory.paginationSelectionKey != selectionKey) {
            memory.paginationSelectionKey = selectionKey
            memory.lastPaginationTriggerIndex = -1
        }
    }
    val pageIdentity = state.nextPageRequest?.toString()
    LaunchedEffect(isActive, selectionKey, pageIdentity) {
        val identity = pageIdentity ?: return@LaunchedEffect
        if (!isActive) return@LaunchedEffect
        snapshotFlow { memory.contentKey }.distinctUntilChanged().collect { key ->
            val focusedIndex = latestItemKeys.indexOf(key)
            libraryPageTriggerIndex(
                focusedIndex = focusedIndex,
                itemCount = latestItemKeys.size,
                columns = LIBRARY_COLUMNS,
                lastTriggerIndex = memory.lastPaginationTriggerIndex,
            )?.let { triggerIndex ->
                memory.lastPaginationTriggerIndex = triggerIndex
                onLoadNextPage(identity)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = TvDimens.safeHorizontal, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("Library", style = MaterialTheme.typography.titleLarge, color = TvColors.primaryText, maxLines = 1)

        state.filterGroups.forEachIndexed { groupIndex, group ->
            Column {
                Text(group.label, color = TvColors.secondaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    group.options.forEachIndexed { optionIndex, option ->
                        val requester = filterRequester(group.key, option.key)
                        val previousGroup = state.filterGroups.getOrNull(groupIndex - 1)
                        val nextGroup = state.filterGroups.getOrNull(groupIndex + 1)
                        val left = group.options.getOrNull(optionIndex - 1)?.let { filterRequester(group.key, it.key) } ?: requester
                        val right = group.options.getOrNull(optionIndex + 1)?.let { filterRequester(group.key, it.key) } ?: requester
                        val up = previousGroup?.options?.getOrNull(optionIndex.coerceAtMost(previousGroup.options.lastIndex))
                            ?.let { filterRequester(previousGroup.key, it.key) } ?: if (groupIndex == 0) navFocusRequester else requester
                        val down = nextGroup?.options?.getOrNull(optionIndex.coerceAtMost(nextGroup.options.lastIndex))
                            ?.let { filterRequester(nextGroup.key, it.key) }
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
                                    this.down = down ?: itemKeys.firstOrNull()?.let { registry.requester("tv:library:$it") } ?: requester
                                }
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && down == null) {
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
            when {
                items.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Fixed(LIBRARY_COLUMNS),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(items, key = { index, _ -> itemKeys[index] }, contentType = { _, _ -> "library-poster" }) { index, item ->
                        val key = itemKeys[index]
                        val requester = registry.requester("tv:library:$key")
                        val row = index / LIBRARY_COLUMNS
                        val col = index % LIBRARY_COLUMNS
                        val leftKey = itemKeys[if (col > 0) index - 1 else index]
                        val rightIndex = if (col < LIBRARY_COLUMNS - 1 && index + 1 < items.size && (index + 1) / LIBRARY_COLUMNS == row) index + 1 else index
                        TvPosterCard(
                            item = item,
                            isFocused = focusedKey == key,
                            enabled = isActive,
                            requester = requester,
                            left = registry.requester("tv:library:$leftKey"),
                            right = registry.requester("tv:library:${itemKeys[rightIndex]}"),
                            showLibraryStatus = true,
                            onFocus = {
                                focusedKey = key
                                memory.focusRegion = "content"
                                memory.contentKey = key
                                memory.indexHint = index
                            },
                            onFocusLost = { if (focusedKey == key) focusedKey = null },
                            onUp = {
                                val target = libraryGridVerticalTarget(index, items.size, LIBRARY_COLUMNS, -1)
                                if (target != null) focusItem(target)
                                else if (state.filterGroups.isNotEmpty()) {
                                    val lastGroup = state.filterGroups.lastIndex
                                    focusFilter(lastGroup, col.coerceAtMost(state.filterGroups[lastGroup].options.lastIndex))
                                } else navFocusRequester.requestFocus()
                            },
                            onDown = { libraryGridVerticalTarget(index, items.size, LIBRARY_COLUMNS, 1)?.let(::focusItem) },
                            onActivate = {
                                memory.contentKey = key
                                memory.indexHint = index
                                memory.focusRegion = "content"
                                onOpenDetails(item)
                            },
                        )
                    }
                }
                state.isLoading -> Row(
                    Modifier.align(Alignment.TopStart).padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = TvColors.accent,
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("Loading library…", color = TvColors.secondaryText, style = MaterialTheme.typography.bodySmall)
                }
                else -> Text(
                    "No items in your library",
                    Modifier.align(Alignment.TopStart).padding(top = 14.dp),
                    color = TvColors.secondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (items.isNotEmpty() && state.isLoading) {
                Spacer(
                    Modifier.align(Alignment.TopEnd).fillMaxWidth().height(1.dp)
                        .background(TvColors.divider.copy(alpha = 0.5f)),
                )
            }
        }
    }
}

private fun libraryGridVerticalTarget(index: Int, itemCount: Int, columns: Int, direction: Int): Int? {
    if (index !in 0 until itemCount || direction !in -1..1 || direction == 0) return null
    val sourceColumn = index % columns
    val targetRow = index / columns + direction
    if (targetRow < 0) return null
    val start = targetRow * columns
    if (start >= itemCount) return null
    return (start + sourceColumn).coerceAtMost(minOf(start + columns - 1, itemCount - 1))
}
