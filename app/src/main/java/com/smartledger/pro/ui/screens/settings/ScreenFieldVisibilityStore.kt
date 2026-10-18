package com.smartledger.pro.ui.screens.settings

import android.content.Context
import android.content.SharedPreferences
import com.smartledger.pro.domain.model.AppUsageMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * مخزن التحكم الدقيق في إظهار وإخفاء عناصر وبطاقات وحقول وأزرار كل شاشة (Granular UI Visibility Engine).
 * يتيح للمستخدم سيطرة كاملة 100% على ما يراه في كل شاشة ونافذة، كما يدعم القوالب الجاهزة
 * للأوضاع الثلاثة: الكلاسيكي (CLASSIC)، العادي (STANDARD)، والاحترافي (PRO).
 */
data class ScreenFieldVisibility(
    // الحقول الأساسية المتوافقة مع الشاشات الحالية
    val inventoryBarcode: Boolean = true,
    val productBarcode: Boolean = true,
    val productShelf: Boolean = true,
    val posBarcode: Boolean = true,

    // 1. تخصيص الواجهة الرئيسية (مركز القيادة المالي)
    val showDashboardCashPulse: Boolean = true,      // نبض السيولة: كل ما دخل وكل ما خرج والصافي
    val showDashboardTreasuries: Boolean = true,     // الصناديق الرئيسية والفرعية وحركتها
    val showDashboardDebtRadar: Boolean = true,      // ميزان الديون والذمم (لنا / علينا / الصافي)
    val showDashboardStockRadar: Boolean = true,     // رادار المخزن والأصناف الحرجة
    val showDashboardSmartActions: Boolean = true,   // شريط الأزرار الذكية المدمجة
    val showDashboardLiveStream: Boolean = true,     // سجل الحركات الموحد مع الفلترة اللحظية

    // 2. تخصيص شاشة المخزن والأصناف
    val showStockSummaryStrip: Boolean = true,       // شريط إجمالي قيمة المخزون والربح المتوقع
    val showCostAndMarginInInventory: Boolean = true,// إظهار التكلفة وهامش الربح في بطاقة الصنف
    val showWarehouseBadgesInInventory: Boolean = true, // شارة اسم المخزن والوحدات المركبة
    val showQuickStocktakeButton: Boolean = true,    // زر الجرد السريع المباشر على بطاقة الصنف

    // 3. تخصيص شاشة الحسابات والديون والصناديق
    val showAccountSummaryHeader: Boolean = true,    // شريط ملخص الديون الكلي (لنا / علينا / الصافي) أعلى الحسابات
    val showQuickInlineVoucherButtons: Boolean = true, // أزرار (قبض / صرف / كشف) المدمجة داخل بطاقة العميل
    val showSubTreasuriesTree: Boolean = true,       // عرض الصناديق الفرعية مرتبطة بالرئيسية

    // 4. تخصيص نوافذ الإدخال السريعة ونقطة البيع
    val showLiveBalancePreviewInModals: Boolean = true, // معاينة الرصيد قبل وبعد العملية داخل النافذة
    val showQuickAmountChipsInModals: Boolean = true,   // أزرار المبالغ والنسب الذكية السريعة داخل النوافذ
    val showPosQuickProductsRibbon: Boolean = true      // شريط الأصناف السريعة أعلى نقطة البيع
)

object ScreenFieldVisibilityStore {
    private const val PREFS_NAME = "smartledger_pro_settings"
    private const val INVENTORY_BARCODE = "showBarcodeInInventory"
    private const val PRODUCT_BARCODE = "showBarcodeInProductForm"
    private const val PRODUCT_SHELF = "showShelfLocationInProductForm"
    private const val POS_BARCODE = "showBarcodeInPos"

    private const val DASH_CASH_PULSE = "vis_dash_cash_pulse"
    private const val DASH_TREASURIES = "vis_dash_treasuries"
    private const val DASH_DEBT_RADAR = "vis_dash_debt_radar"
    private const val DASH_STOCK_RADAR = "vis_dash_stock_radar"
    private const val DASH_SMART_ACTIONS = "vis_dash_smart_actions"
    private const val DASH_LIVE_STREAM = "vis_dash_live_stream"

    private const val INV_SUMMARY_STRIP = "vis_inv_summary_strip"
    private const val INV_COST_MARGIN = "vis_inv_cost_margin"
    private const val INV_WAREHOUSE_BADGES = "vis_inv_warehouse_badges"
    private const val INV_QUICK_STOCKTAKE = "vis_inv_quick_stocktake"

    private const val ACC_SUMMARY_HEADER = "vis_acc_summary_header"
    private const val ACC_INLINE_VOUCHERS = "vis_acc_inline_vouchers"
    private const val SUB_TREASURIES_TREE = "vis_sub_treasuries_tree"

    private const val MODAL_LIVE_BALANCE = "vis_modal_live_balance"
    private const val MODAL_QUICK_CHIPS = "vis_modal_quick_chips"
    private const val POS_QUICK_RIBBON = "vis_pos_quick_ribbon"

    private val allWatchedKeys = setOf(
        INVENTORY_BARCODE, PRODUCT_BARCODE, PRODUCT_SHELF, POS_BARCODE,
        "showBarcodeField", "showShelfLocation",
        DASH_CASH_PULSE, DASH_TREASURIES, DASH_DEBT_RADAR, DASH_STOCK_RADAR,
        DASH_SMART_ACTIONS, DASH_LIVE_STREAM,
        INV_SUMMARY_STRIP, INV_COST_MARGIN, INV_WAREHOUSE_BADGES, INV_QUICK_STOCKTAKE,
        ACC_SUMMARY_HEADER, ACC_INLINE_VOUCHERS, SUB_TREASURIES_TREE,
        MODAL_LIVE_BALANCE, MODAL_QUICK_CHIPS, POS_QUICK_RIBBON
    )

    private val state = MutableStateFlow(ScreenFieldVisibility())
    private var preferences: SharedPreferences? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    @Synchronized
    fun observe(context: Context): StateFlow<ScreenFieldVisibility> {
        val prefs = preferences ?: context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .also { loaded ->
                preferences = loaded
                state.value = read(loaded)
                val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { source, key ->
                    if (key == null || key in allWatchedKeys) {
                        state.value = read(source)
                    }
                }
                listener = changeListener
                loaded.registerOnSharedPreferenceChangeListener(changeListener)
            }
        if (state.value == ScreenFieldVisibility()) state.value = read(prefs)
        return state.asStateFlow()
    }

    fun update(context: Context, transform: (ScreenFieldVisibility) -> ScreenFieldVisibility) {
        val prefs = synchronized(this) {
            observe(context)
            preferences!!
        }
        val next = transform(state.value)
        prefs.edit()
            .putBoolean(INVENTORY_BARCODE, next.inventoryBarcode)
            .putBoolean(PRODUCT_BARCODE, next.productBarcode)
            .putBoolean(PRODUCT_SHELF, next.productShelf)
            .putBoolean(POS_BARCODE, next.posBarcode)
            .putBoolean(DASH_CASH_PULSE, next.showDashboardCashPulse)
            .putBoolean(DASH_TREASURIES, next.showDashboardTreasuries)
            .putBoolean(DASH_DEBT_RADAR, next.showDashboardDebtRadar)
            .putBoolean(DASH_STOCK_RADAR, next.showDashboardStockRadar)
            .putBoolean(DASH_SMART_ACTIONS, next.showDashboardSmartActions)
            .putBoolean(DASH_LIVE_STREAM, next.showDashboardLiveStream)
            .putBoolean(INV_SUMMARY_STRIP, next.showStockSummaryStrip)
            .putBoolean(INV_COST_MARGIN, next.showCostAndMarginInInventory)
            .putBoolean(INV_WAREHOUSE_BADGES, next.showWarehouseBadgesInInventory)
            .putBoolean(INV_QUICK_STOCKTAKE, next.showQuickStocktakeButton)
            .putBoolean(ACC_SUMMARY_HEADER, next.showAccountSummaryHeader)
            .putBoolean(ACC_INLINE_VOUCHERS, next.showQuickInlineVoucherButtons)
            .putBoolean(SUB_TREASURIES_TREE, next.showSubTreasuriesTree)
            .putBoolean(MODAL_LIVE_BALANCE, next.showLiveBalancePreviewInModals)
            .putBoolean(MODAL_QUICK_CHIPS, next.showQuickAmountChipsInModals)
            .putBoolean(POS_QUICK_RIBBON, next.showPosQuickProductsRibbon)
            .apply()
        state.value = next
    }

    /**
     * تطبيق القالب الذكي لوضع الاستخدام المختار (كلاسيكي / عادي / احترافي)
     * مع بقاء حرية المستخدم في تعديل أي خيار مفرد بعد ذلك!
     */
    fun applyUsageModePreset(context: Context, mode: AppUsageMode) {
        update(context) {
            when (mode) {
                AppUsageMode.CLASSIC -> ScreenFieldVisibility(
                    inventoryBarcode = false,
                    productBarcode = false,
                    productShelf = false,
                    posBarcode = false,
                    showDashboardCashPulse = true,
                    showDashboardTreasuries = false,
                    showDashboardDebtRadar = true,
                    showDashboardStockRadar = false,
                    showDashboardSmartActions = true,
                    showDashboardLiveStream = true,
                    showStockSummaryStrip = false,
                    showCostAndMarginInInventory = false,
                    showWarehouseBadgesInInventory = false,
                    showQuickStocktakeButton = false,
                    showAccountSummaryHeader = true,
                    showQuickInlineVoucherButtons = true,
                    showSubTreasuriesTree = false,
                    showLiveBalancePreviewInModals = true,
                    showQuickAmountChipsInModals = false,
                    showPosQuickProductsRibbon = true
                )
                AppUsageMode.STANDARD -> ScreenFieldVisibility(
                    inventoryBarcode = true,
                    productBarcode = true,
                    productShelf = false,
                    posBarcode = true,
                    showDashboardCashPulse = true,
                    showDashboardTreasuries = true,
                    showDashboardDebtRadar = true,
                    showDashboardStockRadar = true,
                    showDashboardSmartActions = true,
                    showDashboardLiveStream = true,
                    showStockSummaryStrip = true,
                    showCostAndMarginInInventory = false,
                    showWarehouseBadgesInInventory = true,
                    showQuickStocktakeButton = true,
                    showAccountSummaryHeader = true,
                    showQuickInlineVoucherButtons = true,
                    showSubTreasuriesTree = true,
                    showLiveBalancePreviewInModals = true,
                    showQuickAmountChipsInModals = true,
                    showPosQuickProductsRibbon = true
                )
                AppUsageMode.PRO -> ScreenFieldVisibility()
            }
        }
    }

    fun reset(context: Context) {
        update(context) { ScreenFieldVisibility() }
    }

    private fun read(prefs: SharedPreferences) = ScreenFieldVisibility(
        inventoryBarcode = prefs.getBoolean(INVENTORY_BARCODE, prefs.getBoolean("showBarcodeField", true)),
        productBarcode = prefs.getBoolean(PRODUCT_BARCODE, prefs.getBoolean("showBarcodeField", true)),
        productShelf = prefs.getBoolean(PRODUCT_SHELF, prefs.getBoolean("showShelfLocation", true)),
        posBarcode = prefs.getBoolean(POS_BARCODE, prefs.getBoolean("showBarcodeField", true)),
        showDashboardCashPulse = prefs.getBoolean(DASH_CASH_PULSE, true),
        showDashboardTreasuries = prefs.getBoolean(DASH_TREASURIES, true),
        showDashboardDebtRadar = prefs.getBoolean(DASH_DEBT_RADAR, true),
        showDashboardStockRadar = prefs.getBoolean(DASH_STOCK_RADAR, true),
        showDashboardSmartActions = prefs.getBoolean(DASH_SMART_ACTIONS, true),
        showDashboardLiveStream = prefs.getBoolean(DASH_LIVE_STREAM, true),
        showStockSummaryStrip = prefs.getBoolean(INV_SUMMARY_STRIP, true),
        showCostAndMarginInInventory = prefs.getBoolean(INV_COST_MARGIN, true),
        showWarehouseBadgesInInventory = prefs.getBoolean(INV_WAREHOUSE_BADGES, true),
        showQuickStocktakeButton = prefs.getBoolean(INV_QUICK_STOCKTAKE, true),
        showAccountSummaryHeader = prefs.getBoolean(ACC_SUMMARY_HEADER, true),
        showQuickInlineVoucherButtons = prefs.getBoolean(ACC_INLINE_VOUCHERS, true),
        showSubTreasuriesTree = prefs.getBoolean(SUB_TREASURIES_TREE, true),
        showLiveBalancePreviewInModals = prefs.getBoolean(MODAL_LIVE_BALANCE, true),
        showQuickAmountChipsInModals = prefs.getBoolean(MODAL_QUICK_CHIPS, true),
        showPosQuickProductsRibbon = prefs.getBoolean(POS_QUICK_RIBBON, true)
    )
}
