package com.stremio.mobile.presentation.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import coil3.compose.AsyncImage
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf

@Composable
internal fun TvHomeScreen(
    shelves: List<CatalogShelf>,
    isBoardLoading: Boolean,
    isActive: Boolean,
    focusMemory: TvFocusMemory,
    onOpenDetails: (CatalogItem) -> Unit,
) {
    val shelf = shelves.firstOrNull { it.items.isNotEmpty() }
        ?: shelves.firstOrNull { it.error != null }
        ?: shelves.firstOrNull { it.isLoading }
        ?: shelves.firstOrNull()
    val items = shelf?.items.orEmpty()
    val contentKeys = remember(items) { items.map { contentFocusKey(it.type, it.id) } }
    val requesters = remember(contentKeys) { contentKeys.associateWith { FocusRequester() } }
    val listState = rememberLazyListState()
    var focusedKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isActive, contentKeys) {
        if (!isActive || contentKeys.isEmpty()) return@LaunchedEffect
        val oldKey = focusMemory.focusedContentKey
        val restore = oldKey in contentKeys
        val targetKey = if (restore) oldKey!! else contentKeys.first()
        val targetIndex = contentKeys.indexOf(targetKey)
        if (targetIndex >= 0) listState.scrollToItem(targetIndex)
        withFrameNanos { }
        requesters[targetKey]?.requestFocus()
        focusedKey = targetKey
        focusMemory.focusedContentKey = targetKey
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 38.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Stremio",
            modifier = Modifier.padding(horizontal = 64.dp),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = shelf?.title ?: "Home",
            modifier = Modifier.padding(horizontal = 64.dp),
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
        )
        Spacer(Modifier.height(22.dp))

        when {
            items.isNotEmpty() -> LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 64.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(items, key = { _, item -> contentFocusKey(item.type, item.id) }) { index, item ->
                    val key = contentKeys[index]
                    val requester = requesters.getValue(key)
                    var hasFocus by remember(key) { mutableStateOf(false) }
                    val previous = contentKeys.getOrNull(index - 1)?.let(requesters::getValue) ?: requester
                    val next = contentKeys.getOrNull(index + 1)?.let(requesters::getValue) ?: requester
                    TvPosterCard(
                        item = item,
                        isFocused = hasFocus,
                        enabled = isActive,
                        onFocus = {
                            hasFocus = true
                            focusedKey = key
                            focusMemory.focusedContentKey = key
                        },
                        onFocusLost = { hasFocus = false },
                        requester = requester,
                        leftRequester = previous,
                        rightRequester = next,
                        upRequester = requester,
                        downRequester = requester,
                        onActivate = { onOpenDetails(item) },
                    )
                }
            }
            isBoardLoading || shelf?.isLoading == true -> Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            shelf?.error != null -> Text(
                text = shelf.error,
                modifier = Modifier.padding(horizontal = 64.dp, vertical = 40.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleLarge,
            )
            else -> Text(
                text = "No titles are available in this catalog yet.",
                modifier = Modifier.padding(horizontal = 64.dp, vertical = 40.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (focusedKey != null && isActive) {
            Text(
                text = "Press Select for details",
                modifier = Modifier.padding(start = 64.dp, top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun TvPosterCard(
    item: CatalogItem,
    isFocused: Boolean,
    enabled: Boolean,
    onFocus: () -> Unit,
    onFocusLost: () -> Unit,
    requester: FocusRequester,
    leftRequester: FocusRequester,
    rightRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    onActivate: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .size(width = 170.dp, height = 286.dp)
            .focusRequester(requester)
            .focusProperties {
                canFocus = enabled
                left = leftRequester
                right = rightRequester
                up = upRequester
                down = downRequester
            }
            .onFocusChanged { if (it.isFocused) onFocus() else onFocusLost() }
            .onKeyEvent { event ->
                if (enabled && event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onActivate()
                    true
                } else false
            }
            .focusable()
            .border(
                border = BorderStroke(if (isFocused) 4.dp else 1.dp, if (isFocused) MaterialTheme.colorScheme.primary else Color(0xFF454A55)),
                shape = shape,
            )
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            model = item.poster,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(238.dp).border(1.dp, Color(0xFF30343D), shape),
        )
        Text(
            text = item.name,
            modifier = Modifier.padding(horizontal = 5.dp),
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
