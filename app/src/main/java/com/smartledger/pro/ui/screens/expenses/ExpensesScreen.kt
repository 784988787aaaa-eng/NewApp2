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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.entity.ExpenseEntity
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.viewmodels.ExpensesViewModel
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.launch

@Composable
fun ExpensesScreen(
    onBack: (() -> Unit)? = null,
    createAction: String? = null,
    onCreateActionConsumed: () -> Unit = {},
    viewModel: ExpensesViewModel = viewModel()
) {
    val allExpenses by viewModel.expenses.collectAsState()
    val userCategories by viewModel.categories.collectAsState()
    val financialAccounts by viewModel.financialAccounts.collectAsState()
    val scope = rememberCoroutineScope()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTypeFilter by rememberSaveable { mutableStateOf("ALL") } // ALL, EXPENSE, REVENUE
    var selectedCategoryFilter by rememberSaveable { mutableStateOf("ALL") }
    var showAddExpenseDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(createAction) {
        if (createAction == "expense") {
            showAddExpenseDialog = true
            onCreateActionConsumed()
        }
    }
    var expenseIdToDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val expenseToDelete = remember(allExpenses, expenseIdToDelete) { expenseIdToDelete?.let { id -> allExpenses.firstOrNull { it.Id == id } } }
    var lastDeletedExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val dynamicCategoryChips = remember(userCategories, allExpenses) {
        val fromEntities = userCategories.map { it.Name }
        val fromStatements = allExpenses.mapNotNull { exp ->
            if (exp.Statement.startsWith("[") && exp.Statement.contains("]")) {
                exp.Statement.substringAfter("[").substringBefore("]").trim().takeIf { it.isNotEmpty() }
            } else null
        }
        val combined = (fromEntities + fromStatements).distinct()
        listOf("ALL" to "كل التصنيفات") + combined.map { it to it }
    }

    val filteredExpenses = remember(allExpenses, searchQuery, selectedTypeFilter, selectedCategoryFilter) {
        allExpenses.filter { exp ->
            val entryType = exp.EntryType ?: "EXPENSE"
            val matchesType = selectedTypeFilter == "ALL" || entryType.equals(selectedTypeFilter, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                exp.Statement.contains(searchQuery, ignoreCase = true) ||
                exp.PaymentSource.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCategoryFilter == "ALL" || exp.Statement.contains(selectedCategoryFilter, ignoreCase = true)
            matchesType && matchesQuery && matchesCat
        }
    }

    val totalExpenses = remember(filteredExpenses) {
        filteredExpenses.filter { (it.EntryType ?: "EXPENSE") == "EXPENSE" }.sumOf { it.Amount }
    }
    val totalRevenues = remember(filteredExpenses) {
        filteredExpenses.filter { it.EntryType == "REVENUE" }.sumOf { it.Amount }
    }
    val netOperatingDiff = totalRevenues - totalExpenses

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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                                text = "المصروفات والإيرادات التشغيلية",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            Text(
                                text = "${filteredExpenses.size} سجل • تصنيفات هرمية مرنة",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                        }
                    }

                    Button(
                        onClick = { showAddExpenseDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 36.dp).pressScaleEffect()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مصروف / إيراد", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                SmartScreenSummaryStrip(
                    firstLabel = "إجمالي المصروفات (-)",
                    firstValue = NumberFormatter.formatCurrency(totalExpenses, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                    firstColor = SmartLedgerColors.StateDanger,
                    secondLabel = "إجمالي الإيرادات (+)",
                    secondValue = NumberFormatter.formatCurrency(totalRevenues, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                    secondColor = SmartLedgerColors.StateSuccess,
                    thirdLabel = "صافي التشغيل",
                    thirdValue = NumberFormatter.formatCurrency(netOperatingDiff, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                    thirdColor = if (netOperatingDiff >= 0.0) SmartLedgerColors.AccentLight else SmartLedgerColors.StateDanger
                )

                CompactSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "ابحث في البيان أو التصنيف أو الصندوق...",
                    resultCount = filteredExpenses.size
                )

                SegmentedOptionRow(
                    options = listOf(
                        "ALL" to "الكل",
                        "EXPENSE" to "مصروفات (-)",
                        "REVENUE" to "إيرادات (+)"
                    ),
                    selectedOption = selectedTypeFilter,
                    onOptionSelected = { selectedTypeFilter = it }
                )

                if (dynamicCategoryChips.size > 1) {
                    FilterChipRow(
                        items = dynamicCategoryChips,
                        selectedItem = selectedCategoryFilter,
                        onItemSelected = { selectedCategoryFilter = it }
                    )
                }
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
                    actionLabel = if (lastDeletedExpense != null) "تراجع عن الأرشفة" else null,
                    onAction = if (lastDeletedExpense != null) {
                        {
                            val exp = lastDeletedExpense!!
                            viewModel.restoreExpenseSnapshot(exp)
                            lastDeletedExpense = null
                            statusMessage = "تمت استعادة السجل «${exp.Statement}» بمعرفه الأصلي (#${exp.Id}) بنجاح."
                        }
                    } else null,
                    onDismiss = {
                        statusMessage = null
                        lastDeletedExpense = null
                    }
                )
            }

            if (filteredExpenses.isEmpty()) {
                item {
                    val isFiltered = searchQuery.isNotBlank() || selectedCategoryFilter != "ALL" || selectedTypeFilter != "ALL"
                    SmartEmptyState(
                        icon = Icons.Default.TrendingDown,
                        title = "لا توجد سجلات مصروفات أو إيرادات بعد",
                        subtitle = "سجّل المصروفات أو الإيرادات التشغيلية بتصنيفاتك وصناديقك الخاصة دون أي قوائم مفروضة.",
                        actionText = "تسجيل مصروف أو إيراد",
                        onActionClick = { showAddExpenseDialog = true },
                        isFiltered = isFiltered,
                        onClearFilters = {
                            searchQuery = ""
                            selectedCategoryFilter = "ALL"
                            selectedTypeFilter = "ALL"
                        }
                    )
                }
            } else {
                items(filteredExpenses, key = { it.Id }) { expense ->
                    val isRevenue = expense.EntryType == "REVENUE"
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
                                    text = (if (isRevenue) "+" else "-") + NumberFormatter.formatCurrency(expense.Amount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRevenue) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isRevenue) SmartLedgerColors.StateSuccessSubtle else SmartLedgerColors.StateDangerSubtle)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (isRevenue) "إيراد آخر (+)" else "مصروف (-)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isRevenue) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
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
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = expense.ExpenseDate.take(16),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SmartLedgerColors.TextMuted
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { expenseIdToDelete = expense.Id },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "أرشفة", tint = SmartLedgerColors.StateDanger, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }

        if (showAddExpenseDialog) {
            FloatingModalDialog(onDismissRequest = { showAddExpenseDialog = false }) {
                var isSavingExpense by remember { mutableStateOf(false) }
                var saveExpenseError by remember { mutableStateOf<String?>(null) }

                AddExpenseDialog(
                    categories = userCategories,
                    financialAccounts = financialAccounts,
                    isSaving = isSavingExpense,
                    errorMessage = saveExpenseError,
                    onDismiss = { showAddExpenseDialog = false },
                    onSave = { entryType, amount, statement, category, source, categoryId, financialAccountId ->
                        isSavingExpense = true
                        saveExpenseError = null
                        scope.launch {
                            val res = viewModel.saveExpenseAsync(
                                amount = amount,
                                statement = statement,
                                category = category,
                                source = source,
                                entryType = entryType,
                                categoryId = categoryId,
                                financialAccountId = financialAccountId
                            )
                            isSavingExpense = false
                            if (res.isSuccess) {
                                showAddExpenseDialog = false
                                lastDeletedExpense = null
                                statusMessage = if (entryType == "REVENUE") {
                                    "تم تسجيل الإيراد التشغيلي وإضافته إلى قائمة الدخل والسيولة."
                                } else {
                                    "تم تسجيل النفقة التشغيلية وخصمها من قائمة الدخل والسيولة."
                                }
                            } else {
                                saveExpenseError = res.exceptionOrNull()?.message ?: "تعذر حفظ السجل؛ يرجى التحقق والمحاولة مرة أخرى."
                            }
                        }
                    }
                )
            }
        }

        if (expenseToDelete != null) {
            val target = expenseToDelete!!
            ConfirmActionDialog(
                title = "أرشفة السجل المالي؟",
                message = "سيتم أرشفة «${target.Statement}» بقيمة ${NumberFormatter.formatCurrency(target.Amount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} ونقله إلى سلة المحذوفات.",
                confirmText = "أرشفة السجل",
                requireReason = true,
                onConfirm = { reason ->
                    lastDeletedExpense = target
                    viewModel.deleteExpense(target.Id, reason)
                    expenseIdToDelete = null
                    statusMessage = "تمت أرشفة «${target.Statement}»."
                },
                onDismiss = { expenseIdToDelete = null }
            )
        }
    }
}
