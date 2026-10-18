package com.smartledger.pro.ui.screens.inventory

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.dao.ProductStockItem
import com.smartledger.pro.data.local.entity.CategoryEntity
import com.smartledger.pro.data.local.entity.WarehouseEntity
import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.screens.settings.ScreenFieldVisibilityStore
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.viewmodels.InventoryViewModel
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.launch

@Composable
fun InventoryScreen(
    onNavigateToPurchases: (() -> Unit)? = null,
    createAction: String? = null,
    onCreateActionConsumed: () -> Unit = {},
    viewModel: InventoryViewModel = viewModel()
) {
    val products by viewModel.products.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val stockFilter by viewModel.stockFilter.collectAsState()
    val productCategories by viewModel.productCategories.collectAsState()
    val units by viewModel.units.collectAsState()
    val warehouses by viewModel.warehouses.collectAsState()
    val fieldVisibility by ScreenFieldVisibilityStore.observe(LocalContext.current).collectAsState()
    var productIdForWarehouseTransfer by rememberSaveable { mutableStateOf<Long?>(null) }
    val productForWarehouseTransfer = remember(products, productIdForWarehouseTransfer) { productIdForWarehouseTransfer?.let { id -> products.firstOrNull { it.Id == id } } }
    val scope = rememberCoroutineScope()

    var showAddOrEditSheet by rememberSaveable { mutableStateOf(false) }
    var editingProductId by rememberSaveable { mutableStateOf<Long?>(null) }
    var initialScannedBarcode by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(createAction) {
        if (createAction == "product") {
            editingProductId = null
            initialScannedBarcode = null
            showAddOrEditSheet = true
            onCreateActionConsumed()
        }
    }

    var selectedProductIdForDetails by rememberSaveable { mutableStateOf<Long?>(null) }
    var productIdToDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val productToDelete = remember(products, productIdToDelete) { productIdToDelete?.let { id -> products.firstOrNull { it.Id == id } } }
    var lastArchivedProductId by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastArchivedProductName by rememberSaveable { mutableStateOf<String?>(null) }
    var showRapidStockCountSheet by rememberSaveable { mutableStateOf(false) }
    var initialStockCountProductId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showBarcodeScanner by rememberSaveable { mutableStateOf(false) }
    var statusBanner by rememberSaveable { mutableStateOf<String?>(null) }

    val editingProduct = remember(products, editingProductId) {
        editingProductId?.let { id -> products.firstOrNull { it.Id == id } }
    }
    val selectedProductForDetails = remember(products, selectedProductIdForDetails) {
        selectedProductIdForDetails?.let { id -> products.firstOrNull { it.Id == id } }
    }

    val knownBarcodes = remember(products) {
        products.mapNotNull { p ->
            if (!p.Barcode.isNullOrBlank()) Pair(p.Barcode, p.Name) else null
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "المخزون والأصناف",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        Text(
                            text = "${products.size} صنف معروض | إدارة الوحدات والجرد",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (onNavigateToPurchases != null) {
                            OutlinedButton(
                                onClick = onNavigateToPurchases,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartLedgerColors.AccentLight),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = SolidColor(SmartLedgerColors.BorderSubtle)
                                ),
                                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .pressScaleEffect()
                            ) {
                                Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("توريد", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                initialStockCountProductId = null
                                showRapidStockCountSheet = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartLedgerColors.StateWarning),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = SolidColor(SmartLedgerColors.StateWarningBorder)
                            ),
                            shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .pressScaleEffect()
                        ) {
                            Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("جرد", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }

                    }
                }

                CompactSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = "ابحث باسم الصنف، الباركود، أو موقع الرف...",
                    resultCount = products.size,
                    onBarcodeClick = if (fieldVisibility.inventoryBarcode) {
                        { showBarcodeScanner = true }
                    } else null
                )

                FilterChipRow(
                    items = listOf(
                        "ALL" to "كل الأصناف",
                        "LOW_STOCK" to "قارب النفاد",
                        "OUT_OF_STOCK" to "نافد (0)",
                        "MULTI_UNIT" to "متعدد الوحدات"
                    ),
                    selectedItem = stockFilter,
                    onItemSelected = { viewModel.onStockFilterChanged(it) }
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
                    message = statusBanner,
                    actionLabel = if (lastArchivedProductId != null) "تراجع عن الأرشفة" else null,
                    onAction = if (lastArchivedProductId != null) {
                        {
                            val targetId = lastArchivedProductId!!
                            val restoredName = lastArchivedProductName ?: "الصنف"
                            viewModel.restoreProduct(targetId) {
                                lastArchivedProductId = null
                                lastArchivedProductName = null
                                statusBanner = "تمت استعادة الصنف «$restoredName» إلى المخزن النشط."
                            }
                        }
                    } else null,
                    onDismiss = {
                        statusBanner = null
                        lastArchivedProductId = null
                        lastArchivedProductName = null
                    }
                )
            }

            if (products.isEmpty()) {
                item {
                    val isFiltered = searchQuery.isNotBlank() || stockFilter != "ALL"
                    SmartEmptyState(
                        icon = Icons.Default.Inventory2,
                        title = "لا توجد أصناف في المخزن بعد",
                        subtitle = "اضغط على «صنف جديد» لتعريف البضاعة ووحداتها الصغرى والكبرى وأسعار البيع.",
                        isFiltered = isFiltered,
                        onClearFilters = { viewModel.clearFilters() }
                    )
                }
            } else {
                items(products, key = { it.Id }) { product ->
                    ProductCardItem(
                        product = product,
                        onClick = { selectedProductIdForDetails = product.Id },
                        onEdit = {
                            editingProductId = product.Id
                            initialScannedBarcode = null
                            showAddOrEditSheet = true
                        },
                        onQuickStocktake = {
                            initialStockCountProductId = product.Id
                            showRapidStockCountSheet = true
                        },
                        onDelete = { productIdToDelete = product.Id }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }

        // نافذة مسح الباركود العائمة
        if (showBarcodeScanner) {
            BarcodeScannerSheet(
                title = "مسح باركود الصنف",
                subtitle = "امسح الباركود للبحث عن الصنف أو تعريفه في المخزن",
                knownBarcodes = knownBarcodes,
                onBarcodeResult = { scannedCode ->
                    showBarcodeScanner = false
                    val existing = products.firstOrNull { it.Barcode.equals(scannedCode, ignoreCase = true) }
                    if (existing != null) {
                        selectedProductIdForDetails = existing.Id
                    } else {
                        editingProductId = null
                        initialScannedBarcode = scannedCode
                        showAddOrEditSheet = true
                    }
                },
                onDismiss = { showBarcodeScanner = false }
            )
        }

        // نافذة تفاصيل الصنف وسجل حركاته العائمة
        if (selectedProductForDetails != null) {
            FloatingModalDialog(onDismissRequest = { selectedProductIdForDetails = null }) {
                ProductDetailsSheetContent(
                    product = selectedProductForDetails,
                    viewModel = viewModel,
                    onEdit = {
                        val pId = selectedProductForDetails.Id
                        selectedProductIdForDetails = null
                        editingProductId = pId
                        showAddOrEditSheet = true
                    },
                    onAdjustStock = {
                        val pId = selectedProductForDetails.Id
                        selectedProductIdForDetails = null
                        initialStockCountProductId = pId
                        showRapidStockCountSheet = true
                    },
                    onTransferWarehouse = if (warehouses.size >= 2) {
                        {
                            val pId = selectedProductForDetails.Id
                            selectedProductIdForDetails = null
                            productIdForWarehouseTransfer = pId
                        }
                    } else null,
                    onClose = { selectedProductIdForDetails = null }
                )
            }
        }

        // نافذة الجرد المخزني العائمة فوق لوحة المفاتيح
        if (showRapidStockCountSheet) {
            FloatingModalDialog(onDismissRequest = { showRapidStockCountSheet = false }) {
                var isAdjusting by remember { mutableStateOf(false) }
                var adjustError by remember { mutableStateOf<String?>(null) }

                RapidStockCountSheetContent(
                    products = products,
                    initialProductId = initialStockCountProductId,
                    isSaving = isAdjusting,
                    errorMessage = adjustError,
                    onCommitAdjustment = { productId, actual, current, notes, onSuccessStep ->
                        isAdjusting = true
                        adjustError = null
                        scope.launch {
                            val res = viewModel.commitStockAdjustmentAsync(productId, actual, current, notes)
                            isAdjusting = false
                            if (res.isSuccess) {
                                lastArchivedProductId = null
                                statusBanner = "تم تسجيل تسوية الجرد وحفظ أثر الحركة في سجل المخزون."
                                onSuccessStep()
                            } else {
                                adjustError = res.exceptionOrNull()?.message ?: "تعذر حفظ تسوية الجرد."
                            }
                        }
                    },
                    onClose = { showRapidStockCountSheet = false }
                )
            }
        }

        // نافذة إدخال المخزن العائمة الرشيقة والأنيقة (فوق لوحة المفاتيح) مع حماية كاملة من الإغلاق عند الخطأ
        if (showAddOrEditSheet) {
            FloatingModalDialog(onDismissRequest = { showAddOrEditSheet = false }) {
                var isSavingProduct by remember { mutableStateOf(false) }
                var saveProductError by remember { mutableStateOf<String?>(null) }

                AddProductDialog(
                    existingProduct = editingProduct,
                    initialBarcode = initialScannedBarcode,
                    allProducts = products,
                    categories = productCategories,
                    unitSuggestions = remember(units, products) { (units.map { it.Name } + products.map { it.SmallUnit } + products.mapNotNull { it.LargeUnit }).distinct() },
                    warehouseSuggestions = remember(warehouses, products) { (warehouses.map { it.Name } + products.map { it.Warehouse }).distinct() },
                    showBarcodeField = fieldVisibility.productBarcode,
                    showShelfLocationField = fieldVisibility.productShelf,
                    isSaving = isSavingProduct,
                    saveErrorMessage = saveProductError,
                    onOpenExistingProduct = { duplicateProduct ->
                        showAddOrEditSheet = false
                        selectedProductIdForDetails = duplicateProduct.Id
                    },
                    onDismiss = { showAddOrEditSheet = false },
                    onSave = { id, barcode, name, categoryId, warehouse, shelf, sUnit, lUnit, factor, cost, retail, wholesale, threshold, openMaj, openMin ->
                        isSavingProduct = true
                        saveProductError = null
                        scope.launch {
                            val res = viewModel.saveProductAsync(
                                id = id,
                                barcode = barcode,
                                name = name,
                                categoryId = categoryId,
                                warehouse = warehouse,
                                shelfLocation = shelf,
                                smallUnit = sUnit,
                                largeUnit = lUnit,
                                unitFactor = factor,
                                costPrice = cost,
                                retailPrice = retail,
                                wholesalePrice = wholesale,
                                alertThreshold = threshold,
                                openingMajorQty = openMaj,
                                openingMinorQty = openMin
                            )
                            isSavingProduct = false
                            if (res.isSuccess) {
                                showAddOrEditSheet = false
                                lastArchivedProductId = null
                                statusBanner = if (id > 0) "تم تحديث بيانات الصنف «$name» بنجاح." else "تمت إضافة الصنف «$name» إلى المخزن بنجاح."
                            } else {
                                saveProductError = res.exceptionOrNull()?.message ?: "تعذر حفظ بيانات الصنف؛ يرجى التحقق من المدخلات والمحاولة مجدداً."
                            }
                        }
                    }
                )
            }
        }

        if (productForWarehouseTransfer != null && warehouses.size >= 2) {
            val targetProd = productForWarehouseTransfer!!
            FloatingModalDialog(onDismissRequest = { productIdForWarehouseTransfer = null }) {
                var fromWId by rememberSaveable { mutableStateOf<Long?>(warehouses.firstOrNull()?.Id) }
                var toWId by rememberSaveable { mutableStateOf<Long?>(warehouses.drop(1).firstOrNull()?.Id) }
                var transferQtyText by rememberSaveable { mutableStateOf("1") }
                var transferNote by rememberSaveable { mutableStateOf("") }
                var transferErr by remember { mutableStateOf<String?>(null) }

                FormSheetContainer(
                    title = "تحويل مخزني بين المخازن",
                    subtitle = "الصنف: ${targetProd.Name} (${targetProd.SmallUnit})",
                    headerIcon = Icons.Default.SwapHoriz,
                    onDismiss = { productIdForWarehouseTransfer = null },
                    primaryButtonText = "تنفيذ التحويل المخزني",
                    errorMessage = transferErr,
                    onPrimaryClick = {
                        val qtyVal = NumberFormatter.parseDouble(transferQtyText, 0.0)
                        scope.launch {
                            val res = viewModel.transferStockBetweenWarehousesAsync(
                                productId = targetProd.Id,
                                fromWarehouseId = fromWId ?: 0L,
                                toWarehouseId = toWId ?: 0L,
                                quantityMinor = qtyVal,
                                notes = transferNote
                            )
                            if (res.isSuccess) {
                                productIdForWarehouseTransfer = null
                                statusBanner = "تم تنفيذ التحويل المخزني للصنف «${targetProd.Name}» بنجاح."
                            } else {
                                transferErr = res.exceptionOrNull()?.message
                            }
                        }
                    }
                ) {
                    FormSectionGroup(title = "مسار التحويل والكمية") {
                        val wChips = warehouses.map { it.Id as Long? to it.Name }
                        Text("من مخزن:", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                        FilterChipRow(items = wChips, selectedItem = fromWId, onItemSelected = { fromWId = it })
                        Text("إلى مخزن:", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                        FilterChipRow(items = wChips, selectedItem = toWId, onItemSelected = { toWId = it })
                        SmartTextField(
                            value = transferQtyText,
                            onValueChange = { transferQtyText = it },
                            label = "الكمية المحولة (${targetProd.SmallUnit})",
                            isRequired = true,
                            keyboardType = KeyboardType.Decimal
                        )
                        SmartTextField(
                            value = transferNote,
                            onValueChange = { transferNote = it },
                            label = "ملاحظة التحويل (اختياري)",
                            imeAction = ImeAction.Done
                        )
                    }
                }
            }
        }

        // تأكيد أرشفة صنف
        if (productToDelete != null) {
            val target = productToDelete!!
            ConfirmActionDialog(
                title = "أرشفة الصنف «${target.Name}»؟",
                message = "سيتم نقل الصنف إلى سلة المحذوفات وإخفاؤه من قوائم البيع مع الحفاظ على الفواتير التاريخية المرتبطة به. يمكنك التراجع فوراً أو استعادته من سلة المحذوفات.",
                confirmText = "أرشفة الصنف",
                requireReason = true,
                reasonLabel = "سبب الأرشفة (اختياري)",
                onConfirm = { reason ->
                    lastArchivedProductId = target.Id
                    lastArchivedProductName = target.Name
                    viewModel.deleteProduct(target.Id, reason)
                    productIdToDelete = null
                    statusBanner = "تمت أرشفة الصنف «${target.Name}»."
                },
                onDismiss = { productIdToDelete = null }
            )
        }
    }
}
