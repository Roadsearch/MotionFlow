package com.roadsearch.openeditvideo.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** MotionFlow design tokens. Single source of truth for every screen. */
object MfColors {
    val Background = Color(0xFF0A0A10)
    val Surface = Color(0xFF0E0E16)
    val Card = Color(0xFF161622)
    val CardHigh = Color(0xFF1E1E2D)
    val Outline = Color(0xFF2A2A3D)

    val Violet = Color(0xFFC546FF)
    val Indigo = Color(0xFF6C63FF)
    val Cyan = Color(0xFF00A3FF)
    val Active = Color(0xFF6B7CFF)   // selected tab / toggle (periwinkle, sampled from the mockup)
    val Gold = Color(0xFFFFC857)
    val Danger = Color(0xFFFF5C7A)

    val TextPrimary = Color.White
    val TextSecondary = Color(0xFFA2A2B8)
    val TextMuted = Color(0xFF6E6E85)

    val BrandGradient: List<Color> = listOf(Color(0xFF9F3CF9), Color(0xFF5B7CFF), Color(0xFF00CEFD))
    fun brandBrush(): Brush = Brush.linearGradient(BrandGradient)
}

internal val MotionDarkColorScheme = darkColorScheme(
    primary = MfColors.Violet,
    onPrimary = Color.White,
    secondary = MfColors.Cyan,
    onSecondary = Color.White,
    background = MfColors.Background,
    onBackground = Color.White,
    surface = MfColors.Surface,
    onSurface = Color.White,
    surfaceVariant = MfColors.Card,
    onSurfaceVariant = MfColors.TextSecondary,
    surfaceContainerLowest = MfColors.Background,
    surfaceContainerLow = MfColors.Surface,
    surfaceContainer = MfColors.Card,
    surfaceContainerHigh = MfColors.CardHigh,
    surfaceContainerHighest = MfColors.CardHigh,
    outline = MfColors.Outline,
    outlineVariant = MfColors.Outline,
    error = MfColors.Danger,
)
