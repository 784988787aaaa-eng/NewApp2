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
import com.smartledger.pro.domain.usecase.LedgerDirectionPolicy
import com.smartledger.pro.ui.components.SmartEmptyState
import com.smartledger.pro.ui.components.bounceClick
import com.smartledger.pro.ui.components.pressScaleEffect
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.screens.settings.FirstRunOnboardingDialog
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
    val settings by viewModel.settings.collectAsState()
    val financialAccounts by viewModel.financialAccounts.collectAsState()

    var selectedOpDetail by remember { mutableStateOf<RecentOperationWithPerson?>(null) }

    val netPosition = metrics.TotalOwedToUs - metrics.TotalWeOwe
    val todayNetCashFlow = (metrics.TodaySales + metrics.TodayReceipts) - (metrics.TodayPurchases + metrics.TodayPayments)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SmartLedgerColors.SurfaceWindow)
            .padding(horizontal = SmartLedgerTokens.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(SmartLedgerTokens.sectionSpacing)
    ) {
        item { Spacer(modifier = Modifier.height(6.dp)) }

        // 1. شريط الترحيب والهوية التجارية مع أزرار الخصوصية والتنبيهات والإعدادات
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SmartLedgerColors.AccentSubtle)
                            .border(1.dp, SmartLedgerColors.AccentPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = "شعار المنشأة",
                            tint = SmartLedgerColors.AccentLight,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = identity?.TradeName?.ifBlank { "نشاطي التجاري" } ?: "نشاطي التجاري",
                            style = MaterialTheme.typography.titleLarge,
                            color = SmartLedgerColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "المركز المالي الموحد • ${metrics.ActiveAccountsCount} حساب نشط",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onTogglePrivacy,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SmartLedgerColors.SurfaceCard)
                            .border(1.dp, SmartLedgerColors.BorderSubtle, CircleShape)
                            .pressScaleEffect()
                    ) {
                        Icon(
                            imageVector = if (privacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (privacyMode) "إظهار المبالغ" else "إخفاء المبالغ للخصوصية",
                            tint = if (privacyMode) SmartLedgerColors.StateWarning else SmartLedgerColors.AccentLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { onNavigate("reports") },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SmartLedgerColors.SurfaceCard)
                            .border(1.dp, SmartLedgerColors.BorderSubtle, CircleShape)
                            .pressScaleEffect()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "التقارير المالية",
                            tint = SmartLedgerColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. تنبيه ذكي للمخزون المنخفض (إن وجد)
        if (metrics.LowStockCount > 0) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick { onNavigate("inventory") },
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.StateWarningSubtle),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(SmartLedgerColors.StateWarningBorder)
                    ),
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
                                contentDescription = "تنبيه مخزون",
                                tint = SmartLedgerColors.StateWarning,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "تنبيه مخزني: يوجد (${metrics.LowStockCount}) أصناف قاربت على النفاد أو نفدت",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SmartLedgerColors.TextPrimary
                            )
                        }
                        Text(
                            text = "معاينة ←",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.StateWarning
                        )
                    }
                }
            }
        }

        // 2-ب. بطاقة السيولة النقدية الحية في الصناديق والمحافظ (عند تعريفها)
        if (financialAccounts.isNotEmpty()) {
            item {
                val totalTreasuryBalance = financialAccounts.sumOf { it.NetBalance }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick { onNavigate("settings") },
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                    shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = SmartLedgerColors.AccentLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "أرصدة الصناديق والمحافظ الحية (${financialAccounts.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = SmartLedgerColors.TextPrimary
                                )
                            }
                            Text(
                                text = NumberFormatter.formatCurrency(
                                    totalTreasuryBalance,
                                    AppDesignState.currencySymbol,
                                    AppDesignState.decimalPlaces,
                                    privacyMode
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (totalTreasuryBalance >= 0.0) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            financialAccounts.take(3).forEach { fa ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SmartLedgerColors.SurfaceCardElevated)
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = fa.Name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SmartLedgerColors.TextSecondary,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = NumberFormatter.formatCurrency(
                                                fa.NetBalance,
                                                fa.CurrencySymbol,
                                                AppDesignState.decimalPlaces,
                                                privacyMode
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (fa.NetBalance >= 0.0) SmartLedgerColors.AccentLight else SmartLedgerColors.StateDanger,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. بطاقة المركز المالي الموحد (إجمالي لنا، إجمالي علينا، صافي المركز، وملخص اليوم)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfacePanel),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = SolidColor(SmartLedgerColors.BorderStrong)
                ),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "صافي المركز الائتماني (لنا - علينا)",
                                style = MaterialTheme.typography.labelMedium,
                                color = SmartLedgerColors.TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = NumberFormatter.formatCurrency(
                                    amount = abs(netPosition),
                                    currencySymbol = AppDesignState.currencySymbol,
                                    decimalPlaces = AppDesignState.decimalPlaces,
                                    privacyMode = privacyMode
                                ),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    netPosition > 0 -> SmartLedgerColors.StateSuccess
                                    netPosition < 0 -> SmartLedgerColors.StateDanger
                                    else -> SmartLedgerColors.TextPrimary
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        netPosition > 0 -> SmartLedgerColors.StateSuccessSubtle
                                        netPosition < 0 -> SmartLedgerColors.StateDangerSubtle
                                        else -> SmartLedgerColors.SurfaceCardElevated
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = when {
                                    netPosition > 0 -> "فائض لصالحنا"
                                    netPosition < 0 -> "التزام علينا"
                                    else -> "مركز متوازن"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    netPosition > 0 -> SmartLedgerColors.StateSuccessText
                                    netPosition < 0 -> SmartLedgerColors.StateDangerText
                                    else -> SmartLedgerColors.TextSecondary
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // بطاقة لنا (ديون على العملاء)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .bounceClick { onNavigate("accounts") },
                            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = SolidColor(SmartLedgerColors.StateDangerBorder.copy(alpha = 0.5f))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CallMade,
                                        contentDescription = null,
                                        tint = SmartLedgerColors.StateDanger,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "إجمالي لنا (مدين)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SmartLedgerColors.TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = NumberFormatter.formatCurrency(
                                        metrics.TotalOwedToUs,
                                        AppDesignState.currencySymbol,
                                        AppDesignState.decimalPlaces,
                                        privacyMode
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SmartLedgerColors.StateDanger
                                )
                                Text(
                                    text = "مستحقات لدى العملاء",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextMuted
                                )
                            }
                        }

                        // بطاقة علينا (التزامات للموردين)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .bounceClick { onNavigate("accounts") },
                            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = SolidColor(SmartLedgerColors.StateSuccessBorder.copy(alpha = 0.5f))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CallReceived,
                                        contentDescription = null,
                                        tint = SmartLedgerColors.StateSuccess,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "إجمالي علينا (دائن)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SmartLedgerColors.TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = NumberFormatter.formatCurrency(
                                        metrics.TotalWeOwe,
                                        AppDesignState.currencySymbol,
                                        AppDesignState.decimalPlaces,
                                        privacyMode
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SmartLedgerColors.StateSuccess
                                )
                                Text(
                                    text = "مستحقات للموردين",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextMuted
                                )
                            }
                        }
                    }

                    Divider(color = SmartLedgerColors.BorderSubtle)

                    // ملخص حركة اليوم في صف واحد متناسق
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "حركة اليوم النقدية والبيع",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SmartLedgerColors.TextSecondary
                            )
                            Text(
                                text = "صافي اليوم: ${if (todayNetCashFlow >= 0) "+" else ""}${
                                    NumberFormatter.formatCurrency(
                                        todayNetCashFlow,
                                        AppDesignState.currencySymbol,
                                        AppDesignState.decimalPlaces,
                                        privacyMode
                                    )
                                }",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (todayNetCashFlow >= 0) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TodayMiniMetric(
                                label = "مبيعات اليوم",
                                amount = metrics.TodaySales,
                                color = SmartLedgerColors.AccentLight,
                                privacyMode = privacyMode
                            )
                            TodayMiniMetric(
                                label = "مقبوضات اليوم",
                                amount = metrics.TodayReceipts,
                                color = SmartLedgerColors.StateSuccess,
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
        val isCredit = LedgerDirectionPolicy.isCreditOperation(op.Type)
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

    if (!settings.onboardingCompleted) {
        FirstRunOnboardingDialog(
            defaultCurrency = settings.currencySymbol,
            onComplete = { tradeName, currencySymbol, cashbox, warehouse ->
                viewModel.completeOnboarding(tradeName, currencySymbol, cashbox, warehouse)
            },
            onSkip = { viewModel.skipOnboarding() }
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
    val isCredit = LedgerDirectionPolicy.isCreditOperation(op.Type)
    val typeLabel = when (op.Type) {
        "Receive" -> "سند قبض نقدية"
        "Payment" -> "سند صرف نقدية"
        "Sale" -> "فاتورة مبيعات آجل"
        "Purchase" -> "فاتورة مشتريات آجل"
        "SaleReturn" -> "مرتجع مبيعات"
        "PurchaseReturn" -> "مرتجع مشتريات"
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
