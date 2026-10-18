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

class VouchersViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.getInstance(application)
    private val voucherRepo = container.voucherRepository
    private val accountRepo = container.accountRepository
    private val invoiceRepo = container.invoiceRepository
    private val financialAccountRepo = container.financialAccountRepository

    val accounts: StateFlow<List<AccountBalanceSummary>> = accountRepo.getAccountSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financialAccounts: StateFlow<List<FinancialAccountBalanceSummary>> =
        financialAccountRepo.observeAccountBalances(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentVouchers: StateFlow<List<RecentOperationWithPerson>> = voucherRepo.observeRecentOperations(limit = 30)
        .map { list -> list.filter { it.Type == "Receive" || it.Type == "Payment" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getOpenInvoicesForPerson(personId: Long): Flow<List<InvoiceEntity>> {
        return invoiceRepo.getOpenInvoicesForPerson(personId)
    }

    suspend fun createVoucher(
        personId: Long,
        type: String,
        amount: Double,
        statement: String,
        refNumber: String? = null,
        financialAccountId: Long? = null,
        targetInvoiceId: Long? = null
    ): Result<Long> {
        val defaultAccountId = financialAccountId
            ?: financialAccounts.value.firstOrNull { it.IsDefault }?.Id
            ?: financialAccounts.value.firstOrNull()?.Id
        return voucherRepo.createVoucher(
            personId = personId,
            type = type,
            amount = amount,
            statement = statement,
            refNumber = refNumber,
            financialAccountId = defaultAccountId,
            targetInvoiceId = targetInvoiceId
        )
    }
}
