package com.smartledger.pro.ui.screens.purchases

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.util.AppSettingsManager
import com.smartledger.pro.util.Formatters
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

    val suppliers: StateFlow<List<AccountBalanceSummary>> = accountRepo.getAccountSummaries(bookId = null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductStockItem>> = inventoryRepo.getProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentPurchases: StateFlow<List<InvoiceEntity>> = invoiceRepo.getRecentInvoices(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getInvoiceLines(invoiceId: Long): List<InvoiceLineEntity> {
        return invoiceRepo.getInvoiceLines(invoiceId)
    }

    suspend fun reversePurchaseInvoice(invoiceId: Long, reason: String): Result<Boolean> {
        return invoiceRepo.reverseInvoice(invoiceId, reason)
    }

    suspend fun createPurchase(
        supplier: AccountBalanceSummary?,
        product: ProductStockItem,
        purchaseAsMajorUnit: Boolean,
        enteredQuantity: Double,
        enteredUnitCost: Double,
        paidCash: Double,
        referenceNo: String?,
        notes: String?
    ): Result<Long> {
        val factor = if (purchaseAsMajorUnit && !product.LargeUnit.isNullOrBlank() && product.UnitFactor > 1.0) {
            product.UnitFactor
        } else 1.0

        val totalMinorQty = if (purchaseAsMajorUnit && factor > 1.0) {
            UnitConversionEngine.toMinorQuantity(enteredQuantity, 0.0, factor)
        } else {
            enteredQuantity
        }

        val minorUnitCost = if (purchaseAsMajorUnit && factor > 1.0) {
            UnitConversionEngine.minorCostFromMajorCost(enteredUnitCost, factor)
        } else {
            enteredUnitCost
        }

        val total = Formatters.roundAmount(enteredQuantity * enteredUnitCost)
        val actualPaid = paidCash.coerceIn(0.0, total)
        val remaining = kotlin.math.max(0.0, total - actualPaid)

        if (remaining > 0.001 && (supplier == null || supplier.PersonId <= 0L)) {
            return Result.failure(IllegalArgumentException("الشراء الآجل أو الجزئي يتطلب اختيار حساب مورد مسجل."))
        }

        val now = Formatters.nowTimestamp()
        val dateCode = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val invoiceNo = referenceNo?.trim()?.ifBlank { null }
            ?: "PUR-$dateCode-${(System.currentTimeMillis() % 10000)}"

        val unitDisplay = if (purchaseAsMajorUnit && factor > 1.0) {
            "${Formatters.formatQuantity(enteredQuantity)} ${product.LargeUnit} (${Formatters.formatQuantity(totalMinorQty)} ${product.SmallUnit})"
        } else {
            "${Formatters.formatQuantity(enteredQuantity)} ${product.SmallUnit}"
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
            PaidAmount = actualPaid,
            RemainingAmount = remaining,
            PaymentType = if (remaining == 0.0) "Cash" else if (actualPaid == 0.0) "Credit" else "Partial",
            Notes = notes?.trim()?.ifBlank { null },
            CreatedAt = now
        )

        val line = InvoiceLineEntity(
            InvoiceId = 0L,
            ProductId = product.Id,
            ProductNameSnapshot = product.Name,
            BarcodeSnapshot = product.Barcode,
            UnitName = unitDisplay,
            IsMajorUnit = purchaseAsMajorUnit && factor > 1.0,
            ConversionFactor = factor,
            QuantityMinor = totalMinorQty,
            UnitPrice = minorUnitCost,
            LineCost = minorUnitCost,
            LineTotal = total,
            SortOrder = 1
        )

        return invoiceRepo.createInvoice(invoice, listOf(line))
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
    val settings by AppSettingsManager.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var showAddPurchaseSheet by remember { mutableStateOf(false) }
    var invoicePendingReverse by remember { mutableStateOf<InvoiceEntity?>(null) }
    var bannerText by remember { mutableStateOf<String?>(null) }

    val purchaseList = remember(allInvoices, searchQuery) {
        val base = allInvoices.filter { it.Type == "Purchase" }
        if (searchQuery.isBlank()) base
        else base.filter {
            it.InvoiceNumber.contains(searchQuery, ignoreCase = true) ||
                it.PersonNameSnapshot.contains(searchQuery, ignoreCase = true) ||
                (it.Notes?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

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
                                text = "المشتريات والتوريد المخزني",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            Text(
                                text = "إدخال البضائع بالوحدة الصغرى أو الكبرى وقيد التزام المورد",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                        }
                    }

                    Button(
                        onClick = rememberDebouncedClick { showAddPurchaseSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فاتورة شراء", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                CompactSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "ابحث برقم فاتورة الشراء أو اسم المورد..."
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

            if (!bannerText.isNullOrBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SmartLedgerColors.StateSuccessSubtle)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(bannerText!!, style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.StateSuccessText, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { bannerText = null }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = SmartLedgerColors.StateSuccessText, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            if (purchaseList.isEmpty()) {
                item {
                    SmartEmptyState(
                        icon = Icons.Default.LocalShipping,
                        title = "لا توجد فواتير مشتريات مسجلة",
                        description = "سجّل فواتير الشراء لزيادة كميات المخزن وتحديث أرصدة الموردين تلقائياً.",
                        actionText = "تسجيل فاتورة شراء جديدة",
                        onActionClick = { showAddPurchaseSheet = true }
                    )
                }
            } else {
                items(purchaseList, key = { it.Id }) { invoice ->
                    val isReversed = invoice.Status == "Reversed"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "المورد: ${invoice.PersonNameSnapshot}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SmartLedgerColors.TextPrimary
                                    )
                                    Text(
                                        text = "مرجع: ${invoice.InvoiceNumber} • ${invoice.IssuedAt.take(16)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SmartLedgerColors.TextMuted
                                    )
                                }
                                Text(
                                    text = Formatters.formatCurrency(invoice.FinalTotal, settings.currencySymbol, settings.decimalPlaces),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SmartLedgerColors.AccentLight
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "المدفوع: ${Formatters.formatCurrency(invoice.PaidAmount, settings.currencySymbol, settings.decimalPlaces)} • المتبقي للمورد: ${Formatters.formatCurrency(invoice.RemainingAmount, settings.currencySymbol, settings.decimalPlaces)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SmartLedgerColors.TextSecondary
                                    )
                                    Text(
                                        text = if (isReversed) "الحالة: مرتجع مشتريات (معكوسة)" else "الحالة: مرحلة للمخزن والحساب",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isReversed) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess
                                    )
                                }

                                if (!isReversed) {
                                    OutlinedButton(
                                        onClick = { invoicePendingReverse = invoice },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartLedgerColors.StateDanger),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("مرتجع شراء", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (showAddPurchaseSheet) {
            AddPurchaseSheetContent(
                suppliers = suppliers,
                products = products,
                currencySymbol = settings.currencySymbol,
                decimals = settings.decimalPlaces,
                onCommit = { supplier, product, asMajor, qty, cost, paid, refNo, notes, onError ->
                    scope.launch {
                        val res = viewModel.createPurchase(supplier, product, asMajor, qty, cost, paid, refNo, notes)
                        if (res.isSuccess) {
                            showAddPurchaseSheet = false
                            bannerText = "تم حفظ فاتورة المشتريات وتوريد الكمية للمخزن بنجاح."
                        } else {
                            onError(res.exceptionOrNull()?.message ?: "تعذر حفظ فاتورة الشراء")
                        }
                    }
                },
                onDismiss = { showAddPurchaseSheet = false }
            )
        }

        if (invoicePendingReverse != null) {
            ConfirmActionDialog(
                title = "تأكيد مرتجع وعكس فاتورة المشتريات",
                message = "سيتم عكس فاتورة الشراء رقم (${invoicePendingReverse!!.InvoiceNumber}):\n• خصم الكميات الموردة من المخزن.\n• تخفيض مديونية المورد بنفس القيمة.\nهل تريد المتابعة؟",
                confirmText = "تأكيد مرتجع الشراء",
                onConfirm = {
                    val targetId = invoicePendingReverse!!.Id
                    invoicePendingReverse = null
                    scope.launch {
                        viewModel.reversePurchaseInvoice(targetId, "مرتجع مشتريات معتمد")
                        bannerText = "تم عكس فاتورة الشراء وتحديث المخزن وحساب المورد."
                    }
                },
                onDismiss = { invoicePendingReverse = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPurchaseSheetContent(
    suppliers: List<AccountBalanceSummary>,
    products: List<ProductStockItem>,
    currencySymbol: String,
    decimals: Int,
    onCommit: (
        supplier: AccountBalanceSummary?,
        product: ProductStockItem,
        purchaseAsMajorUnit: Boolean,
        quantity: Double,
        unitCost: Double,
        paidCash: Double,
        referenceNo: String?,
        notes: String?,
        onError: (String) -> Unit
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val sortedSuppliers = remember(suppliers) {
        suppliers.sortedBy { if (it.BookId == 2L) 0 else 1 }
    }

    var selectedSupplier by remember { mutableStateOf(sortedSuppliers.firstOrNull { it.BookId == 2L }) }
    var selectedProduct by remember { mutableStateOf(products.firstOrNull()) }
    var purchaseAsMajorUnit by remember(selectedProduct) {
        mutableStateOf(selectedProduct != null && !selectedProduct!!.LargeUnit.isNullOrBlank() && selectedProduct!!.UnitFactor > 1.0)
    }
    var quantityText by remember { mutableStateOf("1") }
    var costPriceText by remember(selectedProduct, purchaseAsMajorUnit) {
        val p = selectedProduct
        val defaultCost = if (p == null) 0.0
        else if (purchaseAsMajorUnit && p.UnitFactor > 1.0) (p.CostPrice * p.UnitFactor)
        else p.CostPrice
        mutableStateOf(Formatters.formatQuantity(defaultCost))
    }
    var paidCashText by remember { mutableStateOf("0") }
    var showOptionalSection by remember { mutableStateOf(false) }
    var referenceNo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var showProductPicker by remember { mutableStateOf(false) }
    var showSupplierPicker by remember { mutableStateOf(false) }

    val qty = Formatters.parseDoubleSafe(quantityText, 0.0)
    val cost = Formatters.parseDoubleSafe(costPriceText, 0.0)
    val total = Formatters.roundAmount(qty * cost)
    val paid = Formatters.parseDoubleSafe(paidCashText, 0.0).coerceIn(0.0, total)
    val remaining = kotlin.math.max(0.0, total - paid)

    val hasMajorOption = selectedProduct != null && !selectedProduct!!.LargeUnit.isNullOrBlank() && selectedProduct!!.UnitFactor > 1.0
    val addedMinorQty = if (selectedProduct != null && purchaseAsMajorUnit && hasMajorOption) {
        UnitConversionEngine.toMinorQuantity(qty, 0.0, selectedProduct!!.UnitFactor)
    } else qty

    val isFormValid = selectedProduct != null && qty > 0.0 && cost > 0.0 && !(remaining > 0.001 && selectedSupplier == null)

    AdaptiveFormHost(
        mode = FormWindowMode.BOTTOM_SHEET,
        title = "تسجيل فاتورة مشتريات وتوريد",
        subtitle = "اختر المورد والصنف والوحدة، وسيتم تحديث المخزن وكشف الحساب ذرياً",
        onDismiss = onDismiss,
        primaryButtonText = "تأكيد وترحيل فاتورة الشراء",
        isPrimaryEnabled = isFormValid,
        hasUnsavedChanges = qty > 1.0 || referenceNo.isNotBlank() || notes.isNotBlank(),
        errorMessage = errorMsg ?: if (remaining > 0.001 && selectedSupplier == null) {
            "الفاتورة الآجلة تتطلب اختيار مورد مسجل بدلاً من «مورد نقدي عام»."
        } else null,
        onPrimaryClick = {
            if (isFormValid && selectedProduct != null) {
                onCommit(
                    selectedSupplier,
                    selectedProduct!!,
                    purchaseAsMajorUnit && hasMajorOption,
                    qty,
                    cost,
                    paid,
                    referenceNo,
                    notes
                ) { err -> errorMsg = err }
            }
        }
    ) {
        Text("المورد (الطرف المقابل) *", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SmartLedgerColors.SurfaceInput)
                .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(8.dp))
                .clickable { showSupplierPicker = true }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedSupplier?.PersonName ?: "مورد نقدي عام (سداد نقدي فوري)",
                fontWeight = FontWeight.Bold,
                color = SmartLedgerColors.TextPrimary
            )
            Text("تغيير المورد ▾", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight)
        }

        Text("الصنف المراد توريده للمخزن *", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary, fontWeight = FontWeight.SemiBold)
        if (products.isEmpty()) {
            Text("لا توجد أصناف معرفة في المخزن بعد. يرجى تعريف الصنف من شاشة المخزون أولاً.", color = SmartLedgerColors.StateDanger, style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SmartLedgerColors.SurfaceInput)
                    .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(8.dp))
                    .clickable { showProductPicker = true }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(selectedProduct?.Name ?: "اختر الصنف", fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                    if (selectedProduct != null) {
                        Text(
                            text = "الرصيد الحالي: ${Formatters.formatQuantity(selectedProduct!!.CurrentStockMinor)} ${selectedProduct!!.SmallUnit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.AccentLight
                        )
                    }
                }
                Text("تغيير الصنف ▾", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight)
            }
        }

        if (hasMajorOption && selectedProduct != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SmartLedgerColors.SurfaceCardElevated)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (purchaseAsMajorUnit) SmartLedgerColors.AccentPrimary else Color.Transparent)
                        .clickable { purchaseAsMajorUnit = true }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "شراء بالـ ${selectedProduct!!.LargeUnit} (${Formatters.formatQuantity(selectedProduct!!.UnitFactor)} ${selectedProduct!!.SmallUnit})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (purchaseAsMajorUnit) SmartLedgerColors.TextInverse else SmartLedgerColors.TextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (!purchaseAsMajorUnit) SmartLedgerColors.AccentPrimary else Color.Transparent)
                        .clickable { purchaseAsMajorUnit = false }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "شراء بالـ ${selectedProduct!!.SmallUnit} (الوحدة الصغرى)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (!purchaseAsMajorUnit) SmartLedgerColors.TextInverse else SmartLedgerColors.TextSecondary
                    )
                }
            }
        }

        val activeUnitLabel = if (purchaseAsMajorUnit && hasMajorOption) selectedProduct?.LargeUnit ?: "كرتون" else selectedProduct?.SmallUnit ?: "قطعة"

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmartNumberField(
                value = quantityText,
                onValueChange = { quantityText = it },
                label = "الكمية ($activeUnitLabel)",
                isRequired = true,
                modifier = Modifier.weight(1f)
            )
            SmartNumberField(
                value = costPriceText,
                onValueChange = { costPriceText = it },
                label = "تكلفة الـ $activeUnitLabel",
                suffixText = currencySymbol,
                isRequired = true,
                modifier = Modifier.weight(1f)
            )
        }

        SmartNumberField(
            value = paidCashText,
            onValueChange = { paidCashText = it },
            label = "المبلغ المسدد نقداً للمورد الآن",
            suffixText = currencySymbol,
            imeAction = ImeAction.Done
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (selectedProduct != null) {
                    Text(
                        text = "الأثر المخزني: +${Formatters.formatQuantity(addedMinorQty)} ${selectedProduct!!.SmallUnit} تضاف للمخزن",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.AccentLight,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("إجمالي فاتورة الشراء:", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextSecondary)
                    Text(Formatters.formatCurrency(total, currencySymbol, decimals), fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("المتبقي آجل للمورد (له):", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.StateSuccessText)
                    Text(Formatters.formatCurrency(remaining, currencySymbol, decimals), fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateSuccess)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SmartLedgerColors.SurfaceCardElevated)
                .clickable { showOptionalSection = !showOptionalSection }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("رقم الفاتورة المرجعي والملاحظات (اختياري)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
            Icon(if (showOptionalSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = SmartLedgerColors.AccentLight)
        }

        AnimatedVisibility(visible = showOptionalSection) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmartTextField(
                    value = referenceNo,
                    onValueChange = { referenceNo = it },
                    label = "رقم فاتورة المورد الورقية (اختياري)"
                )
                SmartTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "ملاحظات التوريد",
                    imeAction = ImeAction.Done
                )
            }
        }
    }

    if (showSupplierPicker) {
        AlertDialog(
            onDismissRequest = { showSupplierPicker = false },
            containerColor = SmartLedgerColors.SurfaceDialog,
            title = { Text("اختر المورد", fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SmartLedgerColors.SurfaceCardElevated)
                                .clickable {
                                    selectedSupplier = null
                                    paidCashText = Formatters.formatQuantity(total)
                                    showSupplierPicker = false
                                }
                                .padding(10.dp)
                        ) {
                            Text("مورد نقدي عام (سداد نقدي كامل)", fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                        }
                    }
                    items(sortedSuppliers, key = { it.PersonId }) { sup ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SmartLedgerColors.SurfaceCard)
                                .clickable {
                                    selectedSupplier = sup
                                    showSupplierPicker = false
                                }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(sup.PersonName, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                            Text(Formatters.formatCurrency(sup.NetBalance, currencySymbol, decimals), color = SmartLedgerColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSupplierPicker = false }) {
                    Text("إغلاق", color = SmartLedgerColors.TextMuted)
                }
            }
        )
    }

    if (showProductPicker) {
        AlertDialog(
            onDismissRequest = { showProductPicker = false },
            containerColor = SmartLedgerColors.SurfaceDialog,
            title = { Text("اختر الصنف المراد توريده", fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(products, key = { it.Id }) { prod ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SmartLedgerColors.SurfaceCard)
                                .clickable {
                                    selectedProduct = prod
                                    showProductPicker = false
                                }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(prod.Name, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                                Text("المتاح: ${Formatters.formatQuantity(prod.CurrentStockMinor)} ${prod.SmallUnit}", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight)
                            }
                            Text(Formatters.formatCurrency(prod.CostPrice, currencySymbol, decimals), style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProductPicker = false }) {
                    Text("إغلاق", color = SmartLedgerColors.TextMuted)
                }
            }
        )
    }
}
