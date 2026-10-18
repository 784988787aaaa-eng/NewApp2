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
object SequentialInvoiceNumberGenerator {
    private val atomicCounter = AtomicLong(0L)

    fun nextCandidate(prefix: String, dateCode: String, currentMaxId: Long = 0L): String {
        val cleanPrefix = prefix.trim().ifBlank { "INV" }
        val seq = atomicCounter.updateAndGet { prev -> max(prev + 1L, currentMaxId + 1L) }
        return "$cleanPrefix-$dateCode-${seq.toString().padStart(4, '0')}"
    }
}
