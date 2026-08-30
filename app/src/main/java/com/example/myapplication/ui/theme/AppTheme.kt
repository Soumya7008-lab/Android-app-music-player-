package com.example.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Pitch Black and Pure White Palette
private val PitchBlack = Color(0xFF000000)
private val PureWhite = Color(0xFFFFFFFF)

// HIGH CLARITY DARK PALETTE
private val DarkSurfaceElevated = Color(0xFF121212) // FIXED: Elevated surface for clarity
private val DarkSurfaceCard = Color(0xFF1A1A1A)     // FIXED: Distinct card surface
private val DarkTextSecondary = Color(0xFFB0B0B0)  // FIXED: High visibility grey

// HIGH CLARITY LIGHT PALETTE
private val LightSurface = Color(0xFFF8F9FA)
private val LightTextSecondary = Color(0xFF5B7083)

private val AppDarkColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = PitchBlack,
    background = PitchBlack,
    onBackground = PureWhite,
    surface = DarkSurfaceElevated,
    onSurface = PureWhite,
    surfaceVariant = DarkSurfaceCard,
    onSurfaceVariant = DarkTextSecondary,
    secondary = PureWhite 
)

private val AppLightColorScheme = lightColorScheme(
    primary = PitchBlack,
    onPrimary = PureWhite,
    background = PureWhite,
    onBackground = PitchBlack,
    surface = LightSurface,
    onSurface = PitchBlack,
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = LightTextSecondary,
    secondary = PitchBlack
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    )
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AppDarkColorScheme else AppLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
