/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF27313B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAF0),
    onPrimaryContainer = Color(0xFF14242B),
    secondary = Color(0xFF007C9C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9ECF8),
    onSecondaryContainer = Color(0xFF003544),
    tertiary = Color(0xFF9A5B12),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B9),
    onTertiaryContainer = Color(0xFF331B00),
    background = Color(0xFFF2F5F7),
    onBackground = Color(0xFF182028),
    surface = Color(0xFFFBFCFD),
    onSurface = Color(0xFF182028),
    surfaceVariant = Color(0xFFE0E6EA),
    onSurfaceVariant = Color(0xFF4C5963),
    outline = Color(0xFF73808A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD8E2EB),
    onPrimary = Color(0xFF1B2630),
    primaryContainer = Color(0xFF344754),
    onPrimaryContainer = Color(0xFFD8E2EB),
    secondary = Color(0xFF68D1EF),
    onSecondary = Color(0xFF003543),
    secondaryContainer = Color(0xFF004B5F),
    onSecondaryContainer = Color(0xFFBCEBFA),
    tertiary = Color(0xFFFFC477),
    onTertiary = Color(0xFF4B2700),
    tertiaryContainer = Color(0xFF713E00),
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF11161B),
    onBackground = Color(0xFFE1E7EB),
    surface = Color(0xFF161C22),
    onSurface = Color(0xFFE1E7EB),
    surfaceVariant = Color(0xFF28333C),
    onSurfaceVariant = Color(0xFFBEC9D1),
    outline = Color(0xFF8998A3),
)

private val AppTypography = Typography(
    headlineSmall = appTextStyle(24.sp, FontWeight.SemiBold),
    titleLarge = appTextStyle(20.sp, FontWeight.Medium),
    titleMedium = appTextStyle(16.sp, FontWeight.Medium),
    titleSmall = appTextStyle(14.sp, FontWeight.Medium),
    bodyLarge = appTextStyle(16.sp, FontWeight.Normal),
    bodyMedium = appTextStyle(14.sp, FontWeight.Normal),
    bodySmall = appTextStyle(12.sp, FontWeight.Normal),
    labelLarge = appTextStyle(14.sp, FontWeight.Medium),
    labelMedium = appTextStyle(12.sp, FontWeight.Medium),
    labelSmall = appTextStyle(11.sp, FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun LittleNavmapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

private fun appTextStyle(
    size: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size,
    letterSpacing = 0.sp,
)
