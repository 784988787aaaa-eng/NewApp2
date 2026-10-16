package com.smartledger.pro.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.smartledger.pro.util.AppSettingsManager

@Composable
fun SmartLedgerTheme(content: @Composable () -> Unit) {
    val settings by AppSettingsManager.settings.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val useLight = when (settings.themeMode) {
        "LIGHT" -> true
        "SYSTEM" -> !systemDark
        else -> false
    }
    SmartLedgerColors.isLightMode = useLight

    val colorScheme = if (useLight) {
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
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = SmartLedgerColors.SurfaceWindow.toArgb()
            window.navigationBarColor = SmartLedgerColors.SurfacePanel.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = useLight
            controller.isAppearanceLightNavigationBars = useLight
        }
    }

    val currentDensity = LocalDensity.current
    val scaledDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * settings.fontScale
    )

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildSmartLedgerTypography(),
            content = content
        )
    }
}
