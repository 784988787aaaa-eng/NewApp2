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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.dao.RecentOperationWithPerson
import com.smartledger.pro.domain.usecase.LedgerDirectionPolicy
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.screens.settings.FirstRunOnboardingDialog
import com.smartledger.pro.ui.screens.settings.ScreenFieldVisibilityStore
import com.smartledger.pro.ui.screens.settings.SettingSwitchRow
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
    val context = LocalContext.current
    val metrics by viewModel.metrics.collectAsState()
    val financialSummary by viewModel.financialSummary.collectAsState()
    val products by viewModel.products.collectAsState()
    val recentOps by viewModel.recentOps.collectAsState()
    val identity by viewModel.businessIdentity.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val financialAccounts by viewModel.financialAccounts.collectAsState()
    val fieldVisibility by ScreenFieldVisibilityStore.observe(context).collectAsState()

    var selectedOpDetail by remember { mutableStateOf<RecentOperationWithPerson?>(null) }
    var showCumulativePulse by rememberSaveable { mutableStateOf(false) }
    var opsFilter by rememberSaveable { mutableStateOf("ALL") } // ALL, INFLOW, OUTFLOW, INVOICES, VOUCHERS
    var showQuickCustomizeSheet by rememberSaveable { mutableStateOf(false) }

    val netPosition = metrics.TotalOwedToUs - metrics.TotalWeOwe
    val todayInflow = metrics.TodaySales + metrics.TodayReceipts
    val todayOutflow = metrics.TodayPurchases + metrics.TodayPayments
    val todayNetCashFlow = todayInflow - todayOutflow

    val outOfStockCount = remember(products) {
        products.count { it.CurrentStockMinor <= 0.0 }
    }

    val filteredRecentOps = remember(recentOps, opsFilter) {
        when (opsFilter) {
            "INFLOW" -> recentOps.filter { LedgerDirectionPolicy.isCreditOperation(it.Type) }
            "OUTFLOW" -> recentOps.filter { !LedgerDirectionPolicy.isCreditOperation(it.Type) }
            "INVOICES" -> recentOps.filter { it.Type in listOf("Sale", "Purchase", "SaleReturn", "PurchaseReturn") }
            "VOUCHERS" -> recentOps.filter { it.Type in listOf("Receive", "Payment") }
            else -> recentOps
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SmartLedgerColors.SurfaceWindow)
            .padding(horizontal = SmartLedgerTokens.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(SmartLedgerTokens.sectionSpacing)
    ) {
        item { Spacer(modifier = Modifier.height(6.dp)) }

        // 1. شريط القيادة العلوي المدمج (الهوية + مبدل الوضع السريع + أزرار التحكم الذكية)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            SmartLedgerColors.AccentPrimary.copy(alpha = 0.28f),
                                            SmartLedgerColors.AccentSubtle
                                        )
                                    )
                                )
                                .border(1.dp, SmartLedgerColors.AccentLight.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = "شعار المنشأة",
                                tint = SmartLedgerColors.AccentLight,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = identity?.TradeName?.ifBlank { "نشاطي التجاري" } ?: "نشاطي التجاري",
                                style = MaterialTheme.typography.titleLarge,
                                color = SmartLedgerColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "${metrics.ActiveAccountsCount} حساب • ${products.size} صنف • ${financialAccounts.size} صندوق",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // زر التخصيص الفوري للشاشة الرئيسية
                        IconButton(
                            onClick = { showQuickCustomizeSheet = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SmartLedgerColors.SurfaceCard)
                                .border(1.dp, SmartLedgerColors.BorderSubtle, CircleShape)
                                .pressScaleEffect()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "تخصيص عناصر الواجهة",
                                tint = SmartLedgerColors.AccentLight,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        IconButton(
                            onClick = onTogglePrivacy,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SmartLedgerColors.SurfaceCard)
                                .border(1.dp, SmartLedgerColors.BorderSubtle, CircleShape)
                                .pressScaleEffect()
                        ) {
                            Icon(
                                imageVector = if (privacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (privacyMode) "إظهار المبالغ" else "إخفاء المبالغ للخصوصية",
                                tint = if (privacyMode) SmartLedgerColors.StateWarning else SmartLedgerColors.AccentLight,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        IconButton(
                            onClick = { onNavigate("reports") },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SmartLedgerColors.SurfaceCard)
                                .border(1.dp, SmartLedgerColors.BorderSubtle, CircleShape)
                                .pressScaleEffect()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = "التقارير المالية",
                                tint = SmartLedgerColors.StateSuccess,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                // شريط التبديل الذكي بين أوضاع الاستخدام (كلاسيكي / عادي / احترافي) + اختصار تخصيص الواجهة
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmartUsageModePill(
                        currentMode = settings.usageMode,
                        onModeSelected = { mode ->
                            viewModel.setUsageMode(context, mode)
                        }
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(SmartLedgerTokens.chipCorner))
                            .background(SmartLedgerColors.SurfaceCard)
                            .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(SmartLedgerTokens.chipCorner))
                            .clickable { showQuickCustomizeSheet = true }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DashboardCustomize,
                            contentDescription = null,
                            tint = SmartLedgerColors.AccentLight,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "إظهار/إخفاء البطاقات",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SmartLedgerColors.TextSecondary
                        )
                    }
                }
            }
        }

        // 2. لوحة نبض السيولة والتدفقات (كل ما دخل • كل ما خرج • الصافي الفعلي)
        if (fieldVisibility.showDashboardCashPulse) {
            item {
                if (!showCumulativePulse) {
                    SmartCashFlowPulseHero(
                        title = "نبض السيولة وحركة اليوم",
                        periodBadge = "حركة اليوم ⇄ اضغط للتراكمي",
                        inflowLabel = "كل ما دخل اليوم (+)",
                        inflowAmount = todayInflow,
                        outflowLabel = "كل ما خرج اليوم (-)",
                        outflowAmount = todayOutflow,
                        netLabel = "صافي اليوم:",
                        netAmount = todayNetCashFlow,
                        privacyMode = privacyMode,
                        subMetrics = listOf(
                            Triple("مبيعات اليوم", metrics.TodaySales, SmartLedgerColors.AccentLight),
                            Triple("مقبوضات السندات", metrics.TodayReceipts, SmartLedgerColors.StateSuccess),
                            Triple("مشتريات اليوم", metrics.TodayPurchases, SmartLedgerColors.StateWarning),
                            Triple("مدفوعات السندات", metrics.TodayPayments, SmartLedgerColors.StateDanger)
                        ),
                        onPeriodToggle = { showCumulativePulse = true },
                        onActionClick = { onNavigate("reports") },
                        actionLabel = "القوائم"
                    )
                } else {
                    SmartCashFlowPulseHero(
                        title = "المركز النقدي والربحي التراكمي",
                        periodBadge = "شامل ⇄ اضغط لحركة اليوم",
                        inflowLabel = "إجمالي المقبوضات والوارد",
                        inflowAmount = financialSummary.CashInflows,
                        outflowLabel = "إجمالي المدفوعات والصادر",
                        outflowAmount = financialSummary.CashOutflows,
                        netLabel = "صافي السيولة:",
                        netAmount = financialSummary.NetCashBalance,
                        privacyMode = privacyMode,
                        subMetrics = listOf(
                            Triple("إجمالي المبيعات", financialSummary.TotalSales, SmartLedgerColors.AccentLight),
                            Triple("مجمل الربح", financialSummary.GrossProfit, SmartLedgerColors.StateSuccess),
                            Triple("المصروفات", financialSummary.TotalExpenses, SmartLedgerColors.StateDanger),
                            Triple("صافي الربح", financialSummary.NetProfit, SmartLedgerColors.StateSuccess)
                        ),
                        onPeriodToggle = { showCumulativePulse = false },
                        onActionClick = { onNavigate("reports") },
                        actionLabel = "التفاصيل"
                    )
                }
            }
        }

        // 3. ميزان الديون والذمم (إجمالي لنا • إجمالي علينا • الصافي) مع أزرار قبض وصرف مدمجة
        if (fieldVisibility.showDashboardDebtRadar) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfacePanel),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderStrong)),
                    shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SmartLedgerTokens.cardInternalPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "ميزان الديون والذمم (لنا - علينا)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SmartLedgerColors.TextSecondary
                                )
                                Text(
                                    text = NumberFormatter.formatCurrency(
                                        amount = abs(netPosition),
                                        currencySymbol = AppDesignState.currencySymbol,
                                        decimalPlaces = AppDesignState.decimalPlaces,
                                        privacyMode = privacyMode
                                    ),
                                    style = MaterialTheme.typography.headlineSmall,
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
                                    .clip(RoundedCornerShape(SmartLedgerTokens.chipCorner))
                                    .background(
                                        when {
                                            netPosition > 0 -> SmartLedgerColors.StateSuccessSubtle
                                            netPosition < 0 -> SmartLedgerColors.StateDangerSubtle
                                            else -> SmartLedgerColors.SurfaceCardElevated
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = when {
                                        netPosition > 0 -> "صافي لصالحنا (لنا)"
                                        netPosition < 0 -> "صافي التزام (علينا)"
                                        else -> "ذمم متوازنة"
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // لنا عند العملاء
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .bounceClick { onNavigate("accounts") },
                                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = SolidColor(SmartLedgerColors.StateDangerBorder.copy(alpha = 0.55f))
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.NorthEast,
                                                contentDescription = null,
                                                tint = SmartLedgerColors.StateDanger,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("ديون لنا (مدين)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                                        }
                                        Text(
                                            text = "+ قبض",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = SmartLedgerColors.StateSuccess,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(SmartLedgerColors.StateSuccessSubtle)
                                                .clickable { onNavigate("create:receipt") }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
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
                                    Text("مستحقات لدى العملاء", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                                }
                            }

                            // علينا للموردين
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .bounceClick { onNavigate("accounts") },
                                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = SolidColor(SmartLedgerColors.StateSuccessBorder.copy(alpha = 0.55f))
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.SouthWest,
                                                contentDescription = null,
                                                tint = SmartLedgerColors.StateSuccess,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("التزام علينا (دائن)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                                        }
                                        Text(
                                            text = "+ صرف",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = SmartLedgerColors.StateDanger,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(SmartLedgerColors.StateDangerSubtle)
                                                .clickable { onNavigate("create:payment") }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
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
                                    Text("مستحقات للموردين", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. منظومة الصناديق الرئيسية والفرعية وحركتها
        if (fieldVisibility.showDashboardTreasuries) {
            item {
                DashboardTreasuriesCard(
                    financialAccounts = financialAccounts,
                    showSubTreasuriesTree = fieldVisibility.showSubTreasuriesTree,
                    privacyMode = privacyMode,
                    onManageClick = { onNavigate("create:treasury") }
                )
            }
        }

        // 5. رادار المخزن وحركة الأصناف والنواقص
        if (fieldVisibility.showDashboardStockRadar) {
            item {
                DashboardWarehouseRadarCard(
                    totalProductsCount = products.size,
                    lowStockCount = metrics.LowStockCount,
                    outOfStockCount = outOfStockCount,
                    inventoryValuation = financialSummary.InventoryValuation,
                    privacyMode = privacyMode,
                    onOpenInventory = { onNavigate("inventory") },
                    onQuickAddProduct = { onNavigate("create:product") },
                    onQuickPurchase = { onNavigate("create:purchase") }
                )
            }
        }

        // 6. الأوامر الذكية المدمجة (مساحة أقل • خدمة أكبر)
        if (fieldVisibility.showDashboardSmartActions) {
            item { DashboardQuickActions(onNavigate) }
        }

        // 7. سجل الحركات الموحد مع فلترة فورية داخل الرئيسية
        if (fieldVisibility.showDashboardLiveStream) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "سجل الحركات اللحظي الموحد (${filteredRecentOps.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        Text(
                            text = "كشوف الحساب ◂",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.AccentLight,
                            modifier = Modifier.clickable { onNavigate("accounts") }
                        )
                    }

                    FilterChipRow(
                        items = listOf(
                            "ALL" to "كل الحركات",
                            "INFLOW" to "الداخل / دائن (+)",
                            "OUTFLOW" to "الخارج / مدين (-)",
                            "INVOICES" to "الفواتير",
                            "VOUCHERS" to "السندات"
                        ),
                        selectedItem = opsFilter,
                        onItemSelected = { opsFilter = it }
                    )
                }
            }

            if (filteredRecentOps.isEmpty()) {
                item {
                    SmartEmptyState(
                        icon = Icons.Default.ReceiptLong,
                        title = "لا توجد حركات مطابقة",
                        subtitle = "ابدأ بإصدار فاتورة مبيعات أو تسجيل سند قبض أو إضافة حساب لتظهر كافة الحركات هنا.",
                        actionText = "إصدار فاتورة مبيعات",
                        isFiltered = opsFilter != "ALL",
                        onClearFilters = { opsFilter = "ALL" },
                        onActionClick = { onNavigate("pos_sales") }
                    )
                }
            } else {
                items(filteredRecentOps, key = { it.Id }) { op ->
                    RealOperationRow(
                        op = op,
                        privacyMode = privacyMode,
                        onClick = { selectedOpDetail = op }
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(22.dp)) }
    }

    // نافذة التخصيص الفوري لعناصر الواجهة الرئيسية (تمنح المستخدم تحكماً لحظياً بما يراه)
    if (showQuickCustomizeSheet) {
        FloatingModalDialog(
            onDismissRequest = { showQuickCustomizeSheet = false },
            allowOutsideClickDismiss = true
        ) {
            FormSheetContainer(
                title = "تخصيص الواجهة الرئيسية ونمط العرض",
                subtitle = "تحكم فوراً فيما يظهر أو يختفي من الشاشة الأولى حسب تفضيلك",
                headerIcon = Icons.Default.DashboardCustomize,
                onDismiss = { showQuickCustomizeSheet = false },
                primaryButtonText = "تم الحفظ والمزامنة",
                onPrimaryClick = { showQuickCustomizeSheet = false }
            ) {
                FormSectionGroup(title = "1. قوالب الاستخدام الجاهزة") {
                    Text(
                        text = "اختر القالب العام أو خصص كل بطاقة يدوياً من الأسفل:",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted
                    )
                    SmartUsageModePill(
                        currentMode = settings.usageMode,
                        onModeSelected = { mode -> viewModel.setUsageMode(context, mode) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                FormSectionGroup(title = "2. إظهار وإخفاء لوحات الشاشة الرئيسية") {
                    SettingSwitchRow(
                        title = "نبض السيولة (كل ما دخل • كل ما خرج • الصافي)",
                        subtitle = "عرض بطاقة التدفق النقدي اللحظي ومؤشرات اليوم",
                        checked = fieldVisibility.showDashboardCashPulse,
                        onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showDashboardCashPulse = v) } }
                    )
                    Divider(color = SmartLedgerColors.BorderSubtle)
                    SettingSwitchRow(
                        title = "ميزان الديون والذمم (لنا / علينا / الصافي)",
                        subtitle = "عرض إجمالي ديون العملاء والتزامات الموردين وأزرار القبض والصرف السريع",
                        checked = fieldVisibility.showDashboardDebtRadar,
                        onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showDashboardDebtRadar = v) } }
                    )
                    Divider(color = SmartLedgerColors.BorderSubtle)
                    SettingSwitchRow(
                        title = "منظومة الصناديق الرئيسية والفرعية",
                        subtitle = "عرض أرصدة الخزائن والمحافظ والوارد والصادر لكل صندوق",
                        checked = fieldVisibility.showDashboardTreasuries,
                        onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showDashboardTreasuries = v) } }
                    )
                    Divider(color = SmartLedgerColors.BorderSubtle)
                    SettingSwitchRow(
                        title = "شجرة الصناديق الفرعية تحت الرئيسية",
                        subtitle = "إظهار الصناديق الفرعية المرتبطة بكل صندوق رئيسي",
                        checked = fieldVisibility.showSubTreasuriesTree,
                        onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showSubTreasuriesTree = v) } }
                    )
                    Divider(color = SmartLedgerColors.BorderSubtle)
                    SettingSwitchRow(
                        title = "رادار المخزن وحركة الأصناف والنواقص",
                        subtitle = "عرض تقييم المخزون وعدد الأصناف الحرجة والنافدة",
                        checked = fieldVisibility.showDashboardStockRadar,
                        onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showDashboardStockRadar = v) } }
                    )
                    Divider(color = SmartLedgerColors.BorderSubtle)
                    SettingSwitchRow(
                        title = "شبكة الأوامر الذكية المدمجة",
                        subtitle = "عرض أزرار الوصول والإضافة السريعة المزدوجة (+)",
                        checked = fieldVisibility.showDashboardSmartActions,
                        onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showDashboardSmartActions = v) } }
                    )
                    Divider(color = SmartLedgerColors.BorderSubtle)
                    SettingSwitchRow(
                        title = "سجل الحركات الموحد المفلتر",
                        subtitle = "عرض أحدث القيود والفواتير والسندات مع شرائح الفلترة",
                        checked = fieldVisibility.showDashboardLiveStream,
                        onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showDashboardLiveStream = v) } }
                    )
                }
            }
        }
    }

    // نافذة تفاصيل العملية السريعة عند الضغط عليها
    if (selectedOpDetail != null) {
        val op = selectedOpDetail!!
        val isCredit = LedgerDirectionPolicy.isCreditOperation(op.Type)
        FloatingModalDialog(
            onDismissRequest = { selectedOpDetail = null },
            allowOutsideClickDismiss = true,
            maxWidth = 440.dp
        ) {
            FormSheetContainer(
                title = "تفاصيل القيد المالي #${op.Id}",
                subtitle = op.OccurredAt,
                headerIcon = Icons.Default.ReceiptLong,
                onDismiss = { selectedOpDetail = null },
                primaryButtonText = "فتح شاشة الحسابات",
                secondaryButtonText = "إغلاق",
                onPrimaryClick = {
                    selectedOpDetail = null
                    onNavigate("accounts")
                }
            ) {
                FormSectionGroup(title = "بيانات الحركة الموثقة") {
                    Text(
                        text = "الحساب: ${op.PersonName ?: "حساب عام"}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.AccentLight
                    )
                    Text(
                        text = "المبلغ: ${NumberFormatter.formatCurrency(op.Amount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces, privacyMode)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCredit) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                    )
                    Text(
                        text = "الأثر المحاسبي: ${if (isCredit) "دائن / وارد (+)" else "مدين / صادر (-)"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SmartLedgerColors.TextSecondary
                    )
                    Text(
                        text = "البيان: ${op.Note ?: "-"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SmartLedgerColors.TextPrimary
                    )
                    if (!op.ReferenceNumber.isNullOrBlank()) {
                        Text(
                            text = "رقم المرجع: ${op.ReferenceNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }
                }
            }
        }
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
