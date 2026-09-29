package com.stremio.mobile.presentation.tv.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import com.stremio.mobile.data.model.CatalogItem

@Composable
internal fun TvPosterCard(
    item: CatalogItem,
    isFocused: Boolean,
    enabled: Boolean,
    requester: FocusRequester,
    left: FocusRequester,
    right: FocusRequester,
    onFocus: () -> Unit,
    onFocusLost: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onActivate: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.size(width = 170.dp, height = 286.dp)
            .focusRequester(requester)
            .focusProperties {
                canFocus = enabled
                this.left = left
                this.right = right
            }
            .onFocusChanged { if (it.isFocused) onFocus() else onFocusLost() }
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { onUp(); true }
                    Key.DirectionDown -> { onDown(); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onActivate(); true }
                    else -> false
                }
            }
            .focusable()
            .border(BorderStroke(if (isFocused) 4.dp else 1.dp, if (isFocused) MaterialTheme.colorScheme.primary else Color(0xFF454A55)), shape)
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(model = item.poster, contentDescription = item.name, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(238.dp).border(1.dp, Color(0xFF30343D), shape))
        Text(item.name, modifier = Modifier.padding(horizontal = 5.dp), color = Color.White,
            style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
