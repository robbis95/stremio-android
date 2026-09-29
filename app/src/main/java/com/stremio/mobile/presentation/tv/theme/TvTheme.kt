package com.stremio.mobile.presentation.tv.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.lightColorScheme

private val TvColorScheme = lightColorScheme(
    primary = TvColors.accent,
    onPrimary = TvColors.elevatedSurface,
    secondary = TvColors.accent,
    background = TvColors.background,
    onBackground = TvColors.primaryText,
    surface = TvColors.surface,
    onSurface = TvColors.primaryText,
    onSurfaceVariant = TvColors.secondaryText,
    border = TvColors.divider,
    borderVariant = TvColors.divider,
    error = TvColors.error,
)

private val TvTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontSize = 54.sp, lineHeight = 62.sp, fontWeight = FontWeight.SemiBold),
        displayMedium = base.displayMedium.copy(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold),
        displaySmall = base.displaySmall.copy(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.copy(fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium),
        titleLarge = base.titleLarge.copy(fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontSize = 20.sp, lineHeight = 29.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodySmall = base.bodySmall.copy(fontSize = 16.sp, lineHeight = 23.sp),
        labelLarge = base.labelLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
internal fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TvColorScheme, typography = TvTypography, content = content)
}
