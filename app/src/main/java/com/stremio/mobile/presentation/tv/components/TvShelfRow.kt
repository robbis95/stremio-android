package com.stremio.mobile.presentation.tv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.presentation.tv.focus.TvFocusRegistry
import com.stremio.mobile.presentation.tv.focus.contentFocusKey
import com.stremio.mobile.presentation.tv.theme.TvDimens

@Composable
internal fun TvShelfRow(
    shelfKey: String,
    items: List<CatalogItem>,
    active: Boolean,
    registry: TvFocusRegistry,
    rowStates: MutableMap<String, LazyListState>,
    onFocused: (String) -> Unit,
    onVertical: (String, Int) -> Unit,
    onActivate: (CatalogItem, String) -> Unit,
) {
    val contentKeys = items.map { contentFocusKey(it.type, it.id) }
    val listState = rememberLazyListState()
    androidx.compose.runtime.SideEffect { rowStates[shelfKey] = listState }
    androidx.compose.runtime.DisposableEffect(shelfKey, listState) {
        onDispose { if (rowStates[shelfKey] === listState) rowStates.remove(shelfKey) }
    }
    LazyRow(state = listState, contentPadding = PaddingValues(horizontal = TvDimens.safeHorizontal, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(items, key = { _, item -> contentFocusKey(item.type, item.id) }) { index, item ->
            val key = contentKeys[index]
            val requester = registry.requester("$shelfKey|$key")
            val left = contentKeys.getOrNull(index - 1)?.let { registry.requester("$shelfKey|$it") } ?: requester
            val right = contentKeys.getOrNull(index + 1)?.let { registry.requester("$shelfKey|$it") } ?: requester
            androidx.compose.runtime.key(key) {
                var focused = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                TvPosterCard(item, focused.value, active, requester, left, right,
                    onFocus = { focused.value = true; onFocused(key) },
                    onFocusLost = { focused.value = false },
                    onUp = { onVertical(key, -1) }, onDown = { onVertical(key, 1) },
                    onActivate = { onActivate(item, key) })
            }
        }
    }
}
