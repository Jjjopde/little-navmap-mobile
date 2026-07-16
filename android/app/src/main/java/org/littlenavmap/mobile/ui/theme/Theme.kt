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
    primary = Color(0xFF006B50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF79F8C6),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF6B5E00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7E36C),
    onSecondaryContainer = Color(0xFF211C00),
    tertiary = Color(0xFF8A4A57),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9DF),
    onTertiaryContainer = Color(0xFF3A0716),
    background = Color(0xFFF8FAF7),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFF8FAF7),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDDE5E0),
    onSurfaceVariant = Color(0xFF414945),
    outline = Color(0xFF717974),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EDCB0),
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF00513C),
    onPrimaryContainer = Color(0xFF79F8C6),
    secondary = Color(0xFFDAC74F),
    onSecondary = Color(0xFF383000),
    secondaryContainer = Color(0xFF514700),
    onSecondaryContainer = Color(0xFFF7E36C),
    tertiary = Color(0xFFFFB1C0),
    onTertiary = Color(0xFF541D2A),
    tertiaryContainer = Color(0xFF6F3340),
    onTertiaryContainer = Color(0xFFFFD9DF),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE0E3E0),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFE0E3E0),
    surfaceVariant = Color(0xFF414945),
    onSurfaceVariant = Color(0xFFC0C9C3),
    outline = Color(0xFF8A938E),
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
