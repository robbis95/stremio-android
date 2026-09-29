package com.stremio.mobile.presentation.tv.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvDimens
import com.stremio.mobile.presentation.tv.theme.tvFocusTreatment

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
    showLibraryStatus: Boolean = false,
) {
    val shape = RoundedCornerShape(TvDimens.controlRadius)
    Column(
        modifier = Modifier.size(width = TvDimens.posterWidth, height = TvDimens.posterHeight)
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
            .clickable(enabled = enabled, onClick = onActivate, indication = null, interactionSource = null)
            .shadow(if (isFocused) 14.dp else 0.dp, shape, clip = false)
            .background(TvColors.surface, shape)
            .tvFocusTreatment(isFocused, TvDimens.controlRadius)
            .padding(TvDimens.posterContentPadding),
        verticalArrangement = Arrangement.spacedBy(TvDimens.posterContentSpacing),
    ) {
        Box(Modifier.fillMaxWidth().height(TvDimens.posterImageHeight).clip(shape).border(1.dp, TvColors.divider, shape)) {
            AsyncImage(
                model = item.poster,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (showLibraryStatus && item.watched) {
                Text(
                    "✓ Watched",
                    Modifier.align(Alignment.TopEnd).padding(6.dp).background(
                        TvColors.mediaSurface.copy(alpha = 0.88f),
                        RoundedCornerShape(6.dp),
                    ).padding(horizontal = 6.dp, vertical = 3.dp),
                    color = TvColors.onMedia,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            val progress = item.progress?.takeIf { it > 0f && it < 1f }
            if (showLibraryStatus && progress != null) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)
                        .background(TvColors.mediaSurface.copy(alpha = 0.82f)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(progress).fillMaxHeight().background(TvColors.accent),
                    )
                }
            }
        }
        Text(item.name, modifier = Modifier.padding(horizontal = 5.dp), color = TvColors.primaryText,
            style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
