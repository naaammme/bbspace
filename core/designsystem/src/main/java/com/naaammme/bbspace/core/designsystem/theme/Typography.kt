package com.naaammme.bbspace.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val BaseTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

fun createTypography(fontScale: Float): Typography {
    if (fontScale == 1.0f) return BaseTypography

    return Typography(
        displayLarge = BaseTypography.displayLarge.copy(fontSize = BaseTypography.displayLarge.fontSize * fontScale),
        displayMedium = BaseTypography.displayMedium.copy(fontSize = BaseTypography.displayMedium.fontSize * fontScale),
        displaySmall = BaseTypography.displaySmall.copy(fontSize = BaseTypography.displaySmall.fontSize * fontScale),
        headlineLarge = BaseTypography.headlineLarge.copy(fontSize = BaseTypography.headlineLarge.fontSize * fontScale),
        headlineMedium = BaseTypography.headlineMedium.copy(fontSize = BaseTypography.headlineMedium.fontSize * fontScale),
        headlineSmall = BaseTypography.headlineSmall.copy(fontSize = BaseTypography.headlineSmall.fontSize * fontScale),
        titleLarge = BaseTypography.titleLarge.copy(fontSize = BaseTypography.titleLarge.fontSize * fontScale),
        titleMedium = BaseTypography.titleMedium.copy(fontSize = BaseTypography.titleMedium.fontSize * fontScale),
        titleSmall = BaseTypography.titleSmall.copy(fontSize = BaseTypography.titleSmall.fontSize * fontScale),
        bodyLarge = BaseTypography.bodyLarge.copy(fontSize = BaseTypography.bodyLarge.fontSize * fontScale),
        bodyMedium = BaseTypography.bodyMedium.copy(fontSize = BaseTypography.bodyMedium.fontSize * fontScale),
        bodySmall = BaseTypography.bodySmall.copy(fontSize = BaseTypography.bodySmall.fontSize * fontScale),
        labelLarge = BaseTypography.labelLarge.copy(fontSize = BaseTypography.labelLarge.fontSize * fontScale),
        labelMedium = BaseTypography.labelMedium.copy(fontSize = BaseTypography.labelMedium.fontSize * fontScale),
        labelSmall = BaseTypography.labelSmall.copy(fontSize = BaseTypography.labelSmall.fontSize * fontScale)
    )
}

