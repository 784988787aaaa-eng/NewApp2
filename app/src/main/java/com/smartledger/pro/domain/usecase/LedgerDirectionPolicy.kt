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
object LedgerDirectionPolicy {
    val DEBIT_TYPES = setOf("DebtOnUs", "Sale", "Payment", "PurchaseReturn")
    val CREDIT_TYPES = setOf("DebtToUs", "Purchase", "Receive", "SaleReturn")

    val STOCK_IN_TYPES = setOf("In", "Purchase", "ReturnIn", "AdjustmentIn", "TransferIn")
    val STOCK_OUT_TYPES = setOf("Out", "Sale", "ReturnOut", "AdjustmentOut", "TransferOut")

    fun isCreditOperation(type: String): Boolean = type in CREDIT_TYPES
    fun isDebitOperation(type: String): Boolean = type in DEBIT_TYPES

    fun signedOperationDelta(type: String, amount: Double): Double {
        val clean = NumberFormatter.roundCurrency(amount)
        return when (type) {
            in DEBIT_TYPES -> clean
            in CREDIT_TYPES -> -clean
            else -> 0.0
        }
    }

    fun signedStockDelta(type: String, quantityMinor: Double): Double {
        return when (type) {
            in STOCK_IN_TYPES -> quantityMinor
            in STOCK_OUT_TYPES -> -quantityMinor
            else -> 0.0
        }
    }
}

/**
 * مولد أرقام الفواتير المتسلسل الفريد (SequentialInvoiceNumberGenerator)
 * يضمن عدم تكرار أرقام الفواتير حتى عند إصدار مئات الفواتير في نفس الثانية.
 */
