package com.smartledger.pro.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartledger.pro.data.repository.AppSettingsSnapshot

typealias AppThemeMode = com.smartledger.pro.domain.model.AppThemeMode
typealias AppUiDensity = com.smartledger.pro.domain.model.AppUiDensity

/**
 * حالة نظام التصميم المركزية القابلة للتخصيص الفوري والدائم.
 * تُزامن من طبقة الواجهة/الحاوية (`SmartLedgerTheme` / `AppContainer`) دون أن تعتمد طبقة البيانات على `ui.theme`.
 */
object AppDesignState {
    var themeMode by mutableStateOf(AppThemeMode.DARK)
    var uiDensity by mutableStateOf(AppUiDensity.COMFORTABLE)
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
            AppThemeMode.LIGHT -> Color(0xFFF3F6FA)
            AppThemeMode.AMOLED -> Color(0xFF000000)
            AppThemeMode.DARK -> Color(0xFF0B111E)
        }

    val SurfacePanel: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.LIGHT -> Color(0xFFFFFFFF)
            AppThemeMode.AMOLED -> Color(0xFF070B12)
            AppThemeMode.DARK -> Color(0xFF10192B)
        }

    val SurfaceCard: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.LIGHT -> Color(0xFFFFFFFF)
            AppThemeMode.AMOLED -> Color(0xFF0B101B)
            AppThemeMode.DARK -> Color(0xFF152036)
        }

    val SurfaceCardElevated: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.LIGHT -> Color(0xFFE9EFF8)
            AppThemeMode.AMOLED -> Color(0xFF131C2E)
            AppThemeMode.DARK -> Color(0xFF1C2B47)
        }

    val SurfaceInput: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.LIGHT -> Color(0xFFF8FAFC)
            AppThemeMode.AMOLED -> Color(0xFF0D1422)
            AppThemeMode.DARK -> Color(0xFF0E1729)
        }

    val SurfaceInputFocused: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.LIGHT -> Color(0xFFFFFFFF)
            AppThemeMode.AMOLED -> Color(0xFF121C30)
            AppThemeMode.DARK -> Color(0xFF132038)
        }

    val SurfaceInputFocus: Color
        get() = SurfaceInputFocused

    val SurfaceDialog: Color
        get() = when (AppDesignState.themeMode) {
            AppThemeMode.LIGHT -> Color(0xFFFFFFFF)
            AppThemeMode.AMOLED -> Color(0xFF0A0F1A)
            AppThemeMode.DARK -> Color(0xFF141F35)
        }

    val SurfaceScrim: Color = Color(0xB3040811)

    val SurfaceOverlay: Color
        get() = SurfaceScrim

    // 2. Border & Divider Tokens
    val BorderSubtle: Color
        get() = if (isLight) Color(0xFFD6E0EE) else Color(0xFF233352)

    val BorderStrong: Color
        get() = if (isLight) Color(0xFFB4C5DD) else Color(0xFF334973)

    val BorderFocus: Color = Color(0xFF2E90FA)

    // 3. Text Hierarchy Tokens
    val TextPrimary: Color
        get() = if (isLight) Color(0xFF0F172A) else Color(0xFFF4F7FB)

    val TextSecondary: Color
        get() = if (isLight) Color(0xFF334155) else Color(0xFFB8C5D9)

    val TextMuted: Color
        get() = if (isLight) Color(0xFF64748B) else Color(0xFF7D8FA9)

    val TextPlaceholder: Color
        get() = if (isLight) Color(0xFF94A3B8) else Color(0xFF5A6C88)

    val TextInverse: Color = Color(0xFFFFFFFF)

    // 4. Brand & Accent Tokens
    val AccentPrimary: Color = Color(0xFF1570EF)
    val AccentHover: Color = Color(0xFF175CD3)
    val AccentLight: Color
        get() = if (isLight) Color(0xFF1570EF) else Color(0xFF53B1FD)
    val AccentSubtle: Color
        get() = if (isLight) Color(0xFFE0EFFE) else Color(0xFF102A4C)
    val AccentText: Color
        get() = if (isLight) Color(0xFF175CD3) else Color(0xFF84CAFF)

    // 5. Semantic Status Tokens (Success / Credit / Profit)
    val StateSuccess: Color
        get() = if (isLight) Color(0xFF079455) else Color(0xFF17B26A)
    val StateSuccessSubtle: Color
        get() = if (isLight) Color(0xFFDCFAE6) else Color(0xFF082E1E)
    val StateSuccessBorder: Color
        get() = if (isLight) Color(0xFF75E0A7) else Color(0xFF0E5336)
    val StateSuccessText: Color
        get() = if (isLight) Color(0xFF067647) else Color(0xFF75E0A7)

    // 6. Semantic Status Tokens (Warning / Low Stock)
    val StateWarning: Color
        get() = if (isLight) Color(0xFFDC6803) else Color(0xFFF79009)
    val StateWarningSubtle: Color
        get() = if (isLight) Color(0xFFFEF0C7) else Color(0xFF332007)
    val StateWarningBorder: Color
        get() = if (isLight) Color(0xFFFEC84B) else Color(0xFF613D0B)
    val StateWarningText: Color
        get() = if (isLight) Color(0xFFB54708) else Color(0xFFFEC84B)

    // 7. Semantic Status Tokens (Danger / Debit / Debt)
    val StateDanger: Color
        get() = if (isLight) Color(0xFFD92D20) else Color(0xFFF04438)
    val StateDangerSubtle: Color
        get() = if (isLight) Color(0xFFFEE4E2) else Color(0xFF351113)
    val StateDangerBorder: Color
        get() = if (isLight) Color(0xFFFDA29B) else Color(0xFF661E20)
    val StateDangerText: Color
        get() = if (isLight) Color(0xFFB42318) else Color(0xFFFDA29B)

    // 8. Semantic Status Tokens (Info / Neutral)
    val StateInfo: Color = Color(0xFF0BA5EC)
    val StateInfoSubtle: Color
        get() = if (isLight) Color(0xFFE0F2FE) else Color(0xFF0B293D)
    val StateInfoBorder: Color
        get() = if (isLight) Color(0xFF7CD4FD) else Color(0xFF15496B)
    val StateInfoText: Color
        get() = if (isLight) Color(0xFF026AA2) else Color(0xFF7CD4FD)
}
