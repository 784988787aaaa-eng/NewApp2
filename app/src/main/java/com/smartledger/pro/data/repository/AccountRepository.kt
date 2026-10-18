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

    suspend fun saveBook(name: String, id: Long = 0L): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val cleanName = name.trim()
                if (cleanName.isEmpty()) {
                    return@withContext Result.failure(Exception("اسم الدفتر مطلوب."))
                }
                val books = bookDao.getAllBooksOnce()
                if (books.any { it.Id != id && it.Name.trim().equals(cleanName, ignoreCase = true) }) {
                    return@withContext Result.failure(Exception("يوجد دفتر بنفس الاسم «$cleanName» مسبقاً."))
                }
                val savedId = if (id > 0L) {
                    val existing = books.firstOrNull { it.Id == id }
                    bookDao.updateBook(
                        BookEntity(
                            Id = id,
                            Name = cleanName,
                            IsSystem = existing?.IsSystem ?: false,
                            SortOrder = existing?.SortOrder ?: books.size + 1
                        )
                    )
                    id
                } else {
                    bookDao.insertBook(
                        BookEntity(
                            Name = cleanName,
                            IsSystem = false,
                            SortOrder = books.size + 1
                        )
                    )
                }
                Result.success(savedId)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر حفظ الدفتر: ${e.localizedMessage}"))
            }
        }
    }

    suspend fun deleteCustomBook(bookId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                bookDao.deleteCustomBook(bookId)
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun getAllDeletedItems(): Flow<List<DeletedItemEntity>> =
        observeAcrossDatabaseRestores { deletedItemDao.getAllDeletedItems() }

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

    suspend fun restoreAccount(id: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val activeDb = db
                activeDb.withTransaction {
                    activeDb.accountDao().restorePerson(id)
                    activeDb.deletedItemDao().deleteByEntityAndOriginalId("Account", id)
                }
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر استعادة الحساب المؤرشف: ${e.localizedMessage}"))
            }
        }
    }

    /**
     * استعادة سجل مؤرشف أو محذوف من سلة المحذوفات (`DeletedItems`):
     * - يدعم استعادة الحسابات (`Account`)، الأصناف (`Product`)، والمصروفات التشغيلية (`Expense`) مع الحفاظ على ثبات `OriginalId`.
     */
    suspend fun restoreDeletedItem(item: DeletedItemEntity): Result<String> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val activeDb = db
                var message = "تمت استعادة السجل بنجاح."
                activeDb.withTransaction {
                    when (item.EntityType) {
                        "Account" -> {
                            activeDb.accountDao().restorePerson(item.OriginalId)
                            activeDb.deletedItemDao().deletePermanently(item.Id)
                            message = "تمت استعادة الحساب «${item.Title}» إلى قائمة الحسابات النشطة."
                        }
                        "Product" -> {
                            activeDb.productDao().restoreProduct(item.OriginalId)
                            activeDb.deletedItemDao().deletePermanently(item.Id)
                            message = "تمت استعادة الصنف «${item.Title}» إلى المخزون النشط."
                        }
                        "Expense" -> {
                            val existingArchived = if (item.OriginalId > 0L) {
                                activeDb.expenseDao().getExpenseById(item.OriginalId)
                            } else null

                            if (existingArchived != null) {
                                activeDb.expenseDao().restoreArchivedExpense(existingArchived.Id)
                                message = "تمت استعادة النفقة التشغيلية «${existingArchived.Statement}» بمعرفها الأصلي (#${existingArchived.Id}) بنجاح."
                            } else {
                                val json = JSONObject(item.SnapshotJson)
                                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                                val restoredExpense = ExpenseEntity(
                                    Id = item.OriginalId.coerceAtLeast(0L),
                                    ExpenseDate = json.optString("expenseDate", now),
                                    CategoryId = json.optLong("categoryId", 0L).takeIf { it > 0L },
                                    Amount = NumberFormatter.roundCurrency(json.optDouble("amount", 0.0)),
                                    Statement = json.optString("statement", item.Title),
                                    PaymentSource = json.optString("paymentSource", "الخزينة الرئيسية"),
                                    CreatedAt = json.optString("createdAt", now),
                                    EntryType = json.optString("entryType", "EXPENSE").ifBlank { "EXPENSE" },
                                    FinancialAccountId = json.optLong("financialAccountId", 0L).takeIf { it > 0L },
                                    IsArchived = 0
                                )
                                if (restoredExpense.Amount > 0.0) {
                                    activeDb.expenseDao().insertExpense(restoredExpense)
                                }
                                message = "تمت استعادة النفقة التشغيلية «${restoredExpense.Statement}» بنجاح."
                            }
                            activeDb.deletedItemDao().deletePermanently(item.Id)
                        }
                        else -> {
                            throw IllegalStateException("هذا السجل يمثل قيداً عكسياً موثقاً للتدقيق ولا يُستعاد بالحذف.")
                        }
                    }
                }
                Result.success(message)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "تعذر استعادة السجل من الأرشيف"))
            }
        }
    }

    suspend fun deleteDeletedItemPermanently(id: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                db.deletedItemDao().deletePermanently(id)
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر حذف السجل نهائياً: ${e.localizedMessage}"))
            }
        }
    }

    fun getAccountOperations(personId: Long): Flow<List<OperationEntity>> {
        return observeAcrossDatabaseRestores { operationDao.getOperationsForPerson(personId) }
    }

    /**
     * إلغاء الحركة المالية عبر قيد عكسي محاسبي موثق (Auditable Accounting Reversal) بدلاً من الحذف المادي:
     * - يمنع عكس الحركات المرتبطة بفاتورة بشكل مستقل لحماية ذرية الفاتورة والمخزون.
     * - يحتفظ بالحركة الأصلية في جدول `Operations` لحماية التاريخ المالي وسجل التدقيق.
     * - يدرج حركة عكسية بالاتجاه المقابل (`REV-OP-<id>`) لتصحيح رصيد الحساب والسيولة بدقة.
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

                if (op.SourceInvoiceId != null && op.SourceInvoiceId > 0L) {
                    return@withContext Result.failure(
                        Exception("لا يمكن عكس حركة مالية مرتبطة بفاتورة بشكل مستقل؛ يرجى تنفيذ الإلغاء أو المرتجع عبر إدارة الفواتير لضمان اتساق الفاتورة والمخزون والحسابات.")
                    )
                }

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
                        CreatedAt = now,
                        FinancialAccountId = op.FinancialAccountId,
                        TargetInvoiceId = op.TargetInvoiceId
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
