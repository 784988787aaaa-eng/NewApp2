package com.smartledger.pro.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppThemeMode {
    DARK,   // الوضع الداكن الاحترافي (الكحلي الفاخر)
    LIGHT,  // الوضع الفاتح النهاري الواضح
    AMOLED  // الوضع الأسود العميق لتوفير الطاقة والتباين العالي
}

enum class AppUiDensity {
    COMFORTABLE, // مريح (مسافات قياسية متوازنة)
    COMPACT      // كثيف (إظهار عناصر أكثر في الشاشة الواحدة)
}

/**
 * حالة نظام التصميم المركزية القابلة للتخصيص الفوري والدائم
 */
object AppDesignState {
    var themeMode by mutableStateOf(AppThemeMode.DARK)
    var uiDensity by mutableStateOf(AppUiDensity.COMFORTABLE)
    var fontScale by mutableStateOf(1.0f)
    var currencySymbol by mutableStateOf("ر.ي")
    var decimalPlaces by mutableStateOf(0)
    var showBarcodeField by mutableStateOf(true)
    var showShelfLocation by mutableStateOf(true)
    var showPhoneInAccounts by mutableStateOf(true)
    var allowNegativeStock by mutableStateOf(true)
    var warnOnLowStockInPos by mutableStateOf(true)
    var warnOnCreditLimit by mutableStateOf(true)
}

/**
 * رموز المسافات والانحناءات الموحدة (Spacing & Corner Tokens)
 */
object SmartLedgerTokens {
    val cardCorner: Dp = 14.dp
    val buttonCorner: Dp = 10.dp
    val inputCorner: Dp = 10.dp
    val sheetCorner: Dp = 22.dp
    val chipCorner: Dp = 8.dp

    val screenHorizontalPadding: Dp
        get() = if (AppDesignState.uiDensity == AppUiDensity.COMPACT) 12.dp else 16.dp

    val cardInternalPadding: Dp
        get() = if (AppDesignState.uiDensity == AppUiDensity.COMPACT) 10.dp else 14.dp

    val itemSpacing: Dp
        get() = if (AppDesignState.uiDensity == AppUiDensity.COMPACT) 8.dp else 10.dp

    val sectionSpacing: Dp
        get() = if (AppDesignState.uiDensity == AppUiDensity.COMPACT) 12.dp else 16.dp
}

/**
 * لوحة الألوان الدلالية الديناميكية (Dynamic Semantic Color Tokens)
 * تتكيف تلقائياً مع الوضع الداكن (Dark)، الوضع الفاتح (Light)، والوضع الأسود العميق (AMOLED).
 */
object SmartLedgerColors {
    val isLight: Boolean
        get() = AppDesignState.themeMode == AppThemeMode.LIGHT

    // 1. Window & Surface Tokens
    val SurfaceWindow: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.DARK -> Color(0xFF0B111E)
            AppThemeMode.AMOLED -> Color(0xFF000000)
            AppThemeMode.LIGHT -> Color(0xFFF4F6F9)
        }

    val SurfaceShell: Color
        get() = SurfaceWindow

    val SurfacePanel: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.DARK -> Color(0xFF131C2E)
            AppThemeMode.AMOLED -> Color(0xFF0B0F17)
            AppThemeMode.LIGHT -> Color(0xFFFFFFFF)
        }

    val SurfaceCard: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.DARK -> Color(0xFF151F32)
            AppThemeMode.AMOLED -> Color(0xFF0E1420)
            AppThemeMode.LIGHT -> Color(0xFFFFFFFF)
        }

    val SurfaceCardElevated: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.DARK -> Color(0xFF1E2B45)
            AppThemeMode.AMOLED -> Color(0xFF172033)
            AppThemeMode.LIGHT -> Color(0xFFEEF2F7)
        }

    val SurfaceDialog: Color
        get() = SurfacePanel

    val SurfaceOverlay: Color
        get() = if (isLight) Color(0x800F172A) else Color(0xB3070B14)

    val SurfaceInput: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.DARK -> Color(0xFF101827)
            AppThemeMode.AMOLED -> Color(0xFF0A0E17)
            AppThemeMode.LIGHT -> Color(0xFFF8FAFC)
        }

    val SurfaceInputFocus: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.DARK -> Color(0xFF172338)
            AppThemeMode.AMOLED -> Color(0xFF111827)
            AppThemeMode.LIGHT -> Color(0xFFFFFFFF)
        }

    // 2. Typography & Content Tokens
    val TextPrimary: Color
        get() = if (isLight) Color(0xFF0F172A) else Color(0xFFF1F5F9)

    val TextSecondary: Color
        get() = if (isLight) Color(0xFF334155) else Color(0xFFCBD5E1)

    val TextMuted: Color
        get() = if (isLight) Color(0xFF64748B) else Color(0xFF94A3B8)

    val TextInverse: Color
        get() = Color(0xFFFFFFFF)

    val TextDisabled: Color
        get() = if (isLight) Color(0xFF94A3B8) else Color(0xFF475569)

    // 3. Primary & Accent Tokens (Financial Royal Blue & Sky Highlights)
    val AccentPrimary: Color
        get() = if (isLight) Color(0xFF0284C7) else Color(0xFF0EA5E9)

    val AccentHover: Color
        get() = if (isLight) Color(0xFF0369A1) else Color(0xFF0284C7)

    val AccentLight: Color
        get() = if (isLight) Color(0xFF0369A1) else Color(0xFF38BDF8)

    val AccentSubtle: Color
        get() = if (isLight) Color(0xFFE0F2FE) else Color(0xFF0C2D48)

    val AccentText: Color
        get() = if (isLight) Color(0xFF075985) else Color(0xFFE0F2FE)

    // 4. Operational State Tokens (Strict Accounting Color System)
    // Positive / Surplus / له (دائن) / مقبوضات
    val StateSuccess: Color
        get() = if (isLight) Color(0xFF059669) else Color(0xFF10B981)

    val StateSuccessSubtle: Color
        get() = if (isLight) Color(0xFFD1FAE5) else Color(0xFF0D2821)

    val StateSuccessBorder: Color
        get() = if (isLight) Color(0xFF6EE7B7) else Color(0xFF155E4B)

    val StateSuccessText: Color
        get() = if (isLight) Color(0xFF065F46) else Color(0xFFA7F3D0)

    // Negative / Debt / عليه (مدين) / مصروفات
    val StateDanger: Color
        get() = if (isLight) Color(0xFFDC2626) else Color(0xFFF43F5E)

    val StateDangerSubtle: Color
        get() = if (isLight) Color(0xFFFEE2E2) else Color(0xFF2C1219)

    val StateDangerBorder: Color
        get() = if (isLight) Color(0xFFFCA5A5) else Color(0xFF6E2030)

    val StateDangerText: Color
        get() = if (isLight) Color(0xFF991B1B) else Color(0xFFFECDD3)

    val StateDangerHover: Color
        get() = Color(0xFFDC2626)

    // Warning / Caution / تنبيهات المخزون
    val StateWarning: Color
        get() = if (isLight) Color(0xFFD97706) else Color(0xFFF59E0B)

    val StateWarningSubtle: Color
        get() = if (isLight) Color(0xFFFEF3C7) else Color(0xFF2D2108)

    val StateWarningBorder: Color
        get() = if (isLight) Color(0xFFFCD34D) else Color(0xFF63470F)

    // 5. Borders & Dividers
    val BorderSubtle: Color
        get() = if (isLight) Color(0xFFE2E8F0) else Color(0xFF24324B)

    val BorderStrong: Color
        get() = if (isLight) Color(0xFFCBD5E1) else Color(0xFF334155)

    val BorderGridLine: Color
        get() = if (isLight) Color(0xFFF1F5F9) else Color(0xFF1B263B)

    val BorderFocusRing: Color
        get() = AccentLight
}
