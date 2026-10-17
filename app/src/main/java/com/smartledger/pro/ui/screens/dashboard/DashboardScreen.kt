package com.smartledger.pro.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.smartledger.pro.data.local.dao.RecentOperationWithPerson
import com.smartledger.pro.ui.components.SmartEmptyState
import com.smartledger.pro.ui.components.bounceClick
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.viewmodels.DashboardViewModel
import com.smartledger.pro.util.NumberFormatter
import kotlin.math.abs

@Composable
fun DashboardScreen(
    privacyMode: Boolean,
    onTogglePrivacy: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val metrics by viewModel.metrics.collectAsState()
    val recentOps by viewModel.recentOps.collectAsState()
    val identity by viewModel.businessIdentity.collectAsState()
    var selectedOpDetail by remember { mutableStateOf<RecentOperationWithPerson?>(null) }

    val netWorkingCapital = metrics.TotalOwedToUs - metrics.TotalWeOwe

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SmartLedgerColors.SurfaceWindow)
            .padding(horizontal = SmartLedgerTokens.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(SmartLedgerTokens.sectionSpacing)
    ) {
        // 1. الترويسة العلوية وهوية المنشأة وزر الخصوصية
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SmartLedgerColors.AccentSubtle)
                            .border(1.5.dp, SmartLedgerColors.AccentPrimary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = "شعار الدفتر الذكي",
                            tint = SmartLedgerColors.AccentLight,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = identity?.TradeName ?: "الدفتر الذكي Pro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        Text(
                            text = "${metrics.ActiveAccountsCount} حساب نشط • منظومة المحاسبة والمخزون",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onTogglePrivacy,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SmartLedgerColors.SurfaceCardElevated)
                            .bounceClick { onTogglePrivacy() }
                    ) {
                        Icon(
                            imageVector = if (privacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "إخفاء أو إظهار الأرقام المالية",
                            tint = if (privacyMode) SmartLedgerColors.AccentLight else SmartLedgerColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { onNavigate("reports") },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SmartLedgerColors.SurfaceCardElevated)
                            .bounceClick { onNavigate("reports") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "التقارير المالية",
                            tint = SmartLedgerColors.AccentLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2. تنبيه ذكي للنواقص المخزنية عند وجود أصناف وصلت لحد الطلب
        if (metrics.LowStockCount > 0) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick { onNavigate("inventory") },
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.StateWarningSubtle),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.StateWarningBorder)),
                    shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "تنبيه المخزون: ${metrics.LowStockCount} أصناف عند حد النفاد",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = SmartLedgerColors.StateWarning
                                )
                                Text(
                                    text = "اضغط لمراجعة الأصناف الناقصة أو تسجيل فاتورة شراء وتوريد",
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
        }

        // 3. بطاقات المركز المالي الأساسي (مستحق لنا / مستحق علينا / صافي المركز)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // مستحق لنا (مدين)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .bounceClick { onNavigate("accounts") },
                        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.StateSuccessBorder)),
                        shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "مستحق لنا (مدين)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SmartLedgerColors.StateSuccessText
                                )
                                Icon(
                                    imageVector = Icons.Default.CallReceived,
                                    contentDescription = null,
                                    tint = SmartLedgerColors.StateSuccess,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = NumberFormatter.formatCurrency(
                                    amount = metrics.TotalOwedToUs,
                                    currencySymbol = AppDesignState.currencySymbol,
                                    decimalPlaces = AppDesignState.decimalPlaces,
                                    privacyMode = privacyMode
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.StateSuccess
                            )
                            Text(
                                text = "ديون لنا لدى العملاء",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                        }
                    }

                    // مستحق علينا (دائن)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .bounceClick { onNavigate("accounts") },
                        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.StateDangerBorder)),
                        shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "مستحق علينا (دائن)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SmartLedgerColors.StateDangerText
                                )
                                Icon(
                                    imageVector = Icons.Default.CallMade,
                                    contentDescription = null,
                                    tint = SmartLedgerColors.StateDanger,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = NumberFormatter.formatCurrency(
                                    amount = metrics.TotalWeOwe,
                                    currencySymbol = AppDesignState.currencySymbol,
                                    decimalPlaces = AppDesignState.decimalPlaces,
                                    privacyMode = privacyMode
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.StateDanger
                            )
                            Text(
                                text = "التزامات للموردين",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                        }
                    }
                }

                // شريط صافي المركز ونبض حركة اليوم
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                    shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "صافي الذمم والمركز المالي",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextMuted
                                )
                                Text(
                                    text = NumberFormatter.formatCurrency(
                                        amount = netWorkingCapital,
                                        currencySymbol = AppDesignState.currencySymbol,
                                        decimalPlaces = AppDesignState.decimalPlaces,
                                        privacyMode = privacyMode,
                                        showSign = true
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (netWorkingCapital >= 0) SmartLedgerColors.AccentLight else SmartLedgerColors.StateDanger
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (netWorkingCapital >= 0) SmartLedgerColors.AccentSubtle else SmartLedgerColors.StateDangerSubtle)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (netWorkingCapital >= 0) "فائض لصالح المنشأة" else "التزامات صافية علينا",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (netWorkingCapital >= 0) SmartLedgerColors.AccentLight else SmartLedgerColors.StateDanger
                                )
                            }
                        }

                        Divider(color = SmartLedgerColors.BorderSubtle)

                        // ملخص حركة اليوم الحقيقي
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TodayMiniMetric(
                                label = "مبيعات اليوم",
                                amount = metrics.TodaySales,
                                color = SmartLedgerColors.StateSuccess,
                                privacyMode = privacyMode
                            )
                            TodayMiniMetric(
                                label = "مقبوضات اليوم",
                                amount = metrics.TodayReceipts,
                                color = SmartLedgerColors.AccentLight,
                                privacyMode = privacyMode
                            )
                            TodayMiniMetric(
                                label = "مشتريات اليوم",
                                amount = metrics.TodayPurchases,
                                color = SmartLedgerColors.TextPrimary,
                                privacyMode = privacyMode
                            )
                            TodayMiniMetric(
                                label = "مدفوعات اليوم",
                                amount = metrics.TodayPayments,
                                color = SmartLedgerColors.StateDanger,
                                privacyMode = privacyMode
                            )
                        }
                    }
                }
            }
        }

        // 4. الإجراءات السريعة المنتقاة (العمليات اليومية الأكثر تكراراً)
        item {
            Text(
                text = "العمليات السريعة",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SmartLedgerColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    QuickActionTile(
                        modifier = Modifier.weight(1f),
                        title = "فاتورة شراء",
                        subtitle = "توريد للمخزن",
                        icon = Icons.Default.LocalShipping,
                        color = SmartLedgerColors.AccentLight
                    ) { onNavigate("purchases") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    QuickActionTile(
                        modifier = Modifier.weight(1f),
                        title = "كشوف الحساب",
                        subtitle = "العملاء والموردون",
                        icon = Icons.Default.AccountBalanceWallet,
                        color = SmartLedgerColors.TextSecondary
                    ) { onNavigate("accounts") }
                }
            }
        }

        // 5. سجل الحركات الأخيرة الفعلي القابل للفتح
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "أحدث القيود والعمليات المسجلة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SmartLedgerColors.TextPrimary
                )
                Text(
                    text = "عرض الدفاتر ←",
                    style = MaterialTheme.typography.labelMedium,
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
                    title = "لا توجد عمليات مسجلة بعد",
                    subtitle = "ابدأ بإصدار أول فاتورة بيع أو تسجيل سند قبض أو إضافة حساب عميل لتظهر أحدث الحركات هنا.",
                    actionText = "إصدار فاتورة مبيعات",
                    onActionClick = { onNavigate("pos_sales") }
                )
            }
        } else {
            items(recentOps, key = { it.Id }) { op ->
                RealOperationRow(
                    op = op,
                    privacyMode = privacyMode,
                    onClick = { selectedOpDetail = op }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    // نافذة تفاصيل العملية السريعة عند الضغط عليها
    if (selectedOpDetail != null) {
        val op = selectedOpDetail!!
        val isCredit = op.Type in listOf("Receive", "DebtToUs", "Purchase", "SaleReturn")
        AlertDialog(
            onDismissRequest = { selectedOpDetail = null },
            containerColor = SmartLedgerColors.SurfacePanel,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "تفاصيل القيد المالي #${op.Id}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SmartLedgerColors.TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("الحساب: ${op.PersonName ?: "حساب عام"}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                    Text("المبلغ: ${NumberFormatter.formatCurrency(op.Amount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces, privacyMode)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isCredit) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger)
                    Text("البيان: ${op.Note ?: "-"}", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextPrimary)
                    Text("التاريخ والوقت: ${op.OccurredAt}", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                    if (!op.ReferenceNumber.isNullOrBlank()) {
                        Text("المرجع: ${op.ReferenceNumber}", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedOpDetail = null
                    onNavigate("accounts")
                }) {
                    Text("فتح الحسابات", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedOpDetail = null }) {
                    Text("إغلاق", color = SmartLedgerColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun TodayMiniMetric(
    label: String,
    amount: Double,
    color: Color,
    privacyMode: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (privacyMode) "••••" else NumberFormatter.formatAmount(amount, AppDesignState.decimalPlaces),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun QuickActionTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .bounceClick { onClick() },
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
        shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SmartLedgerColors.TextPrimary,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.TextMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
fun RealOperationRow(
    op: RecentOperationWithPerson,
    privacyMode: Boolean,
    onClick: () -> Unit
) {
    val isCredit = op.Type in listOf("Receive", "DebtToUs", "Purchase", "SaleReturn")
    val typeLabel = when (op.Type) {
        "Receive" -> "سند قبض نقدية"
        "Payment" -> "سند صرف نقدية"
        "Sale" -> "فاتورة مبيعات آجل"
        "Purchase" -> "فاتورة مشتريات آجل"
        "SaleReturn" -> "مرتجع مبيعات"
        "DebtOnUs" -> "رصيد افتتاحي (عليه)"
        "DebtToUs" -> "رصيد افتتاحي (له)"
        else -> op.Type
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() },
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCredit) SmartLedgerColors.StateSuccessSubtle else SmartLedgerColors.StateDangerSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isCredit) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (!op.PersonName.isNullOrBlank()) "${op.PersonName} — ${op.Note ?: typeLabel}" else (op.Note ?: typeLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "$typeLabel • ${op.OccurredAt.take(16)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = NumberFormatter.formatCurrency(op.Amount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces, privacyMode),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isCredit) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                )
                Text(
                    text = if (isCredit) "دائن (له)" else "مدين (عليه)",
                    style = MaterialTheme.typography.labelSmall,
                    color = SmartLedgerColors.TextMuted
                )
            }
        }
    }
}
