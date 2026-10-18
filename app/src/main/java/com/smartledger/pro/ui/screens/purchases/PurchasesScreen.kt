package com.smartledger.pro.ui.screens.purchases

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.dao.AccountBalanceSummary
import com.smartledger.pro.data.local.dao.ProductStockItem
import com.smartledger.pro.data.local.entity.InvoiceEntity
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.viewmodels.PurchasesViewModel
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.launch

@Composable
fun PurchasesScreen(
    onBack: (() -> Unit)? = null,
    createAction: String? = null,
    onCreateActionConsumed: () -> Unit = {},
    viewModel: PurchasesViewModel = viewModel()
) {
    val allInvoices by viewModel.recentPurchases.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val products by viewModel.products.collectAsState()
    val financialAccounts by viewModel.financialAccounts.collectAsState()
    val scope = rememberCoroutineScope()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showAddPurchaseSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(createAction) {
        if (createAction == "purchase") {
            showAddPurchaseSheet = true
            onCreateActionConsumed()
        }
    }
    var purchaseIdToReverse by rememberSaveable { mutableStateOf<Long?>(null) }
    val purchaseToReverse = remember(allInvoices, purchaseIdToReverse) { purchaseIdToReverse?.let { id -> allInvoices.firstOrNull { it.Id == id } } }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isStatusError by rememberSaveable { mutableStateOf(false) }

    val purchaseList = remember(allInvoices, searchQuery) {
        allInvoices
            .filter { it.Type == "Purchase" }
            .filter {
                searchQuery.isBlank() ||
                    it.InvoiceNumber.contains(searchQuery, ignoreCase = true) ||
                    it.PersonNameSnapshot.contains(searchQuery, ignoreCase = true)
            }
    }

    val totalPurchasesAmount = remember(purchaseList) {
        purchaseList.filter { it.Status != "Reversed" }.sumOf { it.FinalTotal }
    }

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
                                text = "المشتريات والتوريد المخزني",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            Text(
                                text = "إجمالي التوريد المعتمد: ${NumberFormatter.formatCurrency(totalPurchasesAmount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.AccentLight
                            )
                        }
                    }

                }

                CompactSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "ابحث برقم الفاتورة أو اسم المورد...",
                    resultCount = purchaseList.size
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
                    isError = isStatusError,
                    onDismiss = { statusMessage = null }
                )
            }

            if (purchaseList.isEmpty()) {
                item {
                    SmartEmptyState(
                        icon = Icons.Default.LocalShipping,
                        title = "لا توجد فواتير مشتريات مسجلة",
                        subtitle = "سجّل فواتير التوريد لزيادة كميات المخزن تلقائياً وتحديث حسابات الموردين.",
                        isFiltered = searchQuery.isNotBlank(),
                        onClearFilters = { searchQuery = "" }
                    )
                }
            } else {
                items(purchaseList, key = { it.Id }) { invoice ->
                    val isReversed = invoice.Status == "Reversed"
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
                                Column {
                                    Text(
                                        text = invoice.InvoiceNumber,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SmartLedgerColors.AccentLight
                                    )
                                    Text(
                                        text = "المورد: ${invoice.PersonNameSnapshot}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SmartLedgerColors.TextPrimary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = NumberFormatter.formatCurrency(invoice.FinalTotal, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isReversed) SmartLedgerColors.TextMuted else SmartLedgerColors.StateSuccess
                                    )
                                    if (invoice.RemainingAmount > 0.0 && !isReversed) {
                                        Text(
                                            text = "متبقي للمورد: ${NumberFormatter.formatCurrency(invoice.RemainingAmount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SmartLedgerColors.StateDanger
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${invoice.IssuedAt.take(16)}" + if (!invoice.Notes.isNullOrBlank()) " • ${invoice.Notes}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextMuted
                                )

                                if (isReversed) {
                                    Text("مرتجعة / ملغاة", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateDanger, fontWeight = FontWeight.Bold)
                                } else {
                                    TextButton(
                                        onClick = { purchaseIdToReverse = invoice.Id },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("مرتجع شراء", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateDanger, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }

        // نافذة إضافة فاتورة مشتريات العائمة فوق لوحة المفاتيح مع حماية كاملة للبيانات
        if (showAddPurchaseSheet) {
            FloatingModalDialog(onDismissRequest = { showAddPurchaseSheet = false }) {
                var isSavingPurchase by remember { mutableStateOf(false) }
                var formError by remember { mutableStateOf<String?>(null) }

                AddPurchaseSheetContent(
                    suppliers = suppliers,
                    products = products,
                    financialAccounts = financialAccounts,
                    isSaving = isSavingPurchase,
                    errorMessage = formError,
                    onCommit = { supplier, product, useMajor, qty, cost, paid, notes, finAccId ->
                        isSavingPurchase = true
                        formError = null
                        scope.launch {
                            val res = viewModel.createPurchase(supplier, product, useMajor, qty, cost, paid, notes, finAccId)
                            isSavingPurchase = false
                            if (res.isSuccess) {
                                showAddPurchaseSheet = false
                                isStatusError = false
                                statusMessage = "تم حفظ فاتورة الشراء وتوريد البضاعة إلى المخزن بنجاح."
                            } else {
                                formError = res.exceptionOrNull()?.message
                            }
                        }
                    },
                    onDismiss = { showAddPurchaseSheet = false }
                )
            }
        }

        // تأكيد مرتجع المشتريات مع التحقق الفعلي من نجاح العكس (UX-008)
        if (purchaseToReverse != null) {
            val target = purchaseToReverse!!
            ConfirmActionDialog(
                title = "إرجاع فاتورة الشراء ${target.InvoiceNumber}؟",
                message = "سيتم خصم الكمية الموردة من المخزن وعكس المبلغ الآجل من حساب المورد بموجب إشعار مرتجع مشتريات.",
                confirmText = "تأكيد المرتجع",
                requireReason = true,
                onConfirm = { reason ->
                    scope.launch {
                        val res = viewModel.reversePurchaseInvoice(target.Id, reason)
                        purchaseIdToReverse = null
                        if (res.isSuccess) {
                            isStatusError = false
                            statusMessage = "تم عكس فاتورة المشتريات ${target.InvoiceNumber} وخصم البضاعة المرتجعة بنجاح."
                        } else {
                            isStatusError = true
                            statusMessage = res.exceptionOrNull()?.message ?: "تعذر عكس فاتورة المشتريات."
                        }
                    }
                },
                onDismiss = { purchaseIdToReverse = null }
            )
        }
    }
}
