package com.smartledger.pro.ui.screens.reports

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.ui.components.FilterTabItem
import com.smartledger.pro.ui.components.ScrollableFilterTabs
import com.smartledger.pro.ui.components.rememberDebouncedClick
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.viewmodels.ReportsViewModel
import com.smartledger.pro.util.AppSettingsManager
import com.smartledger.pro.util.Formatters

@Composable
fun ReportsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: ReportsViewModel = viewModel()
) {
    val summary by viewModel.summary.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val products by viewModel.products.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val settings by AppSettingsManager.settings.collectAsState()
    val context = LocalContext.current

    var selectedReportSection by remember { mutableStateOf("INCOME") }
    var exportStatusBanner by remember { mutableStateOf<String?>(null) }

    val periodTabs = remember {
        listOf(
            FilterTabItem("ALL", "كل الفترات"),
            FilterTabItem("TODAY", "اليوم"),
            FilterTabItem("WEEK", "هذا الأسبوع"),
            FilterTabItem("MONTH", "هذا الشهر"),
            FilterTabItem("YEAR", "هذه السنة")
        )
    }

    val sectionTabs = remember {
        listOf(
            FilterTabItem("INCOME", "قائمة الدخل والأرباح"),
            FilterTabItem("INVENTORY", "تقييم المخزون والنواقص"),
            FilterTabItem("DEBTS", "الذمم والديون (لنا/علينا)")
        )
    }

    val periodLabel = when (selectedPeriod) {
        "TODAY" -> "تقرير اليوم (${Formatters.todayDate()})"
        "WEEK" -> "منذ ${Formatters.startOfCurrentWeekDate()} حتى اليوم"
        "MONTH" -> "منذ بداية الشهر (${Formatters.startOfCurrentMonthDate()})"
        "YEAR" -> "منذ بداية السنة (${Formatters.startOfCurrentYearDate()})"
        else -> "تراكمي شامل لكل الفترات"
    }

    val buildReportPlainText: () -> String = {
        buildString {
            append("تقرير مالي وإداري — الدفتر الذكي Pro\n")
            append("تاريخ إصدار التقرير: ${Formatters.nowTimestamp()}\n")
            append("النطاق الزمني: $periodLabel\n")
            append("العملة المعتمدة: ${settings.currencySymbol}\n")
            append("====================================\n")
            append("1. قائمة الدخل والأرباح:\n")
            append("- إجمالي المبيعات المحققة: ${Formatters.formatCurrency(summary.TotalSales, settings.currencySymbol, settings.decimalPlaces)}\n")
            append("- تكلفة البضاعة المباعة (COGS): ${Formatters.formatCurrency(summary.CostOfGoodsSold, settings.currencySymbol, settings.decimalPlaces)}\n")
            append("- مجمل الربح التجاري: ${Formatters.formatCurrency(summary.GrossProfit, settings.currencySymbol, settings.decimalPlaces)}\n")
            append("- إجمالي المصروفات التشغيلية: ${Formatters.formatCurrency(summary.TotalExpenses, settings.currencySymbol, settings.decimalPlaces)}\n")
            append("- صافي الربح الفعلي: ${Formatters.formatCurrency(summary.NetProfit, settings.currencySymbol, settings.decimalPlaces)}\n")
            append("====================================\n")
            append("2. المركز المالي والمخزون:\n")
            append("- تقييم بضاعة المخزن بسعر التكلفة: ${Formatters.formatCurrency(summary.InventoryValuation, settings.currencySymbol, settings.decimalPlaces)}\n")
            append("- إجمالي ديون المنشأة على العملاء (لنا): ${Formatters.formatCurrency(summary.TotalDebtsToUs, settings.currencySymbol, settings.decimalPlaces)}\n")
            append("- إجمالي التزامات المنشأة للموردين (علينا): ${Formatters.formatCurrency(summary.TotalDebtsOnUs, settings.currencySymbol, settings.decimalPlaces)}\n")
            append("- صافي المركز المالي: ${Formatters.formatCurrency(summary.TotalDebtsToUs - summary.TotalDebtsOnUs, settings.currencySymbol, settings.decimalPlaces)}\n")
        }
    }

    val buildCsvText: () -> String = {
        buildString {
            append("البند,القيمة,العملة,الفترة\n")
            append("\"إجمالي المبيعات\",${Formatters.formatAmountOnly(summary.TotalSales, settings.decimalPlaces)},\"${settings.currencySymbol}\",\"$periodLabel\"\n")
            append("\"تكلفة البضاعة المباعة (COGS)\",${Formatters.formatAmountOnly(summary.CostOfGoodsSold, settings.decimalPlaces)},\"${settings.currencySymbol}\",\"$periodLabel\"\n")
            append("\"مجمل الربح التجاري\",${Formatters.formatAmountOnly(summary.GrossProfit, settings.decimalPlaces)},\"${settings.currencySymbol}\",\"$periodLabel\"\n")
            append("\"إجمالي المصروفات\",${Formatters.formatAmountOnly(summary.TotalExpenses, settings.decimalPlaces)},\"${settings.currencySymbol}\",\"$periodLabel\"\n")
            append("\"صافي الربح الفعلي\",${Formatters.formatAmountOnly(summary.NetProfit, settings.decimalPlaces)},\"${settings.currencySymbol}\",\"$periodLabel\"\n")
            append("\"تقييم المخزون بالتكلفة\",${Formatters.formatAmountOnly(summary.InventoryValuation, settings.decimalPlaces)},\"${settings.currencySymbol}\",\"$periodLabel\"\n")
            append("\"إجمالي لنا (مدين)\",${Formatters.formatAmountOnly(summary.TotalDebtsToUs, settings.decimalPlaces)},\"${settings.currencySymbol}\",\"$periodLabel\"\n")
            append("\"إجمالي علينا (دائن)\",${Formatters.formatAmountOnly(summary.TotalDebtsOnUs, settings.decimalPlaces)},\"${settings.currencySymbol}\",\"$periodLabel\"\n")
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    out.write(buildCsvText().toByteArray(Charsets.UTF_8))
                }
                exportStatusBanner = "تم تصدير وحفظ ملف جدول التقرير (CSV) بنجاح."
            } catch (e: Exception) {
                exportStatusBanner = "تعذر حفظ ملف التقرير: ${e.message}"
            }
        }
    }

    Scaffold(
        containerColor = SmartLedgerColors.SurfaceWindow,
        topBar = {
            Column(
                modifier = Modifier
                    .background(SmartLedgerColors.SurfacePanel)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(34.dp).clip(CircleShape).background(SmartLedgerColors.SurfaceCardElevated)
                            ) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "رجوع", tint = SmartLedgerColors.TextPrimary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column {
                            Text(
                                "التقارير والمؤشرات المالية",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            Text(
                                "تاريخ التقرير: ${Formatters.todayDate()} • العملة: ${settings.currencySymbol}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ScrollableFilterTabs(
                    tabs = periodTabs,
                    selectedTabId = selectedPeriod,
                    onTabSelected = { viewModel.onPeriodSelected(it) }
                )

                Spacer(modifier = Modifier.height(6.dp))

                ScrollableFilterTabs(
                    tabs = sectionTabs,
                    selectedTabId = selectedReportSection,
                    onTabSelected = { selectedReportSection = it }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            if (!exportStatusBanner.isNullOrBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SmartLedgerColors.StateSuccessSubtle)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(exportStatusBanner!!, color = SmartLedgerColors.StateSuccessText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { exportStatusBanner = null }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = SmartLedgerColors.StateSuccessText, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SmartLedgerColors.SurfaceCardElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("النطاق الزمني: $periodLabel", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                    Text("العملة: ${settings.currencySymbol}", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                }
            }

            when (selectedReportSection) {
                "INCOME" -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("قائمة الدخل والأرباح الصافية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                                Spacer(modifier = Modifier.height(10.dp))

                                ReportMetricRow("إجمالي المبيعات المحققة", summary.TotalSales, settings.currencySymbol, settings.decimalPlaces, SmartLedgerColors.TextPrimary)
                                ReportMetricRow("تكلفة البضاعة المباعة (COGS)", summary.CostOfGoodsSold, settings.currencySymbol, settings.decimalPlaces, SmartLedgerColors.TextMuted)
                                Divider(color = SmartLedgerColors.BorderGridLine, modifier = Modifier.padding(vertical = 4.dp))
                                ReportMetricRow("مجمل الربح التجاري", summary.GrossProfit, settings.currencySymbol, settings.decimalPlaces, SmartLedgerColors.AccentLight, isBold = true)
                                ReportMetricRow("إجمالي المصروفات التشغيلية", summary.TotalExpenses, settings.currencySymbol, settings.decimalPlaces, SmartLedgerColors.StateDanger)
                                Divider(color = SmartLedgerColors.BorderGridLine, modifier = Modifier.padding(vertical = 4.dp))
                                ReportMetricRow(
                                    "صافي الربح الفعلي",
                                    summary.NetProfit,
                                    settings.currencySymbol,
                                    settings.decimalPlaces,
                                    if (summary.NetProfit >= 0) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger,
                                    isBold = true
                                )
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
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("مؤشر توازن الإيرادات والمصروفات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                                Spacer(modifier = Modifier.height(12.dp))

                                val totalVolume = (summary.TotalSales + summary.TotalExpenses).coerceAtLeast(1.0)
                                val salesRatio = (summary.TotalSales / totalVolume).toFloat().coerceIn(0.05f, 0.95f)
                                val expRatio = 1f - salesRatio

                                Canvas(modifier = Modifier.fillMaxWidth().height(42.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    drawRoundRect(
                                        color = SmartLedgerColors.StateSuccess,
                                        topLeft = Offset(0f, 0f),
                                        size = Size(w * salesRatio, h),
                                        cornerRadius = CornerRadius(10f, 10f)
                                    )
                                    drawRoundRect(
                                        color = SmartLedgerColors.StateDanger,
                                        topLeft = Offset(w * salesRatio + 4.dp.toPx(), 0f),
                                        size = Size((w * expRatio - 4.dp.toPx()).coerceAtLeast(8f), h),
                                        cornerRadius = CornerRadius(10f, 10f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(SmartLedgerColors.StateSuccess))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "المبيعات: ${Formatters.formatCurrency(summary.TotalSales, settings.currencySymbol, settings.decimalPlaces)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SmartLedgerColors.TextSecondary
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(SmartLedgerColors.StateDanger))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "المصروفات: ${Formatters.formatCurrency(summary.TotalExpenses, settings.currencySymbol, settings.decimalPlaces)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SmartLedgerColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                "INVENTORY" -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            val totalRetailValue = products.sumOf { it.CurrentStockMinor.coerceAtLeast(0.0) * it.RetailPrice }
                            val expectedStockProfit = totalRetailValue - summary.InventoryValuation
                            val lowStockItems = products.filter { it.CurrentStockMinor <= it.AlertThreshold }

                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("تقييم بضاعة المخزن الحالية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                                ReportMetricRow("عدد الأصناف المسجلة", products.size.toDouble(), "صنف", 0, SmartLedgerColors.TextPrimary)
                                ReportMetricRow("قيمة المخزون بسعر التكلفة", summary.InventoryValuation, settings.currencySymbol, settings.decimalPlaces, SmartLedgerColors.AccentLight, isBold = true)
                                ReportMetricRow("القيمة البيعية المتوقعة للمخزون", totalRetailValue, settings.currencySymbol, settings.decimalPlaces, SmartLedgerColors.StateSuccess)
                                ReportMetricRow("الأرباح الكامنة في المخزون", expectedStockProfit, settings.currencySymbol, settings.decimalPlaces, SmartLedgerColors.StateSuccessText)
                                Divider(color = SmartLedgerColors.BorderGridLine)
                                Text("الأصناف التي وصلت لحد النفاد (${lowStockItems.size}):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateWarning)
                                if (lowStockItems.isEmpty()) {
                                    Text("جميع الأصناف فوق حد التنبيه الأدنى.", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateSuccess)
                                } else {
                                    lowStockItems.take(10).forEach { item ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("• ${item.Name}", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextPrimary)
                                            Text(
                                                "${Formatters.formatQuantity(item.CurrentStockMinor)} ${item.SmallUnit} (الحد: ${Formatters.formatQuantity(item.AlertThreshold)})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SmartLedgerColors.StateDanger,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "DEBTS" -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            val topDebtors = accounts.filter { it.NetBalance > 0.001 }.sortedByDescending { it.NetBalance }.take(8)
                            val topCreditors = accounts.filter { it.NetBalance < -0.001 }.sortedBy { it.NetBalance }.take(8)

                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("ملخص الذمم المدينة والدائنة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                                ReportMetricRow("إجمالي لنا عند العملاء (مدين)", summary.TotalDebtsToUs, settings.currencySymbol, settings.decimalPlaces, SmartLedgerColors.StateDanger, isBold = true)
                                ReportMetricRow("إجمالي علينا للموردين (دائن)", summary.TotalDebtsOnUs, settings.currencySymbol, settings.decimalPlaces, SmartLedgerColors.StateSuccess, isBold = true)
                                ReportMetricRow("صافي المركز المالي", summary.TotalDebtsToUs - summary.TotalDebtsOnUs, settings.currencySymbol, settings.decimalPlaces, SmartLedgerColors.AccentLight, isBold = true)

                                Divider(color = SmartLedgerColors.BorderGridLine)
                                Text("أعلى الحسابات المدينة (لنا عليهم):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateDanger)
                                if (topDebtors.isEmpty()) {
                                    Text("لا توجد ديون قائمة على العملاء.", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                                } else {
                                    topDebtors.forEach { acc ->
                                        ReportMetricRow(acc.PersonName, acc.NetBalance, settings.currencySymbol, settings.decimalPlaces, SmartLedgerColors.StateDanger)
                                    }
                                }

                                Divider(color = SmartLedgerColors.BorderGridLine)
                                Text("أعلى الحسابات الدائنة (لهم علينا):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateSuccess)
                                if (topCreditors.isEmpty()) {
                                    Text("لا توجد التزامات قائمة للموردين.", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                                } else {
                                    topCreditors.forEach { acc ->
                                        ReportMetricRow(acc.PersonName, kotlin.math.abs(acc.NetBalance), settings.currencySymbol, settings.decimalPlaces, SmartLedgerColors.StateSuccess)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = rememberDebouncedClick {
                            try {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "التقرير المالي — الدفتر الذكي Pro")
                                    putExtra(Intent.EXTRA_TEXT, buildReportPlainText())
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "مشاركة التقرير المالي"))
                            } catch (e: Exception) {
                                exportStatusBanner = "تعذر فتح نافذة المشاركة على الجهاز."
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة التقرير", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = rememberDebouncedClick {
                            csvExportLauncher.launch("SmartLedgerPro_Report_${Formatters.todayDate()}.csv")
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = SmartLedgerColors.StateSuccess, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حفظ جدول CSV / Excel", color = SmartLedgerColors.StateSuccess, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun ReportMetricRow(
    title: String,
    amount: Double,
    currencySymbol: String,
    decimals: Int,
    color: Color,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isBold) SmartLedgerColors.TextPrimary else SmartLedgerColors.TextSecondary,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = Formatters.formatCurrency(amount, currencySymbol, decimals),
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}
