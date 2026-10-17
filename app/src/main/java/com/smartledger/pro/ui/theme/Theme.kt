package com.smartledger.pro.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat

@Composable
fun SmartLedgerTheme(
    content: @Composable () -> Unit
) {
    val isLight = SmartLedgerColors.isLight
    val colorScheme = if (isLight) {
        lightColorScheme(
            primary = SmartLedgerColors.AccentPrimary,
            onPrimary = SmartLedgerColors.TextInverse,
            primaryContainer = SmartLedgerColors.AccentSubtle,
            onPrimaryContainer = SmartLedgerColors.AccentText,
            secondary = SmartLedgerColors.AccentLight,
            onSecondary = SmartLedgerColors.TextInverse,
            background = SmartLedgerColors.SurfaceWindow,
            onBackground = SmartLedgerColors.TextPrimary,
            surface = SmartLedgerColors.SurfaceCard,
            onSurface = SmartLedgerColors.TextPrimary,
            surfaceVariant = SmartLedgerColors.SurfaceCardElevated,
            onSurfaceVariant = SmartLedgerColors.TextSecondary,
            outline = SmartLedgerColors.BorderSubtle,
            error = SmartLedgerColors.StateDanger,
            onError = SmartLedgerColors.TextInverse,
            errorContainer = SmartLedgerColors.StateDangerSubtle,
            onErrorContainer = SmartLedgerColors.StateDangerText
        )
    } else {
        darkColorScheme(
            primary = SmartLedgerColors.AccentPrimary,
            onPrimary = SmartLedgerColors.TextInverse,
            primaryContainer = SmartLedgerColors.AccentSubtle,
            onPrimaryContainer = SmartLedgerColors.AccentText,
            secondary = SmartLedgerColors.AccentLight,
            onSecondary = SmartLedgerColors.SurfaceWindow,
            background = SmartLedgerColors.SurfaceWindow,
            onBackground = SmartLedgerColors.TextPrimary,
            surface = SmartLedgerColors.SurfaceCard,
            onSurface = SmartLedgerColors.TextPrimary,
            surfaceVariant = SmartLedgerColors.SurfaceCardElevated,
            onSurfaceVariant = SmartLedgerColors.TextSecondary,
            outline = SmartLedgerColors.BorderSubtle,
            error = SmartLedgerColors.StateDanger,
            onError = SmartLedgerColors.TextInverse,
            errorContainer = SmartLedgerColors.StateDangerSubtle,
            onErrorContainer = SmartLedgerColors.StateDangerText
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            if (activity != null) {
                val window = activity.window
                window.statusBarColor = SmartLedgerColors.SurfacePanel.toArgb()
                window.navigationBarColor = SmartLedgerColors.SurfacePanel.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = isLight
                controller.isAppearanceLightNavigationBars = isLight
            }
        }
    }

    // فرض اتجاه RTL الصحيح لجميع شاشات ونوافذ التطبيق بغض النظر عن لغة جهاز المستخدم
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildSmartLedgerTypography(AppDesignState.fontScale),
            content = content
        )
    }
}
