package com.stremio.mobile.presentation.tv.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object TvColors {
    val background = Color(0xFFF4F2EE)
    val surface = Color(0xFFFFFEFC)
    val elevatedSurface = Color(0xFFFFFFFF)
    val primaryText = Color(0xFF202126)
    val secondaryText = Color(0xFF65666C)
    val divider = Color(0xFFE2DFDA)
    val accent = Color(0xFF6750A4)
    val focus = Color(0xFF6045B8)
    val focusSoft = Color(0x336045B8)
    val disabled = Color(0xFFAAA8A4)
    val error = Color(0xFFB3261E)
}

internal object TvDimens {
    val safeHorizontal: Dp = 72.dp
    val safeVertical: Dp = 44.dp
    val cardRadius: Dp = 16.dp
    val controlRadius: Dp = 12.dp
    val focusBorder: Dp = 3.dp
    val shelfSpacing: Dp = 30.dp
    val titleSpacing: Dp = 12.dp
    val buttonHeight: Dp = 56.dp
    val posterWidth: Dp = 184.dp
    val posterHeight: Dp = 306.dp
}
