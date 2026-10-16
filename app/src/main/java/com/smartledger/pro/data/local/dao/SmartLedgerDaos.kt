package com.smartledger.pro.data.local.dao

import androidx.room.*
import com.smartledger.pro.data.local.entity.*
import kotlinx.coroutines.flow.Flow

data class AccountBalanceSummary(
    val PersonId: Long,
    val PersonName: String,
    val Phone: String?,
    val BookId: Long,
    val AccountCode: String? = null,
    val CreditLimit: Double = 0.0,
    val Note: String? = null,
    val TotalDebit: Double,
    val TotalCredit: Double,
    val NetBalance: Double,
    val LastOperationAt: String?
)

data class DashboardMetrics(
    val TotalOwedToUs: Double,
    val TotalWeOwe: Double,
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
    val Notes: String? = null,
    val CurrentStockMinor: Double
)

data class FinancialReportSummary(
    val TotalSales: Double,
    val CostOfGoodsSold: Double,
    val GrossProfit: Double,
    val TotalExpenses: Double,
    val NetProfit: Double,
    val InventoryValuation: Double,
    val TotalDebtsToUs: Double,
    val TotalDebtsOnUs: Double
)

@Dao
interface AccountDao {
    @Query("""
        SELECT 
            p.Id AS PersonId,
            p.Name AS PersonName,
            p.Phone AS Phone,
            p.BookId AS BookId,
            p.AccountCode AS AccountCode,
            p.CreditLimit AS CreditLimit,
            p.Note AS Note,
            COALESCE(SUM(CASE WHEN o.Type IN ('DebtOnUs', 'Sale', 'Payment', 'PurchaseReturn') THEN o.Amount ELSE 0.0 END), 0.0) AS TotalDebit,
            COALESCE(SUM(CASE WHEN o.Type IN ('DebtToUs', 'Purchase', 'Receive', 'SaleReturn') THEN o.Amount ELSE 0.0 END), 0.0) AS TotalCredit,
            COALESCE(SUM(CASE 
                WHEN o.Type IN ('DebtOnUs', 'Sale', 'Payment', 'PurchaseReturn') THEN o.Amount 
                WHEN o.Type IN ('DebtToUs', 'Purchase', 'Receive', 'SaleReturn') THEN -o.Amount 
                ELSE 0.0 
            END), 0.0) AS NetBalance,
            p.LastOperationAt AS LastOperationAt
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
            p.AccountCode AS AccountCode,
            p.CreditLimit AS CreditLimit,
            p.Note AS Note,
            COALESCE(SUM(CASE WHEN o.Type IN ('DebtOnUs', 'Sale', 'Payment', 'PurchaseReturn') THEN o.Amount ELSE 0.0 END), 0.0) AS TotalDebit,
            COALESCE(SUM(CASE WHEN o.Type IN ('DebtToUs', 'Purchase', 'Receive', 'SaleReturn') THEN o.Amount ELSE 0.0 END), 0.0) AS TotalCredit,
            COALESCE(SUM(CASE 
                WHEN o.Type IN ('DebtOnUs', 'Sale', 'Payment', 'PurchaseReturn') THEN o.Amount 
                WHEN o.Type IN ('DebtToUs', 'Purchase', 'Receive', 'SaleReturn') THEN -o.Amount 
                ELSE 0.0 
            END), 0.0) AS NetBalance,
            p.LastOperationAt AS LastOperationAt
        FROM People p
        LEFT JOIN Operations o ON p.Id = o.PersonId
        WHERE p.IsActive = 1 
          AND (:bookId IS NULL OR p.BookId = :bookId)
          AND (
              p.Name LIKE '%' || :query || '%' 
              OR p.Phone LIKE '%' || :query || '%' 
              OR p.AccountCode LIKE '%' || :query || '%'
              OR p.Note LIKE '%' || :query || '%'
          )
        GROUP BY p.Id
        ORDER BY p.Name ASC
    """)
    fun searchAccounts(query: String, bookId: Long? = null): Flow<List<AccountBalanceSummary>>

    @Query("SELECT * FROM People WHERE Id = :id LIMIT 1")
    suspend fun getPersonById(id: Long): PersonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Query("UPDATE People SET IsActive = 0 WHERE Id = :id")
    suspend fun softDeletePerson(id: Long)

    @Query("UPDATE People SET LastOperationAt = :datetime WHERE Id = :id")
    suspend fun updateLastOperationAt(id: Long, datetime: String)

    @Query("SELECT COUNT(*) FROM People WHERE IsActive = 1")
    suspend fun getActiveAccountsCount(): Int
}

@Dao
interface BookDao {
    @Query("SELECT * FROM Books ORDER BY SortOrder ASC, Id ASC")
    fun getAllBooks(): Flow<List<BookEntity>>

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
            p.UnitFactor, p.CostPrice, p.RetailPrice, p.WholesalePrice, p.AlertThreshold, p.Notes,
            COALESCE(SUM(CASE 
                WHEN sm.Type IN ('In', 'Purchase', 'ReturnIn', 'AdjustmentIn') THEN sm.QuantityMinor 
                WHEN sm.Type IN ('Out', 'Sale', 'ReturnOut', 'AdjustmentOut') THEN -sm.QuantityMinor 
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
            p.UnitFactor, p.CostPrice, p.RetailPrice, p.WholesalePrice, p.AlertThreshold, p.Notes,
            COALESCE(SUM(CASE 
                WHEN sm.Type IN ('In', 'Purchase', 'ReturnIn', 'AdjustmentIn') THEN sm.QuantityMinor 
                WHEN sm.Type IN ('Out', 'Sale', 'ReturnOut', 'AdjustmentOut') THEN -sm.QuantityMinor 
                ELSE 0.0 
            END), 0.0) AS CurrentStockMinor
        FROM Products p
        LEFT JOIN StockMovements sm ON p.Id = sm.ProductId
        WHERE p.IsActive = 1 AND (
            p.Name LIKE '%' || :query || '%' 
            OR p.Barcode LIKE '%' || :query || '%' 
            OR p.ShelfLocation LIKE '%' || :query || '%'
            OR p.Warehouse LIKE '%' || :query || '%'
        )
        GROUP BY p.Id
        ORDER BY p.Name ASC
    """)
    fun searchProducts(query: String): Flow<List<ProductStockItem>>

    @Query("SELECT * FROM Products WHERE Barcode = :barcode AND IsActive = 1 LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM Products WHERE Barcode = :barcode AND Id != :excludeId AND IsActive = 1 LIMIT 1")
    suspend fun findDuplicateBarcode(barcode: String, excludeId: Long): ProductEntity?

    @Query("SELECT * FROM Products WHERE Id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("""
        SELECT COALESCE(SUM(CASE 
            WHEN sm.Type IN ('In', 'Purchase', 'ReturnIn', 'AdjustmentIn') THEN sm.QuantityMinor 
            WHEN sm.Type IN ('Out', 'Sale', 'ReturnOut', 'AdjustmentOut') THEN -sm.QuantityMinor 
            ELSE 0.0 
        END), 0.0)
        FROM StockMovements sm
        WHERE sm.ProductId = :productId
    """)
    suspend fun getProductStock(productId: Long): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE Products SET IsActive = 0 WHERE Id = :id")
    suspend fun softDeleteProduct(id: Long)

    @Query("SELECT COUNT(*) FROM Products WHERE IsActive = 1")
    suspend fun getActiveProductsCount(): Int
}

@Dao
interface StockMovementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovement(movement: StockMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovements(movements: List<StockMovementEntity>): List<Long>

    @Query("SELECT * FROM StockMovements WHERE ProductId = :productId ORDER BY OccurredAt DESC, Id DESC LIMIT 150")
    fun getMovementsForProduct(productId: Long): Flow<List<StockMovementEntity>>
}

@Dao
interface OperationDao {
    @Query("SELECT * FROM Operations WHERE PersonId = :personId ORDER BY OccurredAt DESC, Id DESC")
    fun getOperationsForPerson(personId: Long): Flow<List<OperationEntity>>

    @Query("SELECT * FROM Operations ORDER BY OccurredAt DESC, Id DESC LIMIT :limit")
    fun getRecentOperationsFlow(limit: Int = 25): Flow<List<OperationEntity>>

    @Query("SELECT * FROM Operations WHERE Type IN ('Receive', 'Payment') ORDER BY OccurredAt DESC, Id DESC LIMIT :limit")
    fun getRecentVouchersFlow(limit: Int = 100): Flow<List<OperationEntity>>

    @Query("SELECT * FROM Operations ORDER BY OccurredAt DESC, Id DESC LIMIT :limit OFFSET :offset")
    suspend fun getOperationsPaged(limit: Int, offset: Int): List<OperationEntity>

    @Query("SELECT * FROM Operations WHERE PersonId = :personId AND Type IN ('DebtOnUs', 'DebtToUs') AND ReferenceNumber = 'OPENING' LIMIT 1")
    suspend fun getOpeningOperationForPerson(personId: Long): OperationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperation(op: OperationEntity): Long

    @Update
    suspend fun updateOperation(op: OperationEntity)

    @Delete
    suspend fun deleteOperation(op: OperationEntity)

    @Query("DELETE FROM Operations WHERE Id = :id")
    suspend fun deleteOperationById(id: Long)

    @Query("SELECT * FROM Operations WHERE Id = :id LIMIT 1")
    suspend fun getOperationById(id: Long): OperationEntity?
}

@Dao
interface InvoiceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(lines: List<InvoiceLineEntity>): List<Long>

    @Query("SELECT * FROM Invoices ORDER BY IssuedAt DESC, Id DESC LIMIT :limit OFFSET :offset")
    fun getRecentInvoices(limit: Int = 100, offset: Int = 0): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM Invoices WHERE Id = :id LIMIT 1")
    suspend fun getInvoiceById(id: Long): InvoiceEntity?

    @Query("SELECT * FROM InvoiceLines WHERE InvoiceId = :invoiceId ORDER BY SortOrder ASC")
    suspend fun getLinesForInvoice(invoiceId: Long): List<InvoiceLineEntity>

    @Query("SELECT COUNT(*) FROM Invoices")
    suspend fun getInvoiceCount(): Long

    @Query("UPDATE Invoices SET Status = :status WHERE Id = :invoiceId")
    suspend fun updateInvoiceStatus(invoiceId: Long, status: String)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM Expenses ORDER BY ExpenseDate DESC, Id DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM Expenses WHERE date(ExpenseDate) BETWEEN date(:from) AND date(:to) ORDER BY ExpenseDate DESC")
    fun getExpensesBetweenDates(from: String, to: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM Expenses WHERE Id = :id LIMIT 1")
    suspend fun getExpenseById(id: Long): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("DELETE FROM Expenses WHERE Id = :id")
    suspend fun deleteExpense(id: Long)
}

@Dao
interface DeletedItemDao {
    @Query("SELECT * FROM DeletedItems ORDER BY DeletedAt DESC, Id DESC")
    fun getAllDeletedItems(): Flow<List<DeletedItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedItem(item: DeletedItemEntity): Long

    @Query("DELETE FROM DeletedItems WHERE Id = :id")
    suspend fun deletePermanently(id: Long)

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
            (SELECT COALESCE(SUM(Amount), 0.0) FROM Operations WHERE Type = 'Receive' AND date(OccurredAt) = date('now', 'localtime')) AS TodayReceipts,
            (SELECT COALESCE(SUM(Amount), 0.0) FROM Operations WHERE Type = 'Payment' AND date(OccurredAt) = date('now', 'localtime')) AS TodayPayments,
            (SELECT COUNT(*) FROM Products p WHERE p.IsActive = 1 AND (
                SELECT COALESCE(SUM(CASE 
                    WHEN sm.Type IN ('In', 'Purchase', 'ReturnIn', 'AdjustmentIn') THEN sm.QuantityMinor 
                    WHEN sm.Type IN ('Out', 'Sale', 'ReturnOut', 'AdjustmentOut') THEN -sm.QuantityMinor 
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
            (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses) AS TotalExpenses,
            (
                (SELECT COALESCE(SUM(FinalTotal), 0.0) FROM Invoices WHERE Type = 'Sale' AND Status = 'Completed') - 
                (SELECT COALESCE(SUM(il.QuantityMinor * il.LineCost), 0.0) FROM InvoiceLines il INNER JOIN Invoices i ON il.InvoiceId = i.Id WHERE i.Type = 'Sale' AND i.Status = 'Completed') - 
                (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses)
            ) AS NetProfit,
            (
                SELECT COALESCE(SUM(
                    (SELECT COALESCE(SUM(CASE WHEN sm.Type IN ('In', 'Purchase', 'ReturnIn', 'AdjustmentIn') THEN sm.QuantityMinor WHEN sm.Type IN ('Out', 'Sale', 'ReturnOut', 'AdjustmentOut') THEN -sm.QuantityMinor ELSE 0.0 END), 0.0) FROM StockMovements sm WHERE sm.ProductId = p.Id) * p.CostPrice
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
            ) AS TotalDebtsOnUs
    """)
    fun getFinancialReportSummary(): Flow<FinancialReportSummary>

    @Query("""
        SELECT 
            (SELECT COALESCE(SUM(FinalTotal), 0.0) FROM Invoices WHERE Type = 'Sale' AND Status = 'Completed' AND date(IssuedAt) BETWEEN date(:fromDate) AND date(:toDate)) AS TotalSales,
            (SELECT COALESCE(SUM(il.QuantityMinor * il.LineCost), 0.0) FROM InvoiceLines il INNER JOIN Invoices i ON il.InvoiceId = i.Id WHERE i.Type = 'Sale' AND i.Status = 'Completed' AND date(i.IssuedAt) BETWEEN date(:fromDate) AND date(:toDate)) AS CostOfGoodsSold,
            (
                (SELECT COALESCE(SUM(FinalTotal), 0.0) FROM Invoices WHERE Type = 'Sale' AND Status = 'Completed' AND date(IssuedAt) BETWEEN date(:fromDate) AND date(:toDate)) - 
                (SELECT COALESCE(SUM(il.QuantityMinor * il.LineCost), 0.0) FROM InvoiceLines il INNER JOIN Invoices i ON il.InvoiceId = i.Id WHERE i.Type = 'Sale' AND i.Status = 'Completed' AND date(i.IssuedAt) BETWEEN date(:fromDate) AND date(:toDate))
            ) AS GrossProfit,
            (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE date(ExpenseDate) BETWEEN date(:fromDate) AND date(:toDate)) AS TotalExpenses,
            (
                (SELECT COALESCE(SUM(FinalTotal), 0.0) FROM Invoices WHERE Type = 'Sale' AND Status = 'Completed' AND date(IssuedAt) BETWEEN date(:fromDate) AND date(:toDate)) - 
                (SELECT COALESCE(SUM(il.QuantityMinor * il.LineCost), 0.0) FROM InvoiceLines il INNER JOIN Invoices i ON il.InvoiceId = i.Id WHERE i.Type = 'Sale' AND i.Status = 'Completed' AND date(i.IssuedAt) BETWEEN date(:fromDate) AND date(:toDate)) - 
                (SELECT COALESCE(SUM(Amount), 0.0) FROM Expenses WHERE date(ExpenseDate) BETWEEN date(:fromDate) AND date(:toDate))
            ) AS NetProfit,
            (
                SELECT COALESCE(SUM(
                    (SELECT COALESCE(SUM(CASE WHEN sm.Type IN ('In', 'Purchase', 'ReturnIn', 'AdjustmentIn') THEN sm.QuantityMinor WHEN sm.Type IN ('Out', 'Sale', 'ReturnOut', 'AdjustmentOut') THEN -sm.QuantityMinor ELSE 0.0 END), 0.0) FROM StockMovements sm WHERE sm.ProductId = p.Id) * p.CostPrice
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
            ) AS TotalDebtsOnUs
    """)
    fun getFinancialReportSummaryByPeriod(fromDate: String, toDate: String): Flow<FinancialReportSummary>
}
