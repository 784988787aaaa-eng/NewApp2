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
        refNumber: String? = null,
        financialAccountId: Long? = null,
        targetInvoiceId: Long? = null
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
                        CreatedAt = now,
                        FinancialAccountId = financialAccountId,
                        TargetInvoiceId = targetInvoiceId
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
