package com.smartledger.pro.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartledger.pro.ui.components.SmartSplitActionButton
import com.smartledger.pro.ui.theme.SmartLedgerColors

/**
 * شبكة الأزرار الذكية المدمجة (Smart Compact Split Actions) — "مساحة أقل، خدمة أكبر"
 * كل كبسولة تمنح المستخدم وظيفتين في نفس المكان:
 * - النقر على جسم البطاقة: يفتح الشاشة أو السجل الكامل.
 * - النقر على زر (+) المدمج في طرف البطاقة: يفتح نافذة الإضافة السريعة المباشرة لتلك العملية فوراً!
 */
@Composable
internal fun DashboardQuickActions(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "الأوامر الذكية المدمجة",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SmartLedgerColors.TextPrimary
            )
            Text(
                text = "اضغط للفتح أو (+) للإدخال المباشر",
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.AccentLight
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmartSplitActionButton(
                modifier = Modifier.weight(1f),
                title = "المبيعات POS",
                subtitle = "فواتير وكاشير",
                icon = Icons.Default.PointOfSale,
                accentColor = SmartLedgerColors.AccentPrimary,
                quickActionIcon = Icons.Default.Add,
                quickActionTooltip = "فاتورة بيع جديدة",
                onMainClick = { onNavigate("pos_sales") },
                onQuickActionClick = { onNavigate("create:sale") }
            )

            SmartSplitActionButton(
                modifier = Modifier.weight(1f),
                title = "سندات القبض والصرف",
                subtitle = "تحصيل وسداد",
                icon = Icons.Default.ReceiptLong,
                accentColor = SmartLedgerColors.StateSuccess,
                quickActionIcon = Icons.Default.Add,
                quickActionTooltip = "سند قبض جديد",
                onMainClick = { onNavigate("vouchers") },
                onQuickActionClick = { onNavigate("create:receipt") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmartSplitActionButton(
                modifier = Modifier.weight(1f),
                title = "المشتريات والتوريد",
                subtitle = "فواتير الموردين",
                icon = Icons.Default.LocalShipping,
                accentColor = SmartLedgerColors.AccentLight,
                quickActionIcon = Icons.Default.Add,
                quickActionTooltip = "فاتورة شراء جديدة",
                onMainClick = { onNavigate("purchases") },
                onQuickActionClick = { onNavigate("create:purchase") }
            )

            SmartSplitActionButton(
                modifier = Modifier.weight(1f),
                title = "المخزون والأصناف",
                subtitle = "الأرصدة والجرد",
                icon = Icons.Default.Inventory2,
                accentColor = SmartLedgerColors.StateWarning,
                quickActionIcon = Icons.Default.Add,
                quickActionTooltip = "إضافة صنف جديد",
                onMainClick = { onNavigate("inventory") },
                onQuickActionClick = { onNavigate("create:product") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmartSplitActionButton(
                modifier = Modifier.weight(1f),
                title = "المصروفات والإيراد",
                subtitle = "نفقات التشغيل",
                icon = Icons.Default.TrendingDown,
                accentColor = SmartLedgerColors.StateDanger,
                quickActionIcon = Icons.Default.Add,
                quickActionTooltip = "تسجيل مصروف أو إيراد",
                onMainClick = { onNavigate("expenses") },
                onQuickActionClick = { onNavigate("create:expense") }
            )

            SmartSplitActionButton(
                modifier = Modifier.weight(1f),
                title = "الحسابات والديون",
                subtitle = "عملاء وموردون",
                icon = Icons.Default.AccountBalanceWallet,
                accentColor = SmartLedgerColors.AccentLight,
                quickActionIcon = Icons.Default.Add,
                quickActionTooltip = "فتح حساب جديد",
                onMainClick = { onNavigate("accounts") },
                onQuickActionClick = { onNavigate("create:account") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmartSplitActionButton(
                modifier = Modifier.weight(1f),
                title = "التقارير والأرباح",
                subtitle = "الدخل والمركز المالي",
                icon = Icons.Default.Assessment,
                accentColor = SmartLedgerColors.StateSuccess,
                quickActionIcon = Icons.Default.Assessment,
                quickActionTooltip = "فتح التقارير",
                onMainClick = { onNavigate("reports") },
                onQuickActionClick = null
            )

            SmartSplitActionButton(
                modifier = Modifier.weight(1f),
                title = "الصناديق والإعدادات",
                subtitle = "التحكم الشامل",
                icon = Icons.Default.Settings,
                accentColor = SmartLedgerColors.TextSecondary,
                quickActionIcon = Icons.Default.AccountBalanceWallet,
                quickActionTooltip = "إدارة الصناديق والتحويل",
                onMainClick = { onNavigate("settings") },
                onQuickActionClick = { onNavigate("create:treasury") }
            )
        }
    }
}
