package com.sahyog.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors Material 3 doesn't define but a services app needs:
 * booking/payment status (success, warning), star ratings and card surfaces.
 * Read them through `SahyogTheme.extendedColors`.
 */
@Immutable
data class SahyogExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val rating: Color,
    val ratingTrack: Color,
    /** Card background: white on the tinted light surface, a raised tone in dark. */
    val card: Color,
)

internal val LightExtendedColors = SahyogExtendedColors(
    success = Color(0xFF1E6B2E),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFC6EFC4),
    onSuccessContainer = Color(0xFF002106),
    warning = Color(0xFF7A5900),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDF9E),
    onWarningContainer = Color(0xFF261A00),
    rating = Color(0xFFC77700),
    ratingTrack = Color(0xFFDDE3E0),
    card = Color(0xFFFFFFFF),
)

internal val DarkExtendedColors = SahyogExtendedColors(
    success = Color(0xFF8BD88E),
    onSuccess = Color(0xFF00390F),
    successContainer = Color(0xFF005319),
    onSuccessContainer = Color(0xFFC6EFC4),
    warning = Color(0xFFF5BF48),
    onWarning = Color(0xFF3F2E00),
    warningContainer = Color(0xFF5C4200),
    onWarningContainer = Color(0xFFFFDF9E),
    rating = Color(0xFFFFB866),
    ratingTrack = Color(0xFF3A4240),
    card = Color(0xFF1B211F),
)

internal val LocalSahyogExtendedColors = staticCompositionLocalOf { LightExtendedColors }
