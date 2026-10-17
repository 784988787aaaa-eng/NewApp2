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
import com.smartledger.pro.data.local.entity.ExpenseEntity
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.viewmodels.ExpensesViewModel
import com.smartledger.pro.util.NumberFormatter

@Composable
fun ExpensesScreen(
    onBack: (() -> Unit)? = null,
    viewModel: ExpensesViewModel = viewModel()
) {
    val allExpenses by viewModel.expenses.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<ExpenseEntity?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val filteredExpenses = remember(allExpenses, searchQuery, selectedCategory) {
        allExpenses.filter { exp ->
            val matchesQuery = searchQuery.isBlank() ||
                exp.Statement.contains(searchQuery, ignoreCase = true) ||
                exp.PaymentSource.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCategory == "ALL" || exp.Statement.contains(selectedCategory)
            matchesQuery && matchesCat
        }
    }

    val totalExpenses = remember(filteredExpenses) { filteredExpenses.sumOf { it.Amount } }

    Scaffold(
        containerColor = SmartLedgerColors.SurfaceWindow,
        topBar = {
            Column(
                modifier = Modifier
                    .background(SmartLedgerColors.SurfacePanel)
                    .padding(horizontal = SmartLedgerTokens.screenHorizontalPadding, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(34.dp).clip(CircleShape).background(SmartLedgerColors.SurfaceCardElevated)
                            ) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "رجوع", tint = SmartLedgerColors.TextPrimary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column {
                            Text(
                                text = "المصروفات والنفقات التشغيلية",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            Text(
                                text = "إجمالي النفقات المعروضة: ${NumberFormatter.formatCurrency(totalExpenses, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.StateDangerText
                            )
                        }
                    }

                    Button(
                        onClick = { showAddExpenseDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.StateDanger),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                        modifier = Modifier
                            .height(38.dp)
                            .bounceClick { showAddExpenseDialog = true }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = SmartLedgerColors.TextInverse)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تسجيل نفقة", color = SmartLedgerColors.TextInverse, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }

                CompactSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "ابحث في بيان المصروف أو مصدر الدفع...",
                    resultCount = filteredExpenses.size
                )

                FilterChipRow(
                    items = listOf(
                        "ALL" to "كل التصنيفات",
                        "إيجار ومرافق" to "إيجار ومرافق",
                        "رواتب وأجور" to "رواتب وأجور",
                        "نقل وشحن" to "نقل وشحن",
                        "ضيافة ونثريات" to "ضيافة ونثريات",
                        "صيانة" to "صيانة"
                    ),
                    selectedItem = selectedCategory,
                    onItemSelected = { selectedCategory = it }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = SmartLedgerTokens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SmartLedgerTokens.itemSpacing)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                StatusFeedbackBanner(
                    message = statusMessage,
                    onDismiss = { statusMessage = null }
                )
            }

            if (filteredExpenses.isEmpty()) {
                item {
                    val isFiltered = searchQuery.isNotBlank() || selectedCategory != "ALL"
                    SmartEmptyState(
                        icon = Icons.Default.TrendingDown,
                        title = "لا توجد مصروفات مسجلة",
                        subtitle = "سجّل الإيجار، الكهرباء، أجور النقل، والنثريات لحساب صافي الربح الحقيقي في قائمة الدخل.",
                        actionText = "تسجيل أول نفقة",
                        onActionClick = { showAddExpenseDialog = true },
                        isFiltered = isFiltered,
                        onClearFilters = {
                            searchQuery = ""
                            selectedCategory = "ALL"
                        }
                    )
                }
            } else {
                items(filteredExpenses, key = { it.Id }) { expense ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                        shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
                    ) {
                        Column(modifier = Modifier.padding(SmartLedgerTokens.cardInternalPadding)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = expense.Statement,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SmartLedgerColors.TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = NumberFormatter.formatCurrency(expense.Amount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SmartLedgerColors.StateDanger
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SmartLedgerColors.SurfaceCardElevated)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = expense.PaymentSource,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SmartLedgerColors.AccentLight
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = expense.ExpenseDate.take(16),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SmartLedgerColors.TextMuted
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { expenseToDelete = expense },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف النفقة", tint = SmartLedgerColors.StateDanger, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }

        // نافذة تسجيل نفقة تشغيلية عائمة فوق لوحة المفاتيح
        if (showAddExpenseDialog) {
            FloatingModalDialog(onDismissRequest = { showAddExpenseDialog = false }) {
                AddExpenseDialog(
                    onDismiss = { showAddExpenseDialog = false },
                    onSave = { amount, statement, category, source ->
                        viewModel.saveExpense(amount, statement, category, source)
                        showAddExpenseDialog = false
                        statusMessage = "تم تسجيل النفقة التشغيلية وخصمها من قائمة الدخل."
                    }
                )
            }
        }

        if (expenseToDelete != null) {
            val target = expenseToDelete!!
            ConfirmActionDialog(
                title = "حذف النفقة التشغيلية؟",
                message = "سيتم حذف المصروف «${target.Statement}» بقيمة ${NumberFormatter.formatCurrency(target.Amount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} ونقله إلى سجل المحذوفات.",
                confirmText = "حذف النفقة",
                requireReason = true,
                onConfirm = { reason ->
                    viewModel.deleteExpense(target.Id, reason)
                    expenseToDelete = null
                    statusMessage = "تم حذف النفقة التشغيلية."
                },
                onDismiss = { expenseToDelete = null }
            )
        }
    }
}

@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onSave: (amount: Double, statement: String, category: String, source: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var statement by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("إيجار ومرافق") }
    var source by remember { mutableStateOf("الخزينة الرئيسية") }

    val focusStatement = remember { FocusRequester() }
    val amount = NumberFormatter.parseDouble(amountText, 0.0)
    val isFormValid = amount > 0.0 && statement.trim().isNotEmpty()

    FormSheetContainer(
        title = "تسجيل نفقة تشغيلية",
        subtitle = "توثيق المصروفات التشغيلية لحساب صافي الربح بدقة",
        headerIcon = Icons.Default.TrendingDown,
        onDismiss = onDismiss,
        primaryButtonText = "تأكيد وقيد المصروف",
        primaryButtonColor = SmartLedgerColors.StateDanger,
        isPrimaryEnabled = isFormValid,
        hasUnsavedChanges = amountText.isNotBlank() || statement.isNotBlank(),
        onPrimaryClick = {
            if (isFormValid) {
                onSave(amount, statement.trim(), category, source)
            }
        }
    ) {
        FormSectionGroup(title = "1. تصنيف النفقة ومبلغها") {
            FilterChipRow(
                items = listOf(
                    "إيجار ومرافق" to "إيجار ومرافق",
                    "رواتب وأجور" to "رواتب وأجور",
                    "نقل وشحن" to "نقل وشحن",
                    "ضيافة ونثريات" to "ضيافة ونثريات",
                    "صيانة" to "صيانة"
                ),
                selectedItem = category,
                onItemSelected = { category = it }
            )

            SmartTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = "مبلغ النفقة (${AppDesignState.currencySymbol})",
                placeholder = "مثال: 15000",
                isRequired = true,
                keyboardType = KeyboardType.Decimal,
                nextFocusRequester = focusStatement
            )
        }

        FormSectionGroup(title = "2. بيان الصرف ومصدر الدفع") {
            SmartTextField(
                value = statement,
                onValueChange = { statement = it },
                label = "بيان سبب الصرف ومبرره",
                placeholder = "مثال: سداد فاتورة كهرباء شهر أكتوبر...",
                isRequired = true,
                focusRequester = focusStatement,
                imeAction = ImeAction.Done
            )

            SegmentedOptionRow(
                options = listOf(
                    "الخزينة الرئيسية" to "الخزينة الرئيسية",
                    "الحساب البنكي" to "الحساب البنكي",
                    "عهدة نقدية" to "عهدة نقدية"
                ),
                selectedOption = source,
                onOptionSelected = { source = it }
            )
        }
    }
}
