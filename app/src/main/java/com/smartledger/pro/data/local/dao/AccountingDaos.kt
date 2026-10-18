package com.smartledger.pro.data.local.dao

import androidx.room.*
import com.smartledger.pro.data.local.entity.*
import kotlinx.coroutines.flow.Flow

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
