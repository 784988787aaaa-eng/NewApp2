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

class ExpensesViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.getInstance(application)
    private val expenseRepo = container.expenseRepository
    private val categoryRepo = container.categoryRepository
    private val financialAccountRepo = container.financialAccountRepository

    val expenses: StateFlow<List<ExpenseEntity>> = expenseRepo.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        categoryRepo.observeCategories(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financialAccounts: StateFlow<List<FinancialAccountBalanceSummary>> =
        financialAccountRepo.observeAccountBalances(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun saveExpenseAsync(
        amount: Double,
        statement: String,
        category: String,
        source: String,
        photoUri: String? = null,
        entryType: String = "EXPENSE",
        categoryId: Long? = null,
        financialAccountId: Long? = null
    ): Result<Long> {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val cleanCatName = category.trim()
        val cleanDomain = if (entryType == "REVENUE") "REVENUE" else "EXPENSE"

        // إذا أدخل المستخدم اسم تصنيف جديد غير موجود بعد، يُحفظ تلقائياً في جدول التصنيفات المرن
        val resolvedCategoryId = categoryId ?: if (cleanCatName.isNotEmpty()) {
            categories.value.firstOrNull {
                it.Domain.equals(cleanDomain, ignoreCase = true) && it.Name.equals(cleanCatName, ignoreCase = true)
            }?.Id ?: categoryRepo.saveCategory(domain = cleanDomain, name = cleanCatName).getOrNull()
        } else null

        val resolvedAccountId = financialAccountId ?: if (source.isNotBlank()) {
            financialAccounts.value.firstOrNull { it.Name.equals(source.trim(), ignoreCase = true) }?.Id
        } else null

        val fullStatement = if (cleanCatName.isNotBlank() && !statement.startsWith("[$cleanCatName]")) {
            "[$cleanCatName] ${statement.trim()}"
        } else {
            statement.trim()
        }
        val entity = ExpenseEntity(
            ExpenseDate = now,
            CategoryId = resolvedCategoryId,
            Amount = NumberFormatter.roundCurrency(amount),
            Statement = fullStatement,
            PaymentSource = source.trim().ifBlank { "صندوق عام" },
            ReceiptPhotoUri = photoUri,
            CreatedAt = now,
            EntryType = entryType,
            FinancialAccountId = resolvedAccountId,
            IsArchived = 0
        )
        return expenseRepo.saveExpense(entity)
    }

    fun deleteExpense(id: Long, reason: String? = null) {
        viewModelScope.launch {
            expenseRepo.deleteExpense(id, reason)
        }
    }

    fun restoreExpenseSnapshot(expense: ExpenseEntity) {
        viewModelScope.launch {
            expenseRepo.restoreExpenseSnapshot(expense)
        }
    }
}
