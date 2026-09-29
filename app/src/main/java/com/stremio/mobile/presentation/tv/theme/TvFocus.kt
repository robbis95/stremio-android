package com.stremio.mobile.presentation.tv.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun Modifier.tvFocusTreatment(
    focused: Boolean,
    radius: Dp = TvDimens.cardRadius,
    unfocusedBorder: Color = Color.Transparent,
): Modifier {
    val scale = animateFloatAsState(if (focused) 1.035f else 1f, label = "tv-focus-scale")
    return this.scale(scale.value).border(
        BorderStroke(if (focused) TvDimens.focusBorder else 1.dp, if (focused) TvColors.focus else unfocusedBorder),
        RoundedCornerShape(radius),
    )
}
