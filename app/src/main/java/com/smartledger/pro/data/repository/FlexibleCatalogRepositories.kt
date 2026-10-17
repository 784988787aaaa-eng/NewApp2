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
class MeasurementUnitRepository(private val dbProvider: () -> SmartLedgerDatabase) {
    constructor(db: SmartLedgerDatabase) : this({ db })

    private val db: SmartLedgerDatabase get() = dbProvider()
    private val writeMutex = Mutex()

    fun observeUnits(onlyActive: Boolean = false): Flow<List<MeasurementUnitEntity>> {
        return observeAcrossRestores {
            db.measurementUnitDao().observeUnits(if (onlyActive) 1 else 0)
        }
    }

    suspend fun saveUnit(
        id: Long = 0L,
        name: String,
        symbol: String = "",
        decimalPrecision: Int = 0,
        baseUnitId: Long? = null,
        conversionFactorToBase: Double = 1.0
    ): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val activeDb = db
                val unitDao = activeDb.measurementUnitDao()
                val existingUnits = unitDao.getAllUnitsOnce()
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val candidate = MeasurementUnitEntity(
                    Id = id,
                    Name = name.trim(),
                    Symbol = symbol.trim().ifBlank { name.trim() },
                    DecimalPrecision = decimalPrecision.coerceIn(0, 4),
                    BaseUnitId = if (baseUnitId != null && baseUnitId > 0L) baseUnitId else null,
                    ConversionFactorToBase = if (baseUnitId != null && baseUnitId > 0L) conversionFactorToBase else 1.0,
                    IsActive = true,
                    CreatedAt = now
                )
                val validation = FlexibleUnitValidator.validateUnit(candidate, existingUnits)
                if (validation.isFailure) {
                    return@withContext Result.failure(validation.exceptionOrNull()!!)
                }
                val savedId = if (id > 0L) {
                    unitDao.updateUnit(candidate)
                    id
                } else {
                    unitDao.insertUnit(candidate)
                }
                Result.success(savedId)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "تعذر حفظ وحدة القياس"))
            }
        }
    }

    suspend fun ensureUnitExists(name: String) = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isEmpty()) return@withContext
        runCatching {
            val dao = db.measurementUnitDao()
            if (dao.findUnitByName(clean) == null) {
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                dao.insertUnit(
                    MeasurementUnitEntity(
                        Name = clean,
                        Symbol = clean,
                        DecimalPrecision = 0,
                        ConversionFactorToBase = 1.0,
                        IsActive = true,
                        CreatedAt = now
                    )
                )
            }
        }
    }

    suspend fun setUnitActive(id: Long, isActive: Boolean) = withContext(Dispatchers.IO) {
        db.measurementUnitDao().setUnitActive(id, isActive)
    }
}

/**
 * مستودع الصناديق والحسابات المالية والمحافظ والتحويلات الداخلية الذرية (FinancialAccountRepository)
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
class WarehouseRepository(private val dbProvider: () -> SmartLedgerDatabase) {
    constructor(db: SmartLedgerDatabase) : this({ db })

    private val db: SmartLedgerDatabase get() = dbProvider()
    private val writeMutex = Mutex()

    fun observeWarehouses(onlyActive: Boolean = false): Flow<List<WarehouseEntity>> {
        return observeAcrossRestores {
            db.warehouseDao().observeWarehouses(if (onlyActive) 1 else 0)
        }
    }

    suspend fun saveWarehouse(
        id: Long = 0L,
        name: String,
        code: String? = null,
        location: String? = null,
        description: String? = null,
        isDefault: Boolean = false
    ): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val cleanName = name.trim()
                if (cleanName.isEmpty()) {
                    return@withContext Result.failure(Exception("اسم المخزن مطلوب."))
                }
                val activeDb = db
                val dao = activeDb.warehouseDao()
                val existing = dao.getAllWarehousesOnce().firstOrNull {
                    it.Id != id && it.Name.trim().equals(cleanName, ignoreCase = true)
                }
                if (existing != null) {
                    return@withContext Result.failure(Exception("يوجد مخزن بنفس الاسم «$cleanName» مسبقاً."))
                }
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val entity = WarehouseEntity(
                    Id = id,
                    Name = cleanName,
                    Code = code?.trim()?.ifBlank { null },
                    Location = location?.trim()?.ifBlank { null },
                    Description = description?.trim()?.ifBlank { null },
                    IsDefault = isDefault,
                    IsActive = true,
                    CreatedAt = now
                )
                val savedId = if (id > 0L) {
                    dao.updateWarehouse(entity)
                    id
                } else {
                    dao.insertWarehouse(entity)
                }
                Result.success(savedId)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "تعذر حفظ بيانات المخزن"))
            }
        }
    }

    /**
     * تحويل مخزني ذري للصنف بين مخزنين (`fromWarehouseId -> toWarehouseId`) داخل معاملة واحدة:
     * ينشئ حركتي مخزون مترابطتين (`TransferOut` من المصدر و`TransferIn` إلى الوجهة) بنفس التكلفة.
     */
    suspend fun transferStockBetweenWarehouses(
        productId: Long,
        fromWarehouseId: Long,
        toWarehouseId: Long,
        quantityMinor: Double,
        allowNegativeStock: Boolean = false,
        notes: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                if (fromWarehouseId <= 0L || toWarehouseId <= 0L) {
                    return@withContext Result.failure(Exception("يرجى اختيار مخزن المصدر ومخزن الوجهة."))
                }
                if (fromWarehouseId == toWarehouseId) {
                    return@withContext Result.failure(Exception("لا يمكن تحويل المخزون من المخزن إلى نفسه."))
                }
                if (quantityMinor <= 0.0) {
                    return@withContext Result.failure(Exception("يجب أن تكون كمية التحويل المخزني أكبر من صفر."))
                }

                val activeDb = db
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val batchId = System.currentTimeMillis()

                activeDb.withTransaction {
                    val product = activeDb.productDao().getProductById(productId)
                        ?: throw IllegalStateException("الصنف المطلوب تحويله غير موجود.")
                    val fromWarehouse = activeDb.warehouseDao().getWarehouseById(fromWarehouseId)
                        ?: throw IllegalStateException("مخزن المصدر غير موجود.")
                    val toWarehouse = activeDb.warehouseDao().getWarehouseById(toWarehouseId)
                        ?: throw IllegalStateException("مخزن الوجهة غير موجود.")

                    if (!allowNegativeStock) {
                        val availableInSource = activeDb.productDao().getProductStockInWarehouse(productId, fromWarehouseId)
                        val globalStock = activeDb.productDao().getProductStock(productId)
                        val effectiveAvailable = if (availableInSource > 0.0) availableInSource else globalStock
                        if (quantityMinor > effectiveAvailable + 0.0001) {
                            throw IllegalStateException(
                                "الكمية المطلوبة للتحويل (${NumberFormatter.formatQuantity(quantityMinor)} ${product.SmallUnit}) تتجاوز المتاح في «${fromWarehouse.Name}» (${NumberFormatter.formatQuantity(effectiveAvailable)} ${product.SmallUnit})."
                            )
                        }
                    }

                    val transferNote = notes?.trim()?.ifBlank { null }
                        ?: "تحويل مخزني من «${fromWarehouse.Name}» إلى «${toWarehouse.Name}»"

                    activeDb.stockMovementDao().insertMovements(
                        listOf(
                            StockMovementEntity(
                                ProductId = productId,
                                OccurredAt = now,
                                Type = "TransferOut",
                                QuantityMinor = quantityMinor,
                                UnitCost = product.CostPrice,
                                Notes = "$transferNote (إخراج)",
                                BatchId = batchId,
                                CreatedAt = now,
                                WarehouseId = fromWarehouseId
                            ),
                            StockMovementEntity(
                                ProductId = productId,
                                OccurredAt = now,
                                Type = "TransferIn",
                                QuantityMinor = quantityMinor,
                                UnitCost = product.CostPrice,
                                Notes = "$transferNote (إدخال)",
                                BatchId = batchId,
                                CreatedAt = now,
                                WarehouseId = toWarehouseId
                            )
                        )
                    )
                }
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "تعذر تنفيذ التحويل المخزني"))
            }
        }
    }
}
