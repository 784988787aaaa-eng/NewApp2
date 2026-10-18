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
class CategoryRepository(private val dbProvider: () -> SmartLedgerDatabase) {
    constructor(db: SmartLedgerDatabase) : this({ db })

    private val db: SmartLedgerDatabase get() = dbProvider()
    private val writeMutex = Mutex()

    fun observeCategories(domain: String? = null, onlyActive: Boolean = false): Flow<List<CategoryEntity>> {
        val cleanDomain = domain?.trim()?.uppercase()?.ifBlank { null }
        return observeAcrossRestores {
            db.categoryDao().observeCategories(cleanDomain, if (onlyActive) 1 else 0)
        }
    }

    suspend fun getAllCategoriesOnce(domain: String? = null): List<CategoryEntity> = withContext(Dispatchers.IO) {
        db.categoryDao().getAllCategoriesOnce(domain?.trim()?.uppercase()?.ifBlank { null })
    }

    suspend fun saveCategory(
        id: Long = 0L,
        domain: String,
        name: String,
        parentId: Long? = null,
        description: String? = null,
        colorHex: String? = null,
        sortOrder: Int = 0
    ): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val activeDb = db
                val categoryDao = activeDb.categoryDao()
                val cleanDomain = domain.trim().uppercase()
                val allInDomain = categoryDao.getAllCategoriesOnce(cleanDomain)
                val normalizedParent = if (parentId != null && parentId > 0L) parentId else null

                val validation = HierarchicalCategoryValidator.validateAndCheckCycle(
                    categoryId = id,
                    name = name,
                    domain = cleanDomain,
                    parentId = normalizedParent,
                    existingCategories = allInDomain
                )
                if (validation.isFailure) {
                    return@withContext Result.failure(validation.exceptionOrNull()!!)
                }

                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                var savedId = id
                if (id > 0L) {
                    val existing = categoryDao.getCategoryById(id)
                        ?: return@withContext Result.failure(Exception("التصنيف المراد تعديله غير موجود."))
                    categoryDao.updateCategory(
                        existing.copy(
                            Name = name.trim(),
                            ParentId = normalizedParent,
                            Description = description?.trim()?.ifBlank { null },
                            ColorHex = colorHex?.trim()?.ifBlank { null },
                            SortOrder = sortOrder
                        )
                    )
                } else {
                    savedId = categoryDao.insertCategory(
                        CategoryEntity(
                            Domain = cleanDomain,
                            Name = name.trim(),
                            ParentId = normalizedParent,
                            Description = description?.trim()?.ifBlank { null },
                            ColorHex = colorHex?.trim()?.ifBlank { null },
                            IsActive = true,
                            SortOrder = sortOrder,
                            CreatedAt = now
                        )
                    )
                }
                Result.success(savedId)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "تعذر حفظ التصنيف"))
            }
        }
    }

    /**
     * تعطيل أو حذف التصنيف بأمان:
     * - إذا كان للتصنيف فروع تابعة أو كان مرتبطاً بمصروفات أو أصناف، يُمنع الحذف النهائي ويُعطّل (`IsActive = false`)
     *   للحفاظ على التقارير التاريخية مع إخفائه من قوائم الإدخال الجديدة.
     */
    suspend fun deleteOrDeactivateCategory(categoryId: Long): Result<String> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val activeDb = db
                val categoryDao = activeDb.categoryDao()
                val existing = categoryDao.getCategoryById(categoryId)
                    ?: return@withContext Result.failure(Exception("التصنيف غير موجود."))

                val childCount = categoryDao.countChildCategories(categoryId)
                val linkedExpenses = activeDb.expenseDao().countExpensesByCategory(categoryId)
                val linkedProducts = activeDb.productDao().countProductsByCategory(categoryId)

                if (childCount > 0 || linkedExpenses > 0 || linkedProducts > 0) {
                    categoryDao.setCategoryActive(categoryId, false)
                    Result.success(
                        "التصنيف «${existing.Name}» مرتبط بسجلات أو تصنيفات فرعية؛ تم تعطيله وإخفاؤه من العمليات الجديدة مع الحفاظ على ظهوره في التقارير التاريخية."
                    )
                } else {
                    categoryDao.deleteCategoryById(categoryId)
                    Result.success("تم حذف التصنيف غير المرتبط «${existing.Name}» بنجاح.")
                }
            } catch (e: Exception) {
                Result.failure(Exception("تعذر معالجة التصنيف: ${e.localizedMessage}"))
            }
        }
    }

    suspend fun reactivateCategory(categoryId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                db.categoryDao().setCategoryActive(categoryId, true)
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

/**
 * مستودع وحدات القياس المرنة وقواعد التحويل (MeasurementUnitRepository)
 */
