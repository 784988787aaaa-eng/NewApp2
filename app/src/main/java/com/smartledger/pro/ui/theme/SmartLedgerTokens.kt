package com.smartledger.pro.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartledger.pro.ui.components.AppSensoryConfig

object SmartLedgerTokens {
    val SpaceXXS: Dp = 4.dp
    val SpaceXS: Dp = 8.dp
    val SpaceSM: Dp
        @Composable @ReadOnlyComposable
        get() = if (AppSensoryConfig.compactDensity) 8.dp else 12.dp
    val SpaceMD: Dp
        @Composable @ReadOnlyComposable
        get() = if (AppSensoryConfig.compactDensity) 12.dp else 16.dp
    val SpaceLG: Dp
        @Composable @ReadOnlyComposable
        get() = if (AppSensoryConfig.compactDensity) 18.dp else 24.dp
    val SpaceXL: Dp = 32.dp

    val RadiusSmall: Dp = 8.dp
    val RadiusButton: Dp = 12.dp
    val RadiusCard: Dp = 16.dp
    val RadiusSheet: Dp = 24.dp
    val RadiusPill: Dp = 999.dp

    val ButtonHeightPrimary: Dp = 48.dp
    val ButtonHeightCompact: Dp = 36.dp
    val SearchBarHeight: Dp = 42.dp
}
