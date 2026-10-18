package com.smartledger.pro.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
    val allProducts by viewModel.products.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val stockFilter by viewModel.stockFilter.collectAsState()
    val productCategories by viewModel.productCategories.collectAsState()
    val units by viewModel.units.collectAsState()
    val warehouses by viewModel.warehouses.collectAsState()
    val fieldVisibility by ScreenFieldVisibilityStore.observe(LocalContext.current).collectAsState()
    val scope = rememberCoroutineScope()

    var selectedWarehouseFilter by rememberSaveable { mutableStateOf("ALL") }

    val products = remember(allProducts, selectedWarehouseFilter) {
        if (selectedWarehouseFilter == "ALL") allProducts
        else allProducts.filter { it.Warehouse.equals(selectedWarehouseFilter, ignoreCase = true) }
    }

    var productIdForWarehouseTransfer by rememberSaveable { mutableStateOf<Long?>(null) }
    val productForWarehouseTransfer = remember(allProducts, productIdForWarehouseTransfer) { productIdForWarehouseTransfer?.let { id -> allProducts.firstOrNull { it.Id == id } } }

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
    val productToDelete = remember(allProducts, productIdToDelete) { productIdToDelete?.let { id -> allProducts.firstOrNull { it.Id == id } } }
    var lastArchivedProductId by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastArchivedProductName by rememberSaveable { mutableStateOf<String?>(null) }
    var showRapidStockCountSheet by rememberSaveable { mutableStateOf(false) }
    var initialStockCountProductId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showBarcodeScanner by rememberSaveable { mutableStateOf(false) }
    var statusBanner by rememberSaveable { mutableStateOf<String?>(null) }

    val editingProduct = remember(allProducts, editingProductId) {
        editingProductId?.let { id -> allProducts.firstOrNull { it.Id == id } }
    }
    val selectedProductForDetails = remember(allProducts, selectedProductIdForDetails) {
        selectedProductIdForDetails?.let { id -> allProducts.firstOrNull { it.Id == id } }
    }

    val knownBarcodes = remember(allProducts) {
        allProducts.mapNotNull { p ->
            if (!p.Barcode.isNullOrBlank()) Pair(p.Barcode, p.Name) else null
        }
    }

    // حساب تقييم المخزون بالتكلفة والقيمة السوقية بالبيع والربح الكامن
    val totalCostValuation = remember(products) {
        products.sumOf { it.CurrentStockMinor.coerceAtLeast(0.0) * it.CostPrice }
    }
    val totalRetailValuation = remember(products) {
        products.sumOf { it.CurrentStockMinor.coerceAtLeast(0.0) * it.RetailPrice }
    }
    val projectedInventoryProfit = (totalRetailValuation - totalCostValuation).coerceAtLeast(0.0)

    val distinctWarehouses = remember(allProducts, warehouses) {
        (warehouses.map { it.Name } + allProducts.map { it.Warehouse })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
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
                            text = "المخزن وحركة الأصناف",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        Text(
                            text = "${products.size} صنف • ${distinctWarehouses.size.coerceAtLeast(1)} مخزن • متابعة دقيقة",
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
                                    .heightIn(min = 36.dp)
                                    .pressScaleEffect()
                            ) {
                                Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(14.dp))
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
                                .heightIn(min = 36.dp)
                                .pressScaleEffect()
                        ) {
                            Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("جرد", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                editingProductId = null
                                initialScannedBarcode = null
                                showAddOrEditSheet = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                            shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .pressScaleEffect()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("صنف", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // شريط ملخص تقييم المخزن والقيمة البيعية والربح المتوقع
                if (fieldVisibility.showStockSummaryStrip) {
                    SmartScreenSummaryStrip(
                        firstLabel = "قيمة المخزون (بالتكلفة)",
                        firstValue = NumberFormatter.formatCurrency(totalCostValuation, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                        firstColor = SmartLedgerColors.AccentLight,
                        secondLabel = "القيمة السوقية (بالبيع)",
                        secondValue = NumberFormatter.formatCurrency(totalRetailValuation, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                        secondColor = SmartLedgerColors.TextPrimary,
                        thirdLabel = "الربح الكامن بالمخزن",
                        thirdValue = "+${NumberFormatter.formatCurrency(projectedInventoryProfit, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)}",
                        thirdColor = SmartLedgerColors.StateSuccess
                    )
                }

                CompactSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = if (fieldVisibility.inventoryBarcode) {
                        "ابحث بالاسم أو الباركود أو المخزن أو الرف..."
                    } else {
                        "ابحث بالاسم أو المخزن أو الرف..."
                    },
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

                if (distinctWarehouses.size > 1) {
                    FilterChipRow(
                        items = listOf("ALL" to "كل المخازن") + distinctWarehouses.map { it to "مخزن: $it" },
                        selectedItem = selectedWarehouseFilter,
                        onItemSelected = { selectedWarehouseFilter = it }
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
                    val isFiltered = searchQuery.isNotBlank() || stockFilter != "ALL" || selectedWarehouseFilter != "ALL"
                    SmartEmptyState(
                        icon = Icons.Default.Inventory2,
                        title = "لا توجد أصناف في المخزن بعد",
                        subtitle = "اضغط على «صنف» لتعريف البضاعة ووحداتها الصغرى والكبرى وأسعار البيع.",
                        actionText = "إضافة صنف جديد",
                        onActionClick = {
                            editingProductId = null
                            initialScannedBarcode = null
                            showAddOrEditSheet = true
                        },
                        isFiltered = isFiltered,
                        onClearFilters = {
                            viewModel.clearFilters()
                            selectedWarehouseFilter = "ALL"
                        }
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
                        onDelete = { productIdToDelete = product.Id },
                        showBarcodeField = fieldVisibility.inventoryBarcode,
                        showShelfLocationField = fieldVisibility.productShelf,
                        showCostAndMargin = fieldVisibility.showCostAndMarginInInventory,
                        showWarehouseBadge = fieldVisibility.showWarehouseBadgesInInventory,
                        showQuickStocktakeButton = fieldVisibility.showQuickStocktakeButton
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }

        if (showBarcodeScanner) {
            BarcodeScannerSheet(
                title = "مسح باركود الصنف",
                subtitle = "امسح الباركود للبحث عن الصنف أو تعريفه في المخزن",
                knownBarcodes = knownBarcodes,
                onBarcodeResult = { scannedCode ->
                    showBarcodeScanner = false
                    val existing = allProducts.firstOrNull { it.Barcode.equals(scannedCode, ignoreCase = true) }
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
                    onClose = { selectedProductIdForDetails = null },
                    showBarcodeField = fieldVisibility.inventoryBarcode,
                    showShelfLocationField = fieldVisibility.productShelf
                )
            }
        }

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

        if (showAddOrEditSheet) {
            FloatingModalDialog(onDismissRequest = { showAddOrEditSheet = false }) {
                var isSavingProduct by remember { mutableStateOf(false) }
                var saveProductError by remember { mutableStateOf<String?>(null) }

                AddProductDialog(
                    existingProduct = editingProduct,
                    initialBarcode = initialScannedBarcode,
                    allProducts = allProducts,
                    categories = productCategories,
                    unitSuggestions = remember(units, allProducts) { (units.map { it.Name } + allProducts.map { it.SmallUnit } + allProducts.mapNotNull { it.LargeUnit }).distinct() },
                    warehouseSuggestions = remember(warehouses, allProducts) { (warehouses.map { it.Name } + allProducts.map { it.Warehouse }).distinct() },
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

        if (productToDelete != null) {
            val target = productToDelete!!
            ConfirmActionDialog(
                title = "أرشفة الصنف «${target.Name}»؟",
                message = "سيتم نقل الصنف إلى سلة المحذوفات وإخفاؤه من قوائم البيع مع الحفاظ على الفواتير التاريخية المرتبطة به.",
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
