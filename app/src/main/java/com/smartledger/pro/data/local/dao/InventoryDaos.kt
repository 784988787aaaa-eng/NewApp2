package com.smartledger.pro.data.local.dao

import androidx.room.*
import com.smartledger.pro.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("""
        SELECT 
            p.Id, p.Barcode, p.Name, p.CategoryId, p.Warehouse, p.ShelfLocation, p.SmallUnit, p.LargeUnit, 
            p.UnitFactor, p.CostPrice, p.RetailPrice, p.WholesalePrice, p.AlertThreshold,
            COALESCE(SUM(CASE 
                WHEN sm.Type IN ('In', 'Purchase', 'ReturnIn', 'AdjustmentIn', 'TransferIn') THEN sm.QuantityMinor 
                WHEN sm.Type IN ('Out', 'Sale', 'ReturnOut', 'AdjustmentOut', 'TransferOut') THEN -sm.QuantityMinor 
                ELSE 0.0 
            END), 0.0) AS CurrentStockMinor
        FROM Products p
        LEFT JOIN StockMovements sm ON p.Id = sm.ProductId
        WHERE p.IsActive = 1 AND (:categoryId IS NULL OR p.CategoryId = :categoryId)
        GROUP BY p.Id
        ORDER BY p.Name ASC
    """)
    fun getProductStockItems(categoryId: Long? = null): Flow<List<ProductStockItem>>

    @Query("""
        SELECT 
            p.Id, p.Barcode, p.Name, p.CategoryId, p.Warehouse, p.ShelfLocation, p.SmallUnit, p.LargeUnit, 
            p.UnitFactor, p.CostPrice, p.RetailPrice, p.WholesalePrice, p.AlertThreshold,
            COALESCE(SUM(CASE 
                WHEN sm.Type IN ('In', 'Purchase', 'ReturnIn', 'AdjustmentIn', 'TransferIn') THEN sm.QuantityMinor 
                WHEN sm.Type IN ('Out', 'Sale', 'ReturnOut', 'AdjustmentOut', 'TransferOut') THEN -sm.QuantityMinor 
                ELSE 0.0 
            END), 0.0) AS CurrentStockMinor
        FROM Products p
        LEFT JOIN StockMovements sm ON p.Id = sm.ProductId
        WHERE p.IsActive = 1 AND (p.Name LIKE '%' || :query || '%' OR p.Barcode LIKE '%' || :query || '%' OR p.ShelfLocation LIKE '%' || :query || '%')
        GROUP BY p.Id
        ORDER BY p.Name ASC
    """)
    fun searchProducts(query: String): Flow<List<ProductStockItem>>

    @Query("SELECT * FROM Products WHERE Barcode = :barcode AND IsActive = 1 LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM Products WHERE Id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT COUNT(*) FROM Products WHERE IsActive = 1")
    suspend fun getActiveProductsCount(): Int

    @Query("SELECT COUNT(*) FROM Products WHERE CategoryId = :categoryId")
    suspend fun countProductsByCategory(categoryId: Long): Int

    @Query("""
        SELECT COALESCE(SUM(CASE 
            WHEN sm.Type IN ('In', 'Purchase', 'ReturnIn', 'AdjustmentIn', 'TransferIn') THEN sm.QuantityMinor 
            WHEN sm.Type IN ('Out', 'Sale', 'ReturnOut', 'AdjustmentOut', 'TransferOut') THEN -sm.QuantityMinor 
            ELSE 0.0 
        END), 0.0)
        FROM StockMovements sm
        WHERE sm.ProductId = :productId
    """)
    suspend fun getProductStock(productId: Long): Double

    @Query("""
        SELECT COALESCE(SUM(CASE 
            WHEN sm.Type IN ('In', 'Purchase', 'ReturnIn', 'AdjustmentIn', 'TransferIn') THEN sm.QuantityMinor 
            WHEN sm.Type IN ('Out', 'Sale', 'ReturnOut', 'AdjustmentOut', 'TransferOut') THEN -sm.QuantityMinor 
            ELSE 0.0 
        END), 0.0)
        FROM StockMovements sm
        WHERE sm.ProductId = :productId AND (:warehouseId IS NULL OR sm.WarehouseId = :warehouseId)
    """)
    suspend fun getProductStockInWarehouse(productId: Long, warehouseId: Long?): Double

    @Query("UPDATE Products SET CostPrice = :newCostPrice WHERE Id = :productId")
    suspend fun updateProductCostPrice(productId: Long, newCostPrice: Double)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE Products SET IsActive = 0 WHERE Id = :id")
    suspend fun softDeleteProduct(id: Long)

    @Query("UPDATE Products SET IsActive = 1 WHERE Id = :id")
    suspend fun restoreProduct(id: Long)
}

@Dao
interface StockMovementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovement(movement: StockMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovements(movements: List<StockMovementEntity>): List<Long>

    @Query("SELECT * FROM StockMovements WHERE ProductId = :productId ORDER BY OccurredAt DESC, Id DESC LIMIT 100")
    fun getMovementsForProduct(productId: Long): Flow<List<StockMovementEntity>>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM Categories WHERE (:domain IS NULL OR Domain = :domain) AND (:onlyActive = 0 OR IsActive = 1) ORDER BY SortOrder ASC, Name ASC")
    fun observeCategories(domain: String? = null, onlyActive: Int = 0): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM Categories WHERE (:domain IS NULL OR Domain = :domain) ORDER BY SortOrder ASC, Name ASC")
    suspend fun getAllCategoriesOnce(domain: String? = null): List<CategoryEntity>

    @Query("SELECT * FROM Categories WHERE Id = :id LIMIT 1")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM Categories WHERE ParentId = :parentId")
    suspend fun countChildCategories(parentId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("UPDATE Categories SET IsActive = :isActive WHERE Id = :id")
    suspend fun setCategoryActive(id: Long, isActive: Boolean)

    @Query("DELETE FROM Categories WHERE Id = :id")
    suspend fun deleteCategoryById(id: Long)
}

@Dao
interface MeasurementUnitDao {
    @Query("SELECT * FROM MeasurementUnits WHERE (:onlyActive = 0 OR IsActive = 1) ORDER BY Name ASC")
    fun observeUnits(onlyActive: Int = 0): Flow<List<MeasurementUnitEntity>>

    @Query("SELECT * FROM MeasurementUnits ORDER BY Name ASC")
    suspend fun getAllUnitsOnce(): List<MeasurementUnitEntity>

    @Query("SELECT * FROM MeasurementUnits WHERE Id = :id LIMIT 1")
    suspend fun getUnitById(id: Long): MeasurementUnitEntity?

    @Query("SELECT * FROM MeasurementUnits WHERE lower(Name) = lower(:name) LIMIT 1")
    suspend fun findUnitByName(name: String): MeasurementUnitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnit(unit: MeasurementUnitEntity): Long

    @Update
    suspend fun updateUnit(unit: MeasurementUnitEntity)

    @Query("UPDATE MeasurementUnits SET IsActive = :isActive WHERE Id = :id")
    suspend fun setUnitActive(id: Long, isActive: Boolean)
}

@Dao
interface WarehouseDao {
    @Query("SELECT * FROM Warehouses WHERE (:onlyActive = 0 OR IsActive = 1) ORDER BY IsDefault DESC, Name ASC")
    fun observeWarehouses(onlyActive: Int = 0): Flow<List<WarehouseEntity>>

    @Query("SELECT * FROM Warehouses ORDER BY IsDefault DESC, Name ASC")
    suspend fun getAllWarehousesOnce(): List<WarehouseEntity>

    @Query("SELECT * FROM Warehouses WHERE Id = :id LIMIT 1")
    suspend fun getWarehouseById(id: Long): WarehouseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWarehouse(warehouse: WarehouseEntity): Long

    @Update
    suspend fun updateWarehouse(warehouse: WarehouseEntity)

    @Query("UPDATE Warehouses SET IsActive = :isActive WHERE Id = :id")
    suspend fun setWarehouseActive(id: Long, isActive: Boolean)
}
