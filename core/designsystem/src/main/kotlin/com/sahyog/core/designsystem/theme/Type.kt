package com.sahyog.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sahyog.core.designsystem.R

/*
 * Plus Jakarta Sans (SIL OFL 1.1, bundled as one variable font, ~170 KB).
 * Devanagari and other scripts it lacks fall back to the system Noto fonts,
 * so Hindi text renders correctly without bundling more files.
 */
@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) = Font(
    resId = R.font.plus_jakarta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val PlusJakartaSans = FontFamily(
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing,
)

/** Material 3 type scale sizes; weights tuned for a confident, legible brand voice. */
internal val SahyogTypography = Typography(
    displayLarge = style(57, 64, FontWeight.Bold, (-0.5).sp),
    displayMedium = style(45, 52, FontWeight.Bold, (-0.25).sp),
    displaySmall = style(36, 44, FontWeight.Bold, (-0.25).sp),
    headlineLarge = style(32, 40, FontWeight.SemiBold, (-0.25).sp),
    headlineMedium = style(28, 36, FontWeight.SemiBold),
    headlineSmall = style(24, 32, FontWeight.SemiBold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.1.sp),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1.sp),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.15.sp),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.15.sp),
    bodySmall = style(12, 16, FontWeight.Normal, 0.3.sp),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.1.sp),
    labelMedium = style(12, 16, FontWeight.Medium, 0.4.sp),
    labelSmall = style(11, 16, FontWeight.Medium, 0.4.sp),
)
