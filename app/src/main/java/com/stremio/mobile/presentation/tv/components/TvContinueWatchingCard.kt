package com.stremio.mobile.presentation.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.stremio.mobile.data.model.CatalogPosterShape
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvDimens
import com.stremio.mobile.presentation.tv.theme.tvFocusTreatment

@Composable
internal fun TvContinueWatchingCard(
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
    val shape = RoundedCornerShape(TvDimens.controlRadius)
    val landscapeArtwork = item.background?.takeIf { it.isNotBlank() }
        ?: item.poster?.takeIf { item.posterShape == CatalogPosterShape.Landscape }
    Box(
        modifier = Modifier.width(292.dp).height(164.dp)
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
                    else -> false
                }
            }
            .clickable(enabled = enabled, onClick = onActivate, indication = null, interactionSource = null)
            .shadow(if (isFocused) 14.dp else 0.dp, shape, clip = false)
            .background(TvColors.mediaSurface, shape)
            .tvFocusTreatment(isFocused, TvDimens.controlRadius)
            .clip(shape),
    ) {
        if (landscapeArtwork != null) {
            AsyncImage(
                model = landscapeArtwork,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to androidx.compose.ui.graphics.Color.Transparent,
                0.42f to androidx.compose.ui.graphics.Color(0xAA17171A),
                1f to androidx.compose.ui.graphics.Color(0xE617171A),
            )))
            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(item.name, color = TvColors.onMedia,
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                metadataLine(item)?.let {
                    Text(it, color = TvColors.mediaSecondary,
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(9.dp))
                ProgressTrack(item.progress)
            }
        } else {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!item.poster.isNullOrBlank()) {
                    AsyncImage(
                        model = item.poster,
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(132.dp).aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(item.name, color = TvColors.onMedia,
                            style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        metadataLine(item)?.let {
                            Text(it, color = TvColors.mediaSecondary,
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    ProgressTrack(item.progress)
                }
            }
        }
    }
}

@Composable
private fun ProgressTrack(progress: Float?) {
    val value = progress?.coerceIn(0f, 1f) ?: 0f
    Box(Modifier.fillMaxWidth().height(5.dp).background(TvColors.mediaTrack, RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxWidth(value).fillMaxHeight().background(TvColors.accent, RoundedCornerShape(3.dp)))
    }
}

private fun metadataLine(item: CatalogItem): String? = listOfNotNull(
    item.releaseInfo?.takeIf { it.isNotBlank() },
    item.runtime?.takeIf { it.isNotBlank() },
).joinToString(" · ").takeIf { it.isNotBlank() }
