package com.smartledger.pro.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartledger.pro.ui.theme.SmartLedgerColors

/**
 * Daily shortcuts are isolated from dashboard data orchestration and financial calculations.
 * Keep navigation destinations stable; they are consumed by the app's central navigator.
 */
@Composable
internal fun DashboardQuickActions(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "العمليات السريعة",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SmartLedgerColors.TextPrimary
            )
            Text(
                text = "الوصول المباشر إلى أكثر المهام استخداماً",
                style = MaterialTheme.typography.bodySmall,
                color = SmartLedgerColors.TextMuted
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    modifier = Modifier.weight(1f),
                    title = "فاتورة بيع",
                    subtitle = "نقطة البيع POS",
                    icon = Icons.Default.PointOfSale,
                    color = SmartLedgerColors.AccentPrimary
                ) { onNavigate("pos_sales") }

                QuickActionTile(
                    modifier = Modifier.weight(1f),
                    title = "سند قبض / صرف",
                    subtitle = "تحصيل وسداد",
                    icon = Icons.Default.ReceiptLong,
                    color = SmartLedgerColors.StateSuccess
                ) { onNavigate("vouchers") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    modifier = Modifier.weight(1f),
                    title = "فاتورة شراء",
                    subtitle = "توريد للمخزون",
                    icon = Icons.Default.LocalShipping,
                    color = SmartLedgerColors.AccentLight
                ) { onNavigate("purchases") }

                QuickActionTile(
                    modifier = Modifier.weight(1f),
                    title = "المخزون والأصناف",
                    subtitle = "الأرصدة والجرد",
                    icon = Icons.Default.Inventory2,
                    color = SmartLedgerColors.AccentPrimary
                ) { onNavigate("inventory") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    modifier = Modifier.weight(1f),
                    title = "المصروفات",
                    subtitle = "نفقات التشغيل",
                    icon = Icons.Default.TrendingDown,
                    color = SmartLedgerColors.StateDanger
                ) { onNavigate("expenses") }

                QuickActionTile(
                    modifier = Modifier.weight(1f),
                    title = "التقارير المالية",
                    subtitle = "الأرباح والدخل",
                    icon = Icons.Default.Assessment,
                    color = SmartLedgerColors.StateWarning
                ) { onNavigate("reports") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    modifier = Modifier.weight(1f),
                    title = "كشوف الحساب",
                    subtitle = "العملاء والموردون",
                    icon = Icons.Default.AccountBalanceWallet,
                    color = SmartLedgerColors.TextSecondary
                ) { onNavigate("accounts") }

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
