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
object SequentialInvoiceNumberGenerator {
    private val atomicCounter = AtomicLong(0L)

    fun nextCandidate(prefix: String, dateCode: String, currentMaxId: Long = 0L): String {
        val cleanPrefix = prefix.trim().ifBlank { "INV" }
        val seq = atomicCounter.updateAndGet { prev -> max(prev + 1L, currentMaxId + 1L) }
        return "$cleanPrefix-$dateCode-${seq.toString().padStart(4, '0')}"
    }
}

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
class InMemoryLedgerEngine(var allowNegativeStock: Boolean = false) {
    private var nextPersonId = 1L
    private var nextProductId = 1L
    private var nextInvoiceId = 1L
    private var nextLineId = 1L
    private var nextMovementId = 1L
    private var nextOpId = 1L
    private var nextExpenseId = 1L
    private var nextDeletedId = 1L

    val people = mutableListOf<PersonEntity>()
    val products = mutableListOf<ProductEntity>()
    val invoices = mutableListOf<InvoiceEntity>()
    val invoiceLines = mutableListOf<InvoiceLineEntity>()
    val stockMovements = mutableListOf<StockMovementEntity>()
    val operations = mutableListOf<OperationEntity>()
    val expenses = mutableListOf<ExpenseEntity>()
    val deletedItems = mutableListOf<DeletedItemEntity>()

    fun createAccount(
        name: String,
        bookId: Long,
        openingBalance: Double = 0.0,
        isDebit: Boolean = true,
        creditLimit: Double = 0.0
    ): Result<Long> {
        if (name.trim().length < 2) return Result.failure(Exception("اسم الحساب قصير جداً"))
        val now = "2026-10-17 12:00:00"
        val id = nextPersonId++
        people.add(
            PersonEntity(
                Id = id,
                BookId = bookId,
                Name = name.trim(),
                CreditLimit = NumberFormatter.roundCurrency(creditLimit),
                IsActive = true,
                CreatedAt = now,
                LastOperationAt = now
            )
        )
        val cleanOpen = NumberFormatter.roundCurrency(openingBalance)
        if (cleanOpen > 0.0) {
            operations.add(
                OperationEntity(
                    Id = nextOpId++,
                    PersonId = id,
                    Type = if (isDebit) "DebtOnUs" else "DebtToUs",
                    Amount = cleanOpen,
                    OccurredAt = now,
                    Note = "رصيد افتتاحي تأسيسي",
                    ReferenceNumber = "OPEN-$id",
                    CreatedAt = now
                )
            )
        }
        return Result.success(id)
    }

    fun createProduct(
        name: String,
        barcode: String?,
        smallUnit: String = "قطعة",
        largeUnit: String? = "كرتون",
        unitFactor: Double = 24.0,
        costPrice: Double,
        retailPrice: Double,
        wholesalePrice: Double = 0.0,
        openingMajorQty: Double = 0.0,
        openingMinorQty: Double = 0.0
    ): Result<Long> {
        if (name.trim().isEmpty()) return Result.failure(Exception("اسم الصنف مطلوب"))
        if (retailPrice <= 0.0) return Result.failure(Exception("سعر البيع يجب أن يكون أكبر من صفر"))
        val cleanBarcode = barcode?.trim()?.ifBlank { null }
        if (cleanBarcode != null && products.any { it.IsActive && it.Barcode.equals(cleanBarcode, ignoreCase = true) }) {
            return Result.failure(Exception("الباركود مكرر"))
        }
        val now = "2026-10-17 12:00:00"
        val id = nextProductId++
        val safeFactor = if (largeUnit.isNullOrBlank() || unitFactor <= 1.0) 1.0 else unitFactor
        val totalOpeningMinor = UnitConversionEngine.toMinorQuantity(openingMajorQty, openingMinorQty, safeFactor)

        products.add(
            ProductEntity(
                Id = id,
                Barcode = cleanBarcode,
                Name = name.trim(),
                SmallUnit = smallUnit,
                LargeUnit = largeUnit,
                UnitFactor = safeFactor,
                CostPrice = NumberFormatter.roundCurrency(costPrice),
                RetailPrice = NumberFormatter.roundCurrency(retailPrice),
                WholesalePrice = NumberFormatter.roundCurrency(if (wholesalePrice > 0.0) wholesalePrice else retailPrice * safeFactor),
                OpeningQuantityMinor = totalOpeningMinor,
                OpeningCost = NumberFormatter.roundCurrency(costPrice),
                IsActive = true,
                CreatedAt = now
            )
        )
        if (totalOpeningMinor > 0.0) {
            stockMovements.add(
                StockMovementEntity(
                    Id = nextMovementId++,
                    ProductId = id,
                    OccurredAt = now,
                    Type = "In",
                    QuantityMinor = totalOpeningMinor,
                    UnitCost = NumberFormatter.roundCurrency(costPrice),
                    Notes = "رصيد افتتاحي للمخزون",
                    CreatedAt = now
                )
            )
        }
        return Result.success(id)
    }

    fun getProductStockMinor(productId: Long): Double {
        return stockMovements
            .filter { it.ProductId == productId }
            .sumOf { LedgerDirectionPolicy.signedStockDelta(it.Type, it.QuantityMinor) }
    }

    fun getAccountBalanceSummary(personId: Long): AccountBalanceSummary {
        val person = people.first { it.Id == personId }
        val ops = operations.filter { it.PersonId == personId }
        val totalDebit = NumberFormatter.roundCurrency(
            ops.filter { LedgerDirectionPolicy.isDebitOperation(it.Type) }.sumOf { it.Amount }
        )
        val totalCredit = NumberFormatter.roundCurrency(
            ops.filter { LedgerDirectionPolicy.isCreditOperation(it.Type) }.sumOf { it.Amount }
        )
        val netBalance = NumberFormatter.roundCurrency(
            ops.sumOf { LedgerDirectionPolicy.signedOperationDelta(it.Type, it.Amount) }
        )
        return AccountBalanceSummary(
            PersonId = person.Id,
            PersonName = person.Name,
            Phone = person.Phone,
            BookId = person.BookId,
            TotalDebit = totalDebit,
            TotalCredit = totalCredit,
            NetBalance = netBalance,
            LastOperationAt = person.LastOperationAt,
            CreditLimit = person.CreditLimit
        )
    }

    private fun allocateSequentialInvoiceNo(prefix: String): String {
        val dateCode = "20261017"
        val base = "$prefix-$dateCode-"
        val lastSeq = invoices
            .map { it.InvoiceNumber }
            .filter { it.startsWith(base) }
            .mapNotNull { it.substringAfterLast("-").toLongOrNull() }
            .maxOrNull() ?: 0L
        var next = max(lastSeq + 1L, invoices.size + 1L)
        var candidate = "$base${next.toString().padStart(4, '0')}"
        while (invoices.any { it.InvoiceNumber == candidate }) {
            next++
            candidate = "$base${next.toString().padStart(4, '0')}"
        }
        return candidate
    }

    fun postSaleInvoice(
        personId: Long?,
        lines: List<Triple<Long, Double, Double>>, // (productId, majorQty, minorQty)
        discountAmount: Double = 0.0,
        paymentType: String = "Cash",
        paidAmountInput: Double = 0.0
    ): Result<Long> {
        if (lines.isEmpty()) return Result.failure(Exception("الفاتورة فارغة"))

        val invSnap = invoices.toList()
        val lineSnap = invoiceLines.toList()
        val movSnap = stockMovements.toList()
        val opSnap = operations.toList()

        try {
            if (personId != null && personId > 0L) {
                people.firstOrNull { it.Id == personId && it.IsActive }
                    ?: throw IllegalStateException("الحساب المرتبط بالفاتورة غير موجود أو مؤرشف")
            }

            val preparedLines = lines.mapIndexed { idx, (prodId, majQty, minQty) ->
                val prod = products.firstOrNull { it.Id == prodId && it.IsActive }
                    ?: throw IllegalStateException("الصنف غير موجود")
                val calc = UnitConversionEngine.calculateCompositeSale(
                    majorQty = majQty,
                    minorQty = minQty,
                    unitFactor = prod.UnitFactor,
                    minorSalePrice = prod.RetailPrice,
                    majorSalePrice = prod.WholesalePrice,
                    minorUnitCost = prod.CostPrice
                )
                Triple(prod, calc, idx + 1)
            }

            if (!allowNegativeStock) {
                val aggregated = preparedLines.groupBy { it.first.Id }
                for ((prodId, group) in aggregated) {
                    val prod = group.first().first
                    val totalRequiredMinor = group.sumOf { it.second.totalMinorQuantity }
                    val currentLiveStock = getProductStockMinor(prodId)
                    if (totalRequiredMinor > currentLiveStock + 0.0001) {
                        throw IllegalStateException("الكمية المطلوبة من ${prod.Name} ($totalRequiredMinor) تتجاوز المخزون المتاح ($currentLiveStock)")
                    }
                }
            }

            val subtotal = NumberFormatter.roundCurrency(preparedLines.sumOf { it.second.totalLinePrice })
            val cleanDiscount = NumberFormatter.roundCurrency(discountAmount.coerceIn(0.0, subtotal))
            val finalTotal = NumberFormatter.roundCurrency(max(0.0, subtotal - cleanDiscount))
            val paid = NumberFormatter.roundCurrency(
                when (paymentType) {
                    "Cash" -> finalTotal
                    "Credit" -> 0.0
                    else -> paidAmountInput.coerceIn(0.0, finalTotal)
                }
            )
            val remaining = NumberFormatter.roundCurrency(max(0.0, finalTotal - paid))

            if (remaining > 0.0 && (personId == null || personId <= 0L)) {
                throw IllegalStateException("البيع الآجل أو الجزئي يتطلب تحديد عميل مسجل")
            }

            val now = "2026-10-17 12:30:00"
            val invoiceId = nextInvoiceId++
            val invoiceNo = allocateSequentialInvoiceNo("INV")
            val personName = personId?.let { id -> people.firstOrNull { it.Id == id }?.Name } ?: "عميل نقدي عام"

            invoices.add(
                InvoiceEntity(
                    Id = invoiceId,
                    InvoiceNumber = invoiceNo,
                    Type = "Sale",
                    PersonId = personId ?: 0L,
                    PersonNameSnapshot = personName,
                    IssuedAt = now,
                    Subtotal = subtotal,
                    DiscountAmount = cleanDiscount,
                    TaxAmount = 0.0,
                    FinalTotal = finalTotal,
                    PaidAmount = paid,
                    RemainingAmount = remaining,
                    PaymentType = if (remaining == 0.0) "Cash" else if (paid == 0.0) "Credit" else "Partial",
                    Status = "Completed",
                    CreatedAt = now
                )
            )

            preparedLines.forEach { (prod, calc, order) ->
                val unitPrice = if (calc.totalMinorQuantity > 0.0) NumberFormatter.roundCurrency(calc.totalLinePrice / calc.totalMinorQuantity, 4) else prod.RetailPrice
                invoiceLines.add(
                    InvoiceLineEntity(
                        Id = nextLineId++,
                        InvoiceId = invoiceId,
                        ProductId = prod.Id,
                        ProductNameSnapshot = prod.Name,
                        BarcodeSnapshot = prod.Barcode,
                        UnitName = prod.SmallUnit,
                        ConversionFactor = prod.UnitFactor,
                        QuantityMinor = calc.totalMinorQuantity,
                        UnitPrice = unitPrice,
                        LineTotal = calc.totalLinePrice,
                        LineCost = prod.CostPrice,
                        SortOrder = order
                    )
                )
                stockMovements.add(
                    StockMovementEntity(
                        Id = nextMovementId++,
                        ProductId = prod.Id,
                        OccurredAt = now,
                        Type = "Sale",
                        QuantityMinor = calc.totalMinorQuantity,
                        UnitCost = prod.CostPrice,
                        Notes = "فاتورة مبيعات #$invoiceNo",
                        InvoiceId = invoiceId,
                        CreatedAt = now
                    )
                )
            }

            if (remaining > 0.0 && personId != null && personId > 0L) {
                operations.add(
                    OperationEntity(
                        Id = nextOpId++,
                        PersonId = personId,
                        Type = "Sale",
                        Amount = remaining,
                        OccurredAt = now,
                        Note = "متبقي آجل فاتورة مبيعات #$invoiceNo",
                        ReferenceNumber = invoiceNo,
                        SourceInvoiceId = invoiceId,
                        CreatedAt = now
                    )
                )
            }
            return Result.success(invoiceId)
        } catch (e: Exception) {
            invoices.clear(); invoices.addAll(invSnap)
            invoiceLines.clear(); invoiceLines.addAll(lineSnap)
            stockMovements.clear(); stockMovements.addAll(movSnap)
            operations.clear(); operations.addAll(opSnap)
            return Result.failure(e)
        }
    }

    fun postPurchaseInvoice(
        supplierId: Long?,
        productId: Long,
        useMajorUnit: Boolean,
        quantity: Double,
        unitPurchaseCost: Double,
        paidCash: Double
    ): Result<Long> {
        val prodIdx = products.indexOfFirst { it.Id == productId && it.IsActive }
        if (prodIdx < 0) return Result.failure(Exception("الصنف غير موجود"))
        val prod = products[prodIdx]
        if (supplierId != null && supplierId > 0L) {
            if (people.none { it.Id == supplierId && it.IsActive }) {
                return Result.failure(Exception("حساب المورد غير موجود"))
            }
        }
        val safeFactor = if (prod.UnitFactor <= 1.0 || prod.LargeUnit.isNullOrBlank()) 1.0 else prod.UnitFactor
        val totalMinorQty = if (useMajorUnit && safeFactor > 1.0) quantity * safeFactor else quantity
        val minorCost = NumberFormatter.roundCurrency(
            if (useMajorUnit && safeFactor > 1.0) UnitConversionEngine.minorCostFromMajorCost(unitPurchaseCost, safeFactor) else unitPurchaseCost,
            4
        )
        val total = NumberFormatter.roundCurrency(quantity * unitPurchaseCost)
        val safePaid = NumberFormatter.roundCurrency(paidCash.coerceIn(0.0, total))
        val remaining = NumberFormatter.roundCurrency(max(0.0, total - safePaid))

        if (remaining > 0.0 && (supplierId == null || supplierId <= 0L)) {
            return Result.failure(Exception("المشتريات الآجلة تتطلب اختيار مورد مسجل"))
        }

        val existingStockBefore = getProductStockMinor(prod.Id)
        val newWeightedCost = WeightedAverageCostCalculator.calculateNewWeightedCost(
            currentStockMinor = existingStockBefore,
            currentUnitCost = prod.CostPrice,
            purchasedQtyMinor = totalMinorQty,
            purchasedUnitCost = minorCost
        )
        products[prodIdx] = prod.copy(CostPrice = NumberFormatter.roundCurrency(newWeightedCost, 2))

        val now = "2026-10-17 13:00:00"
        val invoiceId = nextInvoiceId++
        val invoiceNo = allocateSequentialInvoiceNo("PUR")
        val supplierName = supplierId?.let { id -> people.firstOrNull { it.Id == id }?.Name } ?: "مورد نقدي عام"

        invoices.add(
            InvoiceEntity(
                Id = invoiceId,
                InvoiceNumber = invoiceNo,
                Type = "Purchase",
                PersonId = supplierId ?: 0L,
                PersonNameSnapshot = supplierName,
                IssuedAt = now,
                Subtotal = total,
                FinalTotal = total,
                PaidAmount = safePaid,
                RemainingAmount = remaining,
                PaymentType = if (remaining == 0.0) "Cash" else if (safePaid == 0.0) "Credit" else "Partial",
                Status = "Completed",
                CreatedAt = now
            )
        )
        invoiceLines.add(
            InvoiceLineEntity(
                Id = nextLineId++,
                InvoiceId = invoiceId,
                ProductId = prod.Id,
                ProductNameSnapshot = prod.Name,
                UnitName = if (useMajorUnit && !prod.LargeUnit.isNullOrBlank()) prod.LargeUnit else prod.SmallUnit,
                IsMajorUnit = useMajorUnit,
                ConversionFactor = safeFactor,
                QuantityMinor = totalMinorQty,
                UnitPrice = minorCost,
                LineCost = minorCost,
                LineTotal = total,
                SortOrder = 1
            )
        )
        stockMovements.add(
            StockMovementEntity(
                Id = nextMovementId++,
                ProductId = prod.Id,
                OccurredAt = now,
                Type = "Purchase",
                QuantityMinor = totalMinorQty,
                UnitCost = minorCost,
                Notes = "فاتورة مشتريات #$invoiceNo",
                InvoiceId = invoiceId,
                CreatedAt = now
            )
        )
        if (remaining > 0.0 && supplierId != null && supplierId > 0L) {
            operations.add(
                OperationEntity(
                    Id = nextOpId++,
                    PersonId = supplierId,
                    Type = "Purchase",
                    Amount = remaining,
                    OccurredAt = now,
                    Note = "متبقي آجل فاتورة مشتريات #$invoiceNo",
                    ReferenceNumber = invoiceNo,
                    SourceInvoiceId = invoiceId,
                    CreatedAt = now
                )
            )
        }
        return Result.success(invoiceId)
    }

    fun reverseInvoice(invoiceId: Long, reason: String = "مرتجع معتمد"): Result<Boolean> {
        val idx = invoices.indexOfFirst { it.Id == invoiceId }
        if (idx < 0) return Result.failure(Exception("الفاتورة غير موجودة"))
        val invoice = invoices[idx]
        if (invoice.Status == "Reversed") return Result.failure(Exception("تم عكس الفاتورة مسبقاً"))

        val now = "2026-10-17 14:00:00"
        invoices[idx] = invoice.copy(Status = "Reversed")

        val lines = invoiceLines.filter { it.InvoiceId == invoiceId }
        val returnMovType = if (invoice.Type == "Sale") "ReturnIn" else "ReturnOut"
        lines.forEach { ln ->
            stockMovements.add(
                StockMovementEntity(
                    Id = nextMovementId++,
                    ProductId = ln.ProductId,
                    OccurredAt = now,
                    Type = returnMovType,
                    QuantityMinor = ln.QuantityMinor,
                    UnitCost = ln.LineCost,
                    Notes = "عكس فاتورة #${invoice.InvoiceNumber}: $reason",
                    InvoiceId = invoiceId,
                    CreatedAt = now
                )
            )
        }

        if (invoice.PersonId > 0L && invoice.RemainingAmount > 0.0) {
            val opType = if (invoice.Type == "Sale") "SaleReturn" else "PurchaseReturn"
            operations.add(
                OperationEntity(
                    Id = nextOpId++,
                    PersonId = invoice.PersonId,
                    Type = opType,
                    Amount = NumberFormatter.roundCurrency(invoice.RemainingAmount),
                    OccurredAt = now,
                    Note = "إشعار مرتجع فاتورة #${invoice.InvoiceNumber} ($reason)",
                    ReferenceNumber = "REV-${invoice.InvoiceNumber}",
                    SourceInvoiceId = invoiceId,
                    CreatedAt = now
                )
            )
        }
        return Result.success(true)
    }

    fun postVoucher(personId: Long, type: String, amount: Double, statement: String): Result<Long> {
        val clean = NumberFormatter.roundCurrency(amount)
        if (clean <= 0.0) return Result.failure(Exception("مبلغ السند غير صالح"))
        if (people.none { it.Id == personId && it.IsActive }) {
            return Result.failure(Exception("الحساب غير موجود"))
        }
        val id = nextOpId++
        operations.add(
            OperationEntity(
                Id = id,
                PersonId = personId,
                Type = type,
                Amount = clean,
                OccurredAt = "2026-10-17 15:00:00",
                Note = statement,
                ReferenceNumber = "${if (type == "Receive") "RV" else "PV"}-20261017-${id.toString().padStart(4, '0')}",
                CreatedAt = "2026-10-17 15:00:00"
            )
        )
        return Result.success(id)
    }

    /**
     * إلغاء الحركة المالية عبر قيد عكسي محاسبي موثق دون حذف السجل الأصلي
     */
    fun cancelOperationWithReversal(operationId: Long, reason: String): Result<Long> {
        val op = operations.firstOrNull { it.Id == operationId }
            ?: return Result.failure(Exception("الحركة غير موجودة"))
        if (op.SourceInvoiceId != null && op.SourceInvoiceId > 0L) {
            return Result.failure(Exception("لا يمكن عكس حركة مالية مرتبطة بفاتورة بشكل مستقل؛ يرجى عكس الفاتورة من إدارة الفواتير."))
        }
        val revRef = "REV-OP-${op.Id}"
        if (op.ReferenceNumber?.startsWith("REV-OP-") == true || operations.any { it.ReferenceNumber == revRef }) {
            return Result.failure(Exception("تم عكس هذه الحركة مسبقاً"))
        }
        val reverseType = when (op.Type) {
            "Receive" -> "Payment"
            "Payment" -> "Receive"
            "DebtOnUs" -> "DebtToUs"
            "DebtToUs" -> "DebtOnUs"
            "Sale" -> "SaleReturn"
            "Purchase" -> "PurchaseReturn"
            "SaleReturn" -> "Sale"
            "PurchaseReturn" -> "Purchase"
            else -> "Receive"
        }
        val revId = nextOpId++
        operations.add(
            OperationEntity(
                Id = revId,
                PersonId = op.PersonId,
                Type = reverseType,
                Amount = op.Amount,
                OccurredAt = "2026-10-17 15:30:00",
                Note = "قيد عكسي لإلغاء الحركة #${op.Id}: $reason",
                ReferenceNumber = revRef,
                SourceInvoiceId = op.SourceInvoiceId,
                CreatedAt = "2026-10-17 15:30:00"
            )
        )
        val safeSnapshot = JSONObject().apply {
            put("originalId", op.Id)
            put("reversalOperationId", revId)
            put("originalType", op.Type)
            put("reversalType", reverseType)
            put("amount", op.Amount)
            put("note", op.Note ?: "")
            put("reason", reason)
        }.toString()
        deletedItems.add(
            DeletedItemEntity(
                Id = nextDeletedId++,
                EntityType = "OperationReversal",
                OriginalId = op.Id,
                Title = "إلغاء قيد مالي #${op.Id}",
                SnapshotJson = safeSnapshot,
                DeletedAt = "2026-10-17 15:30:00",
                Reason = reason
            )
        )
        return Result.success(revId)
    }

    fun recordExpense(
        amount: Double,
        statement: String,
        paymentSource: String = "الخزينة الرئيسية",
        entryType: String = "EXPENSE",
        categoryId: Long? = null
    ): Result<Long> {
        val clean = NumberFormatter.roundCurrency(amount)
        if (clean <= 0.0 || statement.isBlank()) return Result.failure(Exception("بيانات النفقة غير مكتملة"))
        val id = nextExpenseId++
        expenses.add(
            ExpenseEntity(
                Id = id,
                ExpenseDate = "2026-10-17 16:00:00",
                CategoryId = categoryId,
                Amount = clean,
                Statement = statement.trim(),
                PaymentSource = paymentSource,
                CreatedAt = "2026-10-17 16:00:00",
                EntryType = entryType,
                IsArchived = 0
            )
        )
        return Result.success(id)
    }

    fun getFinancialReportSummary(): FinancialReportSummary {
        val completedSales = invoices.filter { it.Type == "Sale" && it.Status == "Completed" }
        val completedPurchases = invoices.filter { it.Type == "Purchase" && it.Status == "Completed" }
        val completedSaleIds = completedSales.map { it.Id }.toSet()

        val activeExpenses = expenses.filter { (it.IsArchived ?: 0) == 0 && (it.EntryType ?: "EXPENSE") == "EXPENSE" }
        val activeRevenues = expenses.filter { (it.IsArchived ?: 0) == 0 && it.EntryType == "REVENUE" }

        val totalSales = NumberFormatter.roundCurrency(completedSales.sumOf { it.FinalTotal })
        val cogs = NumberFormatter.roundCurrency(
            invoiceLines.filter { it.InvoiceId in completedSaleIds }.sumOf { it.QuantityMinor * it.LineCost }
        )
        val grossProfit = NumberFormatter.roundCurrency(totalSales - cogs)
        val totalExpenses = NumberFormatter.roundCurrency(activeExpenses.sumOf { it.Amount })
        val otherRevenues = NumberFormatter.roundCurrency(activeRevenues.sumOf { it.Amount })
        val netProfit = NumberFormatter.roundCurrency(grossProfit + otherRevenues - totalExpenses)

        val inventoryValuation = NumberFormatter.roundCurrency(
            products.filter { it.IsActive }.sumOf { p ->
                getProductStockMinor(p.Id).coerceAtLeast(0.0) * p.CostPrice
            }
        )

        val activeSummaries = people.filter { it.IsActive }.map { getAccountBalanceSummary(it.Id) }
        val debtsToUs = NumberFormatter.roundCurrency(
            activeSummaries.filter { it.NetBalance > 0.0 }.sumOf { it.NetBalance }
        )
        val debtsOnUs = NumberFormatter.roundCurrency(
            activeSummaries.filter { it.NetBalance < 0.0 }.sumOf { abs(it.NetBalance) }
        )

        val voucherInflows = operations.sumOf { op ->
            val isRev = op.ReferenceNumber?.startsWith("REV-OP-") == true
            when {
                op.Type == "Receive" && !isRev -> op.Amount
                op.Type == "Payment" && isRev -> -op.Amount
                else -> 0.0
            }
        }
        val voucherOutflows = operations.sumOf { op ->
            val isRev = op.ReferenceNumber?.startsWith("REV-OP-") == true
            when {
                op.Type == "Payment" && !isRev -> op.Amount
                op.Type == "Receive" && isRev -> -op.Amount
                else -> 0.0
            }
        }

        val cashInflows = NumberFormatter.roundCurrency(
            completedSales.sumOf { it.PaidAmount } + voucherInflows + otherRevenues
        )
        val cashOutflows = NumberFormatter.roundCurrency(
            completedPurchases.sumOf { it.PaidAmount } + voucherOutflows + totalExpenses
        )
        val netCashBalance = NumberFormatter.roundCurrency(cashInflows - cashOutflows)

        return FinancialReportSummary(
            TotalSales = totalSales,
            CostOfGoodsSold = cogs,
            GrossProfit = grossProfit,
            TotalExpenses = totalExpenses,
            NetProfit = netProfit,
            InventoryValuation = inventoryValuation,
            TotalDebtsToUs = debtsToUs,
            TotalDebtsOnUs = debtsOnUs,
            CashInflows = cashInflows,
            CashOutflows = cashOutflows,
            NetCashBalance = netCashBalance
        )
    }
}
