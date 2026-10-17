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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.dao.AccountBalanceSummary
import com.smartledger.pro.data.local.dao.ProductStockItem
import com.smartledger.pro.data.local.entity.InvoiceEntity
import com.smartledger.pro.data.local.entity.InvoiceLineEntity
import com.smartledger.pro.data.repository.AppPreferencesRepository
import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.viewmodels.CartLineItem
import com.smartledger.pro.ui.viewmodels.PosViewModel
import com.smartledger.pro.util.NumberFormatter
import com.smartledger.pro.util.ThermalPrinterHelper
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosSalesScreen(
    viewModel: PosViewModel = viewModel()
) {
    val products by viewModel.products.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val recentInvoices by viewModel.recentInvoices.collectAsState()
    val identity by viewModel.businessIdentity.collectAsState()
    val cartLines = viewModel.cartLines
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefsRepo = remember { AppPreferencesRepository.getInstance(context) }
    val settings by prefsRepo.settings.collectAsState()

    var quickSearchQuery by remember { mutableStateOf("") }
    var showProductPickerSheet by remember { mutableStateOf(false) }
    var showCustomerPickerSheet by remember { mutableStateOf(false) }
    var showCheckoutSheet by remember { mutableStateOf(false) }
    var showInvoicesHistorySheet by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var showClearCartConfirm by remember { mutableStateOf(false) }

    var lineToEditQuantityOrPrice by remember { mutableStateOf<CartLineItem?>(null) }
    var lastRemovedItem by remember { mutableStateOf<CartLineItem?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }

    var completedInvoiceResult by remember { mutableStateOf<Pair<InvoiceEntity, List<Triple<String, String, Double>>>?>(null) }

    val subtotal = cartLines.sumOf { it.calculatedLineTotal }

    val knownBarcodes = remember(products) {
        products.mapNotNull { p ->
            if (!p.Barcode.isNullOrBlank()) Pair(p.Barcode, p.Name) else null
        }
    }

    val filteredQuickProducts = remember(products, quickSearchQuery) {
        if (quickSearchQuery.isBlank()) products.take(12)
        else products.filter {
            it.Name.contains(quickSearchQuery, ignoreCase = true) ||
                (it.Barcode?.contains(quickSearchQuery, ignoreCase = true) == true)
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
                            text = "بيع بالوحدة الصغرى والكبرى • نقدي وآجل",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { showInvoicesHistorySheet = true },
                            shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("الفواتير والمرتجع", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                        }
                        if (cartLines.isNotEmpty()) {
                            IconButton(
                                onClick = { showClearCartConfirm = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(SmartLedgerTokens.buttonCorner))
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

                // شريط اختيار العميل المدمج والواضح
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(SmartLedgerTokens.inputCorner))
                        .background(SmartLedgerColors.SurfaceCard)
                        .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(SmartLedgerTokens.inputCorner))
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

                // شريط بحث ومسح باركود مباشر لإضافة الأصناف للسلة بسرعة
                CompactSearchBar(
                    query = quickSearchQuery,
                    onQueryChange = { quickSearchQuery = it },
                    placeholder = "ابحث عن صنف لإضافته فوراً أو امسح الباركود...",
                    onBarcodeClick = if (AppDesignState.showBarcodeField) {
                        { showBarcodeScanner = true }
                    } else null
                )

                // شريط اختيار سريع للأصناف (يسمح بإضافة الصنف بلمسة واحدة دون فتح نافذة منفصلة)
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
            // شريط الإجمالي والإجراءات السفلي المدمج (لا يحجب السلة ويتكيف مع الشاشات القصيرة)
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
                                modifier = Modifier.height(42.dp),
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
                                    .height(42.dp)
                                    .bounceClick(enabled = cartLines.isNotEmpty()) {
                                        showCheckoutSheet = true
                                    },
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
                    actionLabel = if (lastRemovedItem != null) "تراجع عن الحذف" else null,
                    onAction = if (lastRemovedItem != null) {
                        {
                            val restored = lastRemovedItem!!
                            viewModel.cartLines.add(restored)
                            lastRemovedItem = null
                            statusMessage = null
                        }
                    } else null,
                    onDismiss = {
                        statusMessage = null
                        lastRemovedItem = null
                    }
                )
            }

            if (cartLines.isEmpty()) {
                item {
                    SmartEmptyState(
                        icon = Icons.Default.ShoppingCart,
                        title = "سلة المبيعات جاهزة",
                        subtitle = "اضغط على أي صنف في الشريط العلوي، أو امسح الباركود، أو افتح قائمة الأصناف لبدء فاتورة جديدة.",
                        actionText = "استعراض وإضافة الأصناف",
                        onActionClick = { showProductPickerSheet = true }
                    )
                }
            } else {
                items(cartLines, key = { it.productId }) { line ->
                    CartLineCard(
                        line = line,
                        onUpdateQuantities = { maj, min ->
                            viewModel.updateLineQuantities(line.productId, maj, min)
                        },
                        onEditLineDetails = {
                            lineToEditQuantityOrPrice = line
                        },
                        onRemoveLine = {
                            lastRemovedItem = line
                            viewModel.removeItemFromCart(line.productId)
                            isStatusError = false
                            statusMessage = "تمت إزالة «${line.name}» من السلة."
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // نافذة مسح الباركود لإضافة الصنف مباشرة إلى السلة
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
                        isStatusError = false
                        statusMessage = "تمت إضافة «${match.Name}» إلى السلة."
                    } else {
                        isStatusError = true
                        statusMessage = "الباركود ($code) غير مسجل في المخزن. يمكنك تعريفه من شاشة المخزون."
                    }
                },
                onDismiss = { showBarcodeScanner = false }
            )
        }

        // نافذة تعديل كمية وسعر بند في السلة يدوياً
        if (lineToEditQuantityOrPrice != null) {
            val targetLine = lineToEditQuantityOrPrice!!
            ModalBottomSheet(
                onDismissRequest = { lineToEditQuantityOrPrice = null },
                containerColor = SmartLedgerColors.SurfacePanel
            ) {
                var minorQtyText by remember(targetLine) { mutableStateOf(NumberFormatter.formatQuantity(targetLine.minorQuantity)) }
                var majorQtyText by remember(targetLine) { mutableStateOf(NumberFormatter.formatQuantity(targetLine.majorQuantity)) }
                var minorPriceText by remember(targetLine) { mutableStateOf(NumberFormatter.formatQuantity(targetLine.minorUnitPrice)) }
                var majorPriceText by remember(targetLine) { mutableStateOf(NumberFormatter.formatQuantity(targetLine.majorUnitPrice)) }
                val hasMajor = !targetLine.largeUnit.isNullOrBlank() && targetLine.unitFactor > 1.0

                FormSheetContainer(
                    title = "تعديل الكمية والسعر للبند",
                    subtitle = targetLine.name,
                    onDismiss = { lineToEditQuantityOrPrice = null },
                    primaryButtonText = "تحديث البند",
                    onPrimaryClick = {
                        val newMinQty = NumberFormatter.parseDouble(minorQtyText, 0.0)
                        val newMajQty = if (hasMajor) NumberFormatter.parseDouble(majorQtyText, 0.0) else 0.0
                        val newMinPrice = NumberFormatter.parseDouble(minorPriceText, targetLine.minorUnitPrice)
                        val newMajPrice = NumberFormatter.parseDouble(majorPriceText, targetLine.majorUnitPrice)
                        viewModel.updateLineUnitPrice(targetLine.productId, newMinPrice, newMajPrice)
                        viewModel.updateLineQuantities(targetLine.productId, newMajQty, newMinQty)
                        lineToEditQuantityOrPrice = null
                    }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (hasMajor) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // نافذة اختيار الأصناف التفصيلية مع البحث
        if (showProductPickerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showProductPickerSheet = false },
                containerColor = SmartLedgerColors.SurfacePanel
            ) {
                var pickerSearch by remember { mutableStateOf("") }
                val filtered = remember(products, pickerSearch) {
                    if (pickerSearch.isBlank()) products
                    else products.filter {
                        it.Name.contains(pickerSearch, ignoreCase = true) ||
                            (it.Barcode?.contains(pickerSearch, ignoreCase = true) == true)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "اختيار الأصناف للفاتورة",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        IconButton(
                            onClick = { showProductPickerSheet = false },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(SmartLedgerColors.SurfaceCardElevated)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = SmartLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    CompactSearchBar(
                        query = pickerSearch,
                        onQueryChange = { pickerSearch = it },
                        placeholder = "ابحث عن صنف بالاسم أو الباركود...",
                        resultCount = filtered.size
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
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
                                )
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
                                                shape = RoundedCornerShape(6.dp)
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
                                            shape = RoundedCornerShape(6.dp)
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

        // نافذة اختيار العميل مع البحث الفوري
        if (showCustomerPickerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCustomerPickerSheet = false },
                containerColor = SmartLedgerColors.SurfacePanel
            ) {
                var custSearch by remember { mutableStateOf("") }
                val filteredCustomers = remember(customers, custSearch) {
                    if (custSearch.isBlank()) customers
                    else customers.filter {
                        it.PersonName.contains(custSearch, ignoreCase = true) ||
                            (it.Phone?.contains(custSearch) == true)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "اختيار العميل للفاتورة",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary
                    )

                    CompactSearchBar(
                        query = custSearch,
                        onQueryChange = { custSearch = it },
                        placeholder = "ابحث باسم العميل أو رقم الهاتف...",
                        resultCount = filteredCustomers.size
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SmartLedgerColors.SurfaceCardElevated)
                            .clickable {
                                viewModel.selectedCustomer = null
                                showCustomerPickerSheet = false
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("عميل نقدي عام (افتراضي - بدون ذمم آجلة)", fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                        if (viewModel.selectedCustomer == null) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(18.dp))
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredCustomers, key = { it.PersonId }) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SmartLedgerColors.SurfaceCard)
                                    .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(8.dp))
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

        // نافذة إتمام السداد وترحيل الفاتورة
        if (showCheckoutSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCheckoutSheet = false },
                containerColor = SmartLedgerColors.SurfacePanel
            ) {
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
                    onCommit = { payType, discountAmt, paidAmt, notes ->
                        val itemsSnapshot = cartLines.map {
                            Triple(it.name, it.formattedBreakdown, it.calculatedLineTotal)
                        }
                        isCheckingOut = true
                        checkoutError = null
                        scope.launch {
                            val res = viewModel.checkout(payType, discountAmt, paidAmt, notes)
                            isCheckingOut = false
                            if (res.isSuccess) {
                                val savedInvoice = res.getOrThrow()
                                showCheckoutSheet = false
                                completedInvoiceResult = Pair(savedInvoice, itemsSnapshot)
                                // طباعة تلقائية إن كانت مفعلة في الإعدادات
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

        // نافذة نتيجة الفاتورة بعد الحفظ (الطباعة الحرارية، مشاركة واتساب، أو فاتورة جديدة)
        if (completedInvoiceResult != null) {
            val (inv, itemsSnap) = completedInvoiceResult!!
            var printStatusMsg by remember { mutableStateOf<String?>(null) }
            var isPrinting by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { completedInvoiceResult = null },
                containerColor = SmartLedgerColors.SurfacePanel,
                shape = RoundedCornerShape(18.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = SmartLedgerColors.StateSuccess)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("تم إصدار وترحيل الفاتورة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                            Text("رقم الفاتورة: ${inv.InvoiceNumber}", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight)
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp), tint = SmartLedgerColors.AccentLight)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("طباعة", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
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
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = SmartLedgerColors.StateSuccess)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مشاركة", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.StateSuccess, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { completedInvoiceResult = null },
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                    ) {
                        Text("فاتورة جديدة", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // نافذة سجل الفواتير الصادرة وإدارة المرتجعات المحمية بتأكيد
        if (showInvoicesHistorySheet) {
            var invoiceToReverse by remember { mutableStateOf<InvoiceEntity?>(null) }
            var selectedInvoiceForLines by remember { mutableStateOf<InvoiceEntity?>(null) }
            var selectedInvoiceLines by remember { mutableStateOf<List<InvoiceLineEntity>>(emptyList()) }

            ModalBottomSheet(
                onDismissRequest = { showInvoicesHistorySheet = false },
                containerColor = SmartLedgerColors.SurfacePanel
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "سجل الفواتير وإدارة المرتجعات",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            Text(
                                text = "إرجاع الفاتورة يعيد الكميات للمخزن ويعكس الأثر المالي ذرياً",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                        }
                        IconButton(
                            onClick = { showInvoicesHistorySheet = false },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(SmartLedgerColors.SurfaceCardElevated)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = SmartLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    val sales = recentInvoices.filter { it.Type == "Sale" }
                    if (sales.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("لا توجد فواتير مبيعات صادرة بعد.", color = SmartLedgerColors.TextMuted)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
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
                                                    onClick = { invoiceToReverse = inv },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartLedgerColors.StateDanger),
                                                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.StateDangerBorder)),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
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
                            viewModel.reverseInvoice(targetInv.Id, reason ?: "مرتجع مبيعات معتمد")
                            invoiceToReverse = null
                            isStatusError = false
                            statusMessage = "تم إرجاع الفاتورة ${targetInv.InvoiceNumber} واستعادة المخزون بنجاح."
                        }
                    },
                    onDismiss = { invoiceToReverse = null }
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

@Composable
fun CartLineCard(
    line: CartLineItem,
    onUpdateQuantities: (majorQty: Double, minorQty: Double) -> Unit,
    onEditLineDetails: () -> Unit,
    onRemoveLine: () -> Unit
) {
    val hasMajorUnit = !line.largeUnit.isNullOrBlank() && line.unitFactor > 1.0
    val isExceedingStock = AppDesignState.warnOnLowStockInPos && line.totalMinorQuantity > line.availableStockMinor

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(if (isExceedingStock) SmartLedgerColors.StateWarningBorder else SmartLedgerColors.BorderSubtle)
        ),
        shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).clickable { onEditLineDetails() }) {
                    Text(
                        text = line.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary
                    )
                    Text(
                        text = "الإجمالي: ${line.formattedBreakdown} (${NumberFormatter.formatQuantity(line.totalMinorQuantity)} ${line.smallUnit})",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isExceedingStock) SmartLedgerColors.StateWarning else SmartLedgerColors.AccentLight
                    )
                    if (isExceedingStock) {
                        Text(
                            text = "تنبيه: الكمية تتجاوز المتاح بالمخزن (${NumberFormatter.formatQuantity(line.availableStockMinor)} ${line.smallUnit})",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.StateWarning,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = NumberFormatter.formatCurrency(line.calculatedLineTotal, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.StateSuccess
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onEditLineDetails,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل السعر أو الكمية", tint = SmartLedgerColors.TextMuted, modifier = Modifier.size(15.dp))
                    }
                    IconButton(
                        onClick = onRemoveLine,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الصنف", tint = SmartLedgerColors.StateDanger, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // عداد الوحدة الكبرى (إن وجدت، مثل كرتون)
                if (hasMajorUnit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${line.largeUnit} (${NumberFormatter.formatQuantity(line.unitFactor)}):",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { onUpdateQuantities((line.majorQuantity - 1.0).coerceAtLeast(0.0), line.minorQuantity) },
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(SmartLedgerColors.SurfaceCardElevated)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "إنقاص وحدة كبرى", tint = SmartLedgerColors.TextPrimary, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            text = NumberFormatter.formatQuantity(line.majorQuantity),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.AccentLight,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = { onUpdateQuantities(line.majorQuantity + 1.0, line.minorQuantity) },
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(SmartLedgerColors.AccentSubtle)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "زيادة وحدة كبرى", tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(14.dp))
                        }
                    }
                } else {
                    Text(
                        text = "سعر ${line.smallUnit}: ${NumberFormatter.formatCurrency(line.minorUnitPrice, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted
                    )
                }

                // عداد الوحدة الصغرى (مثل قطعة / حبة)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${line.smallUnit}:",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { onUpdateQuantities(line.majorQuantity, (line.minorQuantity - 1.0).coerceAtLeast(0.0)) },
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(SmartLedgerColors.SurfaceCardElevated)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "إنقاص وحدة صغرى", tint = SmartLedgerColors.TextPrimary, modifier = Modifier.size(14.dp))
                    }
                    Text(
                        text = NumberFormatter.formatQuantity(line.minorQuantity),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.AccentLight,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = { onUpdateQuantities(line.majorQuantity, line.minorQuantity + 1.0) },
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(SmartLedgerColors.AccentPrimary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "زيادة وحدة صغرى", tint = SmartLedgerColors.TextInverse, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CheckoutSheetContent(
    subtotalAmount: Double,
    selectedCustomer: AccountBalanceSummary?,
    isSaving: Boolean,
    errorMessage: String?,
    onSelectCustomerClick: () -> Unit,
    onCommit: (payType: String, discount: Double, paid: Double, notes: String?) -> Unit,
    onClose: () -> Unit
) {
    var paymentType by remember { mutableStateOf("Cash") } // Cash, Credit, Partial
    var discountText by remember { mutableStateOf("") }
    var paidAmountText by remember { mutableStateOf("") }
    var receivedCashText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val discountVal = NumberFormatter.parseDouble(discountText, 0.0).coerceIn(0.0, subtotalAmount)
    val finalTotal = (subtotalAmount - discountVal).coerceAtLeast(0.0)

    val paidAmount = when (paymentType) {
        "Cash" -> finalTotal
        "Credit" -> 0.0
        else -> NumberFormatter.parseDouble(paidAmountText, 0.0).coerceIn(0.0, finalTotal)
    }
    val remaining = (finalTotal - paidAmount).coerceAtLeast(0.0)

    val receivedCash = NumberFormatter.parseDouble(receivedCashText, finalTotal)
    val changeDue = if (paymentType == "Cash" && receivedCash > finalTotal) (receivedCash - finalTotal) else 0.0

    val requiresNamedCustomer = (paymentType == "Credit" || paymentType == "Partial") && selectedCustomer == null
    val exceedsCreditLimit = selectedCustomer != null &&
        selectedCustomer.CreditLimit > 0.0 &&
        (selectedCustomer.NetBalance + remaining) > selectedCustomer.CreditLimit

    FormSheetContainer(
        title = "سداد وترحيل الفاتورة",
        subtitle = "العميل: ${selectedCustomer?.PersonName ?: "عميل نقدي عام"}",
        onDismiss = onClose,
        primaryButtonText = "تأكيد وترحيل الفاتورة",
        isPrimaryEnabled = finalTotal > 0.0 && !requiresNamedCustomer,
        isSaving = isSaving,
        errorMessage = errorMessage,
        onPrimaryClick = {
            if (!requiresNamedCustomer) {
                onCommit(paymentType, discountVal, paidAmount, notes)
            }
        }
    ) {
        // 1. طريقة الدفع
        SegmentedOptionRow(
            options = listOf(
                "Cash" to "نقدي كامل",
                "Credit" to "آجل كامل",
                "Partial" to "سداد جزئي"
            ),
            selectedOption = paymentType,
            onOptionSelected = { paymentType = it }
        )

        // تحذير عند اختيار آجل أو جزئي بدون تحديد عميل مسجل
        if (requiresNamedCustomer) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.StateWarningSubtle),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.StateWarningBorder))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "البيع الآجل أو الجزئي يتطلب تحديد حساب عميل لتسجيل المديونية عليه.",
                        style = MaterialTheme.typography.labelMedium,
                        color = SmartLedgerColors.StateWarning,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onSelectCustomerClick,
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("اختيار عميل", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (exceedsCreditLimit) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.StateDangerSubtle),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.StateDangerBorder))
            ) {
                Text(
                    text = "تنبيه ائتماني: إجمالي مديونية العميل بعد هذه الفاتورة سيتجاوز سقف الائتمان المحدد (${NumberFormatter.formatCurrency(selectedCustomer!!.CreditLimit, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)}).",
                    style = MaterialTheme.typography.labelSmall,
                    color = SmartLedgerColors.StateDangerText,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // 2. حقل الخصم الممنوح والمبلغ المدفوع/المستلم
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmartTextField(
                value = discountText,
                onValueChange = { discountText = it },
                label = "الخصم (${AppDesignState.currencySymbol})",
                placeholder = "0",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f)
            )

            if (paymentType == "Partial") {
                SmartTextField(
                    value = paidAmountText,
                    onValueChange = { paidAmountText = it },
                    label = "المسدد نقداً الآن *",
                    placeholder = "0",
                    isRequired = true,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
            } else if (paymentType == "Cash") {
                SmartTextField(
                    value = receivedCashText,
                    onValueChange = { receivedCashText = it },
                    label = "المبلغ المستلم (لحساب الباقي)",
                    placeholder = NumberFormatter.formatQuantity(finalTotal),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. ملخص الحساب الصافي للفاتورة
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
            shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الإجمالي قبل الخصم:", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextSecondary)
                    Text(NumberFormatter.formatCurrency(subtotalAmount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces), fontWeight = FontWeight.SemiBold, color = SmartLedgerColors.TextPrimary)
                }
                if (discountVal > 0.0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الخصم الممنوح:", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.StateWarning)
                        Text("-${NumberFormatter.formatCurrency(discountVal, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)}", fontWeight = FontWeight.SemiBold, color = SmartLedgerColors.StateWarning)
                    }
                }
                Divider(color = SmartLedgerColors.BorderSubtle)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الصافي النهائي للفاتورة:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                    Text(NumberFormatter.formatCurrency(finalTotal, AppDesignState.currencySymbol, AppDesignState.decimalPlaces), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateSuccess)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("المسدد نقداً للصندوق:", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.StateSuccessText)
                    Text(NumberFormatter.formatCurrency(paidAmount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces), fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateSuccess)
                }
                if (remaining > 0.0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المتبقي آجل على العميل:", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.StateDangerText)
                        Text(NumberFormatter.formatCurrency(remaining, AppDesignState.currencySymbol, AppDesignState.decimalPlaces), fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateDanger)
                    }
                }
                if (changeDue > 0.0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الباقي المرتجع للعميل:", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.AccentLight)
                        Text(NumberFormatter.formatCurrency(changeDue, AppDesignState.currencySymbol, AppDesignState.decimalPlaces), fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                    }
                }
            }
        }

        ExpandableAdvancedSection(title = "ملاحظات أو شروط الفاتورة (اختياري)") {
            SmartTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "ملاحظة على الفاتورة",
                placeholder = "رقم طلب، اسم المستلم، أو بيان إضافي...",
                imeAction = ImeAction.Done
            )
        }
    }
}
