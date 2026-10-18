package com.smartledger.pro.data.local.dao

import androidx.room.*
import com.smartledger.pro.data.local.entity.*
import kotlinx.coroutines.flow.Flow

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
