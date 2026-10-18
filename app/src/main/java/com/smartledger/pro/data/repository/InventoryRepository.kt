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

class InventoryRepository(private val dbProvider: () -> SmartLedgerDatabase) {
    constructor(db: SmartLedgerDatabase) : this({ db })

    private val db: SmartLedgerDatabase get() = dbProvider()
    private val productDao: ProductDao get() = db.productDao()
    private val stockMovementDao: StockMovementDao get() = db.stockMovementDao()
    private val deletedItemDao: DeletedItemDao get() = db.deletedItemDao()
    private val writeMutex = Mutex()

    fun getProducts(categoryId: Long? = null): Flow<List<ProductStockItem>> {
        val cleanCategoryId = if (categoryId == null || categoryId <= 0) null else categoryId
        return observeAcrossDatabaseRestores { productDao.getProductStockItems(cleanCategoryId) }
    }

    fun searchProducts(query: String): Flow<List<ProductStockItem>> {
        return observeAcrossDatabaseRestores { productDao.searchProducts(query) }
    }

    fun getProductMovements(productId: Long): Flow<List<StockMovementEntity>> {
        return observeAcrossDatabaseRestores { stockMovementDao.getMovementsForProduct(productId) }
    }

    suspend fun findByBarcode(barcode: String): ProductEntity? = withContext(Dispatchers.IO) {
        productDao.findByBarcode(barcode)
    }

    suspend fun getProductById(id: Long): ProductEntity? = withContext(Dispatchers.IO) {
        productDao.getProductById(id)
    }

    suspend fun saveProduct(
        product: ProductEntity,
        openingQty: Double = 0.0,
        warehouseId: Long? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                if (product.Name.trim().isEmpty()) {
                    return@withContext Result.failure(Exception("اسم الصنف مطلوب ولا يمكن تركه فارغاً."))
                }
                if (product.RetailPrice <= 0.0) {
                    return@withContext Result.failure(Exception("يجب أن يكون سعر البيع أكبر من صفر."))
                }
                val activeDb = db
                val activeProductDao = activeDb.productDao()
                val activeStockDao = activeDb.stockMovementDao()

                val cleanBarcode = product.Barcode?.trim()?.ifBlank { null }
                if (cleanBarcode != null) {
                    val existingWithBarcode = activeProductDao.findByBarcode(cleanBarcode)
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

                activeDb.withTransaction {
                    if (productId > 0) {
                        val existing = activeProductDao.getProductById(productId)
                        activeProductDao.updateProduct(
                            normalizedProduct.copy(
                                OpeningQuantityMinor = existing?.OpeningQuantityMinor ?: normalizedProduct.OpeningQuantityMinor,
                                OpeningCost = existing?.OpeningCost ?: normalizedProduct.OpeningCost,
                                CreatedAt = existing?.CreatedAt ?: now
                            )
                        )
                    } else {
                        productId = activeProductDao.insertProduct(normalizedProduct)
                        if (openingQty > 0.0) {
                            activeStockDao.insertMovement(
                                StockMovementEntity(
                                    ProductId = productId,
                                    OccurredAt = now,
                                    Type = "In",
                                    QuantityMinor = openingQty,
                                    UnitCost = normalizedProduct.CostPrice,
                                    Notes = "رصيد افتتاحي للمخزون",
                                    CreatedAt = now,
                                    WarehouseId = warehouseId
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
            val activeDb = db
            val product = activeDb.productDao().getProductById(id) ?: return@withContext
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val safeSnapshot = JSONObject().apply {
                put("id", product.Id)
                put("name", product.Name)
                put("barcode", product.Barcode ?: "")
                put("smallUnit", product.SmallUnit)
                put("largeUnit", product.LargeUnit ?: "")
                put("unitFactor", product.UnitFactor)
                put("costPrice", product.CostPrice)
                put("retailPrice", product.RetailPrice)
                put("wholesalePrice", product.WholesalePrice)
            }.toString()

            activeDb.withTransaction {
                activeDb.productDao().softDeleteProduct(id)
                activeDb.deletedItemDao().insertDeletedItem(
                    DeletedItemEntity(
                        EntityType = "Product",
                        OriginalId = id,
                        Title = product.Name,
                        SnapshotJson = safeSnapshot,
                        DeletedAt = now,
                        Reason = reason ?: "أرشفة صنف من المخزون"
                    )
                )
            }
        }
    }

    suspend fun restoreProduct(id: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val activeDb = db
                activeDb.withTransaction {
                    activeDb.productDao().restoreProduct(id)
                    activeDb.deletedItemDao().deleteByEntityAndOriginalId("Product", id)
                }
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر استعادة الصنف المؤرشف: ${e.localizedMessage}"))
            }
        }
    }

    suspend fun commitStockAdjustment(
        productId: Long,
        actualQty: Double,
        currentQty: Double,
        notes: String? = null,
        warehouseId: Long? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val activeDb = db
                val activeProductDao = activeDb.productDao()
                val activeStockDao = activeDb.stockMovementDao()
                val product = activeProductDao.getProductById(productId)
                    ?: return@withContext Result.failure(Exception("الصنف المطلوب تسوية جرده غير موجود."))
                val liveCurrentQty = activeProductDao.getProductStock(productId)
                val delta = actualQty - liveCurrentQty
                if (kotlin.math.abs(delta) < 0.0001) return@withContext Result.success(0L)
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val type = if (delta > 0) "AdjustmentIn" else "AdjustmentOut"
                val cleanNotes = notes?.trim()?.ifBlank { null }
                    ?: "تسوية جرد مخزني للصنف «${product.Name}» (الفعلي: $actualQty، الدفتري: $liveCurrentQty، الفارق: ${if (delta > 0) "+$delta" else "$delta"})"
                val movement = StockMovementEntity(
                    ProductId = productId,
                    OccurredAt = now,
                    Type = type,
                    QuantityMinor = kotlin.math.abs(delta),
                    UnitCost = product.CostPrice,
                    Notes = cleanNotes,
                    CreatedAt = now,
                    WarehouseId = warehouseId
                )
                val id = activeStockDao.insertMovement(movement)
                Result.success(id)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر تسجيل تسوية الجرد: ${e.localizedMessage}"))
            }
        }
    }
}
