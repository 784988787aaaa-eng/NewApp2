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
