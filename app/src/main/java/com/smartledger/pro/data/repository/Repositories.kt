package com.smartledger.pro.data.repository

import androidx.room.withTransaction
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.dao.*
import com.smartledger.pro.data.local.entity.*
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

class AccountRepository(private val dbProvider: () -> SmartLedgerDatabase) {
    constructor(db: SmartLedgerDatabase) : this({ db })

    private val db: SmartLedgerDatabase get() = dbProvider()
    private val accountDao: AccountDao get() = db.accountDao()
    private val bookDao: BookDao get() = db.bookDao()
    private val operationDao: OperationDao get() = db.operationDao()
    private val deletedItemDao: DeletedItemDao get() = db.deletedItemDao()
    private val writeMutex = Mutex()

    fun getAccountSummaries(bookId: Long? = null): Flow<List<AccountBalanceSummary>> {
        val cleanBookId = if (bookId == null || bookId <= 0) null else bookId
        return observeAcrossDatabaseRestores { accountDao.getAccountSummaries(cleanBookId) }
    }

    fun searchAccounts(query: String, bookId: Long? = null): Flow<List<AccountBalanceSummary>> {
        val cleanBookId = if (bookId == null || bookId <= 0) null else bookId
        return observeAcrossDatabaseRestores { accountDao.searchAccounts(query, cleanBookId) }
    }

    fun getAllBooks(): Flow<List<BookEntity>> = observeAcrossDatabaseRestores { bookDao.getAllBooks() }

    suspend fun getAccountById(id: Long): PersonEntity? = withContext(Dispatchers.IO) {
        accountDao.getPersonById(id)
    }

    suspend fun saveAccount(
        person: PersonEntity,
        openingBalance: Double = 0.0,
        isDebit: Boolean = true
    ): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                if (person.Name.trim().length < 2) {
                    return@withContext Result.failure(Exception("يرجى إدخال اسم واضح للحساب (حرفان على الأقل)."))
                }
                var personId = person.Id
                val cleanOpening = NumberFormatter.roundCurrency(openingBalance)
                val cleanCreditLimit = NumberFormatter.roundCurrency(person.CreditLimit)
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val activeDb = db

                activeDb.withTransaction {
                    val activeAccountDao = activeDb.accountDao()
                    val activeOperationDao = activeDb.operationDao()
                    if (personId > 0) {
                        val existing = activeAccountDao.getPersonById(personId)
                        activeAccountDao.updatePerson(
                            person.copy(
                                Name = person.Name.trim(),
                                CreditLimit = cleanCreditLimit,
                                AccountCode = person.AccountCode ?: existing?.AccountCode,
                                CreatedAt = existing?.CreatedAt ?: now,
                                LastOperationAt = existing?.LastOperationAt ?: now
                            )
                        )
                    } else {
                        personId = activeAccountDao.insertPerson(
                            person.copy(
                                Name = person.Name.trim(),
                                CreditLimit = cleanCreditLimit,
                                LastOperationAt = now
                            )
                        )
                        if (cleanOpening > 0.0) {
                            val opType = if (isDebit) "DebtOnUs" else "DebtToUs"
                            val op = OperationEntity(
                                PersonId = personId,
                                Type = opType,
                                Amount = cleanOpening,
                                OccurredAt = now,
                                Note = "رصيد افتتاحي تأسيسي",
                                ReferenceNumber = "OPEN-$personId",
                                CreatedAt = now
                            )
                            activeOperationDao.insertOperation(op)
                            activeAccountDao.updateLastOperationAt(personId, now)
                        }
                    }
                }
                Result.success(personId)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر حفظ بيانات الحساب: ${e.localizedMessage ?: "تأكد من عدم تكرار رمز الحساب"}"))
            }
        }
    }

    suspend fun softDeleteAccount(id: Long, reason: String? = null) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val activeDb = db
            val person = activeDb.accountDao().getPersonById(id) ?: return@withContext
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val safeSnapshot = JSONObject().apply {
                put("id", person.Id)
                put("bookId", person.BookId)
                put("name", person.Name)
                put("phone", person.Phone ?: "")
                put("accountCode", person.AccountCode ?: "")
                put("creditLimit", person.CreditLimit)
                put("note", person.Note ?: "")
                put("createdAt", person.CreatedAt)
            }.toString()

            activeDb.withTransaction {
                activeDb.accountDao().softDeletePerson(id)
                activeDb.deletedItemDao().insertDeletedItem(
                    DeletedItemEntity(
                        EntityType = "Account",
                        OriginalId = id,
                        Title = person.Name,
                        SnapshotJson = safeSnapshot,
                        DeletedAt = now,
                        Reason = reason ?: "أرشفة حساب بواسطة المستخدم"
                    )
                )
            }
        }
    }

    fun getAccountOperations(personId: Long): Flow<List<OperationEntity>> {
        return observeAcrossDatabaseRestores { operationDao.getOperationsForPerson(personId) }
    }

    /**
     * إلغاء الحركة المالية عبر قيد عكسي محاسبي موثق (Auditable Accounting Reversal) بدلاً من الحذف المادي:
     * - يحتفظ بالحركة الأصلية في جدول `Operations` لحماية التاريخ المالي وسجل التدقيق.
     * - يدرج حركة عكسية بالاتجاه المقابل (`REV-OP-<id>`) لتصحيح رصيد الحساب بدقة.
     * - يوثق اللقطة الكاملة بترميز JSON سليم ومهرب (`JSONObject`) داخل `DeletedItems`.
     */
    suspend fun deleteOperation(operationId: Long, reason: String? = null): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val activeDb = db
                val activeOperationDao = activeDb.operationDao()
                val activeAccountDao = activeDb.accountDao()
                val activeDeletedDao = activeDb.deletedItemDao()

                val op = activeOperationDao.getOperationById(operationId)
                    ?: return@withContext Result.failure(Exception("الحركة المالية المطلوبة غير موجودة."))

                val reversalRef = "REV-OP-${op.Id}"
                if (op.ReferenceNumber?.startsWith("REV-OP-") == true ||
                    activeOperationDao.countByReferenceNumber(reversalRef) > 0
                ) {
                    return@withContext Result.failure(Exception("تم عكس وإلغاء هذه الحركة المالية مسبقاً بقيد عكسي موثق."))
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

                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val cleanReason = reason?.trim()?.ifBlank { null } ?: "إلغاء محاسبي معتمد"
                var reversalOpId = 0L

                activeDb.withTransaction {
                    val reversalOp = OperationEntity(
                        PersonId = op.PersonId,
                        Type = reverseType,
                        Amount = NumberFormatter.roundCurrency(op.Amount),
                        OccurredAt = now,
                        Note = "قيد عكسي لإلغاء الحركة #${op.Id} (${op.Note ?: op.Type}) — السبب: $cleanReason",
                        ReferenceNumber = reversalRef,
                        SourceInvoiceId = op.SourceInvoiceId,
                        CreatedAt = now
                    )
                    reversalOpId = activeOperationDao.insertOperation(reversalOp)
                    activeAccountDao.updateLastOperationAt(op.PersonId, now)

                    val safeSnapshot = JSONObject().apply {
                        put("originalId", op.Id)
                        put("personId", op.PersonId)
                        put("originalType", op.Type)
                        put("reversalType", reverseType)
                        put("reversalOperationId", reversalOpId)
                        put("amount", op.Amount)
                        put("occurredAt", op.OccurredAt)
                        put("note", op.Note ?: "")
                        put("referenceNumber", op.ReferenceNumber ?: "")
                        put("sourceInvoiceId", op.SourceInvoiceId ?: 0L)
                        put("reason", cleanReason)
                    }.toString()

                    activeDeletedDao.insertDeletedItem(
                        DeletedItemEntity(
                            EntityType = "OperationReversal",
                            OriginalId = operationId,
                            Title = "إلغاء قيد مالي #$operationId (${op.Amount})",
                            SnapshotJson = safeSnapshot,
                            DeletedAt = now,
                            Reason = cleanReason
                        )
                    )
                }
                Result.success(reversalOpId)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر تنفيذ القيد العكسي للحركة: ${e.localizedMessage}"))
            }
        }
    }
}

class InventoryRepository(private val dbProvider: () -> SmartLedgerDatabase) {
    constructor(db: SmartLedgerDatabase) : this({ db })

    private val db: SmartLedgerDatabase get() = dbProvider()
    private val productDao: ProductDao get() = db.productDao()
    private val stockMovementDao: StockMovementDao get() = db.stockMovementDao()
    private val deletedItemDao: DeletedItemDao get() = db.deletedItemDao()
    private val writeMutex = Mutex()

    fun getProducts(categoryId: Long? = null): Flow<List<ProductStockItem>> {
        val cleanCategoryId = if (categoryId == null || categoryId <= 0) null else categoryId
        return observeAcrossDatabaseRestores { productDao.getProductStockItems(cleanCategoryId) }
    }

    fun searchProducts(query: String): Flow<List<ProductStockItem>> {
        return observeAcrossDatabaseRestores { productDao.searchProducts(query) }
    }

    fun getProductMovements(productId: Long): Flow<List<StockMovementEntity>> {
        return observeAcrossDatabaseRestores { stockMovementDao.getMovementsForProduct(productId) }
    }

    suspend fun findByBarcode(barcode: String): ProductEntity? = withContext(Dispatchers.IO) {
        productDao.findByBarcode(barcode)
    }

    suspend fun getProductById(id: Long): ProductEntity? = withContext(Dispatchers.IO) {
        productDao.getProductById(id)
    }

    suspend fun saveProduct(
        product: ProductEntity,
        openingQty: Double = 0.0
    ): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                if (product.Name.trim().isEmpty()) {
                    return@withContext Result.failure(Exception("اسم الصنف مطلوب ولا يمكن تركه فارغاً."))
                }
                if (product.RetailPrice <= 0.0) {
                    return@withContext Result.failure(Exception("يجب أن يكون سعر البيع أكبر من صفر."))
                }
                val activeDb = db
                val activeProductDao = activeDb.productDao()
                val activeStockDao = activeDb.stockMovementDao()

                val cleanBarcode = product.Barcode?.trim()?.ifBlank { null }
                if (cleanBarcode != null) {
                    val existingWithBarcode = activeProductDao.findByBarcode(cleanBarcode)
                    if (existingWithBarcode != null && existingWithBarcode.Id != product.Id) {
                        return@withContext Result.failure(
                            Exception("الباركود ($cleanBarcode) مسجل مسبقاً للصنف «${existingWithBarcode.Name}».")
                        )
                    }
                }

                var productId = product.Id
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val normalizedProduct = product.copy(
                    Barcode = cleanBarcode,
                    Name = product.Name.trim(),
                    CostPrice = NumberFormatter.roundCurrency(product.CostPrice),
                    RetailPrice = NumberFormatter.roundCurrency(product.RetailPrice),
                    WholesalePrice = NumberFormatter.roundCurrency(product.WholesalePrice),
                    OpeningCost = NumberFormatter.roundCurrency(product.OpeningCost)
                )

                activeDb.withTransaction {
                    if (productId > 0) {
                        val existing = activeProductDao.getProductById(productId)
                        activeProductDao.updateProduct(
                            normalizedProduct.copy(
                                OpeningQuantityMinor = existing?.OpeningQuantityMinor ?: normalizedProduct.OpeningQuantityMinor,
                                OpeningCost = existing?.OpeningCost ?: normalizedProduct.OpeningCost,
                                CreatedAt = existing?.CreatedAt ?: now
                            )
                        )
                    } else {
                        productId = activeProductDao.insertProduct(normalizedProduct)
                        if (openingQty > 0.0) {
                            activeStockDao.insertMovement(
                                StockMovementEntity(
                                    ProductId = productId,
                                    OccurredAt = now,
                                    Type = "In",
                                    QuantityMinor = openingQty,
                                    UnitCost = normalizedProduct.CostPrice,
                                    Notes = "رصيد افتتاحي للمخزون",
                                    CreatedAt = now
                                )
                            )
                        }
                    }
                }
                Result.success(productId)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر حفظ بيانات الصنف: ${e.localizedMessage ?: "خطأ في قاعدة البيانات"}"))
            }
        }
    }

    suspend fun softDeleteProduct(id: Long, reason: String? = null) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val activeDb = db
            val product = activeDb.productDao().getProductById(id) ?: return@withContext
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val safeSnapshot = JSONObject().apply {
                put("id", product.Id)
                put("name", product.Name)
                put("barcode", product.Barcode ?: "")
                put("smallUnit", product.SmallUnit)
                put("largeUnit", product.LargeUnit ?: "")
                put("unitFactor", product.UnitFactor)
                put("costPrice", product.CostPrice)
                put("retailPrice", product.RetailPrice)
                put("wholesalePrice", product.WholesalePrice)
            }.toString()

            activeDb.withTransaction {
                activeDb.productDao().softDeleteProduct(id)
                activeDb.deletedItemDao().insertDeletedItem(
                    DeletedItemEntity(
                        EntityType = "Product",
                        OriginalId = id,
                        Title = product.Name,
                        SnapshotJson = safeSnapshot,
                        DeletedAt = now,
                        Reason = reason ?: "أرشفة صنف من المخزون"
                    )
                )
            }
        }
    }

    suspend fun commitStockAdjustment(
        productId: Long,
        actualQty: Double,
        currentQty: Double,
        notes: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val activeDb = db
                val activeProductDao = activeDb.productDao()
                val activeStockDao = activeDb.stockMovementDao()
                val product = activeProductDao.getProductById(productId)
                    ?: return@withContext Result.failure(Exception("الصنف المطلوب تسوية جرده غير موجود."))
                val liveCurrentQty = activeProductDao.getProductStock(productId)
                val delta = actualQty - liveCurrentQty
                if (kotlin.math.abs(delta) < 0.0001) return@withContext Result.success(0L)
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val type = if (delta > 0) "AdjustmentIn" else "AdjustmentOut"
                val cleanNotes = notes?.trim()?.ifBlank { null }
                    ?: "تسوية جرد مخزني للصنف «${product.Name}» (الفعلي: $actualQty، الدفتري: $liveCurrentQty، الفارق: ${if (delta > 0) "+$delta" else "$delta"})"
                val movement = StockMovementEntity(
                    ProductId = productId,
                    OccurredAt = now,
                    Type = type,
                    QuantityMinor = kotlin.math.abs(delta),
                    UnitCost = product.CostPrice,
                    Notes = cleanNotes,
                    CreatedAt = now
                )
                val id = activeStockDao.insertMovement(movement)
                Result.success(id)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر تسجيل تسوية الجرد: ${e.localizedMessage}"))
            }
        }
    }
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
     * 3. تخصيص رقم فاتورة تسلسلي فريد غير قابل للتصادم (`allocateUniqueInvoiceNumberInsideTx`) داخل المعاملة.
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

                    for ((productId, totalRequiredMinor) in aggregatedRequiredByProduct) {
                        if (totalRequiredMinor <= 0.0) {
                            throw IllegalStateException("كمية الصنف في الفاتورة يجب أن تكون أكبر من صفر.")
                        }
                        val dbProduct = activeProductDao.getProductById(productId)
                            ?: throw IllegalStateException("الصنف (#$productId) غير موجود في قاعدة البيانات.")
                        if (!dbProduct.IsActive) {
                            throw IllegalStateException("الصنف «${dbProduct.Name}» مؤرشف ولا يمكن التعامل عليه.")
                        }
                        if (invoice.Type == "Sale" && !allowNegativeStock) {
                            val liveStockMinor = activeProductDao.getProductStock(productId)
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

                    // 4. حركة المخزون الذرية
                    val stockMovements = linesWithId.map { line ->
                        StockMovementEntity(
                            ProductId = line.ProductId,
                            OccurredAt = now,
                            Type = if (cleanInvoice.Type == "Sale") "Sale" else "Purchase",
                            QuantityMinor = line.QuantityMinor,
                            UnitCost = line.LineCost,
                            Notes = "فاتورة ${if (cleanInvoice.Type == "Sale") "مبيعات" else "مشتريات"} #${cleanInvoice.InvoiceNumber}",
                            InvoiceId = invoiceId,
                            CreatedAt = now
                        )
                    }
                    activeStockDao.insertMovements(stockMovements)

                    // 5. تحديث الحسابات والذمم المدينة والدائنة
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
                                    CreatedAt = now
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
                                    CreatedAt = now
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
                            CreatedAt = now
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
                                CreatedAt = now
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

class VoucherRepository(private val dbProvider: () -> SmartLedgerDatabase) {
    constructor(db: SmartLedgerDatabase) : this({ db })

    private val db: SmartLedgerDatabase get() = dbProvider()
    private val operationDao: OperationDao get() = db.operationDao()
    private val accountDao: AccountDao get() = db.accountDao()
    private val writeMutex = Mutex()

    fun observeRecentOperations(limit: Int = 25): Flow<List<RecentOperationWithPerson>> {
        return observeAcrossDatabaseRestores { operationDao.observeRecentOperationsWithPerson(limit) }
    }

    suspend fun createVoucher(
        personId: Long,
        type: String, // Receive (له), Payment (عليه)
        amount: Double,
        statement: String,
        refNumber: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val cleanAmount = NumberFormatter.roundCurrency(amount)
                if (cleanAmount <= 0.0) {
                    return@withContext Result.failure(Exception("يجب أن يكون مبلغ السند أكبر من صفر."))
                }
                if (personId <= 0L) {
                    return@withContext Result.failure(Exception("يرجى اختيار الحساب المرتبط بالسند."))
                }
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                var opId = 0L
                val activeDb = db
                activeDb.withTransaction {
                    val activeAccountDao = activeDb.accountDao()
                    val activeOperationDao = activeDb.operationDao()
                    val person = activeAccountDao.getPersonById(personId)
                        ?: throw IllegalStateException("الحساب المرتبط بالسند غير موجود في قاعدة البيانات.")
                    if (!person.IsActive) {
                        throw IllegalStateException("الحساب «${person.Name}» مؤرشف ولا يمكن ترحيل سندات جديدة عليه.")
                    }
                    val totalOps = activeOperationDao.getOperationsCount() + 1
                    val autoRef = refNumber?.trim()?.ifBlank { null }
                        ?: "${if (type == "Receive") "RV" else "PV"}-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}-${totalOps.toString().padStart(4, '0')}"
                    val op = OperationEntity(
                        PersonId = personId,
                        Type = type,
                        Amount = cleanAmount,
                        OccurredAt = now,
                        Note = statement.trim(),
                        ReferenceNumber = autoRef,
                        CreatedAt = now
                    )
                    opId = activeOperationDao.insertOperation(op)
                    activeAccountDao.updateLastOperationAt(personId, now)
                }
                Result.success(opId)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "تعذر حفظ السند المالي"))
            }
        }
    }
}

class ExpenseRepository(private val dbProvider: () -> SmartLedgerDatabase) {
    constructor(db: SmartLedgerDatabase) : this({ db })

    private val db: SmartLedgerDatabase get() = dbProvider()
    private val expenseDao: ExpenseDao get() = db.expenseDao()
    private val deletedItemDao: DeletedItemDao get() = db.deletedItemDao()
    private val writeMutex = Mutex()

    fun getAllExpenses(): Flow<List<ExpenseEntity>> = observeAcrossDatabaseRestores { expenseDao.getAllExpenses() }

    suspend fun saveExpense(expense: ExpenseEntity): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val cleanAmount = NumberFormatter.roundCurrency(expense.Amount)
                if (cleanAmount <= 0.0) {
                    return@withContext Result.failure(Exception("يجب أن يكون مبلغ النفقة أكبر من صفر."))
                }
                if (expense.Statement.trim().isEmpty()) {
                    return@withContext Result.failure(Exception("يرجى إدخال بيان أو سبب الصرف."))
                }
                val id = expenseDao.insertExpense(expense.copy(Amount = cleanAmount))
                Result.success(id)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر حفظ النفقة: ${e.localizedMessage}"))
            }
        }
    }

    suspend fun deleteExpense(id: Long, reason: String? = null) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val activeDb = db
            val existing = activeDb.expenseDao().getExpenseById(id) ?: return@withContext
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val safeSnapshot = JSONObject().apply {
                put("id", existing.Id)
                put("expenseDate", existing.ExpenseDate)
                put("amount", existing.Amount)
                put("statement", existing.Statement)
                put("paymentSource", existing.PaymentSource)
                put("createdAt", existing.CreatedAt)
            }.toString()

            activeDb.withTransaction {
                activeDb.expenseDao().deleteExpense(id)
                activeDb.deletedItemDao().insertDeletedItem(
                    DeletedItemEntity(
                        EntityType = "Expense",
                        OriginalId = id,
                        Title = "${existing.Statement} (${existing.Amount})",
                        SnapshotJson = safeSnapshot,
                        DeletedAt = now,
                        Reason = reason ?: "حذف نفقة تشغيلية"
                    )
                )
            }
        }
    }
}

class ReportRepository(private val dbProvider: () -> SmartLedgerDatabase) {
    constructor(db: SmartLedgerDatabase) : this({ db })

    private val db: SmartLedgerDatabase get() = dbProvider()
    private val reportDao: ReportDao get() = db.reportDao()
    private val dashboardDao: DashboardDao get() = db.dashboardDao()

    fun getFinancialReportSummary(): Flow<FinancialReportSummary> =
        observeAcrossDatabaseRestores { reportDao.getFinancialReportSummary() }

    fun getFinancialReportFromDate(fromDate: String): Flow<FinancialReportSummary> =
        observeAcrossDatabaseRestores { reportDao.getFinancialReportFromDate(fromDate) }

    fun getDashboardMetrics(): Flow<DashboardMetrics> =
        observeAcrossDatabaseRestores { dashboardDao.getDashboardMetrics() }
}

/**
 * مستودع الهوية التجارية للمنشأة (`BusinessIdentityRepository`)
 * يعزل جميع الـ ViewModels عن الوصول المباشر إلى `SmartLedgerDatabase` أو `BusinessIdentityDao`.
 */
class BusinessIdentityRepository(private val dbProvider: () -> SmartLedgerDatabase) {
    constructor(db: SmartLedgerDatabase) : this({ db })

    private val db: SmartLedgerDatabase get() = dbProvider()
    private val identityDao: BusinessIdentityDao get() = db.businessIdentityDao()

    fun observeBusinessIdentity(): Flow<BusinessIdentityEntity?> =
        observeAcrossDatabaseRestores { identityDao.getIdentity() }

    suspend fun saveBusinessIdentity(identity: BusinessIdentityEntity) = withContext(Dispatchers.IO) {
        identityDao.saveIdentity(identity)
    }
}
