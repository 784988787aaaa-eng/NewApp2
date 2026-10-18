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

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.getInstance(application)
    private val reportRepo = container.reportRepository
    private val voucherRepo = container.voucherRepository
    private val identityRepo = container.businessIdentityRepository
    private val financialAccountRepo = container.financialAccountRepository
    private val warehouseRepo = container.warehouseRepository
    private val prefsRepo = container.preferencesRepository

    val settings: StateFlow<AppSettingsSnapshot> = prefsRepo.settings

    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityRepo.observeBusinessIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val financialAccounts: StateFlow<List<FinancialAccountBalanceSummary>> =
        financialAccountRepo.observeAccountBalances(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val metrics: StateFlow<DashboardMetrics> = reportRepo.getDashboardMetrics()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DashboardMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0)
        )

    val recentOps: StateFlow<List<RecentOperationWithPerson>> = voucherRepo.observeRecentOperations(limit = 15)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun completeOnboarding(
        tradeName: String,
        currencySymbol: String,
        initialCashboxName: String?,
        initialWarehouseName: String?,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val cleanTrade = tradeName.trim().ifBlank { "نشاطي التجاري" }
            val cleanCurrency = currencySymbol.trim().ifBlank { "ر.ي" }
            identityRepo.saveBusinessIdentity(
                BusinessIdentityEntity(
                    Id = 1,
                    TradeName = cleanTrade,
                    LegalName = cleanTrade,
                    Phone1 = "",
                    Address = "",
                    ReceiptHeader = "مرحباً بكم في $cleanTrade",
                    ReceiptFooter = "شكراً لتعاملكم معنا"
                )
            )
            if (!initialCashboxName.isNullOrBlank()) {
                financialAccountRepo.saveFinancialAccount(
                    name = initialCashboxName.trim(),
                    accountType = "CASH_BOX",
                    currencySymbol = cleanCurrency,
                    isDefault = true
                )
            }
            if (!initialWarehouseName.isNullOrBlank()) {
                warehouseRepo.saveWarehouse(
                    name = initialWarehouseName.trim(),
                    isDefault = true
                )
            }
            prefsRepo.updateSettings {
                it.copy(
                    currencySymbol = cleanCurrency,
                    onboardingCompleted = true
                )
            }
            onComplete()
        }
    }

    fun skipOnboarding() {
        prefsRepo.updateSettings { it.copy(onboardingCompleted = true) }
    }
}
