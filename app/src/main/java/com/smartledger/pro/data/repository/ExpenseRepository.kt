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
                    return@withContext Result.failure(Exception("يجب أن يكون المبلغ أكبر من صفر."))
                }
                if (expense.Statement.trim().isEmpty()) {
                    return@withContext Result.failure(Exception("يرجى إدخال البيان أو الوصف التفصيلي."))
                }
                val id = expenseDao.insertExpense(
                    expense.copy(
                        Amount = cleanAmount,
                        EntryType = expense.EntryType?.ifBlank { "EXPENSE" } ?: "EXPENSE",
                        IsArchived = 0
                    )
                )
                Result.success(id)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر حفظ السجل: ${e.localizedMessage}"))
            }
        }
    }

    /**
     * أرشفة غير تدميرية للنفقة أو الإيراد (`IsArchived = 1`) مع حفظ لقطة في سلة المحذوفات،
     * مما يحافظ على ثبات المعرف الأصلي (`Id`) عند الاستعادة.
     */
    suspend fun deleteExpense(id: Long, reason: String? = null) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val activeDb = db
            val existing = activeDb.expenseDao().getExpenseById(id) ?: return@withContext
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val safeSnapshot = JSONObject().apply {
                put("id", existing.Id)
                put("expenseDate", existing.ExpenseDate)
                put("categoryId", existing.CategoryId ?: 0L)
                put("amount", existing.Amount)
                put("statement", existing.Statement)
                put("paymentSource", existing.PaymentSource)
                put("entryType", existing.EntryType ?: "EXPENSE")
                put("financialAccountId", existing.FinancialAccountId ?: 0L)
                put("createdAt", existing.CreatedAt)
            }.toString()

            activeDb.withTransaction {
                activeDb.expenseDao().archiveExpense(id)
                activeDb.deletedItemDao().insertDeletedItem(
                    DeletedItemEntity(
                        EntityType = "Expense",
                        OriginalId = id,
                        Title = "${existing.Statement} (${existing.Amount})",
                        SnapshotJson = safeSnapshot,
                        DeletedAt = now,
                        Reason = reason ?: "أرشفة نفقة تشغيلية"
                    )
                )
            }
        }
    }

    suspend fun restoreExpenseSnapshot(expense: ExpenseEntity) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val activeDb = db
            activeDb.withTransaction {
                val existing = if (expense.Id > 0L) activeDb.expenseDao().getExpenseById(expense.Id) else null
                if (existing != null) {
                    activeDb.expenseDao().restoreArchivedExpense(expense.Id)
                } else {
                    activeDb.expenseDao().insertExpense(expense.copy(IsArchived = 0))
                }
                activeDb.deletedItemDao().deleteByEntityAndOriginalId("Expense", expense.Id)
            }
        }
    }
}
