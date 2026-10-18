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

class PurchasesViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.getInstance(application)
    private val invoiceRepo = container.invoiceRepository
    private val accountRepo = container.accountRepository
    private val inventoryRepo = container.inventoryRepository
    private val financialAccountRepo = container.financialAccountRepository
    private val createPurchaseUseCase = container.createPurchaseUseCase
    private val prefsRepo = container.preferencesRepository

    val suppliers: StateFlow<List<AccountBalanceSummary>> = accountRepo.getAccountSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductStockItem>> = inventoryRepo.getProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financialAccounts: StateFlow<List<FinancialAccountBalanceSummary>> =
        financialAccountRepo.observeAccountBalances(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentPurchases: StateFlow<List<InvoiceEntity>> = invoiceRepo.getRecentInvoices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun createPurchase(
        supplier: AccountBalanceSummary?,
        product: ProductStockItem,
        useMajorUnit: Boolean,
        quantity: Double,
        unitPurchaseCost: Double,
        paidCash: Double,
        notes: String?,
        financialAccountId: Long? = null
    ): Result<Long> {
        val defaultAccountId = financialAccountId
            ?: financialAccounts.value.firstOrNull { it.IsDefault }?.Id
            ?: financialAccounts.value.firstOrNull()?.Id
        return createPurchaseUseCase.execute(
            supplier = supplier,
            product = product,
            useMajorUnit = useMajorUnit,
            quantity = quantity,
            unitPurchaseCost = unitPurchaseCost,
            paidCash = paidCash,
            notes = notes,
            purchasePrefix = prefsRepo.settings.value.purchasePrefix,
            financialAccountId = defaultAccountId
        )
    }

    suspend fun reversePurchaseInvoice(invoiceId: Long, reason: String?): Result<Boolean> {
        return invoiceRepo.reverseInvoice(invoiceId, reason)
    }
}
