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

fun buildPlainTextReport(
    tradeName: String,
    periodLabel: String,
    currency: String,
    summary: FinancialReportSummary
): String {
    return """
📊 التقرير المالي — $tradeName
الفترة: $periodLabel
تاريخ الإصدار: ${NumberFormatter.nowTimestamp()}
────────────────────────
• إجمالي المبيعات المعتمدة: ${NumberFormatter.formatCurrency(summary.TotalSales, currency)}
• تكلفة البضاعة المباعة (COGS): ${NumberFormatter.formatCurrency(summary.CostOfGoodsSold, currency)}
• مجمل الربح التجاري: ${NumberFormatter.formatCurrency(summary.GrossProfit, currency)}
• إجمالي المصروفات التشغيلية: ${NumberFormatter.formatCurrency(summary.TotalExpenses, currency)}
• صافي الربح المحاسبي: ${NumberFormatter.formatCurrency(summary.NetProfit, currency)}
────────────────────────
• إجمالي المقبوضات النقدية للصندوق: ${NumberFormatter.formatCurrency(summary.CashInflows, currency)}
• إجمالي المدفوعات النقدية من الصندوق: ${NumberFormatter.formatCurrency(summary.CashOutflows, currency)}
• صافي السيولة النقدية بالصندوق: ${NumberFormatter.formatCurrency(summary.NetCashBalance, currency)}
────────────────────────
• قيمة المخزون (بالتكلفة): ${NumberFormatter.formatCurrency(summary.InventoryValuation, currency)}
• إجمالي الديون لنا (مدين): ${NumberFormatter.formatCurrency(summary.TotalDebtsToUs, currency)}
• إجمالي الالتزامات علينا (دائن): ${NumberFormatter.formatCurrency(summary.TotalDebtsOnUs, currency)}
    """.trimIndent()
}

fun buildCsvFinancialReport(
    tradeName: String,
    periodLabel: String,
    currency: String,
    summary: FinancialReportSummary
): String {
    val sb = StringBuilder()
    sb.appendLine("المنشأة,$tradeName")
    sb.appendLine("الفترة,$periodLabel")
    sb.appendLine("تاريخ التقرير,${NumberFormatter.nowTimestamp()}")
    sb.appendLine("البند المالي,المبلغ,العملة")
    sb.appendLine("إجمالي المبيعات المعتمدة,${NumberFormatter.formatAmount(summary.TotalSales)},$currency")
    sb.appendLine("تكلفة البضاعة المباعة (COGS),${NumberFormatter.formatAmount(summary.CostOfGoodsSold)},$currency")
    sb.appendLine("مجمل الربح التجاري,${NumberFormatter.formatAmount(summary.GrossProfit)},$currency")
    sb.appendLine("إجمالي المصروفات التشغيلية,${NumberFormatter.formatAmount(summary.TotalExpenses)},$currency")
    sb.appendLine("صافي الربح المحاسبي,${NumberFormatter.formatAmount(summary.NetProfit)},$currency")
    sb.appendLine("إجمالي المقبوضات النقدية للصندوق,${NumberFormatter.formatAmount(summary.CashInflows)},$currency")
    sb.appendLine("إجمالي المدفوعات النقدية من الصندوق,${NumberFormatter.formatAmount(summary.CashOutflows)},$currency")
    sb.appendLine("صافي السيولة النقدية بالصندوق,${NumberFormatter.formatAmount(summary.NetCashBalance)},$currency")
    sb.appendLine("قيمة بضاعة المخزن بالتكلفة,${NumberFormatter.formatAmount(summary.InventoryValuation)},$currency")
    sb.appendLine("إجمالي الديون لنا لدى العملاء,${NumberFormatter.formatAmount(summary.TotalDebtsToUs)},$currency")
    sb.appendLine("إجمالي الالتزامات علينا للموردين,${NumberFormatter.formatAmount(summary.TotalDebtsOnUs)},$currency")
    return sb.toString()
}

fun buildPrintableHtmlReport(
    tradeName: String,
    periodLabel: String,
    currency: String,
    summary: FinancialReportSummary
): String {
    return """
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="UTF-8">
<title>التقرير المالي — $tradeName</title>
<style>
  body { font-family: sans-serif; margin: 24px; color: #0f172a; background: #ffffff; }
  h1 { margin-bottom: 4px; color: #0284c7; }
  .meta { color: #64748b; font-size: 13px; margin-bottom: 20px; }
  table { width: 100%; border-collapse: collapse; margin-bottom: 24px; }
  th, td { border: 1px solid #cbd5e1; padding: 10px 12px; text-align: right; }
  th { background-color: #f1f5f9; font-weight: bold; }
  .total-row { background-color: #ecfdf5; font-weight: bold; }
</style>
</head>
<body>
  <h1>$tradeName — التقرير المالي</h1>
  <div class="meta">الفترة: $periodLabel | تاريخ الإصدار: ${NumberFormatter.nowTimestamp()} | العملة: $currency</div>
  <table>
    <thead>
      <tr><th>البند المالي</th><th>القيمة ($currency)</th></tr>
    </thead>
    <tbody>
      <tr><td>إجمالي المبيعات المعتمدة</td><td>${NumberFormatter.formatAmount(summary.TotalSales)}</td></tr>
      <tr><td>تكلفة البضاعة المباعة (COGS)</td><td>${NumberFormatter.formatAmount(summary.CostOfGoodsSold)}</td></tr>
      <tr><td>مجمل الربح التجاري</td><td>${NumberFormatter.formatAmount(summary.GrossProfit)}</td></tr>
      <tr><td>إجمالي المصروفات التشغيلية</td><td>${NumberFormatter.formatAmount(summary.TotalExpenses)}</td></tr>
      <tr class="total-row"><td>صافي الربح المحاسبي</td><td>${NumberFormatter.formatAmount(summary.NetProfit)}</td></tr>
      <tr><td>إجمالي المقبوضات النقدية للصندوق</td><td>${NumberFormatter.formatAmount(summary.CashInflows)}</td></tr>
      <tr><td>إجمالي المدفوعات النقدية من الصندوق</td><td>${NumberFormatter.formatAmount(summary.CashOutflows)}</td></tr>
      <tr class="total-row"><td>صافي السيولة النقدية بالصندوق</td><td>${NumberFormatter.formatAmount(summary.NetCashBalance)}</td></tr>
      <tr><td>قيمة بضاعة المخزن بالتكلفة</td><td>${NumberFormatter.formatAmount(summary.InventoryValuation)}</td></tr>
      <tr><td>إجمالي الديون لنا لدى العملاء (مدين)</td><td>${NumberFormatter.formatAmount(summary.TotalDebtsToUs)}</td></tr>
      <tr><td>إجمالي الالتزامات علينا للموردين (دائن)</td><td>${NumberFormatter.formatAmount(summary.TotalDebtsOnUs)}</td></tr>
    </tbody>
  </table>
</body>
</html>
    """.trimIndent()
}
