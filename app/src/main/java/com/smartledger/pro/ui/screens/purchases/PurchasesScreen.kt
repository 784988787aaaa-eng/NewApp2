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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.dao.AccountBalanceSummary
import com.smartledger.pro.data.local.dao.ProductStockItem
import com.smartledger.pro.data.local.entity.InvoiceEntity
import com.smartledger.pro.data.local.entity.InvoiceLineEntity
import com.smartledger.pro.data.repository.AccountRepository
import com.smartledger.pro.data.repository.InventoryRepository
import com.smartledger.pro.data.repository.InvoiceRepository
import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class PurchasesViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val db = SmartLedgerDatabase.getInstance(application)
    private val invoiceRepo = InvoiceRepository(db)
    private val accountRepo = AccountRepository(db)
    private val inventoryRepo = InventoryRepository(db)

    val suppliers: StateFlow<List<AccountBalanceSummary>> = accountRepo.getAccountSummaries(bookId = 2)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductStockItem>> = inventoryRepo.getProducts()
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
        notes: String?
    ): Result<Long> {
        val safeFactor = if (product.UnitFactor <= 0.0 || product.LargeUnit.isNullOrBlank()) 1.0 else product.UnitFactor
        val totalMinorQty = if (useMajorUnit && safeFactor > 1.0) quantity * safeFactor else quantity
        val minorCost = if (useMajorUnit && safeFactor > 1.0) {
            UnitConversionEngine.minorCostFromMajorCost(unitPurchaseCost, safeFactor)
        } else {
            unitPurchaseCost
        }
        val total = quantity * unitPurchaseCost
        val safePaid = paidCash.coerceIn(0.0, total)
        val remaining = kotlin.math.max(0.0, total - safePaid)

        if (remaining > 0.0 && supplier == null) {
            return Result.failure(Exception("المشتريات الآجلة أو الجزئية تتطلب اختيار مورد مسجل لقيد الالتزام له."))
        }

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val dateCode = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val invoiceNo = "PUR-$dateCode-${(System.currentTimeMillis() % 10000)}"
        val unitLabel = if (useMajorUnit && !product.LargeUnit.isNullOrBlank()) {
            "${NumberFormatter.formatQuantity(quantity)} ${product.LargeUnit}"
        } else {
            "${NumberFormatter.formatQuantity(quantity)} ${product.SmallUnit}"
        }

        val invoice = InvoiceEntity(
            InvoiceNumber = invoiceNo,
            Type = "Purchase",
            PersonId = supplier?.PersonId ?: 0L,
            PersonNameSnapshot = supplier?.PersonName ?: "مورد نقدي عام",
            IssuedAt = now,
            Subtotal = total,
            DiscountAmount = 0.0,
            TaxAmount = 0.0,
            FinalTotal = total,
            PaidAmount = safePaid,
            RemainingAmount = remaining,
            PaymentType = if (remaining == 0.0) "Cash" else if (safePaid == 0.0) "Credit" else "Partial",
            Notes = notes?.trim()?.ifBlank { null },
            CreatedAt = now
        )

        val line = InvoiceLineEntity(
            InvoiceId = 0L,
            ProductId = product.Id,
            ProductNameSnapshot = product.Name,
            BarcodeSnapshot = product.Barcode,
            UnitName = unitLabel,
            IsMajorUnit = useMajorUnit,
            ConversionFactor = safeFactor,
            QuantityMinor = totalMinorQty,
            UnitPrice = minorCost,
            LineCost = minorCost,
            LineTotal = total,
            SortOrder = 1
        )

        return invoiceRepo.createInvoice(invoice, listOf(line))
    }

    suspend fun reversePurchaseInvoice(invoiceId: Long, reason: String?): Result<Boolean> {
        return invoiceRepo.reverseInvoice(invoiceId, reason)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(
    onBack: (() -> Unit)? = null,
    viewModel: PurchasesViewModel = viewModel()
) {
    val allInvoices by viewModel.recentPurchases.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val products by viewModel.products.collectAsState()
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var showAddPurchaseSheet by remember { mutableStateOf(false) }
    var purchaseToReverse by remember { mutableStateOf<InvoiceEntity?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }

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

                    Button(
                        onClick = { showAddPurchaseSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                        modifier = Modifier
                            .height(38.dp)
                            .bounceClick { showAddPurchaseSheet = true }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فاتورة شراء", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
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
                        actionText = "تسجيل أول فاتورة شراء",
                        onActionClick = { showAddPurchaseSheet = true },
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
                                        onClick = { purchaseToReverse = invoice },
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

        // نافذة إضافة فاتورة مشتريات وتوريد مخزني
        if (showAddPurchaseSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddPurchaseSheet = false },
                containerColor = SmartLedgerColors.SurfacePanel
            ) {
                var formError by remember { mutableStateOf<String?>(null) }
                AddPurchaseSheetContent(
                    suppliers = suppliers,
                    products = products,
                    errorMessage = formError,
                    onCommit = { supplier, product, useMajor, qty, cost, paid, notes ->
                        scope.launch {
                            val res = viewModel.createPurchase(supplier, product, useMajor, qty, cost, paid, notes)
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

        // تأكيد مرتجع المشتريات
        if (purchaseToReverse != null) {
            val target = purchaseToReverse!!
            ConfirmActionDialog(
                title = "إرجاع فاتورة الشراء ${target.InvoiceNumber}؟",
                message = "سيتم خصم الكمية الموردة من المخزن وعكس المبلغ الآجل من حساب المورد بموجب إشعار مرتجع مشتريات.",
                confirmText = "تأكيد المرتجع",
                requireReason = true,
                onConfirm = { reason ->
                    scope.launch {
                        viewModel.reversePurchaseInvoice(target.Id, reason)
                        purchaseToReverse = null
                        isStatusError = false
                        statusMessage = "تم عكس فاتورة المشتريات ${target.InvoiceNumber}."
                    }
                },
                onDismiss = { purchaseToReverse = null }
            )
        }
    }
}

@Composable
fun AddPurchaseSheetContent(
    suppliers: List<AccountBalanceSummary>,
    products: List<ProductStockItem>,
    errorMessage: String?,
    onCommit: (
        supplier: AccountBalanceSummary?,
        product: ProductStockItem,
        useMajorUnit: Boolean,
        quantity: Double,
        costPrice: Double,
        paidCash: Double,
        notes: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSupplier by remember { mutableStateOf<AccountBalanceSummary?>(suppliers.firstOrNull()) }
    var selectedProduct by remember { mutableStateOf(products.firstOrNull()) }
    var useMajorUnit by remember(selectedProduct) {
        mutableStateOf(selectedProduct != null && !selectedProduct!!.LargeUnit.isNullOrBlank() && selectedProduct!!.UnitFactor > 1.0)
    }
    var quantityText by remember { mutableStateOf("1") }
    var costPriceText by remember(selectedProduct, useMajorUnit) {
        val p = selectedProduct
        val initialCost = if (p == null) 0.0 else if (useMajorUnit && p.UnitFactor > 1.0) (p.CostPrice * p.UnitFactor) else p.CostPrice
        mutableStateOf(if (initialCost > 0.0) NumberFormatter.formatQuantity(initialCost) else "")
    }
    var paymentType by remember { mutableStateOf("Cash") } // Cash, Credit, Partial
    var paidCashText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val qty = NumberFormatter.parseDouble(quantityText, 0.0)
    val unitCost = NumberFormatter.parseDouble(costPriceText, 0.0)
    val total = qty * unitCost
    val paid = when (paymentType) {
        "Cash" -> total
        "Credit" -> 0.0
        else -> NumberFormatter.parseDouble(paidCashText, 0.0).coerceIn(0.0, total)
    }
    val remaining = (total - paid).coerceAtLeast(0.0)

    val requiresSupplier = remaining > 0.0 && selectedSupplier == null
    val isFormValid = selectedProduct != null && qty > 0.0 && unitCost > 0.0 && !requiresSupplier

    FormSheetContainer(
        title = "تسجيل فاتورة مشتريات وتوريد",
        subtitle = "إدخال البضاعة للمخزن بالوحدة الصغرى أو الكبرى وتحديث حساب المورد",
        onDismiss = onDismiss,
        primaryButtonText = "تأكيد وتوريد الفاتورة",
        isPrimaryEnabled = isFormValid,
        errorMessage = errorMessage,
        onPrimaryClick = {
            val prod = selectedProduct
            if (isFormValid && prod != null) {
                onCommit(selectedSupplier, prod, useMajorUnit, qty, unitCost, paid, notes)
            }
        }
    ) {
        // 1. اختيار الصنف المراد توريده (شرائح تفاعلية قابلة للاختيار)
        Text("الصنف المراد توريده *", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.TextSecondary)
        if (products.isEmpty()) {
            Text("لا توجد أصناف معرفة بعد. أضف الصنف أولاً من شاشة المخزون.", color = SmartLedgerColors.StateDanger, style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products, key = { it.Id }) { p ->
                    val isSel = selectedProduct?.Id == p.Id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) SmartLedgerColors.AccentSubtle else SmartLedgerColors.SurfaceCardElevated)
                            .border(1.dp, if (isSel) SmartLedgerColors.AccentPrimary else SmartLedgerColors.BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable { selectedProduct = p }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text(p.Name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (isSel) SmartLedgerColors.AccentLight else SmartLedgerColors.TextPrimary)
                            Text("المتاح: ${NumberFormatter.formatQuantity(p.CurrentStockMinor)} ${p.SmallUnit}", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                        }
                    }
                }
            }
        }

        // اختيار وحدة الشراء (الوحدة الصغرى أو الكبرى)
        val prod = selectedProduct
        if (prod != null && !prod.LargeUnit.isNullOrBlank() && prod.UnitFactor > 1.0) {
            SegmentedOptionRow(
                options = listOf(
                    true to "شراء بالـ ${prod.LargeUnit} (${NumberFormatter.formatQuantity(prod.UnitFactor)} ${prod.SmallUnit})",
                    false to "شراء بالـ ${prod.SmallUnit} المفرد"
                ),
                selectedOption = useMajorUnit,
                onOptionSelected = { useMajorUnit = it }
            )
        }

        // 2. اختيار المورد
        Text("المورد *", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.TextSecondary)
        val supplierOptions = remember(suppliers) {
            listOf<Pair<AccountBalanceSummary?, String>>(null to "مورد نقدي عام") +
                suppliers.map { it to it.PersonName }
        }
        FilterChipRow(
            items = supplierOptions,
            selectedItem = selectedSupplier,
            onItemSelected = { selectedSupplier = it }
        )

        val unitName = if (useMajorUnit && !prod?.LargeUnit.isNullOrBlank()) prod!!.LargeUnit!! else (prod?.SmallUnit ?: "وحدة")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmartTextField(
                value = quantityText,
                onValueChange = { quantityText = it },
                label = "الكمية ($unitName)",
                isRequired = true,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f)
            )
            SmartTextField(
                value = costPriceText,
                onValueChange = { costPriceText = it },
                label = "سعر شراء الـ $unitName",
                isRequired = true,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f)
            )
        }

        // 3. طريقة سداد الفاتورة للمورد
        SegmentedOptionRow(
            options = listOf(
                "Cash" to "نقدي كامل",
                "Credit" to "آجل للمورد",
                "Partial" to "دفعة جزئية"
            ),
            selectedOption = paymentType,
            onOptionSelected = { paymentType = it }
        )

        if (paymentType == "Partial") {
            SmartTextField(
                value = paidCashText,
                onValueChange = { paidCashText = it },
                label = "المبلغ المسدد نقداً للمورد الآن",
                keyboardType = KeyboardType.Decimal
            )
        }

        if (requiresSupplier) {
            Text(
                text = "تنبيه: يرجى اختيار مورد مسجل من الشريط أعلاه لتسجيل المبلغ الآجل له.",
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.StateWarning,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
            shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("إجمالي فاتورة الشراء:", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextSecondary)
                    Text(NumberFormatter.formatCurrency(total, AppDesignState.currencySymbol, AppDesignState.decimalPlaces), fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("المسدد نقداً:", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.StateSuccessText)
                    Text(NumberFormatter.formatCurrency(paid, AppDesignState.currencySymbol, AppDesignState.decimalPlaces), fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateSuccess)
                }
                if (remaining > 0.0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المتبقي آجل للمورد (له):", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.StateDangerText)
                        Text(NumberFormatter.formatCurrency(remaining, AppDesignState.currencySymbol, AppDesignState.decimalPlaces), fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateDanger)
                    }
                }
            }
        }

        ExpandableAdvancedSection(title = "رقم مرجع المورد أو ملاحظات الشحن") {
            SmartTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "ملاحظات التوريد / رقم فاتورة المورد",
                placeholder = "مثال: فاتورة المورد رقم 450...",
                imeAction = ImeAction.Done
            )
        }
    }
}
