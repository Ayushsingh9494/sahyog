package com.sahyog.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Sahyog brand palette.
 *
 * - Primary: cooperative teal-green (trust, growth, community).
 * - Secondary: marigold/saffron (warmth; ratings and highlights).
 * - Tertiary: slate indigo (informational accents).
 * - Neutrals are slightly green-tinted so surfaces harmonise with primary.
 *
 * Every text/background pair is checked against WCAG AA (4.5:1 for text,
 * 3:1 for non-text) in both themes. Re-run the check when changing values.
 */
object SahyogBrand {
    /** Raw brand colors for the logo and illustrations. Not for text. */
    val Teal = Color(0xFF0B6B58)
    val Marigold = Color(0xFFF4A340)
}

internal val LightColors = lightColorScheme(
    primary = Color(0xFF0B6B58),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4EFDB),
    onPrimaryContainer = Color(0xFF00201A),
    inversePrimary = Color(0xFF81D7BF),

    secondary = Color(0xFF8A5100),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDB8),
    onSecondaryContainer = Color(0xFF2C1700),

    tertiary = Color(0xFF4A5C92),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDBE1FF),
    onTertiaryContainer = Color(0xFF00174A),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF181D1B),
    surface = Color(0xFFF7FAF8),
    onSurface = Color(0xFF181D1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceTint = Color(0xFF0B6B58),
    inverseSurface = Color(0xFF2D3230),
    inverseOnSurface = Color(0xFFEEF2EF),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBFC9C4),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFFF7FAF8),
    surfaceDim = Color(0xFFD7DBD8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F2),
    surfaceContainer = Color(0xFFEBEFEC),
    surfaceContainerHigh = Color(0xFFE5E9E6),
    surfaceContainerHighest = Color(0xFFE0E3E1),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF81D7BF),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005142),
    onPrimaryContainer = Color(0xFFB4EFDB),
    inversePrimary = Color(0xFF0B6B58),

    secondary = Color(0xFFFFB866),
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF693C00),
    onSecondaryContainer = Color(0xFFFFDDB8),

    tertiary = Color(0xFFB4C5FF),
    onTertiary = Color(0xFF1A2D60),
    tertiaryContainer = Color(0xFF324478),
    onTertiaryContainer = Color(0xFFDBE1FF),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDFE4E1),
    surface = Color(0xFF0F1513),
    onSurface = Color(0xFFDFE4E1),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    surfaceTint = Color(0xFF81D7BF),
    inverseSurface = Color(0xFFDFE4E1),
    inverseOnSurface = Color(0xFF2D3230),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4945),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFF353B39),
    surfaceDim = Color(0xFF0F1513),
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B29),
    surfaceContainerHighest = Color(0xFF303634),
)
