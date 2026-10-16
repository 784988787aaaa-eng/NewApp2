package com.smartledger.pro.data.repository

import androidx.room.withTransaction
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.dao.*
import com.smartledger.pro.data.local.entity.*
import com.smartledger.pro.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AccountRepository(private val db: SmartLedgerDatabase) {
    private val accountDao = db.accountDao()
    private val bookDao = db.bookDao()
    private val operationDao = db.operationDao()
    private val deletedItemDao = db.deletedItemDao()

    fun getAccountSummaries(bookId: Long? = null): Flow<List<AccountBalanceSummary>> =
        accountDao.getAccountSummaries(if (bookId == null || bookId <= 0) null else bookId)

    fun searchAccounts(query: String, bookId: Long? = null): Flow<List<AccountBalanceSummary>> =
        accountDao.searchAccounts(query, if (bookId == null || bookId <= 0) null else bookId)

    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

    suspend fun getAccountById(id: Long): PersonEntity? = withContext(Dispatchers.IO) {
        accountDao.getPersonById(id)
    }

    suspend fun saveAccount(
        person: PersonEntity,
        openingBalance: Double = 0.0,
        isDebit: Boolean = true,
        updateOpeningIfEdited: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        var personId = person.Id
        val now = Formatters.nowTimestamp()
        db.withTransaction {
            if (personId > 0) {
                val existing = accountDao.getPersonById(personId)
                val updatedPerson = person.copy(
                    AccountCode = person.AccountCode ?: existing?.AccountCode ?: ("ACC-" + personId),
                    CreatedAt = existing?.CreatedAt ?: person.CreatedAt,
                    LastOperationAt = existing?.LastOperationAt ?: now
                )
                accountDao.updatePerson(updatedPerson)

                if (updateOpeningIfEdited) {
                    val existingOpening = operationDao.getOpeningOperationForPerson(personId)
                    if (openingBalance > 0.0) {
                        val opType = if (isDebit) "DebtOnUs" else "DebtToUs"
                        if (existingOpening != null) {
                            operationDao.updateOperation(
                                existingOpening.copy(Type = opType, Amount = openingBalance, Note = "رصيد افتتاحي تأسيسي (معدّل)")
                            )
                        } else {
                            operationDao.insertOperation(
                                OperationEntity(
                                    PersonId = personId,
                                    Type = opType,
                                    Amount = openingBalance,
                                    OccurredAt = now,
                                    Note = "رصيد افتتاحي تأسيسي",
                                    ReferenceNumber = "OPENING",
                                    CreatedAt = now
                                )
                            )
                        }
                    } else if (existingOpening != null) {
                        operationDao.deleteOperationById(existingOpening.Id)
                    }
                }
            } else {
                personId = accountDao.insertPerson(person)
                if (openingBalance > 0.0) {
                    val opType = if (isDebit) "DebtOnUs" else "DebtToUs"
                    operationDao.insertOperation(
                        OperationEntity(
                            PersonId = personId,
                            Type = opType,
                            Amount = openingBalance,
                            OccurredAt = now,
                            Note = "رصيد افتتاحي تأسيسي",
                            ReferenceNumber = "OPENING",
                            CreatedAt = now
                        )
                    )
                    accountDao.updateLastOperationAt(personId, now)
                }
            }
        }
        personId
    }

    suspend fun softDeleteAccount(id: Long, reason: String? = null) = withContext(Dispatchers.IO) {
        val person = accountDao.getPersonById(id) ?: return@withContext
        val now = Formatters.nowTimestamp()
        db.withTransaction {
            accountDao.softDeletePerson(id)
            deletedItemDao.insertDeletedItem(
                DeletedItemEntity(
                    EntityType = "Account",
                    OriginalId = id,
                    Title = person.Name,
                    SnapshotJson = """{"name": "${person.Name}", "phone": "${person.Phone ?: ""}", "bookId": ${person.BookId}}""",
                    DeletedAt = now,
                    Reason = reason ?: "حذف بواسطة المستخدم"
                )
            )
        }
    }

    fun getAccountOperations(personId: Long): Flow<List<OperationEntity>> =
        operationDao.getOperationsForPerson(personId)

    suspend fun deleteOperation(operationId: Long, reason: String? = null) = withContext(Dispatchers.IO) {
        val op = operationDao.getOperationById(operationId) ?: return@withContext
        val now = Formatters.nowTimestamp()
        db.withTransaction {
            operationDao.deleteOperationById(operationId)
            deletedItemDao.insertDeletedItem(
                DeletedItemEntity(
                    EntityType = "Operation",
                    OriginalId = operationId,
                    Title = "معاملة مالية #${operationId} (${Formatters.formatAmountOnly(op.Amount)})",
                    SnapshotJson = """{"type": "${op.Type}", "amount": ${op.Amount}, "personId": ${op.PersonId}}""",
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

    fun getProducts(categoryId: Long? = null): Flow<List<ProductStockItem>> =
        productDao.getProductStockItems(if (categoryId == null || categoryId <= 0) null else categoryId)

    fun searchProducts(query: String): Flow<List<ProductStockItem>> = productDao.searchProducts(query)

    fun getMovementsForProduct(productId: Long): Flow<List<StockMovementEntity>> =
        stockMovementDao.getMovementsForProduct(productId)

    suspend fun findByBarcode(barcode: String): ProductEntity? = withContext(Dispatchers.IO) {
        val clean = barcode.trim()
        if (clean.isEmpty()) null else productDao.findByBarcode(clean)
    }

    suspend fun checkBarcodeConflict(barcode: String?, excludeProductId: Long = 0L): ProductEntity? = withContext(Dispatchers.IO) {
        val clean = barcode?.trim() ?: return@withContext null
        if (clean.isEmpty()) return@withContext null
        productDao.findDuplicateBarcode(clean, excludeProductId)
    }

    suspend fun getProductById(id: Long): ProductEntity? = withContext(Dispatchers.IO) {
        productDao.getProductById(id)
    }

    suspend fun saveProduct(
        product: ProductEntity,
        openingQty: Double = 0.0
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val cleanBarcode = product.Barcode?.trim()?.ifBlank { null }
            if (cleanBarcode != null) {
                val conflict = productDao.findDuplicateBarcode(cleanBarcode, product.Id)
                if (conflict != null) {
                    return@withContext Result.failure(
                        IllegalStateException("الباركود ($cleanBarcode) مسجل مسبقاً للصنف «${conflict.Name}»")
                    )
                }
            }

            var productId = product.Id
            val now = Formatters.nowTimestamp()
            db.withTransaction {
                if (productId > 0) {
                    productDao.updateProduct(product.copy(Barcode = cleanBarcode))
                } else {
                    productId = productDao.insertProduct(product.copy(Barcode = cleanBarcode))
                    if (openingQty > 0.0) {
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
            Result.success(productId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun softDeleteProduct(id: Long, reason: String? = null) = withContext(Dispatchers.IO) {
        val product = productDao.getProductById(id) ?: return@withContext
        val now = Formatters.nowTimestamp()
        db.withTransaction {
            productDao.softDeleteProduct(id)
            deletedItemDao.insertDeletedItem(
                DeletedItemEntity(
                    EntityType = "Product",
                    OriginalId = id,
                    Title = product.Name,
                    SnapshotJson = """{"name": "${product.Name}", "barcode": "${product.Barcode ?: ""}", "price": ${product.RetailPrice}}""",
                    DeletedAt = now,
                    Reason = reason ?: "حذف صنف من المخزن"
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
        val now = Formatters.nowTimestamp()
        val type = if (delta > 0) "AdjustmentIn" else "AdjustmentOut"
        val defaultNote = "تسوية جردية (الفعلي: ${Formatters.formatQuantity(actualQty)}، الدفتري: ${Formatters.formatQuantity(currentQty)})"
        val finalNote = if (!notes.isNullOrBlank()) "${notes.trim()} — $defaultNote" else defaultNote
        stockMovementDao.insertMovement(
            StockMovementEntity(
                ProductId = productId,
                OccurredAt = now,
                Type = type,
                QuantityMinor = kotlin.math.abs(delta),
                UnitCost = 0.0,
                Notes = finalNote,
                CreatedAt = now
            )
        )
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
            if (lines.isEmpty()) return@withContext Result.failure(IllegalArgumentException("لا يمكن حفظ فاتورة بدون بنود"))
            if (invoice.RemainingAmount > 0.001 && invoice.PersonId <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("الفواتير الآجلة أو الجزئية تتطلب تحديد حساب عميل أو مورد مسجل"))
            }

            var invoiceId = 0L
            val now = Formatters.nowTimestamp()

            db.withTransaction {
                invoiceId = invoiceDao.insertInvoice(invoice)
                val linesWithId = lines.map { it.copy(InvoiceId = invoiceId) }
                invoiceDao.insertLines(linesWithId)

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

                if (invoice.Type == "Sale" && invoice.PersonId > 0) {
                    if (invoice.FinalTotal > 0) {
                        operationDao.insertOperation(
                            OperationEntity(
                                PersonId = invoice.PersonId,
                                Type = "Sale",
                                Amount = invoice.FinalTotal,
                                OccurredAt = now,
                                Note = "فاتورة مبيعات #${invoice.InvoiceNumber}",
                                ReferenceNumber = invoice.InvoiceNumber,
                                SourceInvoiceId = invoiceId,
                                CreatedAt = now
                            )
                        )
                    }
                    if (invoice.PaidAmount > 0) {
                        operationDao.insertOperation(
                            OperationEntity(
                                PersonId = invoice.PersonId,
                                Type = "Receive",
                                Amount = invoice.PaidAmount,
                                OccurredAt = now,
                                Note = "سداد نقدي لفاتورة مبيعات #${invoice.InvoiceNumber}",
                                ReferenceNumber = invoice.InvoiceNumber,
                                SourceInvoiceId = invoiceId,
                                CreatedAt = now
                            )
                        )
                    }
                    accountDao.updateLastOperationAt(invoice.PersonId, now)
                } else if (invoice.Type == "Purchase" && invoice.PersonId > 0) {
                    if (invoice.FinalTotal > 0) {
                        operationDao.insertOperation(
                            OperationEntity(
                                PersonId = invoice.PersonId,
                                Type = "Purchase",
                                Amount = invoice.FinalTotal,
                                OccurredAt = now,
                                Note = "فاتورة مشتريات #${invoice.InvoiceNumber}",
                                ReferenceNumber = invoice.InvoiceNumber,
                                SourceInvoiceId = invoiceId,
                                CreatedAt = now
                            )
                        )
                    }
                    if (invoice.PaidAmount > 0) {
                        operationDao.insertOperation(
                            OperationEntity(
                                PersonId = invoice.PersonId,
                                Type = "Payment",
                                Amount = invoice.PaidAmount,
                                OccurredAt = now,
                                Note = "دفعة نقدية لفاتورة مشتريات #${invoice.InvoiceNumber}",
                                ReferenceNumber = invoice.InvoiceNumber,
                                SourceInvoiceId = invoiceId,
                                CreatedAt = now
                            )
                        )
                    }
                    accountDao.updateLastOperationAt(invoice.PersonId, now)
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
                ?: return@withContext Result.failure(Exception("الفاتورة غير موجودة"))
            if (invoice.Status == "Reversed") {
                return@withContext Result.failure(Exception("تم إرجاع وعكس هذه الفاتورة مسبقاً"))
            }

            val lines = invoiceDao.getLinesForInvoice(invoiceId)
            val now = Formatters.nowTimestamp()
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
                        Notes = "مرتجع فاتورة #${invoice.InvoiceNumber} ($cleanReason)",
                        InvoiceId = invoiceId,
                        CreatedAt = now
                    )
                }
                stockMovementDao.insertMovements(returnMovements)

                if (invoice.PersonId > 0) {
                    if (invoice.Type == "Sale") {
                        if (invoice.FinalTotal > 0) {
                            operationDao.insertOperation(
                                OperationEntity(
                                    PersonId = invoice.PersonId,
                                    Type = "SaleReturn",
                                    Amount = invoice.FinalTotal,
                                    OccurredAt = now,
                                    Note = "مرتجع فاتورة مبيعات #${invoice.InvoiceNumber} ($cleanReason)",
                                    ReferenceNumber = "REV-${invoice.InvoiceNumber}",
                                    SourceInvoiceId = invoiceId,
                                    CreatedAt = now
                                )
                            )
                        }
                        if (invoice.PaidAmount > 0) {
                            operationDao.insertOperation(
                                OperationEntity(
                                    PersonId = invoice.PersonId,
                                    Type = "Payment",
                                    Amount = invoice.PaidAmount,
                                    OccurredAt = now,
                                    Note = "رد نقدية مرتجع فاتورة #${invoice.InvoiceNumber}",
                                    ReferenceNumber = "REV-${invoice.InvoiceNumber}",
                                    SourceInvoiceId = invoiceId,
                                    CreatedAt = now
                                )
                            )
                        }
                    } else if (invoice.Type == "Purchase") {
                        if (invoice.FinalTotal > 0) {
                            operationDao.insertOperation(
                                OperationEntity(
                                    PersonId = invoice.PersonId,
                                    Type = "PurchaseReturn",
                                    Amount = invoice.FinalTotal,
                                    OccurredAt = now,
                                    Note = "مرتجع فاتورة مشتريات #${invoice.InvoiceNumber} ($cleanReason)",
                                    ReferenceNumber = "REV-${invoice.InvoiceNumber}",
                                    SourceInvoiceId = invoiceId,
                                    CreatedAt = now
                                )
                            )
                        }
                        if (invoice.PaidAmount > 0) {
                            operationDao.insertOperation(
                                OperationEntity(
                                    PersonId = invoice.PersonId,
                                    Type = "Receive",
                                    Amount = invoice.PaidAmount,
                                    OccurredAt = now,
                                    Note = "استرداد دفعة مرتجع مشتريات #${invoice.InvoiceNumber}",
                                    ReferenceNumber = "REV-${invoice.InvoiceNumber}",
                                    SourceInvoiceId = invoiceId,
                                    CreatedAt = now
                                )
                            )
                        }
                    }
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

    fun getRecentVouchers(limit: Int = 100): Flow<List<OperationEntity>> =
        operationDao.getRecentVouchersFlow(limit)

    suspend fun createVoucher(
        personId: Long,
        type: String,
        amount: Double,
        statement: String,
        refNumber: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val now = Formatters.nowTimestamp()
        val generatedRef = refNumber?.trim()?.ifBlank { null }
            ?: "${if (type == "Receive") "RV" else "PV"}-${System.currentTimeMillis() % 100000}"
        var opId = 0L
        db.withTransaction {
            val op = OperationEntity(
                PersonId = personId,
                Type = type,
                Amount = amount,
                OccurredAt = now,
                Note = statement.trim(),
                ReferenceNumber = generatedRef,
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
        if (expense.Id > 0) {
            expenseDao.updateExpense(expense)
            expense.Id
        } else {
            expenseDao.insertExpense(expense)
        }
    }

    suspend fun deleteExpense(id: Long, reason: String? = null) = withContext(Dispatchers.IO) {
        val exp = expenseDao.getExpenseById(id) ?: return@withContext
        val now = Formatters.nowTimestamp()
        db.withTransaction {
            expenseDao.deleteExpense(id)
            deletedItemDao.insertDeletedItem(
                DeletedItemEntity(
                    EntityType = "Expense",
                    OriginalId = id,
                    Title = exp.Statement,
                    SnapshotJson = """{"statement": "${exp.Statement}", "amount": ${exp.Amount}}""",
                    DeletedAt = now,
                    Reason = reason ?: "حذف مصروف تشغيلي"
                )
            )
        }
    }
}

class ReportRepository(private val db: SmartLedgerDatabase) {
    private val reportDao = db.reportDao()
    private val dashboardDao = db.dashboardDao()

    fun getFinancialReportSummary(): Flow<FinancialReportSummary> = reportDao.getFinancialReportSummary()
    fun getFinancialReportSummaryByPeriod(fromDate: String, toDate: String): Flow<FinancialReportSummary> =
        reportDao.getFinancialReportSummaryByPeriod(fromDate, toDate)
    fun getDashboardMetrics(): Flow<DashboardMetrics> = dashboardDao.getDashboardMetrics()
}
