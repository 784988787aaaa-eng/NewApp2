package com.smartledger.pro.data.repository

import androidx.room.withTransaction
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.dao.*
import com.smartledger.pro.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AccountRepository(private val db: SmartLedgerDatabase) {
    private val accountDao = db.accountDao()
    private val bookDao = db.bookDao()
    private val operationDao = db.operationDao()
    private val deletedItemDao = db.deletedItemDao()

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
    ): Long = withContext(Dispatchers.IO) {
        var personId = person.Id
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        db.withTransaction {
            if (personId > 0) {
                val existing = accountDao.getPersonById(personId)
                accountDao.updatePerson(
                    person.copy(
                        AccountCode = person.AccountCode ?: existing?.AccountCode,
                        CreatedAt = existing?.CreatedAt ?: now,
                        LastOperationAt = existing?.LastOperationAt ?: now
                    )
                )
            } else {
                personId = accountDao.insertPerson(person.copy(LastOperationAt = now))
                // الرصيد الافتتاحي يُسجّل مرة واحدة فقط عند الإنشاء الأولي لمنع الأثر المحاسبي المزدوج
                if (openingBalance > 0) {
                    val opType = if (isDebit) "DebtOnUs" else "DebtToUs"
                    val op = OperationEntity(
                        PersonId = personId,
                        Type = opType,
                        Amount = openingBalance,
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
        personId
    }

    suspend fun softDeleteAccount(id: Long, reason: String? = null) = withContext(Dispatchers.IO) {
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

    fun getAccountOperations(personId: Long): Flow<List<OperationEntity>> {
        return operationDao.getOperationsForPerson(personId)
    }

    suspend fun deleteOperation(operationId: Long, reason: String? = null) = withContext(Dispatchers.IO) {
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

class InventoryRepository(private val db: SmartLedgerDatabase) {
    private val productDao = db.productDao()
    private val stockMovementDao = db.stockMovementDao()
    private val deletedItemDao = db.deletedItemDao()

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
    ): Long = withContext(Dispatchers.IO) {
        var productId = product.Id
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        db.withTransaction {
            if (productId > 0) {
                val existing = productDao.getProductById(productId)
                productDao.updateProduct(
                    product.copy(
                        OpeningQuantityMinor = existing?.OpeningQuantityMinor ?: product.OpeningQuantityMinor,
                        OpeningCost = existing?.OpeningCost ?: product.OpeningCost,
                        CreatedAt = existing?.CreatedAt ?: now
                    )
                )
            } else {
                productId = productDao.insertProduct(product)
                if (openingQty > 0) {
                    stockMovementDao.insertMovement(
                        StockMovementEntity(
                            ProductId = productId,
                            OccurredAt = now,
                            Type = "In",
                            QuantityMinor = openingQty,
                            UnitCost = product.CostPrice,
                            Notes = "رصيد افتتاحي للمخزون",
                            CreatedAt = now
                        )
                    )
                }
            }
        }
        productId
    }

    suspend fun softDeleteProduct(id: Long, reason: String? = null) = withContext(Dispatchers.IO) {
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

    suspend fun commitStockAdjustment(
        productId: Long,
        actualQty: Double,
        currentQty: Double,
        notes: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val delta = actualQty - currentQty
        if (kotlin.math.abs(delta) < 0.0001) return@withContext 0L
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
        stockMovementDao.insertMovement(movement)
    }
}

class InvoiceRepository(private val db: SmartLedgerDatabase) {
    private val invoiceDao = db.invoiceDao()
    private val stockMovementDao = db.stockMovementDao()
    private val operationDao = db.operationDao()
    private val accountDao = db.accountDao()

    suspend fun createInvoice(
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            var invoiceId = 0L
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

            db.withTransaction {
                invoiceId = invoiceDao.insertInvoice(invoice)
                val linesWithId = lines.map { it.copy(InvoiceId = invoiceId) }
                invoiceDao.insertLines(linesWithId)

                // 1. حركة المخزون الذرية
                val stockMovements = lines.map { line ->
                    StockMovementEntity(
                        ProductId = line.ProductId,
                        OccurredAt = now,
                        Type = if (invoice.Type == "Sale") "Sale" else "Purchase",
                        QuantityMinor = line.QuantityMinor,
                        UnitCost = line.LineCost,
                        Notes = "فاتورة ${if (invoice.Type == "Sale") "مبيعات" else "مشتريات"} #${invoice.InvoiceNumber}",
                        InvoiceId = invoiceId,
                        CreatedAt = now
                    )
                }
                stockMovementDao.insertMovements(stockMovements)

                // 2. تحديث الحسابات والذمم المدينة والدائنة
                // القاعدة المحاسبية الدقيقة:
                // - عند ربط الفاتورة بحساب عميل/مورد مسجل (PersonId > 0):
                //   يُسجل إجمالي الفاتورة الصافي كحركة (Sale أو Purchase) ثم يُسجل المبلغ المدفوع نقداً كسند (Receive أو Payment)
                //   أو يُسجل المتبقي الآجل بشكل واضح بحيث يكون صافي الأثر على رصيد العميل = RemainingAmount بالضبط.
                if (invoice.Type == "Sale") {
                    if (invoice.RemainingAmount > 0 && invoice.PersonId > 0) {
                        operationDao.insertOperation(
                            OperationEntity(
                                PersonId = invoice.PersonId,
                                Type = "Sale",
                                Amount = invoice.RemainingAmount,
                                OccurredAt = now,
                                Note = "متبقي آجل فاتورة مبيعات #${invoice.InvoiceNumber}",
                                ReferenceNumber = invoice.InvoiceNumber,
                                SourceInvoiceId = invoiceId,
                                CreatedAt = now
                            )
                        )
                        accountDao.updateLastOperationAt(invoice.PersonId, now)
                    }
                    if (invoice.PaidAmount > 0 && invoice.PersonId > 0 && invoice.RemainingAmount == 0.0) {
                        // في حال البيع النقدي الكامل لعميل مسجل، نحفظ تاريخ آخر تعامل دون زيادة مديونيته
                        accountDao.updateLastOperationAt(invoice.PersonId, now)
                    }
                } else if (invoice.Type == "Purchase") {
                    // المشتريات الآجلة تزيد التزام المورد (له - دائن) بمقدار المتبقي غير المسدد
                    if (invoice.RemainingAmount > 0 && invoice.PersonId > 0) {
                        operationDao.insertOperation(
                            OperationEntity(
                                PersonId = invoice.PersonId,
                                Type = "Purchase",
                                Amount = invoice.RemainingAmount,
                                OccurredAt = now,
                                Note = "متبقي آجل فاتورة مشتريات #${invoice.InvoiceNumber}",
                                ReferenceNumber = invoice.InvoiceNumber,
                                SourceInvoiceId = invoiceId,
                                CreatedAt = now
                            )
                        )
                        accountDao.updateLastOperationAt(invoice.PersonId, now)
                    }
                    if (invoice.PaidAmount > 0 && invoice.PersonId > 0 && invoice.RemainingAmount == 0.0) {
                        accountDao.updateLastOperationAt(invoice.PersonId, now)
                    }
                }
            }
            Result.success(invoiceId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getRecentInvoices(limit: Int = 100): Flow<List<InvoiceEntity>> = invoiceDao.getRecentInvoices(limit)

    suspend fun getInvoiceLines(invoiceId: Long): List<InvoiceLineEntity> = withContext(Dispatchers.IO) {
        invoiceDao.getLinesForInvoice(invoiceId)
    }

    suspend fun reverseInvoice(invoiceId: Long, reason: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
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
                // عكس حركة المخزون
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

                // عكس التأثير المالي الآجل على العميل أو المورد
                if (invoice.PersonId > 0 && invoice.RemainingAmount > 0) {
                    val opType = if (invoice.Type == "Sale") "SaleReturn" else "Payment"
                    operationDao.insertOperation(
                        OperationEntity(
                            PersonId = invoice.PersonId,
                            Type = opType,
                            Amount = invoice.RemainingAmount,
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

class VoucherRepository(private val db: SmartLedgerDatabase) {
    private val operationDao = db.operationDao()
    private val accountDao = db.accountDao()

    fun observeRecentOperations(limit: Int = 25): Flow<List<RecentOperationWithPerson>> {
        return operationDao.observeRecentOperationsWithPerson(limit)
    }

    suspend fun createVoucher(
        personId: Long,
        type: String, // Receive (له), Payment (عليه)
        amount: Double,
        statement: String,
        refNumber: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val autoRef = refNumber?.trim()?.ifBlank { null }
            ?: "${if (type == "Receive") "RV" else "PV"}-${System.currentTimeMillis() % 100000}"
        var opId = 0L
        db.withTransaction {
            val op = OperationEntity(
                PersonId = personId,
                Type = type,
                Amount = amount,
                OccurredAt = now,
                Note = statement,
                ReferenceNumber = autoRef,
                CreatedAt = now
            )
            opId = operationDao.insertOperation(op)
            accountDao.updateLastOperationAt(personId, now)
        }
        opId
    }
}

class ExpenseRepository(private val db: SmartLedgerDatabase) {
    private val expenseDao = db.expenseDao()
    private val deletedItemDao = db.deletedItemDao()

    fun getAllExpenses(): Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()

    suspend fun saveExpense(expense: ExpenseEntity): Long = withContext(Dispatchers.IO) {
        expenseDao.insertExpense(expense)
    }

    suspend fun deleteExpense(id: Long, reason: String? = null) = withContext(Dispatchers.IO) {
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

class ReportRepository(private val db: SmartLedgerDatabase) {
    private val reportDao = db.reportDao()
    private val dashboardDao = db.dashboardDao()

    fun getFinancialReportSummary(): Flow<FinancialReportSummary> = reportDao.getFinancialReportSummary()
    fun getFinancialReportFromDate(fromDate: String): Flow<FinancialReportSummary> = reportDao.getFinancialReportFromDate(fromDate)
    fun getDashboardMetrics(): Flow<DashboardMetrics> = dashboardDao.getDashboardMetrics()
}
