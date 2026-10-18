package com.smartledger.pro.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartledger.pro.data.repository.AppSettingsSnapshot
import com.smartledger.pro.domain.model.AppAccentPalette
import com.smartledger.pro.domain.model.AppCornerStyle
import com.smartledger.pro.domain.model.AppFabStyle
import com.smartledger.pro.domain.model.AppNavStyle
import com.smartledger.pro.domain.model.AppUsageMode

typealias AppThemeMode = com.smartledger.pro.domain.model.AppThemeMode
typealias AppUiDensity = com.smartledger.pro.domain.model.AppUiDensity

/**
 * حالة نظام التصميم المركزية القابلة للتخصيص الفوري والدائم.
 * تُزامن من طبقة الواجهة/الحاوية (`SmartLedgerTheme` / `AppContainer`) دون أن تعتمد طبقة البيانات على `ui.theme`.
 */
object AppDesignState {
    var themeMode by mutableStateOf(AppThemeMode.DARK)
    var uiDensity by mutableStateOf(AppUiDensity.COMFORTABLE)
    var usageMode by mutableStateOf(AppUsageMode.PRO)
    var accentPalette by mutableStateOf(AppAccentPalette.ROYAL_SAPPHIRE)
    var navStyle by mutableStateOf(AppNavStyle.FLOATING_DOCK)
    var fabStyle by mutableStateOf(AppFabStyle.ORBITAL_RADIAL)
    var cornerStyle by mutableStateOf(AppCornerStyle.ROUNDED)
    var fontScale by mutableStateOf(1.0f)
    var currencySymbol by mutableStateOf("ر.ي")
    var decimalPlaces by mutableStateOf(0)
    var defaultSmallUnit by mutableStateOf("قطعة")
    var defaultLargeUnit by mutableStateOf("كرتون")
    var defaultUnitFactor by mutableStateOf(24.0)
    var defaultAlertThreshold by mutableStateOf(5.0)
    var showBarcodeField by mutableStateOf(true)
    var showShelfLocation by mutableStateOf(true)
    var showPhoneInAccounts by mutableStateOf(true)
    var allowNegativeStock by mutableStateOf(true)
    var warnOnLowStockInPos by mutableStateOf(true)
    var warnOnCreditLimit by mutableStateOf(true)

    fun syncFromSettings(s: AppSettingsSnapshot) {
        themeMode = s.themeMode
        uiDensity = s.uiDensity
        usageMode = s.usageMode
        accentPalette = s.accentPalette
        navStyle = s.navStyle
        fabStyle = s.fabStyle
        cornerStyle = s.cornerStyle
        fontScale = s.fontScale
        currencySymbol = s.currencySymbol
        decimalPlaces = s.decimalPlaces
        defaultSmallUnit = s.defaultSmallUnit
        defaultLargeUnit = s.defaultLargeUnit
        defaultUnitFactor = s.defaultUnitFactor
        defaultAlertThreshold = s.defaultAlertThreshold
        showBarcodeField = s.showBarcodeField
        showShelfLocation = s.showShelfLocation
        showPhoneInAccounts = s.showPhoneInAccounts
        allowNegativeStock = s.allowNegativeStock
        warnOnLowStockInPos = s.warnOnLowStockInPos
        warnOnCreditLimit = s.warnOnCreditLimit
    }
}

/**
 * رموز المسافات والانحناءات الموحدة المتكيفة مع اختيار المستخدم (Spacing & Dynamic Corner Tokens)
 */
object SmartLedgerTokens {
    val cardCorner: Dp
        get() = when (AppDesignState.cornerStyle) {
            AppCornerStyle.PILL -> 22.dp
            AppCornerStyle.SHARP -> 6.dp
            AppCornerStyle.ROUNDED -> 16.dp
        }

    val buttonCorner: Dp
        get() = when (AppDesignState.cornerStyle) {
            AppCornerStyle.PILL -> 50.dp
            AppCornerStyle.SHARP -> 4.dp
            AppCornerStyle.ROUNDED -> 12.dp
        }

    val inputCorner: Dp
        get() = when (AppDesignState.cornerStyle) {
            AppCornerStyle.PILL -> 18.dp
            AppCornerStyle.SHARP -> 4.dp
            AppCornerStyle.ROUNDED -> 12.dp
        }

    val sheetCorner: Dp
        get() = when (AppDesignState.cornerStyle) {
            AppCornerStyle.PILL -> 30.dp
            AppCornerStyle.SHARP -> 10.dp
            AppCornerStyle.ROUNDED -> 24.dp
        }

    val chipCorner: Dp
        get() = when (AppDesignState.cornerStyle) {
            AppCornerStyle.PILL -> 50.dp
            AppCornerStyle.SHARP -> 4.dp
            AppCornerStyle.ROUNDED -> 10.dp
        }

    val screenHorizontalPadding: Dp
        get() = if (AppDesignState.uiDensity == AppUiDensity.COMPACT) 10.dp else 15.dp

    val cardInternalPadding: Dp
        get() = if (AppDesignState.uiDensity == AppUiDensity.COMPACT) 10.dp else 14.dp

    val itemSpacing: Dp
        get() = if (AppDesignState.uiDensity == AppUiDensity.COMPACT) 7.dp else 10.dp

    val sectionSpacing: Dp
        get() = if (AppDesignState.uiDensity == AppUiDensity.COMPACT) 10.dp else 14.dp
}

/**
 * لوحة الألوان الدلالية الديناميكية (Dynamic Semantic & Multi-Palette Color Tokens)
 * تتكيف تلقائياً مع الوضع الداكن (Dark)، الوضع الفاتح (Light)، الوضع الأسود العميق (AMOLED)،
 * والباليت اللوني المختار من المستخدم (Royal Sapphire, Emerald Luxe, Amber Gold, Violet Pro).
 */
object SmartLedgerColors {
    val isLight: Boolean
        get() = AppDesignState.themeMode == AppThemeMode.LIGHT

    // 1. Window & Surface Tokens
    val SurfaceWindow: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.LIGHT -> Color(0xFFF4F7FB)
            AppThemeMode.AMOLED -> Color(0xFF000000)
            AppThemeMode.DARK -> Color(0xFF080E1A)
        }

    val SurfacePanel: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.LIGHT -> Color(0xFFFFFFFF)
            AppThemeMode.AMOLED -> Color(0xFF060A12)
            AppThemeMode.DARK -> Color(0xFF0E1729)
        }

    val SurfaceCard: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.LIGHT -> Color(0xFFFFFFFF)
            AppThemeMode.AMOLED -> Color(0xFF0B111E)
            AppThemeMode.DARK -> Color(0xFF131F35)
        }

    val SurfaceCardElevated: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.LIGHT -> Color(0xFFEDF2F9)
            AppThemeMode.AMOLED -> Color(0xFF121C30)
            AppThemeMode.DARK -> Color(0xFF1A2945)
        }

    val SurfaceInput: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.LIGHT -> Color(0xFFF8FAFC)
            AppThemeMode.AMOLED -> Color(0xFF0B1220)
            AppThemeMode.DARK -> Color(0xFF0C1526)
        }

    val SurfaceInputFocused: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.LIGHT -> Color(0xFFFFFFFF)
            AppThemeMode.AMOLED -> Color(0xFF111B30)
            AppThemeMode.DARK -> Color(0xFF12203A)
        }

    val SurfaceInputFocus: Color
        get() = SurfaceInputFocused

    val SurfaceDialog: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.LIGHT -> Color(0xFFFFFFFF)
            AppThemeMode.AMOLED -> Color(0xFF090E18)
            AppThemeMode.DARK -> Color(0xFF121D33)
        }

    val SurfaceScrim: Color = Color(0xBF030710)

    val SurfaceOverlay: Color
        get() = SurfaceScrim

    // 2. Border & Divider Tokens
    val BorderSubtle: Color
        get() = if (isLight) Color(0xFFD8E2F0) else Color(0xFF21314F)

    val BorderStrong: Color
        get() = if (isLight) Color(0xFFB4C5DD) else Color(0xFF314873)

    val BorderFocus: Color
        get() = AccentLight

    // 3. Text Hierarchy Tokens
    val TextPrimary: Color
        get() = if (isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC)

    val TextSecondary: Color
        get() = if (isLight) Color(0xFF334155) else Color(0xFFCBD5E1)

    val TextMuted: Color
        get() = if (isLight) Color(0xFF64748B) else Color(0xFF8395B0)

    val TextPlaceholder: Color
        get() = if (isLight) Color(0xFF94A3B8) else Color(0xFF5A6C88)

    val TextInverse: Color = Color(0xFFFFFFFF)

    // 4. Dynamic Brand & Accent Tokens (تتغير فورياً حسب الباليت المختار)
    val AccentPrimary: Color
        get() = when (AppDesignState.accentPalette) {
            AppAccentPalette.ROYAL_SAPPHIRE -> Color(0xFF1570EF)
            AppAccentPalette.EMERALD_LUXE -> Color(0xFF0D9488)
            AppAccentPalette.AMBER_GOLD -> Color(0xFFD97706)
            AppAccentPalette.VIOLET_PRO -> Color(0xFF7C3AED)
        }

    val AccentHover: Color
        get() = when (AppDesignState.accentPalette) {
            AppAccentPalette.ROYAL_SAPPHIRE -> Color(0xFF175CD3)
            AppAccentPalette.EMERALD_LUXE -> Color(0xFF0F766E)
            AppAccentPalette.AMBER_GOLD -> Color(0xFFB45309)
            AppAccentPalette.VIOLET_PRO -> Color(0xFF6D28D9)
        }

    val AccentLight: Color
        get() = when (AppDesignState.accentPalette) {
            AppAccentPalette.ROYAL_SAPPHIRE -> if (isLight) Color(0xFF1570EF) else Color(0xFF53B1FD)
            AppAccentPalette.EMERALD_LUXE -> if (isLight) Color(0xFF0F766E) else Color(0xFF2DD4BF)
            AppAccentPalette.AMBER_GOLD -> if (isLight) Color(0xFFB45309) else Color(0xFFFBBF24)
            AppAccentPalette.VIOLET_PRO -> if (isLight) Color(0xFF6D28D9) else Color(0xFFA78BFA)
        }

    val AccentSubtle: Color
        get() = when (AppDesignState.accentPalette) {
            AppAccentPalette.ROYAL_SAPPHIRE -> if (isLight) Color(0xFFE0EFFE) else Color(0xFF102A4C)
            AppAccentPalette.EMERALD_LUXE -> if (isLight) Color(0xFFCCFBF1) else Color(0xFF0A2E2C)
            AppAccentPalette.AMBER_GOLD -> if (isLight) Color(0xFFFEF3C7) else Color(0xFF332108)
            AppAccentPalette.VIOLET_PRO -> if (isLight) Color(0xFFEDE9FE) else Color(0xFF231645)
        }

    val AccentText: Color
        get() = when (AppDesignState.accentPalette) {
            AppAccentPalette.ROYAL_SAPPHIRE -> if (isLight) Color(0xFF175CD3) else Color(0xFF84CAFF)
            AppAccentPalette.EMERALD_LUXE -> if (isLight) Color(0xFF0F766E) else Color(0xFF5EEAD4)
            AppAccentPalette.AMBER_GOLD -> if (isLight) Color(0xFFB45309) else Color(0xFFFDE68A)
            AppAccentPalette.VIOLET_PRO -> if (isLight) Color(0xFF5B21B6) else Color(0xFFC4B5FD)
        }

    // 5. Semantic Status Tokens (Success / Inflow / Credit / Profit)
    val StateSuccess: Color
        get() = if (isLight) Color(0xFF079455) else Color(0xFF10B981)
    val StateSuccessSubtle: Color
        get() = if (isLight) Color(0xFFDCFAE6) else Color(0xFF072B1E)
    val StateSuccessBorder: Color
        get() = if (isLight) Color(0xFF75E0A7) else Color(0xFF0F543A)
    val StateSuccessText: Color
        get() = if (isLight) Color(0xFF067647) else Color(0xFF6EE7B7)

    // 6. Semantic Status Tokens (Warning / Low Stock)
    val StateWarning: Color
        get() = if (isLight) Color(0xFFDC6803) else Color(0xFFF59E0B)
    val StateWarningSubtle: Color
        get() = if (isLight) Color(0xFFFEF0C7) else Color(0xFF332007)
    val StateWarningBorder: Color
        get() = if (isLight) Color(0xFFFEC84B) else Color(0xFF66400B)
    val StateWarningText: Color
        get() = if (isLight) Color(0xFFB54708) else Color(0xFFFCD34D)

    // 7. Semantic Status Tokens (Danger / Outflow / Debit / Debt)
    val StateDanger: Color
        get() = if (isLight) Color(0xFFD92D20) else Color(0xFFF43F5E)
    val StateDangerSubtle: Color
        get() = if (isLight) Color(0xFFFEE4E2) else Color(0xFF351018)
    val StateDangerBorder: Color
        get() = if (isLight) Color(0xFFFDA29B) else Color(0xFF6B1D2B)
    val StateDangerText: Color
        get() = if (isLight) Color(0xFFB42318) else Color(0xFFFDA4AF)

    // 8. Semantic Status Tokens (Info / Neutral)
    val StateInfo: Color = Color(0xFF0BA5EC)
    val StateInfoSubtle: Color
        get() = if (isLight) Color(0xFFE0F2FE) else Color(0xFF0B293D)
    val StateInfoBorder: Color
        get() = if (isLight) Color(0xFF7CD4FD) else Color(0xFF15496B)
    val StateInfoText: Color
        get() = if (isLight) Color(0xFF026AA2) else Color(0xFF7CD4FD)
}
