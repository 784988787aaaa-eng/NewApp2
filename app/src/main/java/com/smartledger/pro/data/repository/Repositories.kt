package com.smartledger.pro.data.repository

import androidx.room.withTransaction
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.dao.*
import com.smartledger.pro.data.local.entity.*
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AccountRepository(private val db: SmartLedgerDatabase) {
    private val accountDao = db.accountDao()
    private val bookDao = db.bookDao()
    private val operationDao = db.operationDao()
    private val deletedItemDao = db.deletedItemDao()
    private val writeMutex = Mutex()

    fun getAccountSummaries(bookId: Long? = null): Flow<List<AccountBalanceSummary>> {
        return accountDao.getAccountSummaries(if (bookId == null || bookId <= 0) null else bookId)
    }

    fun searchAccounts(query: String, bookId: Long? = null): Flow<List<AccountBalanceSummary>> {
        return accountDao.searchAccounts(query, if (bookId == null || bookId <= 0) null else bookId)
    }

    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

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

                db.withTransaction {
                    if (personId > 0) {
                        val existing = accountDao.getPersonById(personId)
                        accountDao.updatePerson(
                            person.copy(
                                Name = person.Name.trim(),
                                CreditLimit = cleanCreditLimit,
                                AccountCode = person.AccountCode ?: existing?.AccountCode,
                                CreatedAt = existing?.CreatedAt ?: now,
                                LastOperationAt = existing?.LastOperationAt ?: now
                            )
                        )
                    } else {
                        personId = accountDao.insertPerson(
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
                            operationDao.insertOperation(op)
                            accountDao.updateLastOperationAt(personId, now)
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
            val person = accountDao.getPersonById(id) ?: return@withContext
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            db.withTransaction {
                accountDao.softDeletePerson(id)
                deletedItemDao.insertDeletedItem(
                    DeletedItemEntity(
                        EntityType = "Account",
                        OriginalId = id,
                        Title = person.Name,
                        SnapshotJson = "{\"name\": \"${person.Name}\", \"phone\": \"${person.Phone ?: ""}\"}",
                        DeletedAt = now,
                        Reason = reason ?: "أرشفة حساب بواسطة المستخدم"
                    )
                )
            }
        }
    }

    fun getAccountOperations(personId: Long): Flow<List<OperationEntity>> {
        return operationDao.getOperationsForPerson(personId)
    }

    suspend fun deleteOperation(operationId: Long, reason: String? = null) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val op = operationDao.getOperationById(operationId) ?: return@withContext
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            db.withTransaction {
                operationDao.deleteOperationById(operationId)
                deletedItemDao.insertDeletedItem(
                    DeletedItemEntity(
                        EntityType = "Operation",
                        OriginalId = operationId,
                        Title = "حركة مالية #$operationId (${op.Amount})",
                        SnapshotJson = "{\"type\": \"${op.Type}\", \"amount\": ${op.Amount}, \"personId\": ${op.PersonId}}",
                        DeletedAt = now,
                        Reason = reason ?: "حذف حركة مالية من كشف الحساب"
                    )
                )
            }
        }
    }
}

class InventoryRepository(private val db: SmartLedgerDatabase) {
    private val productDao = db.productDao()
    private val stockMovementDao = db.stockMovementDao()
    private val deletedItemDao = db.deletedItemDao()
    private val writeMutex = Mutex()

    fun getProducts(categoryId: Long? = null): Flow<List<ProductStockItem>> {
        return productDao.getProductStockItems(if (categoryId == null || categoryId <= 0) null else categoryId)
    }

    fun searchProducts(query: String): Flow<List<ProductStockItem>> {
        return productDao.searchProducts(query)
    }

    fun getProductMovements(productId: Long): Flow<List<StockMovementEntity>> {
        return stockMovementDao.getMovementsForProduct(productId)
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
                val cleanBarcode = product.Barcode?.trim()?.ifBlank { null }
                if (cleanBarcode != null) {
                    val existingWithBarcode = productDao.findByBarcode(cleanBarcode)
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

                db.withTransaction {
                    if (productId > 0) {
                        val existing = productDao.getProductById(productId)
                        productDao.updateProduct(
                            normalizedProduct.copy(
                                OpeningQuantityMinor = existing?.OpeningQuantityMinor ?: normalizedProduct.OpeningQuantityMinor,
                                OpeningCost = existing?.OpeningCost ?: normalizedProduct.OpeningCost,
                                CreatedAt = existing?.CreatedAt ?: now
                            )
                        )
                    } else {
                        productId = productDao.insertProduct(normalizedProduct)
                        if (openingQty > 0.0) {
                            stockMovementDao.insertMovement(
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
            val product = productDao.getProductById(id) ?: return@withContext
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            db.withTransaction {
                productDao.softDeleteProduct(id)
                deletedItemDao.insertDeletedItem(
                    DeletedItemEntity(
                        EntityType = "Product",
                        OriginalId = id,
                        Title = product.Name,
                        SnapshotJson = "{\"name\": \"${product.Name}\", \"barcode\": \"${product.Barcode ?: ""}\"}",
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
                val delta = actualQty - currentQty
                if (kotlin.math.abs(delta) < 0.0001) return@withContext Result.success(0L)
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val type = if (delta > 0) "AdjustmentIn" else "AdjustmentOut"
                val cleanNotes = notes?.trim()?.ifBlank { null }
                    ?: "تسوية جرد مخزني (الفعلي: $actualQty، الدفتري: $currentQty، الفارق: ${if (delta > 0) "+$delta" else "$delta"})"
                val movement = StockMovementEntity(
                    ProductId = productId,
                    OccurredAt = now,
                    Type = type,
                    QuantityMinor = kotlin.math.abs(delta),
                    UnitCost = 0.0,
                    Notes = cleanNotes,
                    CreatedAt = now
                )
                val id = stockMovementDao.insertMovement(movement)
                Result.success(id)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر تسجيل تسوية الجرد: ${e.localizedMessage}"))
            }
        }
    }
}

class InvoiceRepository(private val db: SmartLedgerDatabase) {
    private val invoiceDao = db.invoiceDao()
    private val stockMovementDao = db.stockMovementDao()
    private val operationDao = db.operationDao()
    private val accountDao = db.accountDao()
    private val writeMutex = Mutex()

    suspend fun createInvoice(
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>
    ): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                if (lines.isEmpty()) {
                    return@withContext Result.failure(Exception("لا يمكن حفظ فاتورة بدون أصناف."))
                }
                var invoiceId = 0L
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val cleanInvoice = invoice.copy(
                    Subtotal = NumberFormatter.roundCurrency(invoice.Subtotal),
                    DiscountAmount = NumberFormatter.roundCurrency(invoice.DiscountAmount),
                    TaxAmount = NumberFormatter.roundCurrency(invoice.TaxAmount),
                    FinalTotal = NumberFormatter.roundCurrency(invoice.FinalTotal),
                    PaidAmount = NumberFormatter.roundCurrency(invoice.PaidAmount),
                    RemainingAmount = NumberFormatter.roundCurrency(invoice.RemainingAmount)
                )

                db.withTransaction {
                    invoiceId = invoiceDao.insertInvoice(cleanInvoice)
                    val linesWithId = lines.map {
                        it.copy(
                            InvoiceId = invoiceId,
                            UnitPrice = NumberFormatter.roundCurrency(it.UnitPrice, 4),
                            LineCost = NumberFormatter.roundCurrency(it.LineCost, 4),
                            LineTotal = NumberFormatter.roundCurrency(it.LineTotal, 2)
                        )
                    }
                    invoiceDao.insertLines(linesWithId)

                    // 1. حركة المخزون الذرية
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
                    stockMovementDao.insertMovements(stockMovements)

                    // 2. تحديث الحسابات والذمم المدينة والدائنة
                    if (cleanInvoice.Type == "Sale") {
                        if (cleanInvoice.RemainingAmount > 0 && cleanInvoice.PersonId > 0) {
                            operationDao.insertOperation(
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
                            accountDao.updateLastOperationAt(cleanInvoice.PersonId, now)
                        } else if (cleanInvoice.PaidAmount > 0 && cleanInvoice.PersonId > 0) {
                            accountDao.updateLastOperationAt(cleanInvoice.PersonId, now)
                        }
                    } else if (cleanInvoice.Type == "Purchase") {
                        if (cleanInvoice.RemainingAmount > 0 && cleanInvoice.PersonId > 0) {
                            operationDao.insertOperation(
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
                            accountDao.updateLastOperationAt(cleanInvoice.PersonId, now)
                        } else if (cleanInvoice.PaidAmount > 0 && cleanInvoice.PersonId > 0) {
                            accountDao.updateLastOperationAt(cleanInvoice.PersonId, now)
                        }
                    }
                }
                Result.success(invoiceId)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر حفظ الفاتورة: ${e.localizedMessage ?: "خطأ غير متوقع"}"))
            }
        }
    }

    fun getRecentInvoices(limit: Int = 100): Flow<List<InvoiceEntity>> = invoiceDao.getRecentInvoices(limit)

    suspend fun getInvoiceLines(invoiceId: Long): List<InvoiceLineEntity> = withContext(Dispatchers.IO) {
        invoiceDao.getLinesForInvoice(invoiceId)
    }

    suspend fun reverseInvoice(invoiceId: Long, reason: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val invoice = invoiceDao.getInvoiceById(invoiceId)
                    ?: return@withContext Result.failure(Exception("الفاتورة المطلوبة غير موجودة"))
                if (invoice.Status == "Reversed") {
                    return@withContext Result.failure(Exception("تم إرجاع وعكس هذه الفاتورة مسبقاً"))
                }

                val lines = invoiceDao.getLinesForInvoice(invoiceId)
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val cleanReason = reason?.trim()?.ifBlank { null } ?: "مرتجع معتمد"

                db.withTransaction {
                    invoiceDao.updateInvoiceStatus(invoiceId, "Reversed")
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
                    stockMovementDao.insertMovements(returnMovements)

                    if (invoice.PersonId > 0 && invoice.RemainingAmount > 0) {
                        val opType = if (invoice.Type == "Sale") "SaleReturn" else "Payment"
                        operationDao.insertOperation(
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
                        accountDao.updateLastOperationAt(invoice.PersonId, now)
                    }
                }
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

class VoucherRepository(private val db: SmartLedgerDatabase) {
    private val operationDao = db.operationDao()
    private val accountDao = db.accountDao()
    private val writeMutex = Mutex()

    fun observeRecentOperations(limit: Int = 25): Flow<List<RecentOperationWithPerson>> {
        return operationDao.observeRecentOperationsWithPerson(limit)
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
                val autoRef = refNumber?.trim()?.ifBlank { null }
                    ?: "${if (type == "Receive") "RV" else "PV"}-${System.currentTimeMillis() % 100000}"
                var opId = 0L
                db.withTransaction {
                    val op = OperationEntity(
                        PersonId = personId,
                        Type = type,
                        Amount = cleanAmount,
                        OccurredAt = now,
                        Note = statement.trim(),
                        ReferenceNumber = autoRef,
                        CreatedAt = now
                    )
                    opId = operationDao.insertOperation(op)
                    accountDao.updateLastOperationAt(personId, now)
                }
                Result.success(opId)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر حفظ السند المالي: ${e.localizedMessage}"))
            }
        }
    }
}

class ExpenseRepository(private val db: SmartLedgerDatabase) {
    private val expenseDao = db.expenseDao()
    private val deletedItemDao = db.deletedItemDao()
    private val writeMutex = Mutex()

    fun getAllExpenses(): Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()

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
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            db.withTransaction {
                expenseDao.deleteExpense(id)
                deletedItemDao.insertDeletedItem(
                    DeletedItemEntity(
                        EntityType = "Expense",
                        OriginalId = id,
                        Title = "مصروف تشغيلي #$id",
                        SnapshotJson = "{\"id\": $id}",
                        DeletedAt = now,
                        Reason = reason ?: "حذف نفقة تشغيلية"
                    )
                )
            }
        }
    }
}

class ReportRepository(private val db: SmartLedgerDatabase) {
    private val reportDao = db.reportDao()
    private val dashboardDao = db.dashboardDao()

    fun getFinancialReportSummary(): Flow<FinancialReportSummary> = reportDao.getFinancialReportSummary()
    fun getFinancialReportFromDate(fromDate: String): Flow<FinancialReportSummary> = reportDao.getFinancialReportFromDate(fromDate)
    fun getDashboardMetrics(): Flow<DashboardMetrics> = dashboardDao.getDashboardMetrics()
}
