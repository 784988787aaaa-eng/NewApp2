package com.smartledger.pro.ui.screens.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.entity.ExpenseEntity
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.viewmodels.ExpensesViewModel
import com.smartledger.pro.util.AppSettingsManager
import com.smartledger.pro.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    onBack: (() -> Unit)? = null,
    viewModel: ExpensesViewModel = viewModel()
) {
    val expenses by viewModel.expenses.collectAsState()
    val settings by AppSettingsManager.settings.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var expensePendingDelete by remember { mutableStateOf<ExpenseEntity?>(null) }

    val categories = remember {
        listOf("الكل" to "ALL", "إيجار" to "إيجار", "كهرباء ومياه" to "كهرباء ومياه", "رواتب وأجور" to "رواتب وأجور", "نقل ومواصلات" to "نقل ومواصلات", "صيانة" to "صيانة", "نثريات" to "نثريات")
    }

    val filteredExpenses = remember(expenses, searchQuery, selectedCategoryFilter) {
        expenses.filter { exp ->
            val matchesQuery = searchQuery.isBlank() ||
                exp.Statement.contains(searchQuery, ignoreCase = true) ||
                exp.PaymentSource.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCategoryFilter == "ALL" ||
                exp.Statement.contains(selectedCategoryFilter, ignoreCase = true)
            matchesQuery && matchesCat
        }
    }

    val totalFilteredExpenses = filteredExpenses.sumOf { it.Amount }

    Scaffold(
        containerColor = SmartLedgerColors.SurfaceWindow,
        topBar = {
            Column(
                modifier = Modifier
                    .background(SmartLedgerColors.SurfacePanel)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
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
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column {
                            Text(
                                "المصروفات والنفقات التشغيلية",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            Text(
                                "إجمالي المعروض: ${Formatters.formatCurrency(totalFilteredExpenses, settings.currencySymbol, settings.decimalPlaces)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.StateDanger,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = rememberDebouncedClick { showAddExpenseDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.StateDanger),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = SmartLedgerColors.TextInverse)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تسجيل نفقة", color = SmartLedgerColors.TextInverse, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                CompactSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "ابحث في بيان النفقة أو مصدر الصرف..."
                )

                Spacer(modifier = Modifier.height(8.dp))

                ScrollableFilterTabs(
                    tabs = categories.map { (label, id) -> FilterTabItem(id, label) },
                    selectedTabId = selectedCategoryFilter,
                    onTabSelected = { selectedCategoryFilter = it }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            if (filteredExpenses.isEmpty()) {
                item {
                    SmartEmptyState(
                        icon = Icons.Default.ReceiptLong,
                        title = "لا توجد مصروفات مسجلة في هذا العرض",
                        description = "اضغط على «تسجيل نفقة» لتوثيق الإيجار، الكهرباء، الرواتب، أو المصروفات اليومية.",
                        actionText = "تسجيل نفقة جديدة",
                        onActionClick = { showAddExpenseDialog = true }
                    )
                }
            } else {
                items(filteredExpenses, key = { it.Id }) { expense ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderGridLine)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
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
                                    text = Formatters.formatCurrency(expense.Amount, settings.currencySymbol, settings.decimalPlaces),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SmartLedgerColors.StateDanger
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SmartLedgerColors.SurfaceCardElevated)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = expense.PaymentSource,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SmartLedgerColors.AccentLight,
                                        fontSize = 10.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = expense.ExpenseDate.take(16),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SmartLedgerColors.TextMuted
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { expensePendingDelete = expense },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "حذف النفقة",
                                            tint = SmartLedgerColors.StateDanger,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (showAddExpenseDialog) {
            AddExpenseDialog(
                currencySymbol = settings.currencySymbol,
                onDismiss = { showAddExpenseDialog = false },
                onSave = { amount, statement, category, source ->
                    viewModel.saveExpense(amount, statement, category, source)
                    showAddExpenseDialog = false
                }
            )
        }

        if (expensePendingDelete != null) {
            ConfirmActionDialog(
                title = "حذف نفقة تشغيلية",
                message = "هل أنت متأكد من حذف النفقة «${expensePendingDelete!!.Statement}» بقيمة ${Formatters.formatCurrency(expensePendingDelete!!.Amount, settings.currencySymbol, settings.decimalPlaces)}؟ سيتم حفظها في سلة المحذوفات.",
                confirmText = "تأكيد الحذف",
                onConfirm = {
                    viewModel.deleteExpense(expensePendingDelete!!.Id, "حذف من شاشة المصروفات")
                    expensePendingDelete = null
                },
                onDismiss = { expensePendingDelete = null }
            )
        }
    }
}

@Composable
fun AddExpenseDialog(
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (amount: Double, statement: String, category: String, source: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var statement by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("نثريات") }
    var source by remember { mutableStateOf("الخزينة الرئيسية") }

    val amountFocus = remember { FocusRequester() }
    val statementFocus = remember { FocusRequester() }

    val amount = Formatters.parseDoubleSafe(amountText, 0.0)
    val isFormValid = amount > 0.0 && statement.trim().isNotEmpty()

    AdaptiveFormHost(
        mode = FormWindowMode.BOTTOM_SHEET,
        title = "تسجيل نفقة تشغيلية",
        subtitle = "يُخصم المصروف من مجمل الربح لحساب صافي الدخل الحقيقي",
        onDismiss = onDismiss,
        primaryButtonText = "تأكيد وقيد المصروف",
        primaryButtonColor = SmartLedgerColors.StateDanger,
        isPrimaryEnabled = isFormValid,
        hasUnsavedChanges = amountText.isNotBlank() || statement.isNotBlank(),
        onPrimaryClick = {
            if (isFormValid) {
                onSave(amount, statement, category, source)
            }
        }
    ) {
        Text("تصنيف النفقة", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("إيجار", "كهرباء ومياه", "رواتب وأجور", "نقل ومواصلات", "صيانة", "نثريات")) { cat ->
                val selected = category == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) SmartLedgerColors.StateDanger else SmartLedgerColors.SurfaceCardElevated)
                        .clickable { category = cat }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cat,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) SmartLedgerColors.TextInverse else SmartLedgerColors.TextSecondary
                    )
                }
            }
        }

        SmartNumberField(
            value = amountText,
            onValueChange = { amountText = it },
            label = "مبلغ النفقة",
            suffixText = currencySymbol,
            isRequired = true,
            focusRequester = amountFocus,
            nextFocusRequester = statementFocus
        )

        SmartTextField(
            value = statement,
            onValueChange = { statement = it },
            label = "بيان سبب الصرف",
            placeholder = "مثال: سداد فاتورة كهرباء المعرض",
            isRequired = true,
            focusRequester = statementFocus,
            imeAction = ImeAction.Done
        )

        Text("مصدر الدفع", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("الخزينة الرئيسية", "الحساب البنكي", "عهدة موظف")) { src ->
                val selected = source == src
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) SmartLedgerColors.AccentSubtle else SmartLedgerColors.SurfaceCardElevated)
                        .border(1.dp, if (selected) SmartLedgerColors.AccentPrimary else SmartLedgerColors.BorderSubtle, RoundedCornerShape(8.dp))
                        .clickable { source = src }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = src,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) SmartLedgerColors.AccentLight else SmartLedgerColors.TextSecondary
                    )
                }
            }
        }
    }
}
