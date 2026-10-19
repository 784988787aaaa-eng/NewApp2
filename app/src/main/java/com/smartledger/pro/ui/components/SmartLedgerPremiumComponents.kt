package com.smartledger.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartledger.pro.domain.model.AppUsageMode
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens

@Composable
fun SmartScreenSummaryStrip(
    firstLabel: String,
    firstValue: String,
    firstColor: Color,
    secondLabel: String,
    secondValue: String,
    secondColor: Color,
    thirdLabel: String,
    thirdValue: String,
    thirdColor: Color
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple(firstLabel, firstValue, firstColor), Triple(secondLabel, secondValue, secondColor), Triple(thirdLabel, thirdValue, thirdColor)).forEach { (label, value, color) ->
            Column(
                Modifier.weight(1f).background(SmartLedgerColors.SurfaceCard, RoundedCornerShape(12.dp))
                    .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(12.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(value, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SmartSplitActionButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    quickActionIcon: ImageVector,
    quickActionTooltip: String,
    onMainClick: () -> Unit,
    onQuickActionClick: (() -> Unit)?
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 76.dp).background(SmartLedgerColors.SurfaceCard, RoundedCornerShape(14.dp))
            .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(14.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).clickable(onClick = onMainClick).padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onQuickActionClick != null) {
            IconButton(onClick = onQuickActionClick, modifier = Modifier.size(42.dp)) {
                Icon(quickActionIcon, contentDescription = quickActionTooltip, tint = accentColor)
            }
        }
    }
}

@Composable
fun SmartUsageModePill(currentMode: AppUsageMode, onModeSelected: (AppUsageMode) -> Unit) {
    val modes = listOf(AppUsageMode.CLASSIC, AppUsageMode.NORMAL, AppUsageMode.PROFESSIONAL)
    Row(
        Modifier.background(SmartLedgerColors.SurfaceCard, RoundedCornerShape(12.dp))
            .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(12.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { mode ->
            val selected = currentMode == mode
            val label = when (mode) {
                AppUsageMode.CLASSIC -> "كلاسيكي"
                AppUsageMode.NORMAL -> "عادي"
                AppUsageMode.PROFESSIONAL -> "احترافي"
            }
            Text(
                text = label,
                modifier = Modifier.clipIfNeeded(selected).clickable { onModeSelected(mode) }.padding(horizontal = 8.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) SmartLedgerColors.AccentLight else SmartLedgerColors.TextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

private fun Modifier.clipIfNeeded(selected: Boolean): Modifier =
    if (selected) this.background(SmartLedgerColors.SurfacePanel, RoundedCornerShape(9.dp)) else this

@Composable
fun SmartCashFlowPulseHero(
    title: String,
    periodBadge: String,
    inflowLabel: String,
    inflowAmount: Double,
    outflowLabel: String,
    outflowAmount: Double,
    netLabel: String,
    netAmount: Double,
    privacyMode: Boolean,
    subMetrics: List<Triple<String, Double, Color>>,
    onPeriodToggle: () -> Unit,
    onActionClick: () -> Unit,
    actionLabel: String
) {
    Column(
        Modifier.fillMaxWidth().background(SmartLedgerColors.SurfaceCard, RoundedCornerShape(18.dp))
            .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = SmartLedgerColors.TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onPeriodToggle, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                Text(periodBadge, style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCell(inflowLabel, inflowAmount, SmartLedgerColors.StateSuccess, privacyMode, Modifier.weight(1f))
            MetricCell(outflowLabel, outflowAmount, SmartLedgerColors.StateDanger, privacyMode, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(netLabel, color = SmartLedgerColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Text(if (privacyMode) "••••" else String.format(java.util.Locale.US, "%.2f", netAmount), color = if (netAmount >= 0) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            subMetrics.forEach { (label, value, color) ->
                Column(Modifier.weight(1f).background(SmartLedgerColors.SurfacePanel, RoundedCornerShape(10.dp)).padding(8.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (privacyMode) "••••" else String.format(java.util.Locale.US, "%.2f", value), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        TextButton(onClick = onActionClick, modifier = Modifier.align(Alignment.End)) { Text(actionLabel) }
    }
}

@Composable
private fun MetricCell(label: String, value: Double, color: Color, privacyMode: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.background(SmartLedgerColors.SurfacePanel, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
        Text(if (privacyMode) "••••" else String.format(java.util.Locale.US, "%.2f", value), style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

data class SmartTabOption(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val badge: String? = null
)

@Composable
fun SmartCategoryTabStrip(
    tabs: List<SmartTabOption>,
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEach { tab ->
            val selected = selectedTab == tab.id
            Surface(
                modifier = Modifier.weight(1f).clickable { onTabSelected(tab.id) },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) SmartLedgerColors.AccentPrimary.copy(alpha = 0.13f) else SmartLedgerColors.SurfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) SmartLedgerColors.AccentPrimary else SmartLedgerColors.BorderSubtle)
            ) {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(tab.icon, contentDescription = null, tint = if (selected) SmartLedgerColors.AccentLight else SmartLedgerColors.TextMuted, modifier = Modifier.size(17.dp))
                    Text(tab.title, style = MaterialTheme.typography.labelSmall, color = if (selected) SmartLedgerColors.AccentLight else SmartLedgerColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    tab.badge?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted) }
                }
            }
        }
    }
}
