package com.smartledger.pro.data.local.dao

import androidx.room.*
import com.smartledger.pro.data.local.entity.*
import kotlinx.coroutines.flow.Flow

data class AccountBalanceSummary(
    val PersonId: Long,
    val PersonName: String,
    val Phone: String?,
    val BookId: Long,
    val TotalDebit: Double,
    val TotalCredit: Double,
    val NetBalance: Double,
    val LastOperationAt: String?,
    val AccountCode: String? = null,
    val CreditLimit: Double = 0.0,
    val Note: String? = null
)

data class DashboardMetrics(
    val TotalOwedToUs: Double,   // إجمالي لنا (المدين)
    val TotalWeOwe: Double,      // إجمالي علينا (الدائن)
    val TodaySales: Double,
    val TodayPurchases: Double,
    val TodayReceipts: Double,
    val TodayPayments: Double,
    val LowStockCount: Int,
    val ActiveAccountsCount: Int
)

data class ProductStockItem(
    val Id: Long,
    val Barcode: String?,
    val Name: String,
    val CategoryId: Long?,
    val Warehouse: String,
    val ShelfLocation: String? = null,
    val SmallUnit: String,
    val LargeUnit: String?,
    val UnitFactor: Double,
    val CostPrice: Double,
    val RetailPrice: Double,
    val WholesalePrice: Double,
    val AlertThreshold: Double,
    val CurrentStockMinor: Double
)

/**
 * ملخص رصيد الحساب المالي الفعلي (صندوق / محفظة / بنك) المشتق من مصدر حقيقة واحد
 */
data class FinancialAccountBalanceSummary(
    val Id: Long,
    val Name: String,
    val AccountType: String,
    val ParentGroupId: Long?,
    val CurrencySymbol: String,
    val AccountNumberOrPhone: String?,
    val OpeningBalance: Double,
    val AllowNegativeBalance: Boolean,
    val IsDefault: Boolean,
    val IsActive: Boolean,
    val Notes: String?,
    val TotalInflows: Double,
    val TotalOutflows: Double,
    val NetBalance: Double
)

/**
 * ملخص القوائم المالية الشامل:
 * يفصل بوضوح بين:
 * 1. قائمة الدخل المحاسبية (Accrual Income Statement): `TotalSales`, `CostOfGoodsSold`, `GrossProfit`, `TotalExpenses`, `NetProfit`.
 * 2. حركة الصندوق والسيولة النقدية الفعلية (Cashbox Statement): `CashInflows`, `CashOutflows`, `NetCashBalance`.
 * 3. المركز المالي والذمم والمخزون (Balance Sheet): `InventoryValuation`, `TotalDebtsToUs`, `TotalDebtsOnUs`.
 */
data class FinancialReportSummary(
    val TotalSales: Double,
    val CostOfGoodsSold: Double,
    val GrossProfit: Double,
    val TotalExpenses: Double,
    val NetProfit: Double,
    val InventoryValuation: Double,
    val TotalDebtsToUs: Double,
    val TotalDebtsOnUs: Double,
    val CashInflows: Double = 0.0,
    val CashOutflows: Double = 0.0,
    val NetCashBalance: Double = 0.0
)

data class RecentOperationWithPerson(
    val Id: Long,
    val PersonId: Long,
    val PersonName: String?,
    val Type: String,
    val Amount: Double,
    val OccurredAt: String,
    val Note: String?,
    val ReferenceNumber: String?,
    val SourceInvoiceId: Long?
)

@Dao
interface AccountDao {
    @Query("""
        SELECT 
            p.Id AS PersonId,
            p.Name AS PersonName,
            p.Phone AS Phone,
            p.BookId AS BookId,
            COALESCE(SUM(CASE WHEN o.Type IN ('DebtOnUs', 'Sale', 'Payment', 'PurchaseReturn') THEN o.Amount ELSE 0.0 END), 0.0) AS TotalDebit,
            COALESCE(SUM(CASE WHEN o.Type IN ('DebtToUs', 'Purchase', 'Receive', 'SaleReturn') THEN o.Amount ELSE 0.0 END), 0.0) AS TotalCredit,
            COALESCE(SUM(CASE 
                WHEN o.Type IN ('DebtOnUs', 'Sale', 'Payment', 'PurchaseReturn') THEN o.Amount 
                WHEN o.Type IN ('DebtToUs', 'Purchase', 'Receive', 'SaleReturn') THEN -o.Amount 
                ELSE 0.0 
            END), 0.0) AS NetBalance,
            p.LastOperationAt AS LastOperationAt,
            p.AccountCode AS AccountCode,
            p.CreditLimit AS CreditLimit,
            p.Note AS Note
        FROM People p
        LEFT JOIN Operations o ON p.Id = o.PersonId
        WHERE p.IsActive = 1 AND (:bookId IS NULL OR p.BookId = :bookId)
        GROUP BY p.Id
        ORDER BY p.LastOperationAt DESC, p.Name ASC
    """)
    fun getAccountSummaries(bookId: Long? = null): Flow<List<AccountBalanceSummary>>

    @Query("""
        SELECT 
            p.Id AS PersonId,
            p.Name AS PersonName,
            p.Phone AS Phone,
            p.BookId AS BookId,
            COALESCE(SUM(CASE WHEN o.Type IN ('DebtOnUs', 'Sale', 'Payment', 'PurchaseReturn') THEN o.Amount ELSE 0.0 END), 0.0) AS TotalDebit,
            COALESCE(SUM(CASE WHEN o.Type IN ('DebtToUs', 'Purchase', 'Receive', 'SaleReturn') THEN o.Amount ELSE 0.0 END), 0.0) AS TotalCredit,
            COALESCE(SUM(CASE 
                WHEN o.Type IN ('DebtOnUs', 'Sale', 'Payment', 'PurchaseReturn') THEN o.Amount 
                WHEN o.Type IN ('DebtToUs', 'Purchase', 'Receive', 'SaleReturn') THEN -o.Amount 
                ELSE 0.0 
            END), 0.0) AS NetBalance,
            p.LastOperationAt AS LastOperationAt,
            p.AccountCode AS AccountCode,
            p.CreditLimit AS CreditLimit,
            p.Note AS Note
        FROM People p
        LEFT JOIN Operations o ON p.Id = o.PersonId
        WHERE p.IsActive = 1 
          AND (:bookId IS NULL OR p.BookId = :bookId)
          AND (p.Name LIKE '%' || :query || '%' OR p.Phone LIKE '%' || :query || '%' OR p.AccountCode LIKE '%' || :query || '%')
        GROUP BY p.Id
        ORDER BY p.Name ASC
    """)
    fun searchAccounts(query: String, bookId: Long? = null): Flow<List<AccountBalanceSummary>>

    @Query("SELECT * FROM People WHERE Id = :id LIMIT 1")
    suspend fun getPersonById(id: Long): PersonEntity?

    @Query("SELECT COUNT(*) FROM People WHERE IsActive = 1")
    suspend fun getActiveAccountsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Query("UPDATE People SET IsActive = 0 WHERE Id = :id")
    suspend fun softDeletePerson(id: Long)

    @Query("UPDATE People SET IsActive = 1 WHERE Id = :id")
    suspend fun restorePerson(id: Long)

    @Query("UPDATE People SET LastOperationAt = :datetime WHERE Id = :id")
    suspend fun updateLastOperationAt(id: Long, datetime: String)
}

@Dao
interface BookDao {
    @Query("SELECT * FROM Books ORDER BY SortOrder ASC, Id ASC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM Books ORDER BY SortOrder ASC, Id ASC")
    suspend fun getAllBooksOnce(): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("DELETE FROM Books WHERE Id = :id AND IsSystem = 0")
    suspend fun deleteCustomBook(id: Long)
}

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
interface OperationDao {
    @Query("SELECT * FROM Operations WHERE PersonId = :personId ORDER BY OccurredAt DESC, Id DESC")
    fun getOperationsForPerson(personId: Long): Flow<List<OperationEntity>>

    @Query("""
        SELECT 
            o.Id, o.PersonId, p.Name AS PersonName, o.Type, o.Amount, 
            o.OccurredAt, o.Note, o.ReferenceNumber, o.SourceInvoiceId
        FROM Operations o
        LEFT JOIN People p ON o.PersonId = p.Id
        ORDER BY o.OccurredAt DESC, o.Id DESC
        LIMIT :limit
    """)
    fun observeRecentOperationsWithPerson(limit: Int = 25): Flow<List<RecentOperationWithPerson>>

    @Query("SELECT * FROM Operations ORDER BY OccurredAt DESC, Id DESC LIMIT :limit OFFSET :offset")
    suspend fun getOperationsPaged(limit: Int, offset: Int): List<OperationEntity>

    @Query("SELECT COUNT(*) FROM Operations")
    suspend fun getOperationsCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperation(op: OperationEntity): Long

    @Delete
    suspend fun deleteOperation(op: OperationEntity)

    @Query("DELETE FROM Operations WHERE Id = :id")
    suspend fun deleteOperationById(id: Long)

    @Query("SELECT * FROM Operations WHERE Id = :id LIMIT 1")
    suspend fun getOperationById(id: Long): OperationEntity?

    @Query("SELECT COUNT(*) FROM Operations WHERE ReferenceNumber = :refNumber")
    suspend fun countByReferenceNumber(refNumber: String): Int
}

@Dao
interface InvoiceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(lines: List<InvoiceLineEntity>): List<Long>

    @Query("SELECT * FROM Invoices ORDER BY IssuedAt DESC, Id DESC LIMIT :limit OFFSET :offset")
    fun getRecentInvoices(limit: Int = 100, offset: Int = 0): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM Invoices WHERE PersonId = :personId AND Status = 'Completed' AND RemainingAmount > 0.001 ORDER BY IssuedAt ASC, Id ASC")
    fun getOpenInvoicesForPerson(personId: Long): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM Invoices WHERE Id = :id LIMIT 1")
    suspend fun getInvoiceById(id: Long): InvoiceEntity?

    @Query("SELECT * FROM InvoiceLines WHERE InvoiceId = :invoiceId ORDER BY SortOrder ASC")
    suspend fun getLinesForInvoice(invoiceId: Long): List<InvoiceLineEntity>

    @Query("SELECT COUNT(*) FROM Invoices")
    suspend fun getInvoiceCount(): Long

    @Query("SELECT COALESCE(MAX(Id), 0) FROM Invoices")
    suspend fun getMaxInvoiceId(): Long

    @Query("SELECT InvoiceNumber FROM Invoices WHERE InvoiceNumber LIKE :prefixPattern ORDER BY Id DESC LIMIT 1")
    suspend fun getLastInvoiceNumberForPrefix(prefixPattern: String): String?

    @Query("SELECT COUNT(*) FROM Invoices WHERE InvoiceNumber = :invoiceNumber")
    suspend fun countByInvoiceNumber(invoiceNumber: String): Int

    @Query("UPDATE Invoices SET Status = :status WHERE Id = :invoiceId")
    suspend fun updateInvoiceStatus(invoiceId: Long, status: String)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 ORDER BY ExpenseDate DESC, Id DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 AND date(ExpenseDate) BETWEEN date(:from) AND date(:to) ORDER BY ExpenseDate DESC")
    fun getExpensesBetweenDates(from: String, to: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM Expenses WHERE Id = :id LIMIT 1")
    suspend fun getExpenseById(id: Long): ExpenseEntity?

    @Query("SELECT COUNT(*) FROM Expenses WHERE CategoryId = :categoryId")
    suspend fun countExpensesByCategory(categoryId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("UPDATE Expenses SET IsArchived = 1 WHERE Id = :id")
    suspend fun archiveExpense(id: Long)

    @Query("UPDATE Expenses SET IsArchived = 0 WHERE Id = :id")
    suspend fun restoreArchivedExpense(id: Long)

    @Query("DELETE FROM Expenses WHERE Id = :id")
    suspend fun deleteExpense(id: Long)
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
interface FinancialAccountDao {
    @Query("""
        SELECT 
            fa.Id, fa.Name, fa.AccountType, fa.ParentGroupId, fa.CurrencySymbol,
            fa.AccountNumberOrPhone, fa.OpeningBalance, fa.AllowNegativeBalance,
            fa.IsDefault, fa.IsActive, fa.Notes,
            (
                COALESCE((SELECT SUM(PaidAmount) FROM Invoices i WHERE i.FinancialAccountId = fa.Id AND i.Type = 'Sale' AND i.Status = 'Completed'), 0.0) +
                COALESCE((SELECT SUM(CASE WHEN COALESCE(o.ReferenceNumber, '') LIKE 'REV-OP-%' THEN -o.Amount ELSE o.Amount END) FROM Operations o WHERE o.FinancialAccountId = fa.Id AND ((o.Type = 'Receive' AND COALESCE(o.ReferenceNumber, '') NOT LIKE 'REV-OP-%') OR (o.Type = 'Payment' AND COALESCE(o.ReferenceNumber, '') LIKE 'REV-OP-%'))), 0.0) +
                COALESCE((SELECT SUM(Amount) FROM Expenses e WHERE e.FinancialAccountId = fa.Id AND COALESCE(e.IsArchived, 0) = 0 AND e.EntryType = 'REVENUE'), 0.0) +
                COALESCE((SELECT SUM(Amount) FROM FinancialTransfers ft WHERE ft.ToAccountId = fa.Id AND ft.Status = 'Completed'), 0.0)
            ) AS TotalInflows,
            (
                COALESCE((SELECT SUM(PaidAmount) FROM Invoices i WHERE i.FinancialAccountId = fa.Id AND i.Type = 'Purchase' AND i.Status = 'Completed'), 0.0) +
                COALESCE((SELECT SUM(CASE WHEN COALESCE(o.ReferenceNumber, '') LIKE 'REV-OP-%' THEN -o.Amount ELSE o.Amount END) FROM Operations o WHERE o.FinancialAccountId = fa.Id AND ((o.Type = 'Payment' AND COALESCE(o.ReferenceNumber, '') NOT LIKE 'REV-OP-%') OR (o.Type = 'Receive' AND COALESCE(o.ReferenceNumber, '') LIKE 'REV-OP-%'))), 0.0) +
                COALESCE((SELECT SUM(Amount) FROM Expenses e WHERE e.FinancialAccountId = fa.Id AND COALESCE(e.IsArchived, 0) = 0 AND COALESCE(e.EntryType, 'EXPENSE') = 'EXPENSE'), 0.0) +
                COALESCE((SELECT SUM(Amount + FeeAmount) FROM FinancialTransfers ft WHERE ft.FromAccountId = fa.Id AND ft.Status = 'Completed'), 0.0)
            ) AS TotalOutflows,
            (
                fa.OpeningBalance +
                COALESCE((SELECT SUM(PaidAmount) FROM Invoices i WHERE i.FinancialAccountId = fa.Id AND i.Type = 'Sale' AND i.Status = 'Completed'), 0.0) +
                COALESCE((SELECT SUM(CASE WHEN COALESCE(o.ReferenceNumber, '') LIKE 'REV-OP-%' THEN -o.Amount ELSE o.Amount END) FROM Operations o WHERE o.FinancialAccountId = fa.Id AND ((o.Type = 'Receive' AND COALESCE(o.ReferenceNumber, '') NOT LIKE 'REV-OP-%') OR (o.Type = 'Payment' AND COALESCE(o.ReferenceNumber, '') LIKE 'REV-OP-%'))), 0.0) +
                COALESCE((SELECT SUM(Amount) FROM Expenses e WHERE e.FinancialAccountId = fa.Id AND COALESCE(e.IsArchived, 0) = 0 AND e.EntryType = 'REVENUE'), 0.0) +
                COALESCE((SELECT SUM(Amount) FROM FinancialTransfers ft WHERE ft.ToAccountId = fa.Id AND ft.Status = 'Completed'), 0.0) -
                COALESCE((SELECT SUM(PaidAmount) FROM Invoices i WHERE i.FinancialAccountId = fa.Id AND i.Type = 'Purchase' AND i.Status = 'Completed'), 0.0) -
                COALESCE((SELECT SUM(CASE WHEN COALESCE(o.ReferenceNumber, '') LIKE 'REV-OP-%' THEN -o.Amount ELSE o.Amount END) FROM Operations o WHERE o.FinancialAccountId = fa.Id AND ((o.Type = 'Payment' AND COALESCE(o.ReferenceNumber, '') NOT LIKE 'REV-OP-%') OR (o.Type = 'Receive' AND COALESCE(o.ReferenceNumber, '') LIKE 'REV-OP-%'))), 0.0) -
                COALESCE((SELECT SUM(Amount) FROM Expenses e WHERE e.FinancialAccountId = fa.Id AND COALESCE(e.IsArchived, 0) = 0 AND COALESCE(e.EntryType, 'EXPENSE') = 'EXPENSE'), 0.0) -
                COALESCE((SELECT SUM(Amount + FeeAmount) FROM FinancialTransfers ft WHERE ft.FromAccountId = fa.Id AND ft.Status = 'Completed'), 0.0)
            ) AS NetBalance
        FROM FinancialAccounts fa
        WHERE (:onlyActive = 0 OR fa.IsActive = 1)
        ORDER BY fa.IsDefault DESC, fa.Id ASC
    """)
    fun observeAccountBalanceSummaries(onlyActive: Int = 0): Flow<List<FinancialAccountBalanceSummary>>

    @Query("SELECT * FROM FinancialAccounts WHERE Id = :id LIMIT 1")
    suspend fun getFinancialAccountById(id: Long): FinancialAccountEntity?

    @Query("SELECT * FROM FinancialAccounts ORDER BY IsDefault DESC, Id ASC")
    suspend fun getAllFinancialAccountsOnce(): List<FinancialAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinancialAccount(account: FinancialAccountEntity): Long

    @Update
    suspend fun updateFinancialAccount(account: FinancialAccountEntity)

    @Query("UPDATE FinancialAccounts SET IsActive = :isActive WHERE Id = :id")
    suspend fun setFinancialAccountActive(id: Long, isActive: Boolean)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransfer(transfer: FinancialTransferEntity): Long

    @Query("SELECT * FROM FinancialTransfers ORDER BY OccurredAt DESC, Id DESC LIMIT :limit")
    fun observeRecentTransfers(limit: Int = 50): Flow<List<FinancialTransferEntity>>
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

@Dao
interface DeletedItemDao {
    @Query("SELECT * FROM DeletedItems ORDER BY DeletedAt DESC, Id DESC")
    fun getAllDeletedItems(): Flow<List<DeletedItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedItem(item: DeletedItemEntity): Long

    @Query("DELETE FROM DeletedItems WHERE Id = :id")
    suspend fun deletePermanently(id: Long)

    @Query("DELETE FROM DeletedItems WHERE EntityType = :entityType AND OriginalId = :originalId")
    suspend fun deleteByEntityAndOriginalId(entityType: String, originalId: Long)

    @Query("SELECT * FROM DeletedItems WHERE Id = :id LIMIT 1")
    suspend fun getDeletedItemById(id: Long): DeletedItemEntity?
}

@Dao
interface BusinessIdentityDao {
    @Query("SELECT * FROM BusinessIdentity WHERE Id = 1 LIMIT 1")
    fun getIdentity(): Flow<BusinessIdentityEntity?>

    @Query("SELECT * FROM BusinessIdentity WHERE Id = 1 LIMIT 1")
    suspend fun getIdentityOnce(): BusinessIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveIdentity(identity: BusinessIdentityEntity)
}

@Dao
interface AppPreferenceDao {
    @Query("SELECT * FROM AppPreferences")
    fun getAllPreferences(): Flow<List<AppPreferenceEntity>>

    @Query("SELECT * FROM AppPreferences")
    suspend fun getAllPreferencesOnce(): List<AppPreferenceEntity>

    @Query("SELECT Value FROM AppPreferences WHERE Key = :key LIMIT 1")
    suspend fun getPreference(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(pref: AppPreferenceEntity)

    @Query("DELETE FROM AppPreferences")
    suspend fun clearAllPreferences()
}

@Dao
interface DashboardDao {
    @Query("""
        SELECT 
            COALESCE(SUM(CASE WHEN NetBalance > 0 THEN NetBalance ELSE 0 END), 0.0) AS TotalOwedToUs,
            COALESCE(SUM(CASE WHEN NetBalance < 0 THEN ABS(NetBalance) ELSE 0 END), 0.0) AS TotalWeOwe,
            (SELECT COALESCE(SUM(FinalTotal), 0.0) FROM Invoices WHERE Type = 'Sale' AND date(IssuedAt) = date('now', 'localtime') AND Status = 'Completed') AS TodaySales,
            (SELECT COALESCE(SUM(FinalTotal), 0.0) FROM Invoices WHERE Type = 'Purchase' AND date(IssuedAt) = date('now', 'localtime') AND Status = 'Completed') AS TodayPurchases,
            (
                SELECT COALESCE(SUM(CASE WHEN COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%' THEN -Amount ELSE Amount END), 0.0)
                FROM Operations
                WHERE date(OccurredAt) = date('now', 'localtime')
                  AND ((Type = 'Receive' AND COALESCE(ReferenceNumber, '') NOT LIKE 'REV-OP-%')
                    OR (Type = 'Payment' AND COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%'))
            ) AS TodayReceipts,
            (
                SELECT COALESCE(SUM(CASE WHEN COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%' THEN -Amount ELSE Amount END), 0.0)
                FROM Operations
                WHERE date(OccurredAt) = date('now', 'localtime')
                  AND ((Type = 'Payment' AND COALESCE(ReferenceNumber, '') NOT LIKE 'REV-OP-%')
                    OR (Type = 'Receive' AND COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%'))
            ) AS TodayPayments,
            (SELECT COUNT(*) FROM Products p WHERE p.IsActive = 1 AND (
                SELECT COALESCE(SUM(CASE 
                    WHEN sm.Type IN ('In', 'Purchase', 'ReturnIn', 'AdjustmentIn', 'TransferIn') THEN sm.QuantityMinor 
                    WHEN sm.Type IN ('Out', 'Sale', 'ReturnOut', 'AdjustmentOut', 'TransferOut') THEN -sm.QuantityMinor 
                    ELSE 0.0 
                END), 0.0) FROM StockMovements sm WHERE sm.ProductId = p.Id
            ) <= p.AlertThreshold) AS LowStockCount,
            (SELECT COUNT(*) FROM People WHERE IsActive = 1) AS ActiveAccountsCount
        FROM (
            SELECT 
                p.Id,
                COALESCE(SUM(CASE 
                    WHEN o.Type IN ('DebtOnUs', 'Sale', 'Payment', 'PurchaseReturn') THEN o.Amount 
                    WHEN o.Type IN ('DebtToUs', 'Purchase', 'Receive', 'SaleReturn') THEN -o.Amount 
                    ELSE 0.0 
                END), 0.0) AS NetBalance
            FROM People p
            LEFT JOIN Operations o ON p.Id = o.PersonId
            WHERE p.IsActive = 1
            GROUP BY p.Id
        )
    """)
    fun getDashboardMetrics(): Flow<DashboardMetrics>
}

@Dao
interface ReportDao {
    @Query("""
        SELECT 
            (SELECT COALESCE(SUM(FinalTotal), 0.0) FROM Invoices WHERE Type = 'Sale' AND Status = 'Completed') AS TotalSales,
            (SELECT COALESCE(SUM(il.QuantityMinor * il.LineCost), 0.0) FROM InvoiceLines il INNER JOIN Invoices i ON il.InvoiceId = i.Id WHERE i.Type = 'Sale' AND i.Status = 'Completed') AS CostOfGoodsSold,
            (
                (SELECT COALESCE(SUM(FinalTotal), 0.0) FROM Invoices WHERE Type = 'Sale' AND Status = 'Completed') - 
                (SELECT COALESCE(SUM(il.QuantityMinor * il.LineCost), 0.0) FROM InvoiceLines il INNER JOIN Invoices i ON il.InvoiceId = i.Id WHERE i.Type = 'Sale' AND i.Status = 'Completed')
            ) AS GrossProfit,
            (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 AND COALESCE(EntryType, 'EXPENSE') = 'EXPENSE') AS TotalExpenses,
            (
                (SELECT COALESCE(SUM(FinalTotal), 0.0) FROM Invoices WHERE Type = 'Sale' AND Status = 'Completed') +
                (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 AND EntryType = 'REVENUE') -
                (SELECT COALESCE(SUM(il.QuantityMinor * il.LineCost), 0.0) FROM InvoiceLines il INNER JOIN Invoices i ON il.InvoiceId = i.Id WHERE i.Type = 'Sale' AND i.Status = 'Completed') - 
                (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 AND COALESCE(EntryType, 'EXPENSE') = 'EXPENSE')
            ) AS NetProfit,
            (
                SELECT COALESCE(SUM(
                    (SELECT COALESCE(SUM(CASE WHEN sm.Type IN ('In', 'Purchase', 'ReturnIn', 'AdjustmentIn', 'TransferIn') THEN sm.QuantityMinor WHEN sm.Type IN ('Out', 'Sale', 'ReturnOut', 'AdjustmentOut', 'TransferOut') THEN -sm.QuantityMinor ELSE 0.0 END), 0.0) FROM StockMovements sm WHERE sm.ProductId = p.Id) * p.CostPrice
                ), 0.0) FROM Products p WHERE p.IsActive = 1
            ) AS InventoryValuation,
            (
                SELECT COALESCE(SUM(CASE WHEN NetBalance > 0 THEN NetBalance ELSE 0 END), 0.0) FROM (
                    SELECT p.Id, COALESCE(SUM(CASE WHEN o.Type IN ('DebtOnUs', 'Sale', 'Payment', 'PurchaseReturn') THEN o.Amount WHEN o.Type IN ('DebtToUs', 'Purchase', 'Receive', 'SaleReturn') THEN -o.Amount ELSE 0.0 END), 0.0) AS NetBalance
                    FROM People p LEFT JOIN Operations o ON p.Id = o.PersonId WHERE p.IsActive = 1 GROUP BY p.Id
                )
            ) AS TotalDebtsToUs,
            (
                SELECT COALESCE(SUM(CASE WHEN NetBalance < 0 THEN ABS(NetBalance) ELSE 0 END), 0.0) FROM (
                    SELECT p.Id, COALESCE(SUM(CASE WHEN o.Type IN ('DebtOnUs', 'Sale', 'Payment', 'PurchaseReturn') THEN o.Amount WHEN o.Type IN ('DebtToUs', 'Purchase', 'Receive', 'SaleReturn') THEN -o.Amount ELSE 0.0 END), 0.0) AS NetBalance
                    FROM People p LEFT JOIN Operations o ON p.Id = o.PersonId WHERE p.IsActive = 1 GROUP BY p.Id
                )
            ) AS TotalDebtsOnUs,
            (
                (SELECT COALESCE(SUM(PaidAmount), 0.0) FROM Invoices WHERE Type = 'Sale' AND Status = 'Completed') +
                (SELECT COALESCE(SUM(CASE WHEN COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%' THEN -Amount ELSE Amount END), 0.0) FROM Operations WHERE (Type = 'Receive' AND COALESCE(ReferenceNumber, '') NOT LIKE 'REV-OP-%') OR (Type = 'Payment' AND COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%')) +
                (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 AND EntryType = 'REVENUE')
            ) AS CashInflows,
            (
                (SELECT COALESCE(SUM(PaidAmount), 0.0) FROM Invoices WHERE Type = 'Purchase' AND Status = 'Completed') +
                (SELECT COALESCE(SUM(CASE WHEN COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%' THEN -Amount ELSE Amount END), 0.0) FROM Operations WHERE (Type = 'Payment' AND COALESCE(ReferenceNumber, '') NOT LIKE 'REV-OP-%') OR (Type = 'Receive' AND COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%')) +
                (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 AND COALESCE(EntryType, 'EXPENSE') = 'EXPENSE')
            ) AS CashOutflows,
            (
                (
                    (SELECT COALESCE(SUM(PaidAmount), 0.0) FROM Invoices WHERE Type = 'Sale' AND Status = 'Completed') +
                    (SELECT COALESCE(SUM(CASE WHEN COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%' THEN -Amount ELSE Amount END), 0.0) FROM Operations WHERE (Type = 'Receive' AND COALESCE(ReferenceNumber, '') NOT LIKE 'REV-OP-%') OR (Type = 'Payment' AND COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%')) +
                    (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 AND EntryType = 'REVENUE')
                ) -
                (
                    (SELECT COALESCE(SUM(PaidAmount), 0.0) FROM Invoices WHERE Type = 'Purchase' AND Status = 'Completed') +
                    (SELECT COALESCE(SUM(CASE WHEN COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%' THEN -Amount ELSE Amount END), 0.0) FROM Operations WHERE (Type = 'Payment' AND COALESCE(ReferenceNumber, '') NOT LIKE 'REV-OP-%') OR (Type = 'Receive' AND COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%')) +
                    (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 AND COALESCE(EntryType, 'EXPENSE') = 'EXPENSE')
                )
            ) AS NetCashBalance
    """)
    fun getFinancialReportSummary(): Flow<FinancialReportSummary>

    @Query("""
        SELECT 
            (SELECT COALESCE(SUM(FinalTotal), 0.0) FROM Invoices WHERE Type = 'Sale' AND Status = 'Completed' AND date(IssuedAt) >= date(:fromDate)) AS TotalSales,
            (SELECT COALESCE(SUM(il.QuantityMinor * il.LineCost), 0.0) FROM InvoiceLines il INNER JOIN Invoices i ON il.InvoiceId = i.Id WHERE i.Type = 'Sale' AND i.Status = 'Completed' AND date(i.IssuedAt) >= date(:fromDate)) AS CostOfGoodsSold,
            (
                (SELECT COALESCE(SUM(FinalTotal), 0.0) FROM Invoices WHERE Type = 'Sale' AND Status = 'Completed' AND date(IssuedAt) >= date(:fromDate)) - 
                (SELECT COALESCE(SUM(il.QuantityMinor * il.LineCost), 0.0) FROM InvoiceLines il INNER JOIN Invoices i ON il.InvoiceId = i.Id WHERE i.Type = 'Sale' AND i.Status = 'Completed' AND date(i.IssuedAt) >= date(:fromDate))
            ) AS GrossProfit,
            (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 AND COALESCE(EntryType, 'EXPENSE') = 'EXPENSE' AND date(ExpenseDate) >= date(:fromDate)) AS TotalExpenses,
            (
                (SELECT COALESCE(SUM(FinalTotal), 0.0) FROM Invoices WHERE Type = 'Sale' AND Status = 'Completed' AND date(IssuedAt) >= date(:fromDate)) +
                (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 AND EntryType = 'REVENUE' AND date(ExpenseDate) >= date(:fromDate)) -
                (SELECT COALESCE(SUM(il.QuantityMinor * il.LineCost), 0.0) FROM InvoiceLines il INNER JOIN Invoices i ON il.InvoiceId = i.Id WHERE i.Type = 'Sale' AND i.Status = 'Completed' AND date(i.IssuedAt) >= date(:fromDate)) - 
                (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 AND COALESCE(EntryType, 'EXPENSE') = 'EXPENSE' AND date(ExpenseDate) >= date(:fromDate))
            ) AS NetProfit,
            (
                SELECT COALESCE(SUM(
                    (SELECT COALESCE(SUM(CASE WHEN sm.Type IN ('In', 'Purchase', 'ReturnIn', 'AdjustmentIn', 'TransferIn') THEN sm.QuantityMinor WHEN sm.Type IN ('Out', 'Sale', 'ReturnOut', 'AdjustmentOut', 'TransferOut') THEN -sm.QuantityMinor ELSE 0.0 END), 0.0) FROM StockMovements sm WHERE sm.ProductId = p.Id) * p.CostPrice
                ), 0.0) FROM Products p WHERE p.IsActive = 1
            ) AS InventoryValuation,
            (
                SELECT COALESCE(SUM(CASE WHEN NetBalance > 0 THEN NetBalance ELSE 0 END), 0.0) FROM (
                    SELECT p.Id, COALESCE(SUM(CASE WHEN o.Type IN ('DebtOnUs', 'Sale', 'Payment', 'PurchaseReturn') THEN o.Amount WHEN o.Type IN ('DebtToUs', 'Purchase', 'Receive', 'SaleReturn') THEN -o.Amount ELSE 0.0 END), 0.0) AS NetBalance
                    FROM People p LEFT JOIN Operations o ON p.Id = o.PersonId WHERE p.IsActive = 1 GROUP BY p.Id
                )
            ) AS TotalDebtsToUs,
            (
                SELECT COALESCE(SUM(CASE WHEN NetBalance < 0 THEN ABS(NetBalance) ELSE 0 END), 0.0) FROM (
                    SELECT p.Id, COALESCE(SUM(CASE WHEN o.Type IN ('DebtOnUs', 'Sale', 'Payment', 'PurchaseReturn') THEN o.Amount WHEN o.Type IN ('DebtToUs', 'Purchase', 'Receive', 'SaleReturn') THEN -o.Amount ELSE 0.0 END), 0.0) AS NetBalance
                    FROM People p LEFT JOIN Operations o ON p.Id = o.PersonId WHERE p.IsActive = 1 GROUP BY p.Id
                )
            ) AS TotalDebtsOnUs,
            (
                (SELECT COALESCE(SUM(PaidAmount), 0.0) FROM Invoices WHERE Type = 'Sale' AND Status = 'Completed' AND date(IssuedAt) >= date(:fromDate)) +
                (SELECT COALESCE(SUM(CASE WHEN COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%' THEN -Amount ELSE Amount END), 0.0) FROM Operations WHERE date(OccurredAt) >= date(:fromDate) AND ((Type = 'Receive' AND COALESCE(ReferenceNumber, '') NOT LIKE 'REV-OP-%') OR (Type = 'Payment' AND COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%'))) +
                (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 AND EntryType = 'REVENUE' AND date(ExpenseDate) >= date(:fromDate))
            ) AS CashInflows,
            (
                (SELECT COALESCE(SUM(PaidAmount), 0.0) FROM Invoices WHERE Type = 'Purchase' AND Status = 'Completed' AND date(IssuedAt) >= date(:fromDate)) +
                (SELECT COALESCE(SUM(CASE WHEN COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%' THEN -Amount ELSE Amount END), 0.0) FROM Operations WHERE date(OccurredAt) >= date(:fromDate) AND ((Type = 'Payment' AND COALESCE(ReferenceNumber, '') NOT LIKE 'REV-OP-%') OR (Type = 'Receive' AND COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%'))) +
                (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 AND COALESCE(EntryType, 'EXPENSE') = 'EXPENSE' AND date(ExpenseDate) >= date(:fromDate))
            ) AS CashOutflows,
            (
                (
                    (SELECT COALESCE(SUM(PaidAmount), 0.0) FROM Invoices WHERE Type = 'Sale' AND Status = 'Completed' AND date(IssuedAt) >= date(:fromDate)) +
                    (SELECT COALESCE(SUM(CASE WHEN COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%' THEN -Amount ELSE Amount END), 0.0) FROM Operations WHERE date(OccurredAt) >= date(:fromDate) AND ((Type = 'Receive' AND COALESCE(ReferenceNumber, '') NOT LIKE 'REV-OP-%') OR (Type = 'Payment' AND COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%'))) +
                    (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 AND EntryType = 'REVENUE' AND date(ExpenseDate) >= date(:fromDate))
                ) -
                (
                    (SELECT COALESCE(SUM(PaidAmount), 0.0) FROM Invoices WHERE Type = 'Purchase' AND Status = 'Completed' AND date(IssuedAt) >= date(:fromDate)) +
                    (SELECT COALESCE(SUM(CASE WHEN COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%' THEN -Amount ELSE Amount END), 0.0) FROM Operations WHERE date(OccurredAt) >= date(:fromDate) AND ((Type = 'Payment' AND COALESCE(ReferenceNumber, '') NOT LIKE 'REV-OP-%') OR (Type = 'Receive' AND COALESCE(ReferenceNumber, '') LIKE 'REV-OP-%'))) +
                    (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE COALESCE(IsArchived, 0) = 0 AND COALESCE(EntryType, 'EXPENSE') = 'EXPENSE' AND date(ExpenseDate) >= date(:fromDate))
                )
            ) AS NetCashBalance
    """)
    fun getFinancialReportFromDate(fromDate: String): Flow<FinancialReportSummary>
}
