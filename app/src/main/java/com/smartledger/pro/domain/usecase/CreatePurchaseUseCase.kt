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
class CreatePurchaseUseCase(private val invoiceRepository: InvoiceRepository) {

    suspend fun execute(
        supplier: AccountBalanceSummary?,
        product: ProductStockItem,
        useMajorUnit: Boolean,
        quantity: Double,
        unitPurchaseCost: Double,
        paidCash: Double,
        notes: String?,
        purchasePrefix: String = "PUR",
        financialAccountId: Long? = null,
        warehouseId: Long? = null
    ): Result<Long> {
        if (quantity <= 0.0) {
            return Result.failure(Exception("يجب أن تكون كمية الشراء أكبر من صفر."))
        }
        if (unitPurchaseCost <= 0.0) {
            return Result.failure(Exception("يجب أن يكون سعر الشراء أكبر من صفر."))
        }

        val safeFactor = if (product.UnitFactor <= 0.0 || product.LargeUnit.isNullOrBlank()) 1.0 else product.UnitFactor
        val totalMinorQty = if (useMajorUnit && safeFactor > 1.0) quantity * safeFactor else quantity
        val minorCost = NumberFormatter.roundCurrency(
            if (useMajorUnit && safeFactor > 1.0) {
                UnitConversionEngine.minorCostFromMajorCost(unitPurchaseCost, safeFactor)
            } else {
                unitPurchaseCost
            },
            4
        )
        val total = NumberFormatter.roundCurrency(quantity * unitPurchaseCost)
        val safePaid = NumberFormatter.roundCurrency(paidCash.coerceIn(0.0, total))
        val remaining = NumberFormatter.roundCurrency(max(0.0, total - safePaid))

        if (remaining > 0.0 && supplier == null) {
            return Result.failure(Exception("المشتريات الآجلة أو الجزئية تتطلب اختيار مورد مسجل لقيد الالتزام له."))
        }

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val dateCode = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val prefix = purchasePrefix.trim().ifBlank { "PUR" }
        val candidateInvoiceNo = SequentialInvoiceNumberGenerator.nextCandidate(prefix, dateCode)
        val unitLabel = if (useMajorUnit && !product.LargeUnit.isNullOrBlank()) {
            "${NumberFormatter.formatQuantity(quantity)} ${product.LargeUnit}"
        } else {
            "${NumberFormatter.formatQuantity(quantity)} ${product.SmallUnit}"
        }

        val invoice = InvoiceEntity(
            InvoiceNumber = candidateInvoiceNo,
            Type = "Purchase",
            PersonId = supplier?.PersonId ?: 0L,
            PersonNameSnapshot = supplier?.PersonName ?: "مورد نقدي عام",
            IssuedAt = now,
            Subtotal = total,
            DiscountAmount = 0.0,
            TaxAmount = 0.0,
            FinalTotal = total,
            PaidAmount = safePaid,
            RemainingAmount = remaining,
            PaymentType = if (remaining == 0.0) "Cash" else if (safePaid == 0.0) "Credit" else "Partial",
            Notes = notes?.trim()?.ifBlank { null },
            CreatedAt = now,
            FinancialAccountId = financialAccountId,
            WarehouseId = warehouseId
        )

        val line = InvoiceLineEntity(
            InvoiceId = 0L,
            ProductId = product.Id,
            ProductNameSnapshot = product.Name,
            BarcodeSnapshot = product.Barcode,
            UnitName = unitLabel,
            IsMajorUnit = useMajorUnit,
            ConversionFactor = safeFactor,
            QuantityMinor = totalMinorQty,
            UnitPrice = minorCost,
            LineCost = minorCost,
            LineTotal = total,
            SortOrder = 1
        )

        return invoiceRepository.createInvoice(invoice, listOf(line), allowNegativeStock = true)
    }
}

/**
 * محرك الاختبار والتكامل الدفتري الذري (InMemoryLedgerEngine)
 * يطبق نفس القواعد المطبقة في `Repositories` و`SmartLedgerDaos`:
 * - إعادة فحص المخزون الحي وتجميع البنود المكررة داخل المعاملة.
 * - أرقام فواتير تسلسلية فريدة غير قابلة للتكرار.
 * - تحديث تكلفة الصنف بالمتوسط المرجح للتكلفة عند الشراء.
 * - الإلغاء المحاسبي الموثق (`cancelOperationWithReversal`) دون تضخيم التدفقات النقدية الخارجة عند عكس سند قبض.
 */
