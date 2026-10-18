package com.smartledger.pro.ui.screens.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.dao.FinancialAccountBalanceSummary
import com.smartledger.pro.data.local.entity.CategoryEntity
import com.smartledger.pro.data.local.entity.ExpenseEntity
import com.smartledger.pro.domain.usecase.HierarchicalCategoryValidator
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.viewmodels.ExpensesViewModel
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.launch

@Composable
fun AddExpenseDialog(
    categories: List<CategoryEntity> = emptyList(),
    financialAccounts: List<FinancialAccountBalanceSummary> = emptyList(),
    isSaving: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onSave: (
        entryType: String,
        amount: Double,
        statement: String,
        category: String,
        source: String,
        categoryId: Long?,
        financialAccountId: Long?
    ) -> Unit
) {
    var entryType by rememberSaveable { mutableStateOf("EXPENSE") } // EXPENSE or REVENUE
    var amountText by rememberSaveable { mutableStateOf("") }
    var statement by rememberSaveable { mutableStateOf("") }
    var categoryText by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var sourceText by rememberSaveable {
        mutableStateOf(financialAccounts.firstOrNull { it.IsDefault }?.Name ?: financialAccounts.firstOrNull()?.Name ?: "")
    }
    var selectedAccountId by rememberSaveable {
        mutableStateOf(financialAccounts.firstOrNull { it.IsDefault }?.Id ?: financialAccounts.firstOrNull()?.Id)
    }

    val domainCategories = remember(categories, entryType) {
        categories.filter { it.Domain.equals(entryType, ignoreCase = true) && it.IsActive }
    }
    val categorySuggestions = remember(domainCategories) {
        domainCategories.map { HierarchicalCategoryValidator.formatCategoryPath(it.Id, domainCategories) }
    }
    val sourceSuggestions = remember(financialAccounts) {
        financialAccounts.map { it.Name }
    }

    val focusStatement = remember { FocusRequester() }
    val amount = NumberFormatter.parseDouble(amountText, 0.0)
    val isFormValid = amount > 0.0 && statement.trim().isNotEmpty()

    FormSheetContainer(
        title = if (entryType == "REVENUE") "تسجيل إيراد تشغيلي آخر" else "تسجيل نفقة تشغيلية",
        subtitle = "اختر من تصنيفاتك وصناديقك الخاصة أو اكتب اسماً جديداً ليُحفظ تلقائياً",
        headerIcon = if (entryType == "REVENUE") Icons.Default.TrendingUp else Icons.Default.TrendingDown,
        onDismiss = onDismiss,
        primaryButtonText = if (entryType == "REVENUE") "تأكيد وقيد الإيراد" else "تأكيد وقيد المصروف",
        primaryButtonColor = if (entryType == "REVENUE") SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger,
        isPrimaryEnabled = isFormValid,
        isSaving = isSaving,
        errorMessage = errorMessage,
        hasUnsavedChanges = amountText.isNotBlank() || statement.isNotBlank(),
        onPrimaryClick = {
            if (isFormValid) {
                onSave(
                    entryType,
                    amount,
                    statement.trim(),
                    categoryText.trim(),
                    sourceText.trim().ifBlank { "صندوق عام" },
                    selectedCategoryId,
                    selectedAccountId
                )
            }
        }
    ) {
        FormSectionGroup(title = "1. نوع العملية والمبلغ والتصنيف") {
            SegmentedOptionRow(
                options = listOf(
                    "EXPENSE" to "مصروف تشغيلي (-)",
                    "REVENUE" to "إيراد آخر (+)"
                ),
                selectedOption = entryType,
                onOptionSelected = {
                    entryType = it
                    selectedCategoryId = null
                },
                activeColor = if (entryType == "REVENUE") SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
            )

            SmartTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = "المبلغ (${AppDesignState.currencySymbol})",
                placeholder = "0",
                isRequired = true,
                keyboardType = KeyboardType.Decimal,
                autoFocusOnAppear = amountText.isBlank(),
                nextFocusRequester = focusStatement
            )

            SmartAutocompleteField(
                value = categoryText,
                onValueChange = { typed ->
                    categoryText = typed
                    selectedCategoryId = domainCategories.firstOrNull {
                        it.Name.equals(typed.trim(), ignoreCase = true) ||
                            HierarchicalCategoryValidator.formatCategoryPath(it.Id, domainCategories).equals(typed.trim(), ignoreCase = true)
                    }?.Id
                },
                label = "التصنيف (اختر من تصنيفاتك أو اكتب تصنيفاً جديداً)",
                placeholder = if (entryType == "REVENUE") "مثال: إيراد خدمات، عمولات..." else "مثال: إيجار، كهرباء، رواتب...",
                suggestions = categorySuggestions,
                leadingIcon = Icons.Default.AccountTree
            )
        }

        FormSectionGroup(title = "2. البيان والحساب المالي (الصندوق / المحفظة)") {
            SmartTextField(
                value = statement,
                onValueChange = { statement = it },
                label = "البيان والوصف التفصيلي",
                placeholder = "أدخل تفاصيل العملية...",
                isRequired = true,
                focusRequester = focusStatement,
                imeAction = ImeAction.Next
            )

            SmartAutocompleteField(
                value = sourceText,
                onValueChange = { typed ->
                    sourceText = typed
                    selectedAccountId = financialAccounts.firstOrNull { it.Name.equals(typed.trim(), ignoreCase = true) }?.Id
                },
                label = if (entryType == "REVENUE") "الحساب المالي المستلم (صندوق / محفظة / بنك)" else "الحساب المالي المسدد منه (صندوق / محفظة / بنك)",
                placeholder = "اكتب أو اختر الصندوق أو المحفظة...",
                suggestions = sourceSuggestions,
                leadingIcon = Icons.Default.AccountBalanceWallet,
                imeAction = ImeAction.Done
            )
        }
    }
}
