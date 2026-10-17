package com.smartledger.pro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp

/**
 * بناء نظام الخطوط المتكيف مع مقياس حجم النص المختار من المستخدم (Font Scale)
 * مع ضمان اتجاه RTL ووضوح الأرقام المالية والعناوين.
 */
fun buildSmartLedgerTypography(scale: Float = 1.0f): Typography {
    val s = scale.coerceIn(0.85f, 1.25f)
    return Typography(
        headlineLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = (24 * s).sp,
            lineHeight = (32 * s).sp,
            textDirection = TextDirection.Rtl,
            color = SmartLedgerColors.TextPrimary
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = (20 * s).sp,
            lineHeight = (28 * s).sp,
            textDirection = TextDirection.Rtl,
            color = SmartLedgerColors.TextPrimary
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = (18 * s).sp,
            lineHeight = (25 * s).sp,
            textDirection = TextDirection.Rtl,
            color = SmartLedgerColors.TextPrimary
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = (15 * s).sp,
            lineHeight = (22 * s).sp,
            textDirection = TextDirection.Rtl,
            color = SmartLedgerColors.TextPrimary
        ),
        titleSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = (13.5f * s).sp,
            lineHeight = (19 * s).sp,
            textDirection = TextDirection.Rtl,
            color = SmartLedgerColors.TextPrimary
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (14.5f * s).sp,
            lineHeight = (21 * s).sp,
            textDirection = TextDirection.Rtl,
            color = SmartLedgerColors.TextPrimary
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (13 * s).sp,
            lineHeight = (19 * s).sp,
            textDirection = TextDirection.Rtl,
            color = SmartLedgerColors.TextSecondary
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = (13 * s).sp,
            lineHeight = (18 * s).sp,
            textDirection = TextDirection.Rtl,
            color = SmartLedgerColors.TextPrimary
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = (12 * s).sp,
            lineHeight = (16 * s).sp,
            textDirection = TextDirection.Rtl,
            color = SmartLedgerColors.TextSecondary
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = (11.5f * s).sp,
            lineHeight = (16 * s).sp,
            textDirection = TextDirection.Rtl,
            color = SmartLedgerColors.TextMuted
        )
    )
}

val Typography: Typography
    get() = buildSmartLedgerTypography(AppDesignState.fontScale)
