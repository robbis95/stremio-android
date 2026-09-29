package com.stremio.mobile.presentation.tv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.presentation.tv.focus.TvFocusRegistry
import com.stremio.mobile.presentation.tv.focus.contentFocusKey
import com.stremio.mobile.presentation.tv.theme.TvDimens

internal data class TvRowScrollPosition(val index: Int, val scrollOffset: Int)

@Composable
internal fun TvShelfRow(
    shelfKey: String,
    items: List<CatalogItem>,
    active: Boolean,
    registry: TvFocusRegistry,
    rowStates: MutableMap<String, LazyListState>,
    rowScrollPositions: MutableMap<String, TvRowScrollPosition>,
    continueWatching: Boolean,
    onFocused: (String, CatalogItem) -> Unit,
    onVertical: (String, Int) -> Unit,
    onActivate: (CatalogItem, String) -> Unit,
) {
    val contentKeys = items.map { contentFocusKey(it.type, it.id) }
    val savedPosition = rowScrollPositions[shelfKey] ?: TvRowScrollPosition(0, 0)
    val listState = rememberLazyListState(savedPosition.index, savedPosition.scrollOffset)
    androidx.compose.runtime.SideEffect { rowStates[shelfKey] = listState }
    LaunchedEffect(shelfKey, listState) {
        snapshotFlow { TvRowScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .distinctUntilChanged()
            .collect { rowScrollPositions[shelfKey] = it }
    }
    androidx.compose.runtime.DisposableEffect(shelfKey, listState) {
        onDispose {
            rowScrollPositions[shelfKey] = TvRowScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            if (rowStates[shelfKey] === listState) rowStates.remove(shelfKey)
        }
    }
    LazyRow(state = listState, contentPadding = PaddingValues(horizontal = TvDimens.safeHorizontal, vertical = TvDimens.shelfRowVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(items, key = { _, item -> contentFocusKey(item.type, item.id) }) { index, item ->
            val key = contentKeys[index]
            val requester = registry.requester("$shelfKey|$key")
            val left = contentKeys.getOrNull(index - 1)?.let { registry.requester("$shelfKey|$it") } ?: requester
            val right = contentKeys.getOrNull(index + 1)?.let { registry.requester("$shelfKey|$it") } ?: requester
            androidx.compose.runtime.key(key) {
                var focused = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                if (continueWatching) {
                    TvContinueWatchingCard(item, focused.value, active, requester, left, right,
                        onFocus = { focused.value = true; onFocused(key, item) },
                        onFocusLost = { focused.value = false },
                        onUp = { onVertical(key, -1) }, onDown = { onVertical(key, 1) },
                        onActivate = { onActivate(item, key) })
                } else {
                    TvPosterCard(item, focused.value, active, requester, left, right,
                        onFocus = { focused.value = true; onFocused(key, item) },
                        onFocusLost = { focused.value = false },
                        onUp = { onVertical(key, -1) }, onDown = { onVertical(key, 1) },
                        onActivate = { onActivate(item, key) })
                }
            }
        }
    }
}
