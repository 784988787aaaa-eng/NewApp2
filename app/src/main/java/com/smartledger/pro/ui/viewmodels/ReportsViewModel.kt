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

class ReportsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.getInstance(application)
    private val reportRepo = container.reportRepository
    private val identityRepo = container.businessIdentityRepository
    private val financialAccountRepo = container.financialAccountRepository

    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityRepo.observeBusinessIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val financialAccounts: StateFlow<List<FinancialAccountBalanceSummary>> =
        financialAccountRepo.observeAccountBalances(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPeriod = MutableStateFlow("ALL")
    val selectedPeriod = _selectedPeriod.asStateFlow()

    val summary: StateFlow<FinancialReportSummary> = _selectedPeriod
        .flatMapLatest { period ->
            val fromDate = calculateFromDate(period)
            if (fromDate == null) {
                reportRepo.getFinancialReportSummary()
            } else {
                reportRepo.getFinancialReportFromDate(fromDate)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            FinancialReportSummary(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        )

    fun setPeriod(period: String) {
        _selectedPeriod.value = period
    }

    private fun calculateFromDate(period: String): String? {
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return when (period) {
            "TODAY" -> fmt.format(cal.time)
            "WEEK" -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                fmt.format(cal.time)
            }
            "MONTH" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                fmt.format(cal.time)
            }
            "YEAR" -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                fmt.format(cal.time)
            }
            else -> null
        }
    }
}

/**
 * نموذج عرض الإعدادات والفهارس المرنة والنسخ الاحتياطي والأمان (`SettingsViewModel`).
 */
