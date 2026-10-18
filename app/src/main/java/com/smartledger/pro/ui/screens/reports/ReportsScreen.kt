package com.smartledger.pro.ui.screens.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.dao.FinancialReportSummary
import com.smartledger.pro.ui.components.FilterChipRow
import com.smartledger.pro.ui.components.StatusFeedbackBanner
import com.smartledger.pro.ui.components.pressScaleEffect
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.viewmodels.ReportsViewModel
import com.smartledger.pro.util.NumberFormatter
import com.smartledger.pro.util.ThermalPrinterHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

@Composable
fun ReportsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: ReportsViewModel = viewModel()
) {
    val summary by viewModel.summary.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val identity by viewModel.businessIdentity.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var statusBanner by rememberSaveable { mutableStateOf<String?>(null) }
    var isErrorBanner by rememberSaveable { mutableStateOf(false) }

    val periodLabel = when (selectedPeriod) {
        "TODAY" -> "اليوم (${NumberFormatter.todayDate()})"
        "WEEK" -> "آخر 7 أيام"
        "MONTH" -> "هذا الشهر"
        "YEAR" -> "هذا العام"
        else -> "تراكمي (كل الفترات)"
    }

    val grossMarginPct = if (summary.TotalSales > 0.0) {
        ((summary.GrossProfit / summary.TotalSales) * 100.0)
    } else 0.0

    val htmlReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            val tradeNameSnap = identity?.TradeName ?: "الدفتر الذكي Pro"
            val currencySnap = AppDesignState.currencySymbol
            val summarySnap = summary
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val htmlContent = buildPrintableHtmlReport(
                            tradeName = tradeNameSnap,
                            periodLabel = periodLabel,
                            currency = currencySnap,
                            summary = summarySnap
                        )
                        val outStream = context.contentResolver.openOutputStream(uri)
                            ?: throw IOException("تعذر فتح مسار الحفظ المختار")
                        outStream.use { os ->
                            os.write(htmlContent.toByteArray(Charsets.UTF_8))
                            os.flush()
                        }
                    }
                    isErrorBanner = false
                    statusBanner = "تم تصدير التقرير المالي القابل للطباعة بنجاح."
                } catch (e: Exception) {
                    isErrorBanner = true
                    statusBanner = "تعذر حفظ ملف التقرير: ${e.localizedMessage}"
                }
            }
        }
    }

    val csvReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val tradeNameSnap = identity?.TradeName ?: "الدفتر الذكي Pro"
            val currencySnap = AppDesignState.currencySymbol
            val summarySnap = summary
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val csvContent = buildCsvFinancialReport(
                            tradeName = tradeNameSnap,
                            periodLabel = periodLabel,
                            currency = currencySnap,
                            summary = summarySnap
                        )
                        val outStream = context.contentResolver.openOutputStream(uri)
                            ?: throw IOException("تعذر فتح مسار الحفظ المختار")
                        outStream.use { os ->
                            os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                            os.write(csvContent.toByteArray(Charsets.UTF_8))
                            os.flush()
                        }
                    }
                    isErrorBanner = false
                    statusBanner = "تم تصدير جدول البيانات المالي (CSV / Excel) بنجاح."
                } catch (e: Exception) {
                    isErrorBanner = true
                    statusBanner = "تعذر تصدير جدول البيانات: ${e.localizedMessage}"
                }
            }
        }
    }

    Scaffold(
        containerColor = SmartLedgerColors.SurfaceWindow,
        topBar = {
            Column(
                modifier = Modifier
                    .background(SmartLedgerColors.SurfacePanel)
                    .padding(horizontal = SmartLedgerTokens.screenHorizontalPadding, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column {
                            Text(
                                text = "التقارير والقوائم المالية",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            Text(
                                text = "الفترة: $periodLabel • العملة: ${AppDesignState.currencySymbol}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.AccentLight
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val textSummary = buildPlainTextReport(
                                tradeName = identity?.TradeName ?: "الدفتر الذكي Pro",
                                periodLabel = periodLabel,
                                currency = AppDesignState.currencySymbol,
                                summary = summary
                            )
                            ThermalPrinterHelper.shareTextSafely(context, "تقرير مالي — $periodLabel", textSummary)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SmartLedgerColors.SurfaceCardElevated)
                            .pressScaleEffect()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة ملخص التقرير", tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(18.dp))
                    }
                }

                FilterChipRow(
                    items = listOf(
                        "ALL" to "كل الفترات",
                        "TODAY" to "اليوم",
                        "WEEK" to "آخر 7 أيام",
                        "MONTH" to "هذا الشهر",
                        "YEAR" to "هذا العام"
                    ),
                    selectedItem = selectedPeriod,
                    onItemSelected = { viewModel.setPeriod(it) }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = SmartLedgerTokens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                StatusFeedbackBanner(
                    message = statusBanner,
                    isError = isErrorBanner,
                    onDismiss = { statusBanner = null }
                )
            }

            // 1. بطاقة قائمة الدخل والأرباح المحاسبية (Accrual Income Statement)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                    shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "1. قائمة الدخل والأرباح الصافية",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.AccentLight
                            )
                            Text(
                                text = String.format(Locale.US, "هامش الربح: %.1f%%", grossMarginPct),
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.StateSuccess,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        ReportMetricRow("إجمالي المبيعات المعتمدة", summary.TotalSales, SmartLedgerColors.TextPrimary)
                        ReportMetricRow("تكلفة البضاعة المباعة (COGS)", summary.CostOfGoodsSold, SmartLedgerColors.TextMuted)
                        Divider(color = SmartLedgerColors.BorderSubtle)
                        ReportMetricRow("مجمل الربح التجاري", summary.GrossProfit, SmartLedgerColors.AccentLight, isBold = true)
                        ReportMetricRow("إجمالي المصروفات التشغيلية", summary.TotalExpenses, SmartLedgerColors.StateDanger)
                        Divider(color = SmartLedgerColors.BorderSubtle)
                        ReportMetricRow(
                            title = "صافي الربح المحاسبي",
                            amount = summary.NetProfit,
                            color = if (summary.NetProfit >= 0) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger,
                            isBold = true
                        )
                    }
                }
            }

            // 2. بطاقة حركة الصندوق والسيولة النقدية الفعلية (Cashbox & Liquidity Statement)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                    shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "2. حركة الصندوق والسيولة النقدية الفعلية",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.AccentLight
                        )
                        Text(
                            text = "تفصل بين السيولة النقدية الفعلية الداخلة والخارجة من الصندوق وبين المبيعات الآجلة غير المحصلة بعد",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )

                        ReportMetricRow("إجمالي المقبوضات النقدية (مبيعات نقدية + سندات قبض)", summary.CashInflows, SmartLedgerColors.StateSuccess)
                        ReportMetricRow("إجمالي المدفوعات النقدية (مشتريات نقدية + سندات صرف + مصروفات)", summary.CashOutflows, SmartLedgerColors.StateDanger)
                        Divider(color = SmartLedgerColors.BorderSubtle)
                        ReportMetricRow(
                            title = "صافي السيولة النقدية بالصندوق",
                            amount = summary.NetCashBalance,
                            color = if (summary.NetCashBalance >= 0) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger,
                            isBold = true
                        )
                    }
                }
            }

            // 3. بطاقة تقييم المخزون والذمم المدينة والدائنة
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                    shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "3. المركز المالي والذمم والمخزون",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.AccentLight
                        )

                        ReportMetricRow("قيمة بضاعة المخزن (بسعر التكلفة)", summary.InventoryValuation, SmartLedgerColors.AccentLight)
                        ReportMetricRow("إجمالي الديون لنا لدى العملاء (مدين)", summary.TotalDebtsToUs, SmartLedgerColors.StateSuccess)
                        ReportMetricRow("إجمالي الالتزامات علينا للموردين (دائن)", summary.TotalDebtsOnUs, SmartLedgerColors.StateDanger)
                        Divider(color = SmartLedgerColors.BorderSubtle)
                        val netReceivables = summary.TotalDebtsToUs - summary.TotalDebtsOnUs
                        ReportMetricRow(
                            title = "صافي الذمم المالية (لنا - علينا)",
                            amount = netReceivables,
                            color = if (netReceivables >= 0) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger,
                            isBold = true
                        )
                    }
                }
            }

            // 4. مؤشر بصري واضح لتوازن المبيعات والتكاليف والمصروفات
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                    shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "توزيع الإيرادات مقابل التكلفة والمصروفات",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )

                        val totalVolume = (summary.TotalSales + summary.TotalExpenses).coerceAtLeast(1.0)
                        val salesRatio = (summary.TotalSales / totalVolume).toFloat().coerceIn(0.08f, 0.92f)
                        val expRatio = (1f - salesRatio).coerceIn(0.08f, 0.92f)

                        val successColor = SmartLedgerColors.StateSuccess
                        val dangerColor = SmartLedgerColors.StateDanger

                        Canvas(modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp)) {
                            val w = size.width
                            val h = size.height
                            drawRoundRect(
                                color = successColor,
                                topLeft = Offset(0f, 0f),
                                size = Size(w * salesRatio - 3.dp.toPx(), h),
                                cornerRadius = CornerRadius(12f, 12f)
                            )
                            drawRoundRect(
                                color = dangerColor,
                                topLeft = Offset(w * salesRatio + 3.dp.toPx(), 0f),
                                size = Size(w * expRatio - 3.dp.toPx(), h),
                                cornerRadius = CornerRadius(12f, 12f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(SmartLedgerColors.StateSuccess))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "المبيعات (${NumberFormatter.formatCurrency(summary.TotalSales, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextSecondary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(SmartLedgerColors.StateDanger))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "المصروفات (${NumberFormatter.formatCurrency(summary.TotalExpenses, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // 5. أزرار التصدير الفعلي
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val fileName = "SmartLedger_Report_${NumberFormatter.todayDate()}.html"
                            htmlReportLauncher.launch(fileName)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .pressScaleEffect(),
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حفظ تقرير للطباعة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            val fileName = "SmartLedger_Report_${NumberFormatter.todayDate()}.csv"
                            csvReportLauncher.launch(fileName)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .pressScaleEffect(),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.StateSuccess)),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = SmartLedgerColors.StateSuccess, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصدير جدول Excel/CSV", color = SmartLedgerColors.StateSuccess, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}
