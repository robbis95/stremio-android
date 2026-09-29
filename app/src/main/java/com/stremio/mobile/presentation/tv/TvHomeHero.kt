package com.stremio.mobile.presentation.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.stremio.mobile.R
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogPosterShape
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvDimens

@Composable
internal fun TvHomeHero(controller: TvHeroPreviewController, isBoardLoading: Boolean) {
    val preview by controller.item
    val backgroundArtwork = preview?.background?.takeIf { it.isNotBlank() }
        ?: preview?.let { candidate -> candidate.poster?.takeIf { candidate.posterShape == CatalogPosterShape.Landscape } }
    Box(
        Modifier.fillMaxWidth().height(128.dp)
            .background(TvColors.surface, RoundedCornerShape(18.dp)),
    ) {
        if (backgroundArtwork != null) {
            AsyncImage(
                model = backgroundArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to TvColors.background,
                        0.46f to TvColors.background.copy(alpha = 0.94f),
                        0.78f to TvColors.background.copy(alpha = 0.50f),
                        1f to Color.Transparent,
                    ),
                ),
            )
        }
        val containedPoster = if (backgroundArtwork == null) preview?.poster?.takeIf { it.isNotBlank() } else null
        if (containedPoster != null) {
            AsyncImage(
                model = containedPoster,
                contentDescription = preview?.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd).padding(end = 24.dp)
                    .width(88.dp).fillMaxHeight(0.82f).clip(RoundedCornerShape(8.dp)),
            )
        }
        Column(
            Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(
                start = TvDimens.safeHorizontal,
                end = if (containedPoster != null) 132.dp else TvDimens.safeHorizontal,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_stremio_splash_logo),
                    contentDescription = "Stremio",
                    modifier = Modifier.height(17.dp),
                )
                Text("Stremio", color = TvColors.accent, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 19.sp))
            }
            if (preview != null) {
                val logo = preview?.logo?.takeIf { it.isNotBlank() }
                if (logo != null) {
                    AsyncImage(
                        model = logo,
                        contentDescription = preview?.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.widthIn(max = 250.dp).height(27.dp),
                    )
                } else {
                    Text(
                        preview?.name.orEmpty(),
                        color = TvColors.primaryText,
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 25.sp, lineHeight = 30.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                metadataLine(preview!!)?.let { metadata ->
                    Text(metadata, color = TvColors.secondaryText, style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 17.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                preview?.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(description, color = TvColors.primaryText, style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 17.sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text("Home", color = TvColors.primaryText, style = MaterialTheme.typography.headlineSmall)
                if (isBoardLoading) {
                    Text("Loading your catalogs…", color = TvColors.secondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun metadataLine(item: CatalogItem): String? = listOfNotNull(
    item.releaseInfo?.takeIf { it.isNotBlank() },
    item.runtime?.takeIf { it.isNotBlank() },
).joinToString(" · ").takeIf { it.isNotBlank() }
