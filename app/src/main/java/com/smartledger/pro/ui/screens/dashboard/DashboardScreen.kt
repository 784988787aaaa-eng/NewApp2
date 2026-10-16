package com.smartledger.pro.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.entity.OperationEntity
import com.smartledger.pro.ui.components.SmartEmptyState
import com.smartledger.pro.ui.components.bounceClick
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.viewmodels.DashboardViewModel
import com.smartledger.pro.util.AppSettingsManager
import com.smartledger.pro.util.Formatters

@Composable
fun DashboardScreen(
    privacyMode: Boolean,
    onTogglePrivacy: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val metrics by viewModel.metrics.collectAsState()
    val recentOps by viewModel.recentOps.collectAsState()
    val accountsMap by viewModel.accountsMap.collectAsState()
    val settings by AppSettingsManager.settings.collectAsState()

    var selectedOperationForDetails by remember { mutableStateOf<OperationEntity?>(null) }

    val netPosition = metrics.TotalOwedToUs - metrics.TotalWeOwe

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SmartLedgerColors.SurfaceWindow)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SmartLedgerColors.AccentSubtle)
                            .border(1.dp, SmartLedgerColors.AccentPrimary, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = "شعار التطبيق",
                            tint = SmartLedgerColors.AccentLight,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "الدفتر الذكي Pro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        Text(
                            text = "ملخص المركز المالي والحركة اليومية",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onTogglePrivacy,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SmartLedgerColors.SurfaceCardElevated)
                    ) {
                        Icon(
                            imageVector = if (privacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "إخفاء أو إظهار الأرصدة",
                            tint = if (privacyMode) SmartLedgerColors.AccentLight else SmartLedgerColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { onNavigate("reports") },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SmartLedgerColors.SurfaceCardElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "التقارير",
                            tint = SmartLedgerColors.AccentLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .bounceClick { onNavigate("accounts") },
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(SmartLedgerColors.StateDangerBorder)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "إجمالي لنا (مدين)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.StateDanger
                            )
                            Icon(
                                imageVector = Icons.Default.CallReceived,
                                contentDescription = null,
                                tint = SmartLedgerColors.StateDanger,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = Formatters.formatCurrency(metrics.TotalOwedToUs, settings.currencySymbol, settings.decimalPlaces, privacyMode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.StateDanger
                        )
                        Text(
                            text = "مستحق لنا عند العملاء",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .bounceClick { onNavigate("accounts") },
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(SmartLedgerColors.StateSuccessBorder)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "إجمالي علينا (دائن)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.StateSuccess
                            )
                            Icon(
                                imageVector = Icons.Default.CallMade,
                                contentDescription = null,
                                tint = SmartLedgerColors.StateSuccess,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = Formatters.formatCurrency(metrics.TotalWeOwe, settings.currencySymbol, settings.decimalPlaces, privacyMode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.StateSuccess
                        )
                        Text(
                            text = "التزامات للموردين والغير",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "صافي المركز التراكمي (لنا ناقص علينا)",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                            Text(
                                text = Formatters.formatCurrency(netPosition, settings.currencySymbol, settings.decimalPlaces, privacyMode),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (netPosition >= 0) SmartLedgerColors.AccentLight else SmartLedgerColors.StateWarning
                            )
                        }
                        Badge(
                            containerColor = if (netPosition >= 0) SmartLedgerColors.AccentSubtle else SmartLedgerColors.StateWarningSubtle,
                            contentColor = if (netPosition >= 0) SmartLedgerColors.AccentLight else SmartLedgerColors.StateWarning
                        ) {
                            Text(
                                text = if (netPosition >= 0) "صافي لصالح المنشأة" else "صافي التزامات قائمة",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Divider(color = SmartLedgerColors.BorderGridLine)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TodayMetricCell(
                            label = "مبيعات اليوم",
                            value = Formatters.formatAmountOnly(metrics.TodaySales, settings.decimalPlaces),
                            privacyMode = privacyMode,
                            color = SmartLedgerColors.AccentLight
                        )
                        TodayMetricCell(
                            label = "مقبوضات اليوم",
                            value = Formatters.formatAmountOnly(metrics.TodayReceipts, settings.decimalPlaces),
                            privacyMode = privacyMode,
                            color = SmartLedgerColors.StateSuccess
                        )
                        TodayMetricCell(
                            label = "مشتريات اليوم",
                            value = Formatters.formatAmountOnly(metrics.TodayPurchases, settings.decimalPlaces),
                            privacyMode = privacyMode,
                            color = SmartLedgerColors.TextSecondary
                        )
                        TodayMetricCell(
                            label = "مدفوعات اليوم",
                            value = Formatters.formatAmountOnly(metrics.TodayPayments, settings.decimalPlaces),
                            privacyMode = privacyMode,
                            color = SmartLedgerColors.StateDanger
                        )
                    }
                }
            }
        }

        if (metrics.LowStockCount > 0) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SmartLedgerColors.StateWarningSubtle)
                        .border(1.dp, SmartLedgerColors.StateWarningBorder, RoundedCornerShape(10.dp))
                        .clickable { onNavigate("inventory_low_stock") }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = SmartLedgerColors.StateWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "تنبيه مخزون: ${Formatters.formatQuantity(metrics.LowStockCount.toDouble())} أصناف وصلت لحد النفاد",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.StateWarning
                            )
                            Text(
                                text = "اضغط هنا لمراجعة النواقص وإعادة طلبها",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = SmartLedgerColors.StateWarning
                    )
                }
            }
        }

        item {
            Text(
                text = "الإجراءات السريعة",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SmartLedgerColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionTile(modifier = Modifier.weight(1f), title = "فاتورة بيع", icon = Icons.Default.PointOfSale, color = SmartLedgerColors.AccentPrimary) { onNavigate("pos_sales") }
                    QuickActionTile(modifier = Modifier.weight(1f), title = "سند قبض", icon = Icons.Default.ArrowDownward, color = SmartLedgerColors.StateSuccess) { onNavigate("voucher_receipt") }
                    QuickActionTile(modifier = Modifier.weight(1f), title = "سند صرف", icon = Icons.Default.ArrowUpward, color = SmartLedgerColors.StateDanger) { onNavigate("voucher_payment") }
                    QuickActionTile(modifier = Modifier.weight(1f), title = "فاتورة شراء", icon = Icons.Default.LocalShipping, color = SmartLedgerColors.AccentLight) { onNavigate("purchases") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionTile(modifier = Modifier.weight(1f), title = "الحسابات", icon = Icons.Default.AccountBalanceWallet, color = SmartLedgerColors.AccentPrimary) { onNavigate("accounts") }
                    QuickActionTile(modifier = Modifier.weight(1f), title = "المخزون", icon = Icons.Default.Inventory2, color = SmartLedgerColors.AccentLight) { onNavigate("inventory") }
                    QuickActionTile(modifier = Modifier.weight(1f), title = "المصروفات", icon = Icons.Default.TrendingDown, color = SmartLedgerColors.StateDanger) { onNavigate("expenses") }
                    QuickActionTile(modifier = Modifier.weight(1f), title = "التقارير", icon = Icons.Default.Assessment, color = SmartLedgerColors.StateSuccess) { onNavigate("reports") }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "أحدث العمليات المسجلة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SmartLedgerColors.TextPrimary
                )
                Text(
                    text = "عرض كافة الحسابات",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.AccentLight,
                    modifier = Modifier.clickable { onNavigate("accounts") }
                )
            }
        }

        if (recentOps.isEmpty()) {
            item {
                SmartEmptyState(
                    icon = Icons.Default.ReceiptLong,
                    title = "لا توجد حركات مسجلة حتى الآن",
                    description = "ابدأ بإصدار فاتورة مبيعات أو تسجيل سند قبض أو إضافة حساب جديد.",
                    actionText = "فتح نقطة البيع",
                    onActionClick = { onNavigate("pos_sales") }
                )
            }
        } else {
            items(recentOps.size, key = { recentOps[it].Id }) { index ->
                val op = recentOps[index]
                val personName = accountsMap[op.PersonId] ?: "حساب #${op.PersonId}"
                RealOperationRow(
                    op = op,
                    personName = personName,
                    currencySymbol = settings.currencySymbol,
                    decimals = settings.decimalPlaces,
                    privacyMode = privacyMode,
                    onClick = { selectedOperationForDetails = op }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (selectedOperationForDetails != null) {
        val op = selectedOperationForDetails!!
        val personName = accountsMap[op.PersonId] ?: "حساب #${op.PersonId}"
        AlertDialog(
            onDismissRequest = { selectedOperationForDetails = null },
            containerColor = SmartLedgerColors.SurfaceDialog,
            title = {
                Text("تفاصيل العملية #${op.Id}", fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("الطرف الحسابي: $personName", fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                    Text("البيان: ${op.Note ?: op.Type}", color = SmartLedgerColors.TextPrimary)
                    Text("المبلغ: ${Formatters.formatCurrency(op.Amount, settings.currencySymbol, settings.decimalPlaces)}", fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateSuccess)
                    if (!op.ReferenceNumber.isNullOrBlank()) {
                        Text("رقم المرجع: ${op.ReferenceNumber}", color = SmartLedgerColors.TextSecondary)
                    }
                    Text("التاريخ والوقت: ${op.OccurredAt}", color = SmartLedgerColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedOperationForDetails = null }) {
                    Text("إغلاق", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun TodayMetricCell(
    label: String,
    value: String,
    privacyMode: Boolean,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
        Text(
            text = if (privacyMode) "••••" else value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun QuickActionTile(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(74.dp)
            .bounceClick(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = SmartLedgerColors.TextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun RealOperationRow(
    op: OperationEntity,
    personName: String,
    currencySymbol: String,
    decimals: Int,
    privacyMode: Boolean,
    onClick: () -> Unit
) {
    val isCredit = op.Type in listOf("Receive", "DebtToUs", "Purchase", "SaleReturn")
    val typeLabel = when (op.Type) {
        "Receive" -> "سند قبض نقدية"
        "Payment" -> "سند صرف نقدية"
        "Sale" -> "فاتورة مبيعات"
        "Purchase" -> "فاتورة مشتريات"
        "SaleReturn" -> "مرتجع مبيعات"
        "PurchaseReturn" -> "مرتجع مشتريات"
        "DebtOnUs" -> "رصيد افتتاحي (عليه)"
        "DebtToUs" -> "رصيد افتتاحي (له)"
        else -> op.Type
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderGridLine)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCredit) SmartLedgerColors.StateSuccessSubtle else SmartLedgerColors.StateDangerSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isCredit) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = personName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary
                    )
                    Text(
                        text = "${op.Note ?: typeLabel} • $typeLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted,
                        maxLines = 1
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.formatCurrency(op.Amount, currencySymbol, decimals, privacyMode),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isCredit) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                )
                Text(
                    text = op.OccurredAt.take(16),
                    style = MaterialTheme.typography.labelSmall,
                    color = SmartLedgerColors.TextMuted
                )
            }
        }
    }
}
