package com.sahyog.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * Root theme for every Sahyog screen.
 *
 * @param dynamicColor Off by default so the Sahyog brand palette is used consistently.
 * Material You wallpaper colors would otherwise replace it on Android 12+.
 */
@Composable
fun SahyogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalSahyogExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SahyogTypography,
            shapes = SahyogShapes,
            content = content,
        )
    }
}

/**
 * Single access point for theme values, mirroring `MaterialTheme`:
 * `SahyogTheme.colors.primary`, `SahyogTheme.extendedColors.success`, `SahyogTheme.spacing.lg`.
 */
object SahyogTheme {
    val colors: ColorScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme

    val extendedColors: SahyogExtendedColors
        @Composable @ReadOnlyComposable get() = LocalSahyogExtendedColors.current

    val typography: Typography
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes

    val spacing = SahyogSpacing
}
