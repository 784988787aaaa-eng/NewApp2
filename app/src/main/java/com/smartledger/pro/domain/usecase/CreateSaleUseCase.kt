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
class CreateSaleUseCase(private val invoiceRepository: InvoiceRepository) {

    suspend fun execute(
        draftLines: List<SaleLineDraft>,
        customer: AccountBalanceSummary?,
        paymentType: String,
        discountAmount: Double,
        paidAmountInput: Double,
        notes: String?,
        allowNegativeStock: Boolean,
        invoicePrefix: String = "INV",
        financialAccountId: Long? = null,
        warehouseId: Long? = null
    ): Result<InvoiceEntity> {
        if (draftLines.isEmpty()) {
            return Result.failure(Exception("السلة فارغة؛ أضف صنفاً واحداً على الأقل لإصدار الفاتورة."))
        }

        if (!allowNegativeStock) {
            val grouped = draftLines.groupBy { it.productId }
            for ((_, items) in grouped) {
                val first = items.first()
                val totalReq = items.sumOf { it.compositeCalc.totalMinorQuantity }
                if (totalReq > first.availableStockMinor + 0.0001) {
                    return Result.failure(
                        Exception("الكمية المطلوبة من «${first.name}» (${NumberFormatter.formatQuantity(totalReq)} ${first.smallUnit}) تتجاوز المتاح بالمخزن (${NumberFormatter.formatQuantity(first.availableStockMinor)} ${first.smallUnit}).")
                    )
                }
            }
        }

        val subtotal = NumberFormatter.roundCurrency(draftLines.sumOf { it.compositeCalc.totalLinePrice })
        val safeDiscount = NumberFormatter.roundCurrency(discountAmount.coerceIn(0.0, subtotal))
        val finalTotal = NumberFormatter.roundCurrency(max(0.0, subtotal - safeDiscount))

        val effectivePaid = NumberFormatter.roundCurrency(
            when (paymentType) {
                "Cash" -> finalTotal
                "Credit" -> 0.0
                else -> paidAmountInput.coerceIn(0.0, finalTotal)
            }
        )
        val remaining = NumberFormatter.roundCurrency(max(0.0, finalTotal - effectivePaid))

        if (remaining > 0.0 && customer == null) {
            return Result.failure(Exception("الفواتير الآجلة أو الجزئية تتطلب اختيار عميل مسجل لقيد المديونية عليه."))
        }

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val dateCode = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val prefix = invoicePrefix.trim().ifBlank { "INV" }
        val candidateInvoiceNo = SequentialInvoiceNumberGenerator.nextCandidate(prefix, dateCode)

        val invoiceEntity = InvoiceEntity(
            InvoiceNumber = candidateInvoiceNo,
            Type = "Sale",
            PersonId = customer?.PersonId ?: 0L,
            PersonNameSnapshot = customer?.PersonName ?: "عميل نقدي عام",
            IssuedAt = now,
            Subtotal = subtotal,
            DiscountAmount = safeDiscount,
            TaxAmount = 0.0,
            FinalTotal = finalTotal,
            PaidAmount = effectivePaid,
            RemainingAmount = remaining,
            PaymentType = if (remaining == 0.0) "Cash" else if (effectivePaid == 0.0) "Credit" else "Partial",
            Notes = notes?.trim()?.ifBlank { null },
            CreatedAt = now,
            FinancialAccountId = financialAccountId,
            WarehouseId = warehouseId
        )

        val lineEntities = draftLines.mapIndexed { idx, item ->
            val calc = item.compositeCalc
            val totalMinor = calc.totalMinorQuantity
            val effectiveUnitPrice = if (totalMinor > 0.0) NumberFormatter.roundCurrency(calc.totalLinePrice / totalMinor, 4) else item.minorUnitPrice
            val breakdownText = UnitConversionEngine.formatCompositeQuantity(
                majorQty = item.majorQuantity,
                minorQty = item.minorQuantity,
                smallUnit = item.smallUnit,
                largeUnit = item.largeUnit
            )
            InvoiceLineEntity(
                InvoiceId = 0L,
                ProductId = item.productId,
                ProductNameSnapshot = item.name,
                BarcodeSnapshot = item.barcode,
                UnitName = breakdownText,
                IsMajorUnit = item.majorQuantity > 0 && item.minorQuantity == 0.0,
                ConversionFactor = item.unitFactor,
                QuantityMinor = totalMinor,
                UnitPrice = effectiveUnitPrice,
                LineDiscount = 0.0,
                LineTotal = calc.totalLinePrice,
                LineCost = item.minorUnitCost,
                SortOrder = idx + 1
            )
        }

        val saveRes = invoiceRepository.createInvoice(
            invoice = invoiceEntity,
            lines = lineEntities,
            allowNegativeStock = allowNegativeStock
        )
        return if (saveRes.isSuccess) {
            val savedId = saveRes.getOrThrow()
            val persistedInvoice = invoiceRepository.getInvoiceById(savedId) ?: invoiceEntity.copy(Id = savedId)
            Result.success(persistedInvoice)
        } else {
            Result.failure(saveRes.exceptionOrNull() ?: Exception("تعذر حفظ الفاتورة"))
        }
    }
}

/**
 * حالة استخدام إصدار فاتورة المشتريات والتوريد المخزني (CreatePurchaseUseCase).
 */
