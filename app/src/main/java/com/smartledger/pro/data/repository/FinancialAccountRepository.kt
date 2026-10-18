package com.smartledger.pro.data.repository

import androidx.room.withTransaction
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.dao.FinancialAccountBalanceSummary
import com.smartledger.pro.data.local.entity.*
import com.smartledger.pro.domain.usecase.FlexibleUnitValidator
import com.smartledger.pro.domain.usecase.HierarchicalCategoryValidator
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> observeAcrossRestores(block: () -> Flow<T>): Flow<T> {
    return SmartLedgerDatabase.databaseEpoch.flatMapLatest { block() }
}

/**
 * مستودع إدارة التصنيفات الهرمية المرنة متعدد المجالات (CategoryRepository)
 * يطبق كشف الدورات المغلقة ويمنع الحذف النهائي للتصنيفات المرتبطة بسجلات سابقة.
 */
class FinancialAccountRepository(private val dbProvider: () -> SmartLedgerDatabase) {
    constructor(db: SmartLedgerDatabase) : this({ db })

    private val db: SmartLedgerDatabase get() = dbProvider()
    private val writeMutex = Mutex()

    fun observeAccountBalances(onlyActive: Boolean = false): Flow<List<FinancialAccountBalanceSummary>> {
        return observeAcrossRestores {
            db.financialAccountDao().observeAccountBalanceSummaries(if (onlyActive) 1 else 0)
        }
    }

    fun observeRecentTransfers(limit: Int = 50): Flow<List<FinancialTransferEntity>> {
        return observeAcrossRestores {
            db.financialAccountDao().observeRecentTransfers(limit)
        }
    }

    suspend fun saveFinancialAccount(
        id: Long = 0L,
        name: String,
        accountType: String = "CASH_BOX",
        parentGroupId: Long? = null,
        currencySymbol: String = "ر.ي",
        accountNumberOrPhone: String? = null,
        openingBalance: Double = 0.0,
        allowNegativeBalance: Boolean = false,
        isDefault: Boolean = false,
        notes: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val cleanName = name.trim()
                if (cleanName.isEmpty()) {
                    return@withContext Result.failure(Exception("يرجى إدخال اسم الصندوق أو المحفظة أو الحساب البنكي."))
                }
                val activeDb = db
                val dao = activeDb.financialAccountDao()
                val existingAll = dao.getAllFinancialAccountsOnce()
                val duplicate = existingAll.firstOrNull {
                    it.Id != id && it.Name.trim().equals(cleanName, ignoreCase = true)
                }
                if (duplicate != null) {
                    return@withContext Result.failure(Exception("يوجد حساب مالي بنفس الاسم «$cleanName» مسبقاً."))
                }
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val entity = FinancialAccountEntity(
                    Id = id,
                    Name = cleanName,
                    AccountType = accountType,
                    ParentGroupId = if (parentGroupId != null && parentGroupId > 0L) parentGroupId else null,
                    CurrencySymbol = currencySymbol.trim().ifBlank { "ر.ي" },
                    AccountNumberOrPhone = accountNumberOrPhone?.trim()?.ifBlank { null },
                    OpeningBalance = NumberFormatter.roundCurrency(openingBalance),
                    AllowNegativeBalance = allowNegativeBalance,
                    IsDefault = isDefault,
                    IsActive = true,
                    Notes = notes?.trim()?.ifBlank { null },
                    CreatedAt = now
                )
                val savedId = if (id > 0L) {
                    dao.updateFinancialAccount(entity)
                    id
                } else {
                    dao.insertFinancialAccount(entity)
                }
                Result.success(savedId)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "تعذر حفظ الحساب المالي"))
            }
        }
    }

    /**
     * تحويل داخلي ذري بين الصناديق والمحافظ والبنوك داخل معاملة قاعدة بيانات واحدة:
     * - يتحقق من اختلاف الحساب المصدر عن الحساب الوجهة.
     * - يتحقق من كفاية الرصيد الفعلي في الحساب المصدر إذا كان `AllowNegativeBalance == false`.
     * - لا يحتسب التحويل إيراداً ولا مصروفاً (ما عدا رسوم التحويل الاختيارية إن وُجدت).
     */
    suspend fun createInternalTransfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Double,
        feeAmount: Double = 0.0,
        notes: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                if (fromAccountId <= 0L || toAccountId <= 0L) {
                    return@withContext Result.failure(Exception("يرجى تحديد حساب المصدر وحساب الوجهة للتحويل."))
                }
                if (fromAccountId == toAccountId) {
                    return@withContext Result.failure(Exception("لا يمكن التحويل من الحساب المالي إلى نفسه."))
                }
                val cleanAmount = NumberFormatter.roundCurrency(amount)
                val cleanFee = NumberFormatter.roundCurrency(feeAmount.coerceAtLeast(0.0))
                if (cleanAmount <= 0.0) {
                    return@withContext Result.failure(Exception("يجب أن يكون مبلغ التحويل أكبر من صفر."))
                }

                val activeDb = db
                var transferId = 0L
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

                activeDb.withTransaction {
                    val summaries = activeDb.financialAccountDao().observeAccountBalanceSummaries(0).first()
                    val fromSummary = summaries.firstOrNull { it.Id == fromAccountId && it.IsActive }
                        ?: throw IllegalStateException("حساب المصدر غير موجود أو غير نشط.")
                    val toSummary = summaries.firstOrNull { it.Id == toAccountId && it.IsActive }
                        ?: throw IllegalStateException("حساب الوجهة غير موجود أو غير نشط.")

                    val totalRequired = cleanAmount + cleanFee
                    if (!fromSummary.AllowNegativeBalance && totalRequired > fromSummary.NetBalance + 0.001) {
                        throw IllegalStateException(
                            "الرصيد المتاح في «${fromSummary.Name}» (${NumberFormatter.formatCurrency(fromSummary.NetBalance, fromSummary.CurrencySymbol)}) غير كافٍ لتغطية مبلغ التحويل والرسوم (${NumberFormatter.formatCurrency(totalRequired, fromSummary.CurrencySymbol)})."
                        )
                    }

                    val refNo = "TRF-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}"
                    transferId = activeDb.financialAccountDao().insertTransfer(
                        FinancialTransferEntity(
                            FromAccountId = fromAccountId,
                            ToAccountId = toAccountId,
                            Amount = cleanAmount,
                            FeeAmount = cleanFee,
                            OccurredAt = now,
                            ReferenceNumber = refNo,
                            Notes = notes?.trim()?.ifBlank { "تحويل من ${fromSummary.Name} إلى ${toSummary.Name}" },
                            Status = "Completed",
                            CreatedAt = now
                        )
                    )
                }
                Result.success(transferId)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "تعذر تنفيذ التحويل الداخلي"))
            }
        }
    }

    /**
     * جرد ومطابقة الصندوق أو المحفظة مع النقد الفعلي المعدود وتسجيل قيد تسوية موثق عند وجود فارق.
     */
    suspend fun reconcileAccountBalance(
        accountId: Long,
        actualBalance: Double,
        notes: String? = null
    ): Result<Double> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val activeDb = db
                val summaries = activeDb.financialAccountDao().observeAccountBalanceSummaries(0).first()
                val summary = summaries.firstOrNull { it.Id == accountId }
                    ?: return@withContext Result.failure(Exception("الحساب المالي غير موجود."))

                val delta = NumberFormatter.roundCurrency(actualBalance - summary.NetBalance)
                if (abs(delta) < 0.001) {
                    return@withContext Result.success(0.0)
                }

                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val isSurplus = delta > 0.0
                val cleanNotes = notes?.trim()?.ifBlank { null }
                    ?: "تسوية جرد ومطابقة «${summary.Name}» (الفعلي: $actualBalance، الدفتري: ${summary.NetBalance})"

                activeDb.expenseDao().insertExpense(
                    ExpenseEntity(
                        ExpenseDate = now,
                        CategoryId = null,
                        Amount = abs(delta),
                        Statement = cleanNotes,
                        PaymentSource = summary.Name,
                        CreatedAt = now,
                        EntryType = if (isSurplus) "REVENUE" else "EXPENSE",
                        FinancialAccountId = summary.Id,
                        Notes = cleanNotes,
                        IsArchived = 0
                    )
                )
                Result.success(delta)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "تعذر تسجيل تسوية مطابقة الصندوق"))
            }
        }
    }
}

/**
 * مستودع إدارة المخازن المتعددة والتحويلات المخزنية الذرية (WarehouseRepository)
 */
