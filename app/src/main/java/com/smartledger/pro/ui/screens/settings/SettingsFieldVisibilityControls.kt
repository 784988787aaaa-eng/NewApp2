package com.smartledger.pro.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartledger.pro.ui.theme.SmartLedgerColors

/**
 * وحدة التحكم التفصيلية في إظهار وإخفاء عناصر وحقول وبطاقات كل شاشة (SettingsFieldVisibilityControls)
 * تحافظ على المعاملات الأساسية مع توفير تحكم شامل بكل تفصيلة في التطبيق.
 */
@Composable
internal fun SettingsFieldVisibilityControls(
    inventoryBarcode: Boolean,
    productBarcode: Boolean,
    productShelf: Boolean,
    posBarcode: Boolean,
    onInventoryBarcodeChange: (Boolean) -> Unit,
    onProductBarcodeChange: (Boolean) -> Unit,
    onProductShelfChange: (Boolean) -> Unit,
    onPosBarcodeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val vis = ScreenFieldVisibilityStore.observe(context).value

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "1. تخصيص لوحات الشاشة الرئيسية (مركز القيادة)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = SmartLedgerColors.AccentLight
        )
        Text(
            text = "تحكم في كل بطاقة ومؤشر يظهر في الواجهة الأولى للتطبيق دون التأثير على البيانات المحفوظة.",
            style = MaterialTheme.typography.bodySmall,
            color = SmartLedgerColors.TextMuted
        )
        Spacer(modifier = Modifier.height(2.dp))
        SettingSwitchRow(
            title = "لوحة نبض السيولة (كل ما دخل • كل ما خرج • الصافي)",
            subtitle = "إظهار شريط التدفق النقدي اللحظي ومؤشرات اليوم والتراكمي",
            checked = vis.showDashboardCashPulse,
            onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showDashboardCashPulse = v) } }
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "ميزان الديون والذمم (لنا / علينا / الصافي)",
            subtitle = "إظهار بطاقة مديونيات العملاء والتزامات الموردين وأزرار القبض والصرف السريع",
            checked = vis.showDashboardDebtRadar,
            onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showDashboardDebtRadar = v) } }
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "منظومة الصناديق الرئيسية والفرعية",
            subtitle = "إظهار أرصدة الخزائن والمحافظ والوارد والصادر في الشاشة الرئيسية",
            checked = vis.showDashboardTreasuries,
            onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showDashboardTreasuries = v) } }
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "تفصيل الصناديق الفرعية تحت الرئيسية",
            subtitle = "إظهار شجرة الصناديق الفرعية التابعة لكل صندوق رئيسي",
            checked = vis.showSubTreasuriesTree,
            onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showSubTreasuriesTree = v) } }
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "رادار المخزن وحركة الأصناف والنواقص",
            subtitle = "إظهار تقييم المخزون والأصناف القاربة على النفاد أو النافدة",
            checked = vis.showDashboardStockRadar,
            onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showDashboardStockRadar = v) } }
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "شبكة الأوامر الذكية المدمجة (+)",
            subtitle = "إظهار الأزرار الكبسولية المزدوجة للفتح والإضافة المباشرة",
            checked = vis.showDashboardSmartActions,
            onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showDashboardSmartActions = v) } }
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "سجل الحركات اللحظي الموحد",
            subtitle = "إظهار أحدث الحركات مع شرائح الفلترة السريعة أسفل الرئيسية",
            checked = vis.showDashboardLiveStream,
            onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showDashboardLiveStream = v) } }
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "2. تخصيص شاشة المخزن وبطاقة الصنف",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = SmartLedgerColors.AccentLight
        )
        SettingSwitchRow(
            title = "شريط ملخص قيمة المخزون والربح المتوقع",
            subtitle = "إظهار إجمالي التكلفة والقيمة السوقية والربح المتوقع أعلى شاشة المخزون",
            checked = vis.showStockSummaryStrip,
            onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showStockSummaryStrip = v) } }
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "إظهار التكلفة وهامش الربح في بطاقة الصنف",
            subtitle = "عرض سعر الشراء ونسبة الربح بجوار سعر البيع (مناسب للوضع الاحترافي)",
            checked = vis.showCostAndMarginInInventory,
            onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showCostAndMarginInInventory = v) } }
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "إظهار شارة المخزن وزر الجرد السريع",
            subtitle = "عرض اسم المخزن وزر الجرد المباشر داخل بطاقة الصنف",
            checked = vis.showQuickStocktakeButton,
            onCheckedChange = { v ->
                ScreenFieldVisibilityStore.update(context) {
                    it.copy(showQuickStocktakeButton = v, showWarehouseBadgesInInventory = v)
                }
            }
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "الباركود في المخزون",
            subtitle = "إظهار أو إخفاء حقل الباركود في شاشة المخزون",
            checked = inventoryBarcode,
            onCheckedChange = onInventoryBarcodeChange
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "الباركود في بطاقة الصنف",
            subtitle = "إظهار حقل الباركود عند إضافة الصنف أو تعديله",
            checked = productBarcode,
            onCheckedChange = onProductBarcodeChange
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "موقع الرف في بطاقة الصنف",
            subtitle = "إظهار موقع الرف للمتاجر التي تنظّم الأصناف على الرفوف",
            checked = productShelf,
            onCheckedChange = onProductShelfChange
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "3. تخصيص شاشات الحسابات ونقطة البيع ونوافذ الإدخال",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = SmartLedgerColors.AccentLight
        )
        SettingSwitchRow(
            title = "شريط ملخص الديون الكلي أعلى شاشة الحسابات",
            subtitle = "عرض إجمالي (لنا مدين • علينا دائن • الصافي) أعلى قائمة الحسابات",
            checked = vis.showAccountSummaryHeader,
            onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showAccountSummaryHeader = v) } }
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "أزرار (قبض / صرف / كشف) المدمجة في بطاقة الحساب",
            subtitle = "إظهار شريط الإجراءات السريعة مباشرة داخل بطاقة كل عميل أو مورد",
            checked = vis.showQuickInlineVoucherButtons,
            onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showQuickInlineVoucherButtons = v) } }
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "الباركود في نقطة البيع",
            subtitle = "إظهار زر وحقل الباركود في شاشة البيع السريع POS",
            checked = posBarcode,
            onCheckedChange = onPosBarcodeChange
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "شريط الأصناف السريعة أعلى نقطة البيع",
            subtitle = "إظهار كبسولات الإضافة بلمسة واحدة أعلى سلة المبيعات",
            checked = vis.showPosQuickProductsRibbon,
            onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showPosQuickProductsRibbon = v) } }
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "معاينة الرصيد (قبل وبعد) داخل نوافذ السندات",
            subtitle = "عرض الرصيد الحالي والرصيد المتوقع وزر تصفية كامل الرصيد داخل النافذة",
            checked = vis.showLiveBalancePreviewInModals,
            onCheckedChange = { v -> ScreenFieldVisibilityStore.update(context) { it.copy(showLiveBalancePreviewInModals = v) } }
        )
    }
}
