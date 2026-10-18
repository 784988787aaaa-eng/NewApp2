package com.smartledger.pro.ui.screens.pos

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.dao.AccountBalanceSummary
import com.smartledger.pro.data.local.dao.ProductStockItem
import com.smartledger.pro.data.local.entity.InvoiceEntity
import com.smartledger.pro.data.local.entity.InvoiceLineEntity
import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.screens.settings.ScreenFieldVisibilityStore
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.viewmodels.CartLineItem
import com.smartledger.pro.ui.viewmodels.PosViewModel
import com.smartledger.pro.util.NumberFormatter
import com.smartledger.pro.util.ThermalPrinterHelper
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PosSalesScreen(
    onNavigateToInventory: (() -> Unit)? = null,
    viewModel: PosViewModel = viewModel()
) {
    val products by viewModel.products.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val recentInvoices by viewModel.recentInvoices.collectAsState()
    val identity by viewModel.businessIdentity.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val fieldVisibility by ScreenFieldVisibilityStore.observe(context).collectAsState()
    val financialAccounts by viewModel.financialAccounts.collectAsState()

    val cartLines = viewModel.cartLines
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showProductPickerSheet by rememberSaveable { mutableStateOf(false) }
    var showCustomerPickerSheet by rememberSaveable { mutableStateOf(false) }
    var showCheckoutSheet by rememberSaveable { mutableStateOf(false) }
    var showInvoicesHistorySheet by rememberSaveable { mutableStateOf(false) }
    var showClearCartConfirm by rememberSaveable { mutableStateOf(false) }
    var showBarcodeScanner by rememberSaveable { mutableStateOf(false) }

    var editingLineProductId by rememberSaveable { mutableStateOf<Long?>(null) }
    val lineToEditQuantityOrPrice = remember(cartLines.toList(), editingLineProductId) {
        editingLineProductId?.let { pId -> cartLines.firstOrNull { it.productId == pId } }
    }
    var lastRemovedItem by remember { mutableStateOf<CartLineItem?>(null) }
    var lastUnmatchedBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isStatusError by rememberSaveable { mutableStateOf(false) }
    var quickSearchQuery by rememberSaveable { mutableStateOf("") }

    var completedInvoiceResult by remember {
        mutableStateOf<Pair<InvoiceEntity, List<Triple<String, String, Double>>>?>(null)
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            isStatusError = true
            statusMessage = "تم رفض إذن البلوتوث؛ يلزم منح الإذن للاتصال بالطابعة الحرارية."
        }
    }

    val subtotal = remember(cartLines.toList()) {
        NumberFormatter.roundCurrency(cartLines.sumOf { it.calculatedLineTotal })
    }

    val knownBarcodes = remember(products) {
        products.mapNotNull { p ->
            if (!p.Barcode.isNullOrBlank()) Pair(p.Barcode, p.Name) else null
        }
    }

    val filteredQuickProducts = remember(products, quickSearchQuery) {
        if (quickSearchQuery.isBlank()) {
            products.take(16)
        } else {
            val q = quickSearchQuery.trim()
            products.filter {
                it.Name.contains(q, ignoreCase = true) ||
                    (it.Barcode?.contains(q, ignoreCase = true) == true)
            }.take(20)
        }
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
                    Column {
                        Text(
                            text = "نقطة البيع السريعة (POS)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        Text(
                            text = "دعم البيع بالوحدة الكبرى والصغرى مع احتساب تلقائي للربح والمخزون",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { showInvoicesHistorySheet = true },
                            shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .pressScaleEffect()
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("الفواتير (${recentInvoices.count { it.Type == "Sale" }})", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextPrimary)
                        }

                        if (cartLines.isNotEmpty()) {
                            IconButton(
                                onClick = { showClearCartConfirm = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(SmartLedgerTokens.buttonCorner))
                                    .background(SmartLedgerColors.StateDangerSubtle)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "إفراغ السلة", tint = SmartLedgerColors.StateDanger, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // شريط اختيار العميل السريع
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SmartLedgerColors.SurfaceCard)
                        .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(10.dp))
                        .clickable { showCustomerPickerSheet = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = SmartLedgerColors.AccentLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = viewModel.selectedCustomer?.PersonName ?: "عميل نقدي عام (افتراضي)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            val cust = viewModel.selectedCustomer
                            if (cust != null) {
                                Text(
                                    text = "رصيده الحالي: ${NumberFormatter.formatCurrency(abs(cust.NetBalance), AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} ${if (cust.NetBalance >= 0) "عليه" else "له"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (cust.NetBalance > 0) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (viewModel.selectedCustomer != null) {
                            IconButton(
                                onClick = { viewModel.selectedCustomer = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إعادة لعميل نقدي", tint = SmartLedgerColors.TextMuted, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = "تغيير العميل ▾",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.AccentLight
                        )
                    }
                }

                CompactSearchBar(
                    query = quickSearchQuery,
                    onQueryChange = { quickSearchQuery = it },
                    placeholder = "ابحث عن صنف لإضافته فوراً أو امسح الباركود...",
                    onBarcodeClick = if (fieldVisibility.posBarcode) {
                        { showBarcodeScanner = true }
                    } else null
                )

                if (products.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filteredQuickProducts, key = { it.Id }) { prod ->
                            val isOut = prod.CurrentStockMinor <= 0.0
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SmartLedgerColors.SurfaceCardElevated)
                                    .border(
                                        1.dp,
                                        if (isOut) SmartLedgerColors.StateDangerBorder else SmartLedgerColors.BorderSubtle,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .bounceClick {
                                        viewModel.addItemToCart(prod, asMajorUnit = false)
                                        if (quickSearchQuery.isNotBlank()) quickSearchQuery = ""
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = prod.Name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SmartLedgerColors.TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = NumberFormatter.formatAmount(prod.RetailPrice, AppDesignState.decimalPlaces),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SmartLedgerColors.StateSuccess,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = SmartLedgerColors.SurfacePanel,
                tonalElevation = 8.dp,
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SmartLedgerTokens.screenHorizontalPadding, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "إجمالي السلة (${cartLines.size} أصناف)",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                            Text(
                                text = NumberFormatter.formatCurrency(subtotal, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.StateSuccess
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showProductPickerSheet = true },
                                modifier = Modifier.heightIn(min = 42.dp).pressScaleEffect(),
                                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("الأصناف", color = SmartLedgerColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    if (cartLines.isNotEmpty()) showCheckoutSheet = true
                                },
                                enabled = cartLines.isNotEmpty(),
                                modifier = Modifier
                                    .heightIn(min = 42.dp)
                                    .pressScaleEffect(enabled = cartLines.isNotEmpty()),
                                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("سداد وترحيل الفاتورة", fontWeight = FontWeight.Bold)
                            }
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
                .padding(horizontal = SmartLedgerTokens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                StatusFeedbackBanner(
                    message = statusMessage,
                    isError = isStatusError,
                    actionLabel = when {
                        lastRemovedItem != null -> "تراجع عن الحذف"
                        lastUnmatchedBarcode != null && onNavigateToInventory != null -> "تعريف بالمخزون"
                        else -> null
                    },
                    onAction = when {
                        lastRemovedItem != null -> {
                            {
                                val restored = lastRemovedItem!!
                                viewModel.restoreRemovedCartItem(restored)
                                lastRemovedItem = null
                                statusMessage = null
                            }
                        }
                        lastUnmatchedBarcode != null && onNavigateToInventory != null -> {
                            {
                                lastUnmatchedBarcode = null
                                statusMessage = null
                                onNavigateToInventory()
                            }
                        }
                        else -> null
                    },
                    onDismiss = {
                        statusMessage = null
                        lastRemovedItem = null
                        lastUnmatchedBarcode = null
                    }
                )
            }

            posCartItems(
                cartLines = cartLines,
                onAddProducts = { showProductPickerSheet = true },
                onUpdateQuantities = { line, major, minor ->
                    viewModel.updateLineQuantities(line.productId, major, minor)
                },
                onEditLineDetails = { line -> editingLineProductId = line.productId },
                onRemoveLine = { line ->
                    lastRemovedItem = line
                    lastUnmatchedBarcode = null
                    viewModel.removeItemFromCart(line.productId)
                    isStatusError = false
                    statusMessage = "تمت إزالة «${line.name}» من السلة."
                }
            )

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // نافذة مسح الباركود العائمة
        if (showBarcodeScanner) {
            BarcodeScannerSheet(
                title = "مسح باركود الصنف للبيع",
                subtitle = "امسح الرمز لإضافة الصنف فوراً إلى سلة الفاتورة",
                knownBarcodes = knownBarcodes,
                onBarcodeResult = { code ->
                    showBarcodeScanner = false
                    val match = products.firstOrNull { it.Barcode.equals(code, ignoreCase = true) }
                    if (match != null) {
                        viewModel.addItemToCart(match, asMajorUnit = false)
                        lastUnmatchedBarcode = null
                        isStatusError = false
                        statusMessage = "تمت إضافة «${match.Name}» إلى السلة."
                    } else {
                        lastRemovedItem = null
                        lastUnmatchedBarcode = code
                        isStatusError = true
                        statusMessage = "الباركود ($code) غير مسجل في المخزن. يمكنك تعريفه من شاشة المخزون."
                    }
                },
                onDismiss = { showBarcodeScanner = false }
            )
        }

        // نافذة تعديل كمية وسعر بند في السلة العائمة فوق لوحة المفاتيح مع حماية هامش الربح (UX-003)
        if (lineToEditQuantityOrPrice != null) {
            val targetLine = lineToEditQuantityOrPrice!!
            FloatingModalDialog(onDismissRequest = { editingLineProductId = null }) {
                var minorQtyText by rememberSaveable(targetLine.productId) { mutableStateOf(NumberFormatter.formatQuantity(targetLine.minorQuantity)) }
                var majorQtyText by rememberSaveable(targetLine.productId) { mutableStateOf(NumberFormatter.formatQuantity(targetLine.majorQuantity)) }
                var minorPriceText by rememberSaveable(targetLine.productId) { mutableStateOf(NumberFormatter.formatQuantity(targetLine.minorUnitPrice)) }
                var majorPriceText by rememberSaveable(targetLine.productId) { mutableStateOf(NumberFormatter.formatQuantity(targetLine.majorUnitPrice)) }
                val hasMajor = !targetLine.largeUnit.isNullOrBlank() && targetLine.unitFactor > 1.0

                val enteredMinorPrice = NumberFormatter.parseDouble(minorPriceText, targetLine.minorUnitPrice)
                val enteredMajorPrice = NumberFormatter.parseDouble(majorPriceText, targetLine.majorUnitPrice)
                val isMinorBelowCost = enteredMinorPrice > 0.0 && targetLine.minorUnitCost > 0.0 && enteredMinorPrice < targetLine.minorUnitCost
                val isMajorBelowCost = hasMajor && enteredMajorPrice > 0.0 && targetLine.minorUnitCost > 0.0 &&
                    enteredMajorPrice < (targetLine.minorUnitCost * targetLine.unitFactor)

                FormSheetContainer(
                    title = "تعديل الكمية والسعر للبند",
                    subtitle = "${targetLine.name} • التكلفة المسجلة: ${NumberFormatter.formatCurrency(targetLine.minorUnitCost, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} / ${targetLine.smallUnit}",
                    headerIcon = Icons.Default.EditNote,
                    onDismiss = { editingLineProductId = null },
                    primaryButtonText = "تحديث البند",
                    onPrimaryClick = {
                        val newMinQty = NumberFormatter.parseDouble(minorQtyText, 0.0)
                        val newMajQty = if (hasMajor) NumberFormatter.parseDouble(majorQtyText, 0.0) else 0.0
                        val newMinPrice = NumberFormatter.parseDouble(minorPriceText, targetLine.minorUnitPrice)
                        val newMajPrice = NumberFormatter.parseDouble(majorPriceText, targetLine.majorUnitPrice)
                        viewModel.updateLineUnitPrice(targetLine.productId, newMinPrice, newMajPrice)
                        viewModel.updateLineQuantities(targetLine.productId, newMajQty, newMinQty)
                        editingLineProductId = null
                    }
                ) {
                    if (isMinorBelowCost || isMajorBelowCost) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SmartLedgerColors.StateDangerSubtle)
                                .border(1.dp, SmartLedgerColors.StateDangerBorder, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = SmartLedgerColors.StateDanger, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تنبيه: السعر المدخل أقل من تكلفة شراء الصنف المسجلة (${NumberFormatter.formatCurrency(targetLine.minorUnitCost, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} للـ ${targetLine.smallUnit}).",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.StateDangerText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    FormSectionGroup(title = "الوحدة الصغرى (${targetLine.smallUnit})") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SmartTextField(
                                value = minorQtyText,
                                onValueChange = { minorQtyText = it },
                                label = "الكمية (${targetLine.smallUnit})",
                                keyboardType = KeyboardType.Decimal,
                                modifier = Modifier.weight(1f)
                            )
                            SmartTextField(
                                value = minorPriceText,
                                onValueChange = { minorPriceText = it },
                                label = "سعر الـ ${targetLine.smallUnit}",
                                keyboardType = KeyboardType.Decimal,
                                imeAction = if (hasMajor) ImeAction.Next else ImeAction.Done,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (hasMajor) {
                        FormSectionGroup(title = "الوحدة الكبرى (${targetLine.largeUnit})") {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SmartTextField(
                                    value = majorQtyText,
                                    onValueChange = { majorQtyText = it },
                                    label = "الكمية (${targetLine.largeUnit})",
                                    keyboardType = KeyboardType.Decimal,
                                    modifier = Modifier.weight(1f)
                                )
                                SmartTextField(
                                    value = majorPriceText,
                                    onValueChange = { majorPriceText = it },
                                    label = "سعر الـ ${targetLine.largeUnit}",
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Done,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // نافذة اختيار الأصناف العائمة فوق لوحة المفاتيح
        if (showProductPickerSheet) {
            FloatingModalDialog(onDismissRequest = { showProductPickerSheet = false }) {
                var pickerSearch by rememberSaveable { mutableStateOf("") }
                val filtered = remember(products, pickerSearch) {
                    if (pickerSearch.isBlank()) products
                    else products.filter {
                        it.Name.contains(pickerSearch, ignoreCase = true) ||
                            (it.Barcode?.contains(pickerSearch, ignoreCase = true) == true)
                    }
                }

                FormSheetContainer(
                    title = "اختيار الأصناف للفاتورة",
                    subtitle = "اضغط لإضافة الصنف بالوحدة الصغرى أو الكبرى",
                    headerIcon = Icons.Default.AddShoppingCart,
                    onDismiss = { showProductPickerSheet = false },
                    primaryButtonText = "تم (${cartLines.size} أصناف بالسلة)",
                    onPrimaryClick = { showProductPickerSheet = false }
                ) {
                    CompactSearchBar(
                        query = pickerSearch,
                        onQueryChange = { pickerSearch = it },
                        placeholder = "ابحث عن صنف بالاسم أو الباركود...",
                        resultCount = filtered.size
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
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
                                    brush = SolidColor(SmartLedgerColors.BorderSubtle)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = product.Name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = SmartLedgerColors.TextPrimary
                                        )
                                        Text(
                                            text = "المتاح: ${stockBreakdown.formattedText}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (product.CurrentStockMinor <= 0) SmartLedgerColors.StateDanger else SmartLedgerColors.AccentLight
                                        )
                                        Text(
                                            text = "سعر ${product.SmallUnit}: ${NumberFormatter.formatCurrency(product.RetailPrice, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SmartLedgerColors.StateSuccess
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (!product.LargeUnit.isNullOrBlank() && product.UnitFactor > 1.0) {
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.addItemToCart(product, asMajorUnit = true)
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.pressScaleEffect()
                                            ) {
                                                Text(
                                                    text = "+1 ${product.LargeUnit}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SmartLedgerColors.AccentLight,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.addItemToCart(product, asMajorUnit = false)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.pressScaleEffect()
                                        ) {
                                            Text(
                                                text = "+1 ${product.SmallUnit}",
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

        // نافذة اختيار العميل العائمة فوق لوحة المفاتيح
        if (showCustomerPickerSheet) {
            FloatingModalDialog(onDismissRequest = { showCustomerPickerSheet = false }) {
                var custSearch by rememberSaveable { mutableStateOf("") }
                val filteredCustomers = remember(customers, custSearch) {
                    if (custSearch.isBlank()) customers
                    else customers.filter {
                        it.PersonName.contains(custSearch, ignoreCase = true) ||
                            (it.Phone?.contains(custSearch) == true)
                    }
                }

                FormSheetContainer(
                    title = "اختيار العميل للفاتورة",
                    subtitle = "اختر عميلاً مسجلاً أو استمر بعميل نقدي عام",
                    headerIcon = Icons.Default.PersonSearch,
                    onDismiss = { showCustomerPickerSheet = false },
                    primaryButtonText = "عميل نقدي عام (افتراضي)",
                    onPrimaryClick = {
                        viewModel.selectedCustomer = null
                        showCustomerPickerSheet = false
                    }
                ) {
                    CompactSearchBar(
                        query = custSearch,
                        onQueryChange = { custSearch = it },
                        placeholder = "ابحث باسم العميل أو رقم الهاتف...",
                        resultCount = filteredCustomers.size
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredCustomers, key = { it.PersonId }) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SmartLedgerColors.SurfaceCard)
                                    .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.selectedCustomer = c
                                        showCustomerPickerSheet = false
                                    }
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
                                    text = NumberFormatter.formatCurrency(abs(c.NetBalance), AppDesignState.currencySymbol, AppDesignState.decimalPlaces) +
                                        if (c.NetBalance >= 0) " عليه" else " له",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (c.NetBalance >= 0) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess
                                )
                            }
                        }
                    }
                }
            }
        }

        // نافذة سداد وترحيل الفاتورة العائمة فوق لوحة المفاتيح
        if (showCheckoutSheet) {
            FloatingModalDialog(onDismissRequest = { showCheckoutSheet = false }) {
                var checkoutError by remember { mutableStateOf<String?>(null) }
                var isCheckingOut by remember { mutableStateOf(false) }

                CheckoutSheetContent(
                    subtotalAmount = subtotal,
                    selectedCustomer = viewModel.selectedCustomer,
                    isSaving = isCheckingOut,
                    errorMessage = checkoutError,
                    onSelectCustomerClick = {
                        showCheckoutSheet = false
                        showCustomerPickerSheet = true
                    },
                    financialAccounts = financialAccounts,
                    onCommit = { payType, discountAmt, paidAmt, notes, finAccId ->
                        val itemsSnapshot = cartLines.map {
                            Triple(it.name, it.formattedBreakdown, it.calculatedLineTotal)
                        }
                        isCheckingOut = true
                        checkoutError = null
                        scope.launch {
                            val res = viewModel.checkout(payType, discountAmt, paidAmt, notes, finAccId)
                            isCheckingOut = false
                            if (res.isSuccess) {
                                val savedInvoice = res.getOrThrow()
                                showCheckoutSheet = false
                                completedInvoiceResult = Pair(savedInvoice, itemsSnapshot)
                                if (settings.autoPrintAfterSale && settings.savedPrinterAddress.isNotBlank()) {
                                    ThermalPrinterHelper.printInvoiceByAddress(
                                        context = context,
                                        printerAddress = settings.savedPrinterAddress,
                                        paperWidthMm = settings.printerPaperWidthMm,
                                        copies = settings.printerCopies,
                                        tradeName = identity?.TradeName ?: "الدفتر الذكي Pro",
                                        phone = identity?.Phone1 ?: "",
                                        header = identity?.ReceiptHeader ?: "",
                                        invoiceNumber = savedInvoice.InvoiceNumber,
                                        issuedAt = savedInvoice.IssuedAt.take(16),
                                        customerName = savedInvoice.PersonNameSnapshot,
                                        items = itemsSnapshot,
                                        subtotal = savedInvoice.Subtotal,
                                        discount = savedInvoice.DiscountAmount,
                                        finalTotal = savedInvoice.FinalTotal,
                                        paid = savedInvoice.PaidAmount,
                                        remaining = savedInvoice.RemainingAmount,
                                        currencySymbol = settings.currencySymbol,
                                        footer = identity?.ReceiptFooter ?: ""
                                    )
                                }
                            } else {
                                checkoutError = res.exceptionOrNull()?.message ?: "تعذر حفظ الفاتورة"
                            }
                        }
                    },
                    onClose = { showCheckoutSheet = false }
                )
            }
        }

        // نافذة نتيجة الفاتورة بعد الحفظ العائمة
        if (completedInvoiceResult != null) {
            val (inv, itemsSnap) = completedInvoiceResult!!
            var printStatusMsg by remember { mutableStateOf<String?>(null) }
            var isPrinting by remember { mutableStateOf(false) }

            FloatingModalDialog(onDismissRequest = { completedInvoiceResult = null }, maxWidth = 460.dp) {
                FormSheetContainer(
                    title = "تم إصدار وترحيل الفاتورة",
                    subtitle = "رقم الفاتورة: ${inv.InvoiceNumber}",
                    headerIcon = Icons.Default.Verified,
                    onDismiss = { completedInvoiceResult = null },
                    primaryButtonText = "فاتورة جديدة",
                    secondaryButtonText = "إغلاق",
                    onPrimaryClick = { completedInvoiceResult = null }
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("العميل:", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.TextMuted)
                                Text(inv.PersonNameSnapshot, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الصافي النهائي:", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.TextMuted)
                                Text(NumberFormatter.formatCurrency(inv.FinalTotal, settings.currencySymbol, settings.decimalPlaces), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateSuccess)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المسدد نقداً:", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.TextMuted)
                                Text(NumberFormatter.formatCurrency(inv.PaidAmount, settings.currencySymbol, settings.decimalPlaces), style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextPrimary)
                            }
                            if (inv.RemainingAmount > 0.0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("المتبقي آجل:", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.StateDangerText)
                                    Text(NumberFormatter.formatCurrency(inv.RemainingAmount, settings.currencySymbol, settings.decimalPlaces), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateDanger)
                                }
                            }
                        }
                    }

                    if (!printStatusMsg.isNullOrBlank()) {
                        Text(printStatusMsg.orEmpty(), style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val reqPerm = ThermalPrinterHelper.requiredBluetoothRuntimePermission()
                                if (reqPerm != null && !ThermalPrinterHelper.hasBluetoothConnectPermission(context)) {
                                    btPermissionLauncher.launch(reqPerm)
                                    printStatusMsg = "يرجى الموافقة على إذن البلوتوث ثم الضغط على طباعة مرة أخرى."
                                    return@OutlinedButton
                                }
                                isPrinting = true
                                scope.launch {
                                    val printRes = ThermalPrinterHelper.printInvoiceByAddress(
                                        context = context,
                                        printerAddress = settings.savedPrinterAddress,
                                        paperWidthMm = settings.printerPaperWidthMm,
                                        copies = settings.printerCopies,
                                        tradeName = identity?.TradeName ?: "الدفتر الذكي Pro",
                                        phone = identity?.Phone1 ?: "",
                                        header = identity?.ReceiptHeader ?: "",
                                        invoiceNumber = inv.InvoiceNumber,
                                        issuedAt = inv.IssuedAt.take(16),
                                        customerName = inv.PersonNameSnapshot,
                                        items = itemsSnap,
                                        subtotal = inv.Subtotal,
                                        discount = inv.DiscountAmount,
                                        finalTotal = inv.FinalTotal,
                                        paid = inv.PaidAmount,
                                        remaining = inv.RemainingAmount,
                                        currencySymbol = settings.currencySymbol,
                                        footer = identity?.ReceiptFooter ?: ""
                                    )
                                    isPrinting = false
                                    printStatusMsg = if (printRes.isSuccess) {
                                        "تم إرسال الفاتورة إلى الطابعة الحرارية (${settings.printerPaperWidthMm}mm)."
                                    } else {
                                        printRes.exceptionOrNull()?.message ?: "تعذر الاتصال بالطابعة"
                                    }
                                }
                            },
                            enabled = !isPrinting,
                            modifier = Modifier.weight(1f).pressScaleEffect(enabled = !isPrinting),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp), tint = SmartLedgerColors.AccentLight)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("طباعة حرارية", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val text = ThermalPrinterHelper.buildFormattedReceiptText(
                                    tradeName = identity?.TradeName ?: "الدفتر الذكي Pro",
                                    invoiceNumber = inv.InvoiceNumber,
                                    issuedAt = inv.IssuedAt.take(16),
                                    customerName = inv.PersonNameSnapshot,
                                    items = itemsSnap,
                                    subtotal = inv.Subtotal,
                                    discount = inv.DiscountAmount,
                                    finalTotal = inv.FinalTotal,
                                    paid = inv.PaidAmount,
                                    remaining = inv.RemainingAmount,
                                    currencySymbol = settings.currencySymbol,
                                    footer = identity?.ReceiptFooter ?: ""
                                )
                                ThermalPrinterHelper.shareTextSafely(context, "فاتورة ${inv.InvoiceNumber}", text)
                            },
                            modifier = Modifier.weight(1f).pressScaleEffect(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = SmartLedgerColors.StateSuccess)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشاركة / واتساب", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.StateSuccess, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // نافذة سجل الفواتير الصادرة وإدارة المرتجعات العائمة
        if (showInvoicesHistorySheet) {
            var invoiceIdToReverse by rememberSaveable { mutableStateOf<Long?>(null) }
            val invoiceToReverse = remember(recentInvoices, invoiceIdToReverse) { invoiceIdToReverse?.let { id -> recentInvoices.firstOrNull { it.Id == id } } }
            var selectedInvoiceForLines by remember { mutableStateOf<InvoiceEntity?>(null) }
            var selectedInvoiceLines by remember { mutableStateOf<List<InvoiceLineEntity>>(emptyList()) }

            FloatingModalDialog(onDismissRequest = { showInvoicesHistorySheet = false }) {
                FormSheetContainer(
                    title = "سجل الفواتير وإدارة المرتجعات",
                    subtitle = "إرجاع الفاتورة يعيد الكميات للمخزن ويعكس الأثر المالي ذرياً",
                    headerIcon = Icons.Default.History,
                    onDismiss = { showInvoicesHistorySheet = false },
                    primaryButtonText = "إغلاق السجل",
                    onPrimaryClick = { showInvoicesHistorySheet = false }
                ) {
                    val sales = recentInvoices.filter { it.Type == "Sale" }
                    if (sales.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("لا توجد فواتير مبيعات صادرة بعد.", color = SmartLedgerColors.TextMuted)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sales, key = { it.Id }) { inv ->
                                val isReversed = inv.Status == "Reversed"
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                selectedInvoiceLines = viewModel.getInvoiceLines(inv.Id)
                                                selectedInvoiceForLines = inv
                                            }
                                        },
                                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle))
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
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SmartLedgerColors.TextPrimary
                                                )
                                                Text(
                                                    text = "${inv.IssuedAt.take(16)} • الصافي: ${NumberFormatter.formatCurrency(inv.FinalTotal, settings.currencySymbol, settings.decimalPlaces)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SmartLedgerColors.TextMuted
                                                )
                                            }
                                            if (isReversed) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(SmartLedgerColors.StateDangerSubtle)
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("مرتجعة", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateDanger, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = { invoiceIdToReverse = inv.Id },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartLedgerColors.StateDanger),
                                                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.StateDangerBorder)),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.heightIn(min = 32.dp).pressScaleEffect()
                                                ) {
                                                    Text("إرجاع الفاتورة", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        if (selectedInvoiceForLines?.Id == inv.Id && selectedInvoiceLines.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Divider(color = SmartLedgerColors.BorderSubtle)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            selectedInvoiceLines.forEach { ln ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("${ln.ProductNameSnapshot} (${ln.UnitName})", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                                                    Text(NumberFormatter.formatCurrency(ln.LineTotal, settings.currencySymbol, settings.decimalPlaces), style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateSuccess, fontWeight = FontWeight.Bold)
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

            if (invoiceToReverse != null) {
                val targetInv = invoiceToReverse!!
                ConfirmActionDialog(
                    title = "إرجاع وعكس الفاتورة ${targetInv.InvoiceNumber}؟",
                    message = "سيتم إعادة جميع كميات الأصناف إلى المخزن وعكس المبلغ الآجل (${NumberFormatter.formatCurrency(targetInv.RemainingAmount, settings.currencySymbol, settings.decimalPlaces)}) من حساب العميل بموجب إشعار مرتجع مبيعات موثق.",
                    confirmText = "تأكيد المرتجع",
                    requireReason = true,
                    reasonLabel = "سبب المرتجع",
                    onConfirm = { reason ->
                        scope.launch {
                            val res = viewModel.reverseInvoice(targetInv.Id, reason ?: "مرتجع مبيعات معتمد")
                            invoiceIdToReverse = null
                            if (res.isSuccess) {
                                isStatusError = false
                                statusMessage = "تم إرجاع الفاتورة ${targetInv.InvoiceNumber} واستعادة المخزون بنجاح."
                            } else {
                                isStatusError = true
                                statusMessage = res.exceptionOrNull()?.message ?: "تعذر إرجاع الفاتورة."
                            }
                        }
                    },
                    onDismiss = { invoiceIdToReverse = null }
                )
            }
        }

        if (showClearCartConfirm) {
            ConfirmActionDialog(
                title = "إفراغ سلة المبيعات الحالية؟",
                message = "سيتم مسح جميع الأصناف المضافة في السلة الحالية (${cartLines.size} أصناف) للبدء من جديد.",
                confirmText = "إفراغ السلة",
                onConfirm = {
                    viewModel.clearCart()
                    showClearCartConfirm = false
                },
                onDismiss = { showClearCartConfirm = false }
            )
        }
    }
}
