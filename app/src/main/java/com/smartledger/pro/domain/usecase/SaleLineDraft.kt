package com.smartledger.pro.domain.usecase

import com.smartledger.pro.data.local.dao.AccountBalanceSummary
import com.smartledger.pro.data.local.dao.FinancialReportSummary
import com.smartledger.pro.data.local.dao.ProductStockItem
import com.smartledger.pro.data.local.entity.*
import com.smartledger.pro.data.repository.*
import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.util.NumberFormatter
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max

/**
 * السياسة المركزية الموحدة لاتجاهات الحركات المالية والمخزنية (Single Source of Truth for Accounting Rules).
 */
data class SaleLineDraft(
    val productId: Long,
    val name: String,
    val barcode: String?,
    val smallUnit: String,
    val largeUnit: String?,
    val unitFactor: Double,
    val majorQuantity: Double,
    val minorQuantity: Double,
    val minorUnitPrice: Double,
    val majorUnitPrice: Double,
    val minorUnitCost: Double,
    val availableStockMinor: Double
) {
    val compositeCalc
        get() = UnitConversionEngine.calculateCompositeSale(
            majorQty = majorQuantity,
            minorQty = minorQuantity,
            unitFactor = unitFactor,
            minorSalePrice = minorUnitPrice,
            majorSalePrice = majorUnitPrice,
            minorUnitCost = minorUnitCost
        )
}

/**
 * حالة استخدام إصدار فاتورة المبيعات (CreateSaleUseCase)
 */
