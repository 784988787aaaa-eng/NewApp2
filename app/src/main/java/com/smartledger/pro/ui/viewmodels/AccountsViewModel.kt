package com.smartledger.pro.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.smartledger.pro.data.local.dao.*
import com.smartledger.pro.data.local.entity.*
import com.smartledger.pro.data.repository.AppSettingsSnapshot
import com.smartledger.pro.di.AppContainer
import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.domain.usecase.SaleLineDraft
import com.smartledger.pro.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AccountsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.getInstance(application)
    private val accountRepo = container.accountRepository
    private val voucherRepo = container.voucherRepository
    private val invoiceRepo = container.invoiceRepository
    private val financialAccountRepo = container.financialAccountRepository
    private val identityRepo = container.businessIdentityRepository
    private val prefsRepo = container.preferencesRepository

    val settings: StateFlow<AppSettingsSnapshot> = prefsRepo.settings
    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityRepo.observeBusinessIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val financialAccounts: StateFlow<List<FinancialAccountBalanceSummary>> =
        financialAccountRepo.observeAccountBalances(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedBookId = MutableStateFlow<Long?>(null)
    val selectedBookId = _selectedBookId.asStateFlow()

    private val _balanceFilter = MutableStateFlow("ALL")
    val balanceFilter = _balanceFilter.asStateFlow()

    val books: StateFlow<List<BookEntity>> = accountRepo.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<AccountBalanceSummary>> = combine(
        _searchQuery,
        _selectedBookId,
        _balanceFilter
    ) { query, bookId, balFilter ->
        Triple(query, bookId, balFilter)
    }.flatMapLatest { (query, bookId, balFilter) ->
        val baseFlow = if (query.isBlank()) {
            accountRepo.getAccountSummaries(bookId)
        } else {
            accountRepo.searchAccounts(query.trim(), bookId)
        }
        baseFlow.map { list ->
            when (balFilter) {
                "DEBIT" -> list.filter { it.NetBalance > 0.001 }
                "CREDIT" -> list.filter { it.NetBalance < -0.001 }
                else -> list
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
    }

    fun onBookSelected(bookId: Long?) {
        _selectedBookId.value = if (bookId == null || bookId <= 0) null else bookId
    }

    fun onBalanceFilterChanged(filter: String) {
        _balanceFilter.value = filter
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedBookId.value = null
        _balanceFilter.value = "ALL"
    }

    suspend fun saveBookAsync(name: String, id: Long = 0L): Result<Long> {
        return accountRepo.saveBook(name, id)
    }

    suspend fun saveAccountAsync(
        id: Long = 0,
        name: String,
        bookId: Long,
        phone: String?,
        creditLimit: Double,
        openingBalance: Double,
        isDebit: Boolean,
        note: String?
    ): Result<Long> {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val person = PersonEntity(
            Id = id,
            BookId = bookId,
            Name = name.trim(),
            Phone = phone?.trim()?.ifBlank { null },
            AccountCode = if (id > 0) null else "ACC-" + (System.currentTimeMillis() % 100000),
            CreditLimit = NumberFormatter.roundCurrency(creditLimit.coerceAtLeast(0.0)),
            Note = note?.trim()?.ifBlank { null },
            CreatedAt = now
        )
        return accountRepo.saveAccount(person, openingBalance, isDebit)
    }

    fun deleteAccount(id: Long, reason: String? = null) {
        viewModelScope.launch {
            accountRepo.softDeleteAccount(id, reason)
        }
    }

    fun restoreAccount(id: Long, onResult: ((Result<Boolean>) -> Unit)? = null) {
        viewModelScope.launch {
            val res = accountRepo.restoreAccount(id)
            onResult?.invoke(res)
        }
    }

    fun getOperationsForAccount(personId: Long): Flow<List<OperationEntity>> {
        return accountRepo.getAccountOperations(personId)
    }

    fun getOpenInvoicesForAccount(personId: Long): Flow<List<InvoiceEntity>> {
        return invoiceRepo.getOpenInvoicesForPerson(personId)
    }

    fun deleteOperation(
        operationId: Long,
        reason: String? = null,
        onResult: ((Result<Long>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val res = accountRepo.deleteOperation(operationId, reason)
            onResult?.invoke(res)
        }
    }

    suspend fun createQuickVoucherAsync(
        personId: Long,
        type: String,
        amount: Double,
        statement: String,
        refNumber: String? = null,
        financialAccountId: Long? = null,
        targetInvoiceId: Long? = null
    ): Result<Long> {
        return voucherRepo.createVoucher(
            personId = personId,
            type = type,
            amount = amount,
            statement = statement,
            refNumber = refNumber,
            financialAccountId = financialAccountId,
            targetInvoiceId = targetInvoiceId
        )
    }
}
