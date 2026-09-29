package com.stremio.mobile.presentation.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Alignment
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
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvDimens

internal const val TV_HOME_HERO_HEIGHT_DP = 142

@Composable
internal fun TvHomeHero(controller: TvHeroPreviewController, isBoardLoading: Boolean) {
    val preview by controller.item
    val treatment = heroArtworkTreatment(preview)
    val richArtwork = when (treatment) {
        TvHeroArtworkTreatment.RichArtwork -> preview?.background?.takeIf { it.isNotBlank() }
            ?: preview?.poster?.takeIf { it.isNotBlank() }
        TvHeroArtworkTreatment.ContainedPoster, TvHeroArtworkTreatment.Neutral -> null
    }
    val containedPoster = preview?.poster?.takeIf { it.isNotBlank() }
        ?.takeIf { treatment == TvHeroArtworkTreatment.ContainedPoster }
    val shape = RoundedCornerShape(18.dp)

    Box(
        Modifier.fillMaxWidth().height(TV_HOME_HERO_HEIGHT_DP.dp)
            .clip(shape)
            .background(TvColors.surface),
    ) {
        if (richArtwork != null) {
            AsyncImage(
                model = richArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
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

        if (containedPoster != null) {
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(174.dp)
                    .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                    .background(
                        Brush.horizontalGradient(
                            0f to TvColors.surface,
                            0.28f to TvColors.artworkPanel,
                            1f to TvColors.artworkPanel,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = containedPoster,
                    contentDescription = preview?.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(122.dp).aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(9.dp)),
                )
            }
        }

        // Keep the Stremio mark subtle and away from the content-title hierarchy.
        Image(
            painter = painterResource(R.drawable.ic_stremio_splash_logo),
            contentDescription = "Stremio",
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 12.dp, end = 16.dp).height(15.dp),
            alpha = 0.68f,
        )

        Column(
            Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = TvDimens.safeHorizontal,
                    end = if (containedPoster != null) 204.dp else TvDimens.safeHorizontal,
                    bottom = 8.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (preview != null) {
                val logo = preview?.logo?.takeIf { it.isNotBlank() }
                if (logo != null) {
                    AsyncImage(
                        model = logo,
                        contentDescription = preview?.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.widthIn(max = 250.dp).height(28.dp),
                    )
                } else {
                    Text(
                        preview?.name.orEmpty(),
                        color = TvColors.primaryText,
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 31.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                metadataLine(preview!!)?.let { metadata ->
                    Text(
                        metadata,
                        color = TvColors.secondaryText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 17.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                preview?.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        description,
                        color = TvColors.primaryText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 17.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
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
