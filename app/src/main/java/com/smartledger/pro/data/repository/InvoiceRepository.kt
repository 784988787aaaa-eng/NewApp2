package com.smartledger.pro.data.repository

import androidx.room.withTransaction
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.dao.*
import com.smartledger.pro.data.local.entity.*
import com.smartledger.pro.domain.usecase.WeightedAverageCostCalculator
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

/**
 * امتداد مساعد يربط التدفقات التفاعلية (`Flow`) بمؤشر جيل قاعدة البيانات (`SmartLedgerDatabase.databaseEpoch`)،
 * بحيث يُعاد الاشتراك تلقائياً في نسخة `SmartLedgerDatabase` الجديدة فور إتمام استعادة النسخة الاحتياطية.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> observeAcrossDatabaseRestores(block: () -> Flow<T>): Flow<T> {
    return SmartLedgerDatabase.databaseEpoch.flatMapLatest { block() }
}

class InvoiceRepository(private val dbProvider: () -> SmartLedgerDatabase) {
    constructor(db: SmartLedgerDatabase) : this({ db })

    private val db: SmartLedgerDatabase get() = dbProvider()
    private val invoiceDao: InvoiceDao get() = db.invoiceDao()
    private val productDao: ProductDao get() = db.productDao()
    private val stockMovementDao: StockMovementDao get() = db.stockMovementDao()
    private val operationDao: OperationDao get() = db.operationDao()
    private val accountDao: AccountDao get() = db.accountDao()
    private val writeMutex = Mutex()

    /**
     * حفظ الفاتورة وبنودها وحركات المخزون والذمم داخل معاملة قاعدة بيانات ذرية واحدة (`db.withTransaction`)، مع:
     * 1. التحقق المرجعي من وجود الحساب (`PersonId > 0`) ووجود كافة الأصناف (`ProductId`) داخل المعاملة.
     * 2. تجميع كميات كل صنف في الفاتورة وإعادة فحص الرصيد الفعلي من قاعدة البيانات (`productDao.getProductStock`)
     *    داخل المعاملة نفسها لمنع البيع الزائد عند تزامن العمليات (`allowNegativeStock == false`).
     * 3. تحديث تكلفة الصنف بالمتوسط المرجح للتكلفة (`Weighted Average Cost`) عند إصدار فاتورة مشتريات.
     * 4. تخصيص رقم فاتورة تسلسلي فريد غير قابل للتصادم (`allocateUniqueInvoiceNumberInsideTx`) داخل المعاملة.
     */
    suspend fun createInvoice(
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        allowNegativeStock: Boolean = true
    ): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                if (lines.isEmpty()) {
                    return@withContext Result.failure(Exception("لا يمكن حفظ فاتورة بدون أصناف."))
                }
                var invoiceId = 0L
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val activeDb = db

                activeDb.withTransaction {
                    val activeAccountDao = activeDb.accountDao()
                    val activeProductDao = activeDb.productDao()
                    val activeInvoiceDao = activeDb.invoiceDao()
                    val activeStockDao = activeDb.stockMovementDao()
                    val activeOperationDao = activeDb.operationDao()

                    // 1. التحقق المرجعي من وجود الحساب المرتبط إذا لم يكن نقدياً عاماً (PersonId > 0)
                    if (invoice.PersonId > 0L) {
                        val person = activeAccountDao.getPersonById(invoice.PersonId)
                            ?: throw IllegalStateException("الحساب المرتبط بالفاتورة (#${invoice.PersonId}) غير موجود في قاعدة البيانات.")
                        if (!person.IsActive) {
                            throw IllegalStateException("الحساب «${person.Name}» مؤرشف حالياً ولا يمكن قيد فواتير جديدة عليه.")
                        }
                    }

                    // 2. تجميع كميات الأصناف المكررة وإعادة فحص المخزون الحي داخل المعاملة الذرية
                    val aggregatedRequiredByProduct = lines
                        .groupBy { it.ProductId }
                        .mapValues { (_, prodLines) -> prodLines.sumOf { it.QuantityMinor } }

                    val stockBeforeByProduct = mutableMapOf<Long, Pair<ProductEntity, Double>>()

                    for ((productId, totalRequiredMinor) in aggregatedRequiredByProduct) {
                        if (totalRequiredMinor <= 0.0) {
                            throw IllegalStateException("كمية الصنف في الفاتورة يجب أن تكون أكبر من صفر.")
                        }
                        val dbProduct = activeProductDao.getProductById(productId)
                            ?: throw IllegalStateException("الصنف (#$productId) غير موجود في قاعدة البيانات.")
                        if (!dbProduct.IsActive) {
                            throw IllegalStateException("الصنف «${dbProduct.Name}» مؤرشف ولا يمكن التعامل عليه.")
                        }
                        val liveStockMinor = activeProductDao.getProductStock(productId)
                        stockBeforeByProduct[productId] = dbProduct to liveStockMinor
                        if (invoice.Type == "Sale" && !allowNegativeStock) {
                            if (totalRequiredMinor > liveStockMinor + 0.0001) {
                                throw IllegalStateException(
                                    "الكمية المطلوبة من «${dbProduct.Name}» (${NumberFormatter.formatQuantity(totalRequiredMinor)} ${dbProduct.SmallUnit}) تتجاوز الرصيد الفعلي المتاح بالمخزن (${NumberFormatter.formatQuantity(liveStockMinor)} ${dbProduct.SmallUnit})."
                                )
                            }
                        }
                    }

                    // 3. توليد/تأكيد رقم فاتورة تسلسلي فريد داخل المعاملة يمنع أي تصادم في الفهرس الفريد
                    val uniqueInvoiceNo = allocateUniqueInvoiceNumberInsideTx(activeInvoiceDao, invoice.InvoiceNumber, invoice.Type)

                    val cleanInvoice = invoice.copy(
                        InvoiceNumber = uniqueInvoiceNo,
                        Subtotal = NumberFormatter.roundCurrency(invoice.Subtotal),
                        DiscountAmount = NumberFormatter.roundCurrency(invoice.DiscountAmount),
                        TaxAmount = NumberFormatter.roundCurrency(invoice.TaxAmount),
                        FinalTotal = NumberFormatter.roundCurrency(invoice.FinalTotal),
                        PaidAmount = NumberFormatter.roundCurrency(invoice.PaidAmount),
                        RemainingAmount = NumberFormatter.roundCurrency(invoice.RemainingAmount)
                    )

                    invoiceId = activeInvoiceDao.insertInvoice(cleanInvoice)
                    val linesWithId = lines.map {
                        it.copy(
                            InvoiceId = invoiceId,
                            UnitPrice = NumberFormatter.roundCurrency(it.UnitPrice, 4),
                            LineCost = NumberFormatter.roundCurrency(it.LineCost, 4),
                            LineTotal = NumberFormatter.roundCurrency(it.LineTotal, 2)
                        )
                    }
                    activeInvoiceDao.insertLines(linesWithId)

                    // 4. تحديث المتوسط المرجح للتكلفة عند التوريد (Purchase)
                    if (cleanInvoice.Type == "Purchase") {
                        for ((productId, prodLines) in linesWithId.groupBy { it.ProductId }) {
                            val (dbProduct, liveStockBefore) = stockBeforeByProduct[productId] ?: continue
                            val purchasedQty = prodLines.sumOf { it.QuantityMinor }
                            val totalPurchaseCostVal = prodLines.sumOf { it.QuantityMinor * it.LineCost }
                            val avgPurchaseUnitCost = if (purchasedQty > 0.0) totalPurchaseCostVal / purchasedQty else dbProduct.CostPrice
                            val newWeightedCost = WeightedAverageCostCalculator.calculateNewWeightedCost(
                                currentStockMinor = liveStockBefore,
                                currentUnitCost = dbProduct.CostPrice,
                                purchasedQtyMinor = purchasedQty,
                                purchasedUnitCost = avgPurchaseUnitCost
                            )
                            activeProductDao.updateProductCostPrice(productId, NumberFormatter.roundCurrency(newWeightedCost, 2))
                        }
                    }

                    // 5. حركة المخزون الذرية
                    val stockMovements = linesWithId.map { line ->
                        StockMovementEntity(
                            ProductId = line.ProductId,
                            OccurredAt = now,
                            Type = if (cleanInvoice.Type == "Sale") "Sale" else "Purchase",
                            QuantityMinor = line.QuantityMinor,
                            UnitCost = line.LineCost,
                            Notes = "فاتورة ${if (cleanInvoice.Type == "Sale") "مبيعات" else "مشتريات"} #${cleanInvoice.InvoiceNumber}",
                            InvoiceId = invoiceId,
                            CreatedAt = now,
                            WarehouseId = cleanInvoice.WarehouseId
                        )
                    }
                    activeStockDao.insertMovements(stockMovements)

                    // 6. تحديث الحسابات والذمم المدينة والدائنة
                    if (cleanInvoice.Type == "Sale") {
                        if (cleanInvoice.RemainingAmount > 0 && cleanInvoice.PersonId > 0) {
                            activeOperationDao.insertOperation(
                                OperationEntity(
                                    PersonId = cleanInvoice.PersonId,
                                    Type = "Sale",
                                    Amount = cleanInvoice.RemainingAmount,
                                    OccurredAt = now,
                                    Note = "متبقي آجل فاتورة مبيعات #${cleanInvoice.InvoiceNumber}",
                                    ReferenceNumber = cleanInvoice.InvoiceNumber,
                                    SourceInvoiceId = invoiceId,
                                    CreatedAt = now,
                                    FinancialAccountId = cleanInvoice.FinancialAccountId
                                )
                            )
                            activeAccountDao.updateLastOperationAt(cleanInvoice.PersonId, now)
                        } else if (cleanInvoice.PaidAmount > 0 && cleanInvoice.PersonId > 0) {
                            activeAccountDao.updateLastOperationAt(cleanInvoice.PersonId, now)
                        }
                    } else if (cleanInvoice.Type == "Purchase") {
                        if (cleanInvoice.RemainingAmount > 0 && cleanInvoice.PersonId > 0) {
                            activeOperationDao.insertOperation(
                                OperationEntity(
                                    PersonId = cleanInvoice.PersonId,
                                    Type = "Purchase",
                                    Amount = cleanInvoice.RemainingAmount,
                                    OccurredAt = now,
                                    Note = "متبقي آجل فاتورة مشتريات #${cleanInvoice.InvoiceNumber}",
                                    ReferenceNumber = cleanInvoice.InvoiceNumber,
                                    SourceInvoiceId = invoiceId,
                                    CreatedAt = now,
                                    FinancialAccountId = cleanInvoice.FinancialAccountId
                                )
                            )
                            activeAccountDao.updateLastOperationAt(cleanInvoice.PersonId, now)
                        } else if (cleanInvoice.PaidAmount > 0 && cleanInvoice.PersonId > 0) {
                            activeAccountDao.updateLastOperationAt(cleanInvoice.PersonId, now)
                        }
                    }
                }
                Result.success(invoiceId)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "تعذر حفظ الفاتورة"))
            }
        }
    }

    /**
     * تخصيص رقم فاتورة تسلسلي رتيب وفريد لليوم الحالي (`PREFIX-YYYYMMDD-0001`) داخل المعاملة
     */
    private suspend fun allocateUniqueInvoiceNumberInsideTx(
        activeInvoiceDao: InvoiceDao,
        requestedNumber: String,
        type: String
    ): String {
        val trimmed = requestedNumber.trim()
        if (trimmed.isNotEmpty() && activeInvoiceDao.countByInvoiceNumber(trimmed) == 0 && !trimmed.matches(Regex(""".*-\d{4}$"""))) {
            return trimmed
        }
        val defaultPrefix = if (type == "Sale") "INV" else "PUR"
        val prefix = trimmed.substringBefore("-", defaultPrefix).ifBlank { defaultPrefix }
        val dateCode = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val basePrefix = "$prefix-$dateCode-"

        val lastNo = activeInvoiceDao.getLastInvoiceNumberForPrefix("$basePrefix%")
        val lastSeq = lastNo?.substringAfterLast("-")?.toLongOrNull() ?: 0L
        val maxId = activeInvoiceDao.getMaxInvoiceId()
        var nextSeq = max(lastSeq + 1L, maxId + 1L)

        var candidate = "$basePrefix${nextSeq.toString().padStart(4, '0')}"
        while (activeInvoiceDao.countByInvoiceNumber(candidate) > 0) {
            nextSeq++
            candidate = "$basePrefix${nextSeq.toString().padStart(4, '0')}"
        }
        return candidate
    }

    suspend fun getInvoiceById(invoiceId: Long): InvoiceEntity? = withContext(Dispatchers.IO) {
        invoiceDao.getInvoiceById(invoiceId)
    }

    fun getRecentInvoices(limit: Int = 100): Flow<List<InvoiceEntity>> =
        observeAcrossDatabaseRestores { invoiceDao.getRecentInvoices(limit) }

    fun getOpenInvoicesForPerson(personId: Long): Flow<List<InvoiceEntity>> =
        observeAcrossDatabaseRestores { invoiceDao.getOpenInvoicesForPerson(personId) }

    suspend fun getInvoiceLines(invoiceId: Long): List<InvoiceLineEntity> = withContext(Dispatchers.IO) {
        invoiceDao.getLinesForInvoice(invoiceId)
    }

    suspend fun reverseInvoice(invoiceId: Long, reason: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val activeDb = db
                val activeInvoiceDao = activeDb.invoiceDao()
                val activeStockDao = activeDb.stockMovementDao()
                val activeOperationDao = activeDb.operationDao()
                val activeAccountDao = activeDb.accountDao()

                val invoice = activeInvoiceDao.getInvoiceById(invoiceId)
                    ?: return@withContext Result.failure(Exception("الفاتورة المطلوبة غير موجودة"))
                if (invoice.Status == "Reversed") {
                    return@withContext Result.failure(Exception("تم إرجاع وعكس هذه الفاتورة مسبقاً"))
                }

                val lines = activeInvoiceDao.getLinesForInvoice(invoiceId)
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val cleanReason = reason?.trim()?.ifBlank { null } ?: "مرتجع معتمد"

                activeDb.withTransaction {
                    activeInvoiceDao.updateInvoiceStatus(invoiceId, "Reversed")
                    val returnType = if (invoice.Type == "Sale") "ReturnIn" else "ReturnOut"
                    val returnMovements = lines.map { line ->
                        StockMovementEntity(
                            ProductId = line.ProductId,
                            OccurredAt = now,
                            Type = returnType,
                            QuantityMinor = line.QuantityMinor,
                            UnitCost = line.LineCost,
                            Notes = "عكس فاتورة #${invoice.InvoiceNumber}: $cleanReason",
                            InvoiceId = invoiceId,
                            CreatedAt = now,
                            WarehouseId = invoice.WarehouseId
                        )
                    }
                    activeStockDao.insertMovements(returnMovements)

                    if (invoice.PersonId > 0 && invoice.RemainingAmount > 0) {
                        val opType = if (invoice.Type == "Sale") "SaleReturn" else "PurchaseReturn"
                        activeOperationDao.insertOperation(
                            OperationEntity(
                                PersonId = invoice.PersonId,
                                Type = opType,
                                Amount = NumberFormatter.roundCurrency(invoice.RemainingAmount),
                                OccurredAt = now,
                                Note = "إشعار عكس/مرتجع فاتورة #${invoice.InvoiceNumber} ($cleanReason)",
                                ReferenceNumber = "REV-${invoice.InvoiceNumber}",
                                SourceInvoiceId = invoiceId,
                                CreatedAt = now,
                                FinancialAccountId = invoice.FinancialAccountId
                            )
                        )
                        activeAccountDao.updateLastOperationAt(invoice.PersonId, now)
                    }
                }
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
