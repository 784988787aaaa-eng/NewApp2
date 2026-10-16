package com.smartledger.pro.ui.screens.pos

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.dao.AccountBalanceSummary
import com.smartledger.pro.data.local.dao.ProductStockItem
import com.smartledger.pro.data.local.entity.InvoiceEntity
import com.smartledger.pro.data.local.entity.InvoiceLineEntity
import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.viewmodels.CartLineItem
import com.smartledger.pro.ui.viewmodels.PosViewModel
import com.smartledger.pro.util.AppSettingsManager
import com.smartledger.pro.util.Formatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosSalesScreen(
    viewModel: PosViewModel = viewModel()
) {
    val products by viewModel.products.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val recentInvoices by viewModel.recentInvoices.collectAsState()
    val settings by AppSettingsManager.settings.collectAsState()
    val cartLines = viewModel.cartLines
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var quickSearchQuery by remember { mutableStateOf("") }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var showProductPickerSheet by remember { mutableStateOf(false) }
    var showCustomerPickerSheet by remember { mutableStateOf(false) }
    var showCheckoutSheet by remember { mutableStateOf(false) }
    var showInvoicesHistorySheet by remember { mutableStateOf(false) }
    var showClearCartConfirm by remember { mutableStateOf(false) }
    var editingCartLine by remember { mutableStateOf<CartLineItem?>(null) }
    var lastRemovedLine by remember { mutableStateOf<CartLineItem?>(null) }
    var completedInvoiceResult by remember { mutableStateOf<Pair<InvoiceEntity, List<InvoiceLineEntity>>?>(null) }
    var posFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var discountText by remember { mutableStateOf("0") }

    val subtotal = cartLines.sumOf { it.calculatedLineTotal }
    val discount = Formatters.parseDoubleSafe(discountText, 0.0)
    val netTotal = kotlin.math.max(0.0, subtotal - discount)

    val quickMatchedProducts = remember(products, quickSearchQuery) {
        val q = quickSearchQuery.trim()
        if (q.isEmpty()) emptyList()
        else products.filter {
            it.Name.contains(q, ignoreCase = true) ||
                (it.Barcode?.contains(q, ignoreCase = true) == true)
        }.take(6)
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
                    Column {
                        Text(
                            text = "نقطة البيع والفواتير",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        Text(
                            text = "بيع سريع بالباركود والوحدات الصغرى والكبرى",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showInvoicesHistorySheet = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "سجل الفواتير",
                                tint = SmartLedgerColors.AccentLight,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "الفواتير والمرتجعات",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.AccentLight,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (cartLines.isNotEmpty()) {
                            IconButton(
                                onClick = { showClearCartConfirm = true },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SmartLedgerColors.StateDangerSubtle)
                            ) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = "إفراغ السلة",
                                    tint = SmartLedgerColors.StateDanger,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SmartLedgerColors.SurfaceCard)
                        .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(8.dp))
                        .clickable { showCustomerPickerSheet = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = SmartLedgerColors.AccentLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = viewModel.selectedCustomer?.PersonName ?: "عميل نقدي عام (افتراضي)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        if (viewModel.selectedCustomer != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            val bal = viewModel.selectedCustomer!!.NetBalance
                            Text(
                                text = "(رصيده: ${Formatters.formatCurrency(kotlin.math.abs(bal), settings.currencySymbol, settings.decimalPlaces)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (bal > 0) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess
                            )
                        }
                    }
                    Text(
                        text = "تغيير العميل ▾",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.AccentLight
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactSearchBar(
                        query = quickSearchQuery,
                        onQueryChange = { q ->
                            quickSearchQuery = q
                            val exactBarcodeMatch = products.firstOrNull {
                                !it.Barcode.isNullOrBlank() && it.Barcode.equals(q.trim(), ignoreCase = true)
                            }
                            if (exactBarcodeMatch != null) {
                                viewModel.addItemToCart(exactBarcodeMatch, asMajorUnit = false)
                                quickSearchQuery = ""
                                posFeedbackMessage = "تمت إضافة «${exactBarcodeMatch.Name}» إلى السلة"
                            }
                        },
                        placeholder = "ابحث عن صنف لإضافته أو امسح الباركود...",
                        onScanBarcodeClick = { showBarcodeScanner = true },
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = { showProductPickerSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Icon(Icons.Default.GridView, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("الأصناف", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }

                if (quickMatchedProducts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(quickMatchedProducts, key = { it.Id }) { prod ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SmartLedgerColors.AccentSubtle)
                                    .border(1.dp, SmartLedgerColors.AccentPrimary, RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.addItemToCart(prod, asMajorUnit = false)
                                        quickSearchQuery = ""
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${prod.Name} (${Formatters.formatCurrency(prod.RetailPrice, settings.currencySymbol, settings.decimalPlaces)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SmartLedgerColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SmartLedgerColors.SurfacePanel,
                tonalElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "إجمالي الفاتورة (${Formatters.formatQuantity(cartLines.size.toDouble())} بنود)",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                            Text(
                                text = Formatters.formatCurrency(netTotal, settings.currencySymbol, settings.decimalPlaces),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.StateSuccess
                            )
                        }

                        Button(
                            onClick = rememberDebouncedClick {
                                if (cartLines.isNotEmpty()) showCheckoutSheet = true
                            },
                            enabled = cartLines.isNotEmpty(),
                            modifier = Modifier.height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("الدفع وترحيل الفاتورة", fontWeight = FontWeight.Bold)
                        }
                    }
                }
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

            if (lastRemovedLine != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SmartLedgerColors.SurfaceCardElevated)
                            .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تم حذف «${lastRemovedLine!!.name}» من السلة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.TextSecondary
                        )
                        TextButton(
                            onClick = {
                                cartLines.add(lastRemovedLine!!)
                                lastRemovedLine = null
                            }
                        ) {
                            Text("تراجع واسترجاع", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (!posFeedbackMessage.isNullOrBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SmartLedgerColors.AccentSubtle)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = posFeedbackMessage!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.AccentText,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = { posFeedbackMessage = null }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            if (cartLines.isEmpty()) {
                item {
                    SmartEmptyState(
                        icon = Icons.Default.ShoppingCart,
                        title = "سلة المبيعات فارغة",
                        description = "ابحث عن الصنف بالأعلى أو امسح الباركود بالكاميرا أو استعرض قائمة الأصناف لإضافتها.",
                        actionText = "استعراض وإضافة أصناف",
                        onActionClick = { showProductPickerSheet = true }
                    )
                }
            } else {
                items(cartLines, key = { it.productId }) { line ->
                    CartLineCard(
                        line = line,
                        currencySymbol = settings.currencySymbol,
                        decimals = settings.decimalPlaces,
                        warnLowStock = settings.warnOnLowStockInPos,
                        onUpdateQuantities = { maj, min ->
                            viewModel.updateLineQuantities(line.productId, maj, min)
                        },
                        onEditLine = { editingCartLine = line },
                        onRemoveLine = {
                            lastRemovedLine = line
                            viewModel.removeItemFromCart(line.productId)
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (showBarcodeScanner) {
            BarcodeScannerDialog(
                title = "مسح باركود الصنف للبيع",
                subtitle = "امسح الباركود لإضافة الصنف فوراً إلى سلة الفاتورة",
                onBarcodeResult = { code ->
                    showBarcodeScanner = false
                    val matched = viewModel.findAndAddByBarcode(code)
                    posFeedbackMessage = if (matched != null) {
                        "تمت إضافة «${matched.Name}» إلى السلة"
                    } else {
                        "لم يتم العثور على صنف بالباركود ($code)"
                    }
                },
                onDismiss = { showBarcodeScanner = false }
            )
        }

        if (editingCartLine != null) {
            EditCartLineDialog(
                line = editingCartLine!!,
                currencySymbol = settings.currencySymbol,
                onSave = { majQty, minQty, minPrice, majPrice, lineDisc ->
                    viewModel.updateLineQuantities(editingCartLine!!.productId, majQty, minQty)
                    viewModel.updateLinePricing(editingCartLine!!.productId, minPrice, majPrice, lineDisc)
                    editingCartLine = null
                },
                onDismiss = { editingCartLine = null }
            )
        }

        if (showProductPickerSheet) {
            ProductPickerSheet(
                products = products,
                currencySymbol = settings.currencySymbol,
                decimals = settings.decimalPlaces,
                onAddProduct = { prod, asMajor ->
                    viewModel.addItemToCart(prod, asMajorUnit = asMajor)
                    posFeedbackMessage = "تمت إضافة «${prod.Name}»"
                },
                onDismiss = { showProductPickerSheet = false }
            )
        }

        if (showCustomerPickerSheet) {
            CustomerPickerSheet(
                customers = customers,
                selectedCustomer = viewModel.selectedCustomer,
                currencySymbol = settings.currencySymbol,
                decimals = settings.decimalPlaces,
                onSelectCustomer = { cust ->
                    viewModel.selectedCustomer = cust
                    showCustomerPickerSheet = false
                },
                onDismiss = { showCustomerPickerSheet = false }
            )
        }

        if (showCheckoutSheet) {
            CheckoutSheetContent(
                subtotal = subtotal,
                initialDiscountText = discountText,
                onDiscountChange = { discountText = it },
                customer = viewModel.selectedCustomer,
                defaultPaymentType = settings.defaultPaymentType,
                warnCreditLimit = settings.warnOnCreditLimitExceeded,
                currencySymbol = settings.currencySymbol,
                decimals = settings.decimalPlaces,
                onCommit = { payType, finalDisc, paid, notes, onError ->
                    scope.launch {
                        val res = viewModel.checkoutWithDetails(payType, finalDisc, paid, notes)
                        if (res.isSuccess) {
                            discountText = "0"
                            showCheckoutSheet = false
                            completedInvoiceResult = res.getOrThrow()
                        } else {
                            onError(res.exceptionOrNull()?.message ?: "تعذر حفظ الفاتورة")
                        }
                    }
                },
                onClose = { showCheckoutSheet = false }
            )
        }

        if (completedInvoiceResult != null) {
            val (savedInv, savedLines) = completedInvoiceResult!!
            PostInvoiceSuccessDialog(
                invoice = savedInv,
                lines = savedLines,
                currencySymbol = settings.currencySymbol,
                decimals = settings.decimalPlaces,
                onShareInvoice = {
                    val shareText = buildString {
                        append("فاتورة مبيعات رقم: ${savedInv.InvoiceNumber}\n")
                        append("العميل: ${savedInv.PersonNameSnapshot}\n")
                        append("التاريخ: ${savedInv.IssuedAt.take(16)}\n")
                        append("------------------\n")
                        savedLines.forEach { l ->
                            append("• ${l.ProductNameSnapshot} (${l.UnitName}): ${Formatters.formatCurrency(l.LineTotal, settings.currencySymbol, settings.decimalPlaces)}\n")
                        }
                        append("------------------\n")
                        append("الإجمالي الصافي: ${Formatters.formatCurrency(savedInv.FinalTotal, settings.currencySymbol, settings.decimalPlaces)}\n")
                        append("المدفوع: ${Formatters.formatCurrency(savedInv.PaidAmount, settings.currencySymbol, settings.decimalPlaces)}\n")
                        if (savedInv.RemainingAmount > 0) {
                            append("المتبقي آجل: ${Formatters.formatCurrency(savedInv.RemainingAmount, settings.currencySymbol, settings.decimalPlaces)}\n")
                        }
                    }
                    try {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "مشاركة الفاتورة"))
                    } catch (_: Exception) {
                    }
                },
                onStartNewInvoice = { completedInvoiceResult = null }
            )
        }

        if (showInvoicesHistorySheet) {
            InvoicesHistorySheet(
                invoices = recentInvoices.filter { it.Type == "Sale" },
                currencySymbol = settings.currencySymbol,
                decimals = settings.decimalPlaces,
                viewModel = viewModel,
                onReverseConfirmed = { invId, reason ->
                    scope.launch {
                        val res = viewModel.reverseInvoice(invId, reason)
                        if (res.isSuccess) {
                            posFeedbackMessage = "تم إرجاع وعكس الفاتورة واستعادة الكميات للمخزن."
                        }
                    }
                },
                onDismiss = { showInvoicesHistorySheet = false }
            )
        }

        if (showClearCartConfirm) {
            ConfirmActionDialog(
                title = "إفراغ سلة المبيعات",
                message = "هل تريد بالتأكيد إفراغ كافة الأصناف (${cartLines.size} بنود) من السلة الحالية؟",
                confirmText = "نعم، إفراغ السلة",
                onConfirm = {
                    viewModel.clearCart()
                    discountText = "0"
                    showClearCartConfirm = false
                },
                onDismiss = { showClearCartConfirm = false }
            )
        }
    }
}

@Composable
fun CartLineCard(
    line: CartLineItem,
    currencySymbol: String,
    decimals: Int,
    warnLowStock: Boolean,
    onUpdateQuantities: (majorQty: Double, minorQty: Double) -> Unit,
    onEditLine: () -> Unit,
    onRemoveLine: () -> Unit
) {
    val hasMajorUnit = !line.largeUnit.isNullOrBlank() && line.unitFactor > 1.0
    val isExceeding = warnLowStock && line.isExceedingStock

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (isExceeding) SmartLedgerColors.StateWarningBorder else SmartLedgerColors.BorderGridLine
            )
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = line.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary
                    )
                    Text(
                        text = buildString {
                            append("الكمية: ${line.formattedBreakdown}")
                            if (hasMajorUnit) {
                                append(" • (1 ${line.largeUnit} = ${Formatters.formatQuantity(line.unitFactor)} ${line.smallUnit})")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.AccentLight
                    )
                    if (isExceeding) {
                        Text(
                            text = "تنبيه: الكمية المطلوبة تتجاوز الرصيد الدفتري المتاح (${Formatters.formatQuantity(line.availableStockMinor)} ${line.smallUnit})",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.StateWarning,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatCurrency(line.calculatedLineTotal, currencySymbol, decimals),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.StateSuccess
                    )
                    if (line.lineDiscount > 0.0) {
                        Text(
                            text = "خصم: ${Formatters.formatAmountOnly(line.lineDiscount, decimals)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.StateWarning
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (hasMajorUnit) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${line.largeUnit}:",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { onUpdateQuantities((line.majorQuantity - 1.0).coerceAtLeast(0.0), line.minorQuantity) },
                                modifier = Modifier.size(26.dp).clip(CircleShape).background(SmartLedgerColors.SurfaceCardElevated)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "إنقاص وحدة كبرى", tint = SmartLedgerColors.TextPrimary, modifier = Modifier.size(14.dp))
                            }
                            Text(
                                text = Formatters.formatQuantity(line.majorQuantity),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.AccentLight,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            IconButton(
                                onClick = { onUpdateQuantities(line.majorQuantity + 1.0, line.minorQuantity) },
                                modifier = Modifier.size(26.dp).clip(CircleShape).background(SmartLedgerColors.AccentSubtle)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "زيادة وحدة كبرى", tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${line.smallUnit}:",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onUpdateQuantities(line.majorQuantity, (line.minorQuantity - 1.0).coerceAtLeast(0.0)) },
                            modifier = Modifier.size(26.dp).clip(CircleShape).background(SmartLedgerColors.SurfaceCardElevated)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "إنقاص وحدة صغرى", tint = SmartLedgerColors.TextPrimary, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            text = Formatters.formatQuantity(line.minorQuantity),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.AccentLight,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = { onUpdateQuantities(line.majorQuantity, line.minorQuantity + 1.0) },
                            modifier = Modifier.size(26.dp).clip(CircleShape).background(SmartLedgerColors.AccentPrimary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "زيادة وحدة صغرى", tint = SmartLedgerColors.TextInverse, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onEditLine,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SmartLedgerColors.SurfaceCardElevated)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل الكمية أو السعر", tint = SmartLedgerColors.TextSecondary, modifier = Modifier.size(14.dp))
                    }
                    IconButton(
                        onClick = onRemoveLine,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SmartLedgerColors.StateDangerSubtle)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الصنف من السلة", tint = SmartLedgerColors.StateDanger, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditCartLineDialog(
    line: CartLineItem,
    currencySymbol: String,
    onSave: (majQty: Double, minQty: Double, minPrice: Double, majPrice: Double, lineDiscount: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val hasMajor = !line.largeUnit.isNullOrBlank() && line.unitFactor > 1.0
    var minQtyText by remember { mutableStateOf(Formatters.formatQuantity(line.minorQuantity)) }
    var majQtyText by remember { mutableStateOf(Formatters.formatQuantity(line.majorQuantity)) }
    var minPriceText by remember { mutableStateOf(Formatters.formatQuantity(line.minorUnitPrice)) }
    var majPriceText by remember { mutableStateOf(Formatters.formatQuantity(line.majorUnitPrice)) }
    var discountText by remember { mutableStateOf(Formatters.formatQuantity(line.lineDiscount)) }

    val minQty = Formatters.parseDoubleSafe(minQtyText, 0.0)
    val majQty = if (hasMajor) Formatters.parseDoubleSafe(majQtyText, 0.0) else 0.0
    val minPrice = Formatters.parseDoubleSafe(minPriceText, line.minorUnitPrice)
    val majPrice = Formatters.parseDoubleSafe(majPriceText, line.majorUnitPrice)
    val disc = Formatters.parseDoubleSafe(discountText, 0.0)

    AdaptiveFormHost(
        mode = FormWindowMode.COMPACT_DIALOG,
        title = "تعديل بند: ${line.name}",
        subtitle = UnitConversionEngine.describeUnitRelation(line.smallUnit, line.largeUnit, line.unitFactor),
        onDismiss = onDismiss,
        primaryButtonText = "تحديث البند",
        isPrimaryEnabled = (minQty > 0.0 || majQty > 0.0) && minPrice > 0.0,
        onPrimaryClick = {
            onSave(majQty, minQty, minPrice, majPrice, disc)
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmartNumberField(
                value = minQtyText,
                onValueChange = { minQtyText = it },
                label = "الكمية (${line.smallUnit})",
                modifier = Modifier.weight(1f)
            )
            SmartNumberField(
                value = minPriceText,
                onValueChange = { minPriceText = it },
                label = "سعر الـ ${line.smallUnit}",
                suffixText = currencySymbol,
                modifier = Modifier.weight(1f)
            )
        }

        if (hasMajor) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmartNumberField(
                    value = majQtyText,
                    onValueChange = { majQtyText = it },
                    label = "الكمية (${line.largeUnit})",
                    modifier = Modifier.weight(1f)
                )
                SmartNumberField(
                    value = majPriceText,
                    onValueChange = { majPriceText = it },
                    label = "سعر الـ ${line.largeUnit}",
                    suffixText = currencySymbol,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        SmartNumberField(
            value = discountText,
            onValueChange = { discountText = it },
            label = "خصم خاص على هذا البند",
            suffixText = currencySymbol,
            imeAction = ImeAction.Done
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductPickerSheet(
    products: List<ProductStockItem>,
    currencySymbol: String,
    decimals: Int,
    onAddProduct: (ProductStockItem, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(products, search) {
        if (search.isBlank()) products
        else products.filter {
            it.Name.contains(search, ignoreCase = true) ||
                (it.Barcode?.contains(search, ignoreCase = true) == true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SmartLedgerColors.SurfacePanel
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "اختيار أصناف للفاتورة",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SmartLedgerColors.TextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = SmartLedgerColors.TextMuted)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            CompactSearchBar(
                query = search,
                onQueryChange = { search = it },
                placeholder = "ابحث باسم الصنف أو الباركود..."
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.Id }) { product ->
                    val stockBreakdown = UnitConversionEngine.breakdownStock(
                        totalMinorQty = product.CurrentStockMinor,
                        smallUnit = product.SmallUnit,
                        largeUnit = product.LargeUnit,
                        unitFactor = product.UnitFactor
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = SolidColor(SmartLedgerColors.BorderGridLine)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    product.Name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = SmartLedgerColors.TextPrimary
                                )
                                Text(
                                    "المتاح: ${stockBreakdown.formattedText}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (product.CurrentStockMinor <= 0) SmartLedgerColors.StateDanger else SmartLedgerColors.AccentLight
                                )
                                Text(
                                    "سعر ${product.SmallUnit}: ${Formatters.formatCurrency(product.RetailPrice, currencySymbol, decimals)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.StateSuccess
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!product.LargeUnit.isNullOrBlank() && product.UnitFactor > 1.0) {
                                    OutlinedButton(
                                        onClick = { onAddProduct(product, true) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "+1 ${product.LargeUnit}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SmartLedgerColors.AccentLight
                                        )
                                    }
                                }
                                Button(
                                    onClick = { onAddProduct(product, false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "+1 ${product.SmallUnit}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerPickerSheet(
    customers: List<AccountBalanceSummary>,
    selectedCustomer: AccountBalanceSummary?,
    currencySymbol: String,
    decimals: Int,
    onSelectCustomer: (AccountBalanceSummary?) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(customers, search) {
        if (search.isBlank()) customers
        else customers.filter {
            it.PersonName.contains(search, ignoreCase = true) ||
                (it.Phone?.contains(search, ignoreCase = true) == true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SmartLedgerColors.SurfacePanel
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Text(
                "اختيار العميل للفاتورة",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SmartLedgerColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            CompactSearchBar(
                query = search,
                onQueryChange = { search = it },
                placeholder = "ابحث باسم العميل أو رقم الهاتف..."
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedCustomer == null) SmartLedgerColors.AccentSubtle else SmartLedgerColors.SurfaceCardElevated)
                    .border(
                        1.dp,
                        if (selectedCustomer == null) SmartLedgerColors.AccentPrimary else SmartLedgerColors.BorderSubtle,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectCustomer(null) }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("عميل نقدي عام (افتراضي - نقدي فقط)", fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                if (selectedCustomer == null) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.PersonId }) { c ->
                    val isSelected = selectedCustomer?.PersonId == c.PersonId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) SmartLedgerColors.AccentSubtle else SmartLedgerColors.SurfaceCard)
                            .border(
                                1.dp,
                                if (isSelected) SmartLedgerColors.AccentPrimary else SmartLedgerColors.BorderGridLine,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectCustomer(c) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(c.PersonName, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                            if (!c.Phone.isNullOrBlank()) {
                                Text(c.Phone, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                            }
                        }
                        Text(
                            text = Formatters.formatCurrency(c.NetBalance, currencySymbol, decimals),
                            fontWeight = FontWeight.Bold,
                            color = if (c.NetBalance >= 0) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CheckoutSheetContent(
    subtotal: Double,
    initialDiscountText: String,
    onDiscountChange: (String) -> Unit,
    customer: AccountBalanceSummary?,
    defaultPaymentType: String,
    warnCreditLimit: Boolean,
    currencySymbol: String,
    decimals: Int,
    onCommit: (payType: String, discount: Double, paid: Double, notes: String?, onError: (String) -> Unit) -> Unit,
    onClose: () -> Unit
) {
    var paymentType by remember {
        mutableStateOf(if (customer == null) "Cash" else defaultPaymentType)
    }
    var discountLocalText by remember { mutableStateOf(initialDiscountText) }
    val discountVal = Formatters.parseDoubleSafe(discountLocalText, 0.0).coerceIn(0.0, subtotal)
    val netTotal = kotlin.math.max(0.0, subtotal - discountVal)

    var paidAmountText by remember(netTotal, paymentType) {
        mutableStateOf(
            when (paymentType) {
                "Cash" -> Formatters.formatQuantity(netTotal)
                "Credit" -> "0"
                else -> Formatters.formatQuantity(netTotal / 2.0)
            }
        )
    }
    var tenderedCashText by remember(netTotal) { mutableStateOf(Formatters.formatQuantity(netTotal)) }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val paidAmount = when (paymentType) {
        "Cash" -> netTotal
        "Credit" -> 0.0
        else -> Formatters.parseDoubleSafe(paidAmountText, 0.0).coerceIn(0.0, netTotal)
    }
    val remainingDebt = kotlin.math.max(0.0, netTotal - paidAmount)
    val tenderedCash = Formatters.parseDoubleSafe(tenderedCashText, netTotal)
    val changeForCustomer = if (paymentType == "Cash" && tenderedCash > netTotal) (tenderedCash - netTotal) else 0.0

    val requiresRegisteredCustomer = remainingDebt > 0.001 && customer == null
    val exceedsCreditLimit = warnCreditLimit &&
        customer != null &&
        customer.CreditLimit > 0.0 &&
        (customer.NetBalance + remainingDebt) > customer.CreditLimit

    AdaptiveFormHost(
        mode = FormWindowMode.BOTTOM_SHEET,
        title = "سداد وترحيل الفاتورة",
        subtitle = "العميل: ${customer?.PersonName ?: "عميل نقدي عام"}",
        onDismiss = onClose,
        primaryButtonText = "تأكيد وترحيل الفاتورة",
        isPrimaryEnabled = netTotal > 0.0 && !requiresRegisteredCustomer,
        errorMessage = errorMsg ?: if (requiresRegisteredCustomer) {
            "البيع الآجل أو الجزئي يتطلب اختيار عميل مسجل في الدفتر بدلاً من «عميل نقدي عام»."
        } else null,
        onPrimaryClick = {
            onDiscountChange(discountLocalText)
            onCommit(paymentType, discountVal, paidAmount, notes) { err ->
                errorMsg = err
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SmartLedgerColors.SurfaceCardElevated)
                .padding(4.dp)
        ) {
            listOf("Cash" to "نقدي كامل", "Credit" to "آجل كامل", "Partial" to "سداد جزئي").forEach { (type, label) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (paymentType == type) SmartLedgerColors.AccentPrimary else Color.Transparent)
                        .clickable {
                            paymentType = type
                            errorMsg = null
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        color = if (paymentType == type) SmartLedgerColors.TextInverse else SmartLedgerColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        SmartNumberField(
            value = discountLocalText,
            onValueChange = {
                discountLocalText = it
                onDiscountChange(it)
            },
            label = "خصم على إجمالي الفاتورة (اختياري)",
            suffixText = currencySymbol
        )

        if (paymentType == "Partial") {
            SmartNumberField(
                value = paidAmountText,
                onValueChange = { paidAmountText = it },
                label = "المبلغ المدفوع نقداً الآن",
                suffixText = currencySymbol,
                isRequired = true
            )
        } else if (paymentType == "Cash") {
            SmartNumberField(
                value = tenderedCashText,
                onValueChange = { tenderedCashText = it },
                label = "المبلغ المستلم من العميل (لحساب الفكة/المرتجع)",
                suffixText = currencySymbol
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("إجمالي البنود قبل الخصم:", color = SmartLedgerColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(Formatters.formatCurrency(subtotal, currencySymbol, decimals), fontWeight = FontWeight.SemiBold, color = SmartLedgerColors.TextPrimary)
                }
                if (discountVal > 0.0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الخصم الممنوح:", color = SmartLedgerColors.StateWarning, style = MaterialTheme.typography.bodyMedium)
                        Text("-${Formatters.formatCurrency(discountVal, currencySymbol, decimals)}", fontWeight = FontWeight.SemiBold, color = SmartLedgerColors.StateWarning)
                    }
                }
                Divider(color = SmartLedgerColors.BorderSubtle)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الإجمالي الصافي المطلوب:", color = SmartLedgerColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text(Formatters.formatCurrency(netTotal, currencySymbol, decimals), fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight, style = MaterialTheme.typography.titleMedium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("المسدد نقداً:", color = SmartLedgerColors.StateSuccessText, style = MaterialTheme.typography.bodyMedium)
                    Text(Formatters.formatCurrency(paidAmount, currencySymbol, decimals), fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateSuccess)
                }
                if (remainingDebt > 0.0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المتبقي آجل على العميل:", color = SmartLedgerColors.StateDangerText, style = MaterialTheme.typography.bodyMedium)
                        Text(Formatters.formatCurrency(remainingDebt, currencySymbol, decimals), fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateDanger)
                    }
                }
                if (changeForCustomer > 0.0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المتبقي للعميل (الفكة):", color = SmartLedgerColors.AccentLight, style = MaterialTheme.typography.bodyMedium)
                        Text(Formatters.formatCurrency(changeForCustomer, currencySymbol, decimals), fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                    }
                }
            }
        }

        if (exceedsCreditLimit) {
            Text(
                text = "تنبيه ائتماني: إضافة هذا الدين سيجعل رصيد العميل يتجاوز سقف الائتمان المسموح (${Formatters.formatCurrency(customer!!.CreditLimit, currencySymbol, decimals)})!",
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.StateWarning,
                fontWeight = FontWeight.Bold
            )
        }

        SmartTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "ملاحظات على الفاتورة (اختياري)",
            imeAction = ImeAction.Done
        )
    }
}

@Composable
private fun PostInvoiceSuccessDialog(
    invoice: InvoiceEntity,
    lines: List<InvoiceLineEntity>,
    currencySymbol: String,
    decimals: Int,
    onShareInvoice: () -> Unit,
    onStartNewInvoice: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onStartNewInvoice,
        containerColor = SmartLedgerColors.SurfaceDialog,
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = SmartLedgerColors.StateSuccess,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                text = "تم ترحيل الفاتورة بنجاح",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SmartLedgerColors.TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "رقم الفاتورة: ${invoice.InvoiceNumber}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = SmartLedgerColors.AccentLight
                )
                Text("العميل: ${invoice.PersonNameSnapshot}", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextSecondary)
                Text("عدد البنود: ${Formatters.formatQuantity(lines.size.toDouble())}", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextSecondary)
                Divider(color = SmartLedgerColors.BorderSubtle)
                Text(
                    text = "الإجمالي الصافي: ${Formatters.formatCurrency(invoice.FinalTotal, currencySymbol, decimals)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SmartLedgerColors.StateSuccess
                )
                Text(
                    text = "المدفوع: ${Formatters.formatCurrency(invoice.PaidAmount, currencySymbol, decimals)} • المتبقي: ${Formatters.formatCurrency(invoice.RemainingAmount, currencySymbol, decimals)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SmartLedgerColors.TextMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onStartNewInvoice,
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("فاتورة جديدة", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onShareInvoice,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("مشاركة / إرسال")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoicesHistorySheet(
    invoices: List<InvoiceEntity>,
    currencySymbol: String,
    decimals: Int,
    viewModel: PosViewModel,
    onReverseConfirmed: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var invoicePendingReverse by remember { mutableStateOf<InvoiceEntity?>(null) }
    var reverseReason by remember { mutableStateOf("مرتجع مبيعات معتمد") }
    var expandedInvoiceId by remember { mutableStateOf<Long?>(null) }
    var expandedLines by remember { mutableStateOf<List<InvoiceLineEntity>>(emptyList()) }

    LaunchedEffect(expandedInvoiceId) {
        expandedLines = if (expandedInvoiceId != null) {
            viewModel.getInvoiceLines(expandedInvoiceId!!)
        } else emptyList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SmartLedgerColors.SurfacePanel
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "سجل فواتير المبيعات والمرتجعات",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary
                    )
                    Text(
                        "اضغط على الفاتورة لعرض بنودها، أو اختر «إرجاع» لعكس أثرها المحاسبي والمخزني",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = SmartLedgerColors.TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (invoices.isEmpty()) {
                SmartEmptyState(
                    icon = Icons.Default.Receipt,
                    title = "لا توجد فواتير مبيعات صادرة بعد",
                    description = "الفواتير التي تقوم بترحيلها من نقطة البيع ستظهر هنا مع كامل تفاصيلها."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(invoices, key = { it.Id }) { inv ->
                        val isReversed = inv.Status == "Reversed"
                        val isExpanded = expandedInvoiceId == inv.Id

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedInvoiceId = if (isExpanded) null else inv.Id
                                },
                            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = SolidColor(SmartLedgerColors.BorderSubtle)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${inv.InvoiceNumber} • ${inv.PersonNameSnapshot}",
                                            fontWeight = FontWeight.Bold,
                                            color = SmartLedgerColors.TextPrimary
                                        )
                                        Text(
                                            text = "الإجمالي: ${Formatters.formatCurrency(inv.FinalTotal, currencySymbol, decimals)} • ${inv.IssuedAt.take(16)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SmartLedgerColors.TextMuted
                                        )
                                        Text(
                                            text = if (isReversed) "الحالة: معكوسة / مرتجعة" else "الحالة: مكتملة (${inv.PaymentType})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isReversed) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess
                                        )
                                    }

                                    if (!isReversed) {
                                        OutlinedButton(
                                            onClick = { invoicePendingReverse = inv },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartLedgerColors.StateDanger),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("إرجاع الفاتورة", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (isExpanded && expandedLines.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = SmartLedgerColors.BorderGridLine)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    expandedLines.forEach { line ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "• ${line.ProductNameSnapshot} (${line.UnitName})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SmartLedgerColors.TextSecondary
                                            )
                                            Text(
                                                text = Formatters.formatCurrency(line.LineTotal, currencySymbol, decimals),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = SmartLedgerColors.AccentLight
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (invoicePendingReverse != null) {
        ConfirmActionDialog(
            title = "تأكيد إرجاع وعكس الفاتورة",
            message = "سيتم عكس الفاتورة رقم (${invoicePendingReverse!!.InvoiceNumber}) بقيمة ${Formatters.formatCurrency(invoicePendingReverse!!.FinalTotal, currencySymbol, decimals)}:\n• إعادة كافة الكميات المباعة إلى المخزن.\n• قيد إشعار دائن وعكس الأثر المالي على حساب العميل.\nهل أنت متأكد من المتابعة؟",
            confirmText = "تأكيد الإرجاع المحاسبي",
            onConfirm = {
                onReverseConfirmed(invoicePendingReverse!!.Id, reverseReason)
                invoicePendingReverse = null
            },
            onDismiss = { invoicePendingReverse = null }
        )
    }
}
