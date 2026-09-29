package com.stremio.mobile.presentation.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.stremio.mobile.data.model.CatalogShelf
import com.stremio.mobile.presentation.tv.components.TvRowScrollPosition
import com.stremio.mobile.presentation.tv.components.TvShelfRow
import com.stremio.mobile.presentation.tv.focus.TvFocusLocation
import com.stremio.mobile.presentation.tv.focus.TvFocusRegistry
import com.stremio.mobile.presentation.tv.focus.VisibleFocusItem
import com.stremio.mobile.presentation.tv.focus.adjacentFocusableShelf
import com.stremio.mobile.presentation.tv.focus.closestVisibleFocusItem
import com.stremio.mobile.presentation.tv.focus.contentFocusKey
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvDimens
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class PendingSearchTraversal(val shelfKey: String, val anchorX: Float, val direction: Int)

@Composable
internal fun TvSearchScreen(
    query: String,
    results: CatalogShelf,
    shelves: List<CatalogShelf>,
    isActive: Boolean,
    restoreFocusRequestId: Int,
    navFocusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onOpenDetails: (CatalogItem, TvFocusLocation) -> Unit,
) {
    val shelfKeys = remember(shelves) { searchShelfFocusKeys(shelves) }
    val focusShelves = remember(shelves, shelfKeys) {
        shelves.mapIndexed { index, shelf ->
            com.stremio.mobile.presentation.tv.focus.FocusShelf(shelfKeys[index], shelf.items, shelf.isLoading)
        }
    }
    val registry = remember { TvFocusRegistry() }
    val rowStates = remember { mutableStateMapOf<String, LazyListState>() }
    val rowScrollPositions = remember { mutableMapOf<String, TvRowScrollPosition>() }
    val verticalState = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }
    val actionKeys = listOf("action:space", "action:backspace", "action:clear")
    val firstLetterKey = "letter:0:0"
    var lastKeyboardKey by remember { mutableStateOf(firstLetterKey) }
    var lastFocusedResult by remember { mutableStateOf<TvFocusLocation?>(null) }
    var wasResultFocused by remember { mutableStateOf(false) }
    var focusedLocation by remember { mutableStateOf<TvFocusLocation?>(null) }
    var pendingTraversal by remember { mutableStateOf<PendingSearchTraversal?>(null) }
    val latestShelves by rememberUpdatedState(focusShelves)
    val latestKeys by rememberUpdatedState(shelfKeys)
    val scope = rememberCoroutineScope()
    var visibleQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) { visibleQuery = query }

    fun applyKey(action: TvSearchKeyAction) {
        val updated = reduceTvSearchQuery(visibleQuery, action)
        visibleQuery = updated
        onQueryChange(updated)
    }

    fun focusFirstResult() {
        val shelfIndex = focusShelves.indexOfFirst { it.items.isNotEmpty() }
        if (shelfIndex < 0) return
        val shelf = focusShelves[shelfIndex]
        val first = shelf.items.first()
        val contentKey = contentFocusKey(first.type, first.id)
        scope.launch {
            verticalState.scrollToItem(shelfIndex)
            val row = snapshotFlow { rowStates[shelf.key] }.first { it != null }!!
            snapshotFlow { row.layoutInfo.visibleItemsInfo.any { it.key == contentKey } }.first { it }
            registry.requester("${shelf.key}|$contentKey").requestFocus()
        }
    }

    fun restoreSearchFocus() {
        if (wasResultFocused) {
            val location = lastFocusedResult
            val shelfIndex = location?.let { target ->
                focusShelves.indexOfFirst { it.key == target.shelfKey && it.items.any { item -> contentFocusKey(item.type, item.id) == target.contentKey } }
            } ?: -1
            if (location != null && shelfIndex >= 0) {
                scope.launch {
                    verticalState.scrollToItem(shelfIndex)
                    val row = snapshotFlow { rowStates[location.shelfKey] }.first { it != null }!!
                    val index = focusShelves[shelfIndex].items.indexOfFirst {
                        contentFocusKey(it.type, it.id) == location.contentKey
                    }
                    if (index >= 0 && row.layoutInfo.visibleItemsInfo.none { it.key == location.contentKey }) row.scrollToItem(index)
                    snapshotFlow { row.layoutInfo.visibleItemsInfo.any { it.key == location.contentKey } }.first { it }
                    registry.requester("${location.shelfKey}|${location.contentKey}").requestFocus()
                }
                return
            }
        }
        requester(lastKeyboardKey).requestFocus()
    }

    LaunchedEffect(restoreFocusRequestId) {
        if (isActive && restoreFocusRequestId > 0) restoreSearchFocus()
    }
    LaunchedEffect(focusShelves) {
        registry.retain(focusShelves.flatMap { shelf ->
            shelf.items.map { item -> "${shelf.key}|${contentFocusKey(item.type, item.id)}" }
        }.toSet())
    }

    Column(Modifier.fillMaxSize().alpha(if (isActive) 1f else 0f).padding(horizontal = TvDimens.safeHorizontal, vertical = 4.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp)
                .background(TvColors.surface, RoundedCornerShape(TvDimens.controlRadius))
                .border(1.dp, TvColors.divider, RoundedCornerShape(TvDimens.controlRadius))
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = visibleQuery.ifBlank { "Search movies, series and more" },
                color = if (visibleQuery.isBlank()) TvColors.secondaryText else TvColors.primaryText,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))

        tvSearchLetterRows.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { columnIndex, letter ->
                    val key = "letter:$rowIndex:$columnIndex"
                    val left = requester("letter:$rowIndex:${(columnIndex - 1).coerceAtLeast(0)}")
                    val right = requester("letter:$rowIndex:${(columnIndex + 1).coerceAtMost(row.lastIndex)}")
                    val upTarget = if (rowIndex == 0) navFocusRequester else adjacentKeyboardKey(rowIndex, columnIndex, -1)
                        ?.let { (r, c) -> requester("letter:$r:$c") } ?: requester(key)
                    val downTarget = if (rowIndex == tvSearchLetterRows.lastIndex) requester(actionKeys[columnIndex.coerceAtMost(2)])
                        else adjacentKeyboardKey(rowIndex, columnIndex, 1)?.let { (r, c) -> requester("letter:$r:$c") } ?: requester(key)
                    Button(
                        onClick = {
                            lastKeyboardKey = key
                            wasResultFocused = false
                            applyKey(TvSearchKeyAction.Letter(letter))
                        },
                        modifier = Modifier.weight(1f).height(39.dp).focusRequester(requester(key))
                            .focusProperties { canFocus = isActive; this.left = left; this.right = right; this.up = upTarget; this.down = downTarget },
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(9.dp)),
                        colors = ButtonDefaults.colors(containerColor = TvColors.surface, contentColor = TvColors.primaryText),
                    ) { Text(letter.toString(), style = MaterialTheme.typography.titleMedium) }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            actionKeys.forEachIndexed { index, key ->
                val action = when (index) {
                    0 -> TvSearchKeyAction.Space
                    1 -> TvSearchKeyAction.Backspace
                    else -> TvSearchKeyAction.Clear
                }
                val label = when (action) {
                    TvSearchKeyAction.Space -> "SPACE"
                    TvSearchKeyAction.Backspace -> "BACKSPACE"
                    TvSearchKeyAction.Clear -> "CLEAR"
                    is TvSearchKeyAction.Letter -> ""
                }
                val up = requester("letter:${tvSearchLetterRows.lastIndex}:${(index * (tvSearchLetterRows.last().lastIndex) / 2).coerceIn(0, tvSearchLetterRows.last().lastIndex)}")
                val left = requester(actionKeys[(index - 1).coerceAtLeast(0)])
                val right = requester(actionKeys[(index + 1).coerceAtMost(actionKeys.lastIndex)])
                Button(
                    onClick = {
                        lastKeyboardKey = key
                        wasResultFocused = false
                        applyKey(action)
                    },
                    modifier = Modifier.weight(if (index == 0) 2f else 1f).height(39.dp)
                        .focusRequester(requester(key))
                        .focusProperties { canFocus = isActive; this.left = left; this.right = right; this.up = up; this.down = requester(key) }
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                focusFirstResult()
                                focusShelves.any { it.items.isNotEmpty() }
                            } else false
                        },
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(9.dp)),
                    colors = ButtonDefaults.colors(containerColor = TvColors.surface, contentColor = TvColors.primaryText),
                ) { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                visibleQuery.isBlank() -> Text("Enter a title with the on-screen keyboard.", Modifier.align(Alignment.CenterStart), color = TvColors.secondaryText, style = MaterialTheme.typography.bodyMedium)
                !shouldRunTvSearch(visibleQuery) -> Text("Type at least 2 characters to search.", Modifier.align(Alignment.CenterStart), color = TvColors.secondaryText, style = MaterialTheme.typography.bodyMedium)
                shelves.isEmpty() && results.isLoading -> Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.CircularProgressIndicator(color = TvColors.accent, modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    Text("Searching addons…", color = TvColors.secondaryText, style = MaterialTheme.typography.bodySmall)
                }
                shelves.isEmpty() -> Text(results.error ?: "No results for \"${visibleQuery.trim()}\".", Modifier.align(Alignment.CenterStart), color = TvColors.secondaryText, style = MaterialTheme.typography.bodyMedium)
                else -> LazyColumn(state = verticalState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(shelves, key = { index, _ -> shelfKeys[index] }, contentType = { _, _ -> "search-shelf" }) { index, shelf ->
                        val shelfKey = shelfKeys[index]
                        Column {
                            Text(shelf.title, Modifier.padding(bottom = 2.dp), style = MaterialTheme.typography.titleMedium, color = TvColors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            when {
                                shelf.items.isNotEmpty() -> TvShelfRow(
                                    shelfKey = shelfKey,
                                    items = shelf.items,
                                    active = isActive,
                                    registry = registry,
                                    rowStates = rowStates,
                                    rowScrollPositions = rowScrollPositions,
                                    continueWatching = false,
                                    onFocused = { content, _ ->
                                        if (isActive) {
                                            val location = TvFocusLocation(shelfKey, content, index)
                                            focusedLocation = location
                                            lastFocusedResult = location
                                            wasResultFocused = true
                                        }
                                    },
                                    onVertical = { content, direction ->
                                        if (!isActive) return@TvShelfRow
                                        val neighbor = adjacentFocusableShelf(focusShelves, shelfKey, direction)
                                        if (neighbor == null && direction < 0) {
                                            val itemIndex = shelf.items.indexOfFirst { contentFocusKey(it.type, it.id) == content }
                                            val column = if (shelf.items.size <= 1) 0 else itemIndex * tvSearchLetterRows.last().lastIndex / (shelf.items.size - 1)
                                            requester("letter:${tvSearchLetterRows.lastIndex}:$column").requestFocus()
                                            wasResultFocused = false
                                        } else if (neighbor != null) {
                                            val sourceInfo = rowStates[shelfKey]?.layoutInfo?.visibleItemsInfo?.firstOrNull { it.key == content }
                                            if (sourceInfo != null) pendingTraversal = PendingSearchTraversal(neighbor.key, sourceInfo.offset + sourceInfo.size / 2f, direction)
                                        }
                                    },
                                    onActivate = { item, content ->
                                        val location = TvFocusLocation(shelfKey, content, index)
                                        lastFocusedResult = location
                                        wasResultFocused = true
                                        onOpenDetails(item, location)
                                    },
                                )
                                shelf.isLoading -> Text("Loading this catalog…", Modifier.padding(vertical = 16.dp), color = TvColors.secondaryText, style = MaterialTheme.typography.bodySmall)
                                shelf.error != null -> Text(shelf.error, Modifier.padding(vertical = 12.dp), color = TvColors.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(isActive, pendingTraversal) {
        val target = pendingTraversal ?: return@LaunchedEffect
        if (!isActive) return@LaunchedEffect
        val targetIndex = latestKeys.indexOf(target.shelfKey)
        if (targetIndex < 0) { pendingTraversal = null; return@LaunchedEffect }
        val sourceIndex = focusedLocation?.let { latestKeys.indexOf(it.shelfKey) } ?: -1
        val maxSteps = kotlin.math.abs(targetIndex - sourceIndex).coerceAtLeast(1)
        repeat(maxSteps) {
            if (verticalState.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) {
                verticalState.animateScrollToItem(targetIndex)
            }
        }
        val row = snapshotFlow { rowStates[target.shelfKey] }.first { it != null }!!
        val visible = snapshotFlow { row.layoutInfo.visibleItemsInfo.toList() }.first { it.isNotEmpty() }
        val nearest = closestVisibleFocusItem(target.anchorX, visible.mapIndexed { fallback, info ->
            VisibleFocusItem(info.key as String, info.offset + info.size / 2f, info.index.takeIf { it >= 0 } ?: fallback)
        })
        if (nearest != null) {
            registry.requester("${target.shelfKey}|${nearest.key}").requestFocus()
            val location = TvFocusLocation(target.shelfKey, nearest.key, targetIndex)
            focusedLocation = location
            lastFocusedResult = location
            wasResultFocused = true
        }
        pendingTraversal = null
    }
}
