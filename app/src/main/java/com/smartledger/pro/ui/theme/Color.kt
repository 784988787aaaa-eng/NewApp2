package com.smartledger.pro.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object SmartLedgerColors {
    var isLightMode by mutableStateOf(false)

    val SurfaceWindow: Color get() = if (isLightMode) Color(0xFFF4F6F9) else Color(0xFF0D1117)
    val SurfaceShell: Color get() = if (isLightMode) Color(0xFFF4F6F9) else Color(0xFF0D1117)
    val SurfacePanel: Color get() = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF161B22)
    val SurfaceCard: Color get() = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF161B22)
    val SurfaceCardElevated: Color get() = if (isLightMode) Color(0xFFEEF2F6) else Color(0xFF21262D)
    val SurfaceDialog: Color get() = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF161B22)
    val SurfaceOverlay: Color get() = if (isLightMode) Color(0x990F172A) else Color(0xB30D1117)
    val SurfaceInput: Color get() = if (isLightMode) Color(0xFFF8FAFC) else Color(0xFF131820)
    val SurfaceInputFocus: Color get() = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF1A222D)

    val TextPrimary: Color get() = if (isLightMode) Color(0xFF0F172A) else Color(0xFFF0F6FC)
    val TextSecondary: Color get() = if (isLightMode) Color(0xFF334155) else Color(0xFFC9D1D9)
    val TextMuted: Color get() = if (isLightMode) Color(0xFF64748B) else Color(0xFF8B949E)
    val TextInverse: Color get() = Color(0xFFFFFFFF)
    val TextDisabled: Color get() = if (isLightMode) Color(0xFF94A3B8) else Color(0xFF484F58)

    val AccentPrimary: Color get() = if (isLightMode) Color(0xFF0369A1) else Color(0xFF0284C7)
    val AccentHover: Color get() = Color(0xFF0369A1)
    val AccentLight: Color get() = if (isLightMode) Color(0xFF0284C7) else Color(0xFF38BDF8)
    val AccentSubtle: Color get() = if (isLightMode) Color(0xFFE0F2FE) else Color(0xFF0C3456)
    val AccentText: Color get() = if (isLightMode) Color(0xFF075985) else Color(0xFFE0F2FE)

    val StateSuccess: Color get() = if (isLightMode) Color(0xFF059669) else Color(0xFF34D399)
    val StateSuccessSubtle: Color get() = if (isLightMode) Color(0xFFECFDF5) else Color(0xFF10251E)
    val StateSuccessBorder: Color get() = if (isLightMode) Color(0xFFA7F3D0) else Color(0xFF1B4D3E)
    val StateSuccessText: Color get() = if (isLightMode) Color(0xFF065F46) else Color(0xFFA7F3D0)

    val StateDanger: Color get() = if (isLightMode) Color(0xFFDC2626) else Color(0xFFF87171)
    val StateDangerSubtle: Color get() = if (isLightMode) Color(0xFFFEF2F2) else Color(0xFF2A161B)
    val StateDangerBorder: Color get() = if (isLightMode) Color(0xFFFECACA) else Color(0xFF5C252E)
    val StateDangerText: Color get() = if (isLightMode) Color(0xFF991B1B) else Color(0xFFFECACA)
    val StateDangerHover: Color get() = Color(0xFFDC2626)

    val StateWarning: Color get() = if (isLightMode) Color(0xFFD97706) else Color(0xFFFBBF24)
    val StateWarningSubtle: Color get() = if (isLightMode) Color(0xFFFFFBEB) else Color(0xFF2E2305)
    val StateWarningBorder: Color get() = if (isLightMode) Color(0xFFFDE68A) else Color(0xFF5C450A)

    val BorderSubtle: Color get() = if (isLightMode) Color(0xFFE2E8F0) else Color(0xFF30363D)
    val BorderStrong: Color get() = if (isLightMode) Color(0xFFCBD5E1) else Color(0xFF484F58)
    val BorderGridLine: Color get() = if (isLightMode) Color(0xFFF1F5F9) else Color(0xFF21262D)
    val BorderFocusRing: Color get() = if (isLightMode) Color(0xFF0284C7) else Color(0xFF38BDF8)
}
