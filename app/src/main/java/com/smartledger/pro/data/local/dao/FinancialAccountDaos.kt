package com.smartledger.pro.data.local.dao

import androidx.room.*
import com.smartledger.pro.data.local.entity.*
import kotlinx.coroutines.flow.Flow

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
