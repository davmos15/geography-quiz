package com.geoquiz.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Material 3 baseline, for the styles GeoQuiz does not change. */
private val Baseline = Typography()

/**
 * The full type scale. Four styles are GeoQuiz's own (bodyLarge, titleLarge, headlineMedium,
 * labelSmall); the rest are the Material 3 baseline, listed so the whole scale is visible here.
 *
 * Sizes and line heights are in sp, so both scale with the system font size (no clipping at
 * 200%). Never put text in a fixed-height container.
 */
val Typography = Typography(
    displayLarge = Baseline.displayLarge,       // 57 / 64
    displayMedium = Baseline.displayMedium,     // 45 / 52
    displaySmall = Baseline.displaySmall,       // 36 / 44
    headlineLarge = Baseline.headlineLarge,     // 32 / 40
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = Baseline.headlineSmall,     // 24 / 32
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = Baseline.titleMedium,         // 16 / 24, medium
    titleSmall = Baseline.titleSmall,           // 14 / 20, medium
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = Baseline.bodyMedium,           // 14 / 20
    bodySmall = Baseline.bodySmall,             // 12 / 16
    labelLarge = Baseline.labelLarge,           // 14 / 20, medium
    labelMedium = Baseline.labelMedium,         // 12 / 16, medium
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
