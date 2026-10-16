package com.smartledger.pro.ui.screens.inventory

import androidx.compose.animation.AnimatedVisibility
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
import com.smartledger.pro.data.local.dao.ProductStockItem
import com.smartledger.pro.data.local.entity.ProductEntity
import com.smartledger.pro.data.local.entity.StockMovementEntity
import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.navigation.NavigationIntentHolder
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.viewmodels.InventoryViewModel
import com.smartledger.pro.util.AppSettingsManager
import com.smartledger.pro.util.Formatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel = viewModel()
) {
    val products by viewModel.products.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val stockFilter by viewModel.stockFilter.collectAsState()
    val settings by AppSettingsManager.settings.collectAsState()

    var showAddOrEditSheet by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductStockItem?>(null) }
    var selectedProductForDetails by remember { mutableStateOf<ProductStockItem?>(null) }
    var showRapidStockCountSheet by remember { mutableStateOf(false) }
    var rapidCountInitialProductId by remember { mutableStateOf<Long?>(null) }
    var showSearchBarcodeScanner by remember { mutableStateOf(false) }
    var productPendingDelete by remember { mutableStateOf<ProductStockItem?>(null) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(NavigationIntentHolder.inventoryInitialFilter) {
        val initialFilter = NavigationIntentHolder.inventoryInitialFilter
        if (!initialFilter.isNullOrBlank()) {
            viewModel.onStockFilterChanged(initialFilter)
            NavigationIntentHolder.inventoryInitialFilter = null
        }
    }

    val lowStockCount = remember(products) {
        products.count { it.CurrentStockMinor <= it.AlertThreshold }
    }

    val filterTabs = remember(lowStockCount) {
        listOf(
            FilterTabItem("ALL", "الكل"),
            FilterTabItem("LOW_STOCK", "نواقص قاربت النفاد", badgeCount = lowStockCount),
            FilterTabItem("IN_STOCK", "متوفر"),
            FilterTabItem("MULTI_UNIT", "متعدد الوحدات (كبرى/صغرى)")
        )
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
                            text = "المخزون والأصناف",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        Text(
                            text = "${Formatters.formatQuantity(products.size.toDouble())} صنف معروض",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = rememberDebouncedClick {
                                rapidCountInitialProductId = null
                                showRapidStockCountSheet = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartLedgerColors.StateWarning),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = SolidColor(SmartLedgerColors.StateWarningBorder)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                Icons.Default.FactCheck,
                                contentDescription = null,
                                tint = SmartLedgerColors.StateWarning,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "الجرد السريع",
                                color = SmartLedgerColors.StateWarning,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        Button(
                            onClick = rememberDebouncedClick {
                                editingProduct = null
                                showAddOrEditSheet = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("صنف جديد", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                CompactSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = "ابحث باسم الصنف، الباركود، أو موقع الرف...",
                    onScanBarcodeClick = if (settings.showBarcodeField) {
                        { showSearchBarcodeScanner = true }
                    } else null
                )

                Spacer(modifier = Modifier.height(8.dp))

                ScrollableFilterTabs(
                    tabs = filterTabs,
                    selectedTabId = stockFilter,
                    onTabSelected = { viewModel.onStockFilterChanged(it) }
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

            if (!bannerMessage.isNullOrBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SmartLedgerColors.StateSuccessSubtle)
                            .border(1.dp, SmartLedgerColors.StateSuccessBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bannerMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.StateSuccessText,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { bannerMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = SmartLedgerColors.StateSuccessText, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (products.isEmpty()) {
                item {
                    SmartEmptyState(
                        icon = Icons.Default.Inventory2,
                        title = if (searchQuery.isNotBlank() || stockFilter != "ALL") "لا توجد أصناف مطابقة للبحث أو الفلتر" else "المخزن فارغ حالياً",
                        description = if (searchQuery.isNotBlank() || stockFilter != "ALL") {
                            "جرّب مسح نص البحث أو التبديل إلى تبويب «الكل» لعرض جميع الأصناف."
                        } else {
                            "ابدأ بإضافة أول صنف لتتبع الكميات والوحدات والأسعار بدقة."
                        },
                        actionText = if (searchQuery.isNotBlank() || stockFilter != "ALL") "إعادة ضبط البحث" else "إضافة صنف جديد",
                        onActionClick = {
                            if (searchQuery.isNotBlank() || stockFilter != "ALL") {
                                viewModel.onSearchQueryChanged("")
                                viewModel.onStockFilterChanged("ALL")
                            } else {
                                editingProduct = null
                                showAddOrEditSheet = true
                            }
                        }
                    )
                }
            } else {
                items(products, key = { it.Id }) { product ->
                    ProductCardItem(
                        product = product,
                        currencySymbol = settings.currencySymbol,
                        decimals = settings.decimalPlaces,
                        showBarcode = settings.showBarcodeField,
                        showShelf = settings.showShelfLocation,
                        onClick = { selectedProductForDetails = product },
                        onQuickAdjustClick = {
                            rapidCountInitialProductId = product.Id
                            showRapidStockCountSheet = true
                        },
                        onEditClick = {
                            editingProduct = product
                            showAddOrEditSheet = true
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (showSearchBarcodeScanner) {
            BarcodeScannerDialog(
                title = "بحث عن صنف بالباركود",
                subtitle = "امسح الباركود لعرض الصنف أو إضافته إذا لم يكن مسجلاً",
                onBarcodeResult = { code ->
                    showSearchBarcodeScanner = false
                    viewModel.onSearchQueryChanged(code)
                },
                onDismiss = { showSearchBarcodeScanner = false }
            )
        }

        if (selectedProductForDetails != null) {
            val currentProd = products.firstOrNull { it.Id == selectedProductForDetails!!.Id } ?: selectedProductForDetails!!
            ModalBottomSheet(
                onDismissRequest = { selectedProductForDetails = null },
                containerColor = SmartLedgerColors.SurfacePanel,
                tonalElevation = 16.dp
            ) {
                ProductDetailsSheetContent(
                    product = currentProd,
                    currencySymbol = settings.currencySymbol,
                    decimals = settings.decimalPlaces,
                    viewModel = viewModel,
                    onEdit = {
                        selectedProductForDetails = null
                        editingProduct = currentProd
                        showAddOrEditSheet = true
                    },
                    onAdjustStock = {
                        selectedProductForDetails = null
                        rapidCountInitialProductId = currentProd.Id
                        showRapidStockCountSheet = true
                    },
                    onDelete = {
                        selectedProductForDetails = null
                        productPendingDelete = currentProd
                    },
                    onClose = { selectedProductForDetails = null }
                )
            }
        }

        if (showRapidStockCountSheet) {
            ModalBottomSheet(
                onDismissRequest = { showRapidStockCountSheet = false },
                containerColor = SmartLedgerColors.SurfacePanel
            ) {
                RapidStockCountSheetContent(
                    products = products,
                    initialProductId = rapidCountInitialProductId,
                    onCommitAdjustment = { productId, actual, current, note ->
                        viewModel.commitStockAdjustment(productId, actual, current, note)
                        bannerMessage = "تم تسجيل تسوية الجرد وحفظ أثر الحركة في سجل المخزون."
                    },
                    onClose = { showRapidStockCountSheet = false }
                )
            }
        }

        if (showAddOrEditSheet) {
            AddOrEditProductSheet(
                existingProduct = editingProduct,
                defaultSmallUnit = settings.defaultSmallUnit,
                defaultLargeUnit = settings.defaultLargeUnit,
                defaultUnitFactor = settings.defaultUnitFactor,
                defaultAlertThreshold = settings.defaultAlertThreshold,
                showBarcodeSetting = settings.showBarcodeField,
                showShelfSetting = settings.showShelfLocation,
                currencySymbol = settings.currencySymbol,
                viewModel = viewModel,
                onOpenConflictingProduct = { conflictEntity ->
                    showAddOrEditSheet = false
                    val matchedItem = products.firstOrNull { it.Id == conflictEntity.Id }
                    if (matchedItem != null) {
                        editingProduct = matchedItem
                        showAddOrEditSheet = true
                    } else {
                        viewModel.onSearchQueryChanged(conflictEntity.Barcode ?: conflictEntity.Name)
                    }
                },
                onSaved = { isEdit ->
                    showAddOrEditSheet = false
                    bannerMessage = if (isEdit) "تم تحديث بيانات الصنف بنجاح." else "تمت إضافة الصنف الجديد إلى المخزن بنجاح."
                },
                onDismiss = { showAddOrEditSheet = false }
            )
        }

        if (productPendingDelete != null) {
            ConfirmActionDialog(
                title = "حذف الصنف من المخزن",
                message = "هل أنت متأكد من نقل الصنف «${productPendingDelete!!.Name}» إلى سلة المحذوفات؟ لن تتأثر الفواتير التاريخية السابقة.",
                confirmText = "نعم، حذف الصنف",
                onConfirm = {
                    viewModel.deleteProduct(productPendingDelete!!.Id, "حذف من شاشة المخزون")
                    productPendingDelete = null
                    bannerMessage = "تم نقل الصنف إلى سلة المحذوفات."
                },
                onDismiss = { productPendingDelete = null }
            )
        }
    }
}

@Composable
fun ProductCardItem(
    product: ProductStockItem,
    currencySymbol: String,
    decimals: Int,
    showBarcode: Boolean,
    showShelf: Boolean,
    onClick: () -> Unit,
    onQuickAdjustClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val breakdown = remember(product.CurrentStockMinor, product.SmallUnit, product.LargeUnit, product.UnitFactor) {
        UnitConversionEngine.breakdownStock(
            totalMinorQty = product.CurrentStockMinor,
            smallUnit = product.SmallUnit,
            largeUnit = product.LargeUnit,
            unitFactor = product.UnitFactor
        )
    }
    val isOutOfStock = product.CurrentStockMinor <= 0.0
    val isLowStock = !isOutOfStock && product.CurrentStockMinor <= product.AlertThreshold

    val statusText = when {
        isOutOfStock -> "نافد (0)"
        isLowStock -> "قارب النفاد"
        else -> "متوفر"
    }
    val statusColor = when {
        isOutOfStock -> SmartLedgerColors.StateDanger
        isLowStock -> SmartLedgerColors.StateWarning
        else -> SmartLedgerColors.StateSuccess
    }
    val statusBg = when {
        isOutOfStock -> SmartLedgerColors.StateDangerSubtle
        isLowStock -> SmartLedgerColors.StateWarningSubtle
        else -> SmartLedgerColors.StateSuccessSubtle
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (isOutOfStock) SmartLedgerColors.StateDangerBorder
                else if (isLowStock) SmartLedgerColors.StateWarningBorder
                else SmartLedgerColors.BorderSubtle
            )
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.Name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary
                    )
                    val metaParts = buildList {
                        if (showBarcode && !product.Barcode.isNullOrBlank()) add("رمز: ${product.Barcode}")
                        if (showShelf && !product.ShelfLocation.isNullOrBlank()) add("رف: ${product.ShelfLocation}")
                        if (!product.LargeUnit.isNullOrBlank() && product.UnitFactor > 1.0) {
                            add("1 ${product.LargeUnit} = ${Formatters.formatQuantity(product.UnitFactor)} ${product.SmallUnit}")
                        }
                    }
                    if (metaParts.isNotEmpty()) {
                        Text(
                            text = metaParts.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onQuickAdjustClick,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(SmartLedgerColors.SurfaceCardElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = "جرد الصنف",
                            tint = SmartLedgerColors.TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "الكمية المتاحة",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted
                    )
                    Text(
                        text = breakdown.formattedText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isOutOfStock) SmartLedgerColors.StateDanger else if (isLowStock) SmartLedgerColors.StateWarning else SmartLedgerColors.AccentLight
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val hasMajor = !product.LargeUnit.isNullOrBlank() && product.UnitFactor > 1.0
                    Text(
                        text = if (hasMajor) "سعر ${product.SmallUnit} / ${product.LargeUnit}" else "سعر البيع (${product.SmallUnit})",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted
                    )
                    val majorPrice = if (product.WholesalePrice > 0.0) product.WholesalePrice else (product.RetailPrice * product.UnitFactor)
                    Text(
                        text = Formatters.formatCurrency(product.RetailPrice, currencySymbol, decimals) +
                            if (hasMajor) " | ${Formatters.formatCurrency(majorPrice, currencySymbol, decimals)}" else "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.StateSuccess
                    )
                }
            }
        }
    }
}

@Composable
fun ProductDetailsSheetContent(
    product: ProductStockItem,
    currencySymbol: String,
    decimals: Int,
    viewModel: InventoryViewModel,
    onEdit: () -> Unit,
    onAdjustStock: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    val movements by viewModel.getMovementsForProduct(product.Id).collectAsState(initial = emptyList())
    val breakdown = UnitConversionEngine.breakdownStock(
        totalMinorQty = product.CurrentStockMinor,
        smallUnit = product.SmallUnit,
        largeUnit = product.LargeUnit,
        unitFactor = product.UnitFactor
    )
    val unitProfit = product.RetailPrice - product.CostPrice

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.Name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SmartLedgerColors.TextPrimary
                )
                Text(
                    text = buildString {
                        append("باركود: ${product.Barcode ?: "غير محدد"}")
                        append(" • ${product.Warehouse}")
                        if (!product.ShelfLocation.isNullOrBlank()) append(" • رف: ${product.ShelfLocation}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = SmartLedgerColors.TextMuted
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SmartLedgerColors.SurfaceCardElevated)
            ) {
                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = SmartLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("الرصيد الفعلي المتاح", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                        Text(breakdown.formattedText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("العلاقة بين الوحدات", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                        Text(
                            UnitConversionEngine.describeUnitRelation(product.SmallUnit, product.LargeUnit, product.UnitFactor),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SmartLedgerColors.TextPrimary
                        )
                    }
                }

                Divider(color = SmartLedgerColors.BorderSubtle)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("سعر التكلفة (${product.SmallUnit})", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                        Text(Formatters.formatCurrency(product.CostPrice, currencySymbol, decimals), fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("سعر البيع (${product.SmallUnit})", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                        Text(Formatters.formatCurrency(product.RetailPrice, currencySymbol, decimals), fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateSuccess)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("هامش ربح الوحدة", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                        Text(
                            Formatters.formatCurrency(unitProfit, currencySymbol, decimals),
                            fontWeight = FontWeight.Bold,
                            color = if (unitProfit >= 0) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                        )
                    }
                }

                if (!product.Notes.isNullOrBlank()) {
                    Text(
                        text = "ملاحظات: ${product.Notes}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onEdit,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تعديل الصنف", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onAdjustStock,
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.FactCheck, contentDescription = null, tint = SmartLedgerColors.StateWarning, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("جرد وتسوية", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateWarning, fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SmartLedgerColors.StateDangerSubtle)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الصنف", tint = SmartLedgerColors.StateDanger, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "سجل حركات الصنف (${Formatters.formatQuantity(movements.size.toDouble())}):",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = SmartLedgerColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (movements.isEmpty()) {
            Text("لا توجد حركات مخزنية مسجلة لهذا الصنف بعد.", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextMuted)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(movements, key = { it.Id }) { mov ->
                    StockMovementRow(mov = mov, smallUnit = product.SmallUnit)
                }
            }
        }
    }
}

@Composable
private fun StockMovementRow(mov: StockMovementEntity, smallUnit: String) {
    val isInbound = mov.Type in listOf("In", "Purchase", "ReturnIn", "AdjustmentIn")
    val typeLabel = when (mov.Type) {
        "In" -> "رصيد افتتاحي / وارد"
        "Purchase" -> "توريد مشتريات"
        "Sale" -> "صادر مبيعات"
        "ReturnIn" -> "مرتجع مبيعات وارد"
        "ReturnOut" -> "مرتجع مشتريات صادر"
        "AdjustmentIn" -> "تسوية جرد (فائض +)"
        "AdjustmentOut" -> "تسوية جرد (عجز -)"
        else -> mov.Type
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SmartLedgerColors.SurfaceCard)
            .border(1.dp, SmartLedgerColors.BorderGridLine, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mov.Notes ?: typeLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = SmartLedgerColors.TextPrimary
            )
            Text(
                text = "$typeLabel • ${mov.OccurredAt.take(16)}",
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.TextMuted
            )
        }
        Text(
            text = "${if (isInbound) "+" else "-"}${Formatters.formatQuantity(mov.QuantityMinor)} $smallUnit",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (isInbound) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
        )
    }
}

@Composable
fun RapidStockCountSheetContent(
    products: List<ProductStockItem>,
    initialProductId: Long? = null,
    onCommitAdjustment: (productId: Long, actual: Double, current: Double, note: String?) -> Unit,
    onClose: () -> Unit
) {
    var filterQuery by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }

    val filteredProducts = remember(products, filterQuery) {
        if (filterQuery.isBlank()) products
        else products.filter {
            it.Name.contains(filterQuery, ignoreCase = true) ||
                (it.Barcode?.contains(filterQuery, ignoreCase = true) == true)
        }
    }

    var selectedIndex by remember(filteredProducts, initialProductId) {
        val idx = if (initialProductId != null) {
            filteredProducts.indexOfFirst { it.Id == initialProductId }.coerceAtLeast(0)
        } else 0
        mutableStateOf(idx)
    }

    val currentProduct = filteredProducts.getOrNull(selectedIndex)
    var actualCountText by remember(currentProduct) {
        mutableStateOf(Formatters.formatQuantity(currentProduct?.CurrentStockMinor ?: 0.0))
    }
    var adjustmentNote by remember(currentProduct) { mutableStateOf("") }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FactCheck, contentDescription = null, tint = SmartLedgerColors.StateWarning)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "الجرد السريع وتسوية الفروق",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary
                    )
                    Text(
                        "يسجل الفارق تلقائياً كحركة تسوية جردية مع الاحتفاظ بالتاريخ",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = SmartLedgerColors.TextMuted)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        CompactSearchBar(
            query = filterQuery,
            onQueryChange = {
                filterQuery = it
                selectedIndex = 0
            },
            placeholder = "ابحث عن صنف للجرد أو امسح الباركود...",
            onScanBarcodeClick = { showScanner = true }
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (currentProduct == null) {
            SmartEmptyState(
                icon = Icons.Default.Inventory,
                title = "لا توجد أصناف مطابقة للجرد",
                description = "أضف أصنافاً للمخزن أو امسح نص البحث لعرض القائمة."
            )
        } else {
            val actualQty = Formatters.parseDoubleSafe(actualCountText, currentProduct.CurrentStockMinor)
            val delta = actualQty - currentProduct.CurrentStockMinor

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
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
                                currentProduct.Name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            Text(
                                "باركود: ${currentProduct.Barcode ?: "بدون"} • الصنف (${selectedIndex + 1} من ${filteredProducts.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { if (selectedIndex > 0) selectedIndex-- },
                                enabled = selectedIndex > 0,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "السابق")
                            }
                            IconButton(
                                onClick = { if (selectedIndex < filteredProducts.size - 1) selectedIndex++ },
                                enabled = selectedIndex < filteredProducts.size - 1,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "التالي")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("الرصيد الدفتري", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                            Text(
                                "${Formatters.formatQuantity(currentProduct.CurrentStockMinor)} ${currentProduct.SmallUnit}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val next = (actualQty - 1.0).coerceAtLeast(0.0)
                                    actualCountText = Formatters.formatQuantity(next)
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(SmartLedgerColors.SurfacePanel)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "إنقاص", tint = SmartLedgerColors.TextPrimary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            SmartNumberField(
                                value = actualCountText,
                                onValueChange = { actualCountText = it },
                                label = "الفعلي (${currentProduct.SmallUnit})",
                                modifier = Modifier.width(120.dp),
                                imeAction = ImeAction.Done
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val next = actualQty + 1.0
                                    actualCountText = Formatters.formatQuantity(next)
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(SmartLedgerColors.AccentPrimary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "زيادة", tint = SmartLedgerColors.TextInverse)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("فارق الجرد", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                            Text(
                                text = when {
                                    delta > 0.001 -> "+${Formatters.formatQuantity(delta)} (فائض)"
                                    delta < -0.001 -> "${Formatters.formatQuantity(delta)} (عجز)"
                                    else -> "0 (مطابق)"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    delta > 0.001 -> SmartLedgerColors.StateSuccess
                                    delta < -0.001 -> SmartLedgerColors.StateDanger
                                    else -> SmartLedgerColors.TextMuted
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SmartTextField(
                        value = adjustmentNote,
                        onValueChange = { adjustmentNote = it },
                        label = "سبب التسوية / ملاحظة الجرد (اختياري)",
                        placeholder = "مثال: جرد دوري لنهاية الأسبوع أو تلف عبوة",
                        imeAction = ImeAction.Done
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = rememberDebouncedClick {
                    onCommitAdjustment(
                        currentProduct.Id,
                        actualQty,
                        currentProduct.CurrentStockMinor,
                        adjustmentNote
                    )
                    if (selectedIndex < filteredProducts.size - 1) {
                        selectedIndex++
                    } else {
                        onClose()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (selectedIndex < filteredProducts.size - 1) {
                        "اعتماد نتيجة الصنف والانتقال للتالي"
                    } else {
                        "اعتماد نتيجة الصنف وإنهاء الجرد"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showScanner) {
            BarcodeScannerDialog(
                title = "مسح باركود الصنف للجرد",
                onBarcodeResult = { code ->
                    showScanner = false
                    val matchIdx = filteredProducts.indexOfFirst { it.Barcode.equals(code, ignoreCase = true) }
                    if (matchIdx >= 0) {
                        selectedIndex = matchIdx
                    } else {
                        filterQuery = code
                    }
                },
                onDismiss = { showScanner = false }
            )
        }
    }
}

@Composable
fun AddOrEditProductSheet(
    existingProduct: ProductStockItem?,
    defaultSmallUnit: String,
    defaultLargeUnit: String,
    defaultUnitFactor: Double,
    defaultAlertThreshold: Double,
    showBarcodeSetting: Boolean,
    showShelfSetting: Boolean,
    currencySymbol: String,
    viewModel: InventoryViewModel,
    onOpenConflictingProduct: (ProductEntity) -> Unit,
    onSaved: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditMode = existingProduct != null
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(existingProduct?.Name ?: "") }
    var barcode by remember { mutableStateOf(existingProduct?.Barcode ?: "") }
    var smallUnit by remember { mutableStateOf(existingProduct?.SmallUnit ?: defaultSmallUnit) }
    var costPriceText by remember {
        mutableStateOf(if (existingProduct != null) Formatters.formatQuantity(existingProduct.CostPrice) else "")
    }
    var retailPriceText by remember {
        mutableStateOf(if (existingProduct != null) Formatters.formatQuantity(existingProduct.RetailPrice) else "")
    }
    var openingMinorText by remember { mutableStateOf("0") }

    val initialHasMajor = existingProduct != null && !existingProduct.LargeUnit.isNullOrBlank() && existingProduct.UnitFactor > 1.0
    var showAdvancedSection by remember { mutableStateOf(initialHasMajor) }
    var enableMajorUnit by remember { mutableStateOf(initialHasMajor) }
    var largeUnit by remember { mutableStateOf(existingProduct?.LargeUnit ?: defaultLargeUnit) }
    var factorText by remember {
        mutableStateOf(
            Formatters.formatQuantity(
                if (initialHasMajor) existingProduct!!.UnitFactor else defaultUnitFactor
            )
        )
    }
    var wholesalePriceText by remember {
        mutableStateOf(
            if (initialHasMajor && existingProduct!!.WholesalePrice > 0) Formatters.formatQuantity(existingProduct.WholesalePrice) else ""
        )
    }
    var openingMajorText by remember { mutableStateOf("0") }
    var thresholdText by remember {
        mutableStateOf(Formatters.formatQuantity(existingProduct?.AlertThreshold ?: defaultAlertThreshold))
    }
    var warehouse by remember { mutableStateOf(existingProduct?.Warehouse ?: "المستودع الرئيسي") }
    var shelfLocation by remember { mutableStateOf(existingProduct?.ShelfLocation ?: "") }
    var notes by remember { mutableStateOf(existingProduct?.Notes ?: "") }

    var conflictingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    val nameFocus = remember { FocusRequester() }
    val barcodeFocus = remember { FocusRequester() }
    val smallUnitFocus = remember { FocusRequester() }
    val costFocus = remember { FocusRequester() }
    val retailFocus = remember { FocusRequester() }
    val openingMinorFocus = remember { FocusRequester() }

    LaunchedEffect(barcode) {
        val clean = barcode.trim()
        conflictingProduct = if (clean.isNotEmpty()) {
            viewModel.checkBarcodeConflict(clean, existingProduct?.Id ?: 0L)
        } else null
    }

    val costVal = Formatters.parseDoubleSafe(costPriceText, 0.0)
    val retailVal = Formatters.parseDoubleSafe(retailPriceText, 0.0)
    val rawFactor = Formatters.parseDoubleSafe(factorText, 1.0)
    val unitValidation = UnitConversionEngine.validateUnitSetup(
        smallUnit = smallUnit,
        largeUnit = if (enableMajorUnit) largeUnit else null,
        unitFactor = if (enableMajorUnit) rawFactor else 1.0,
        hasMajorUnit = enableMajorUnit
    )
    val effectiveFactor = if (enableMajorUnit) unitValidation.sanitizedFactor else 1.0
    val openMajVal = if (!isEditMode && enableMajorUnit) Formatters.parseDoubleSafe(openingMajorText, 0.0) else 0.0
    val openMinVal = if (!isEditMode) Formatters.parseDoubleSafe(openingMinorText, 0.0) else 0.0
    val totalOpeningMinor = UnitConversionEngine.toMinorQuantity(openMajVal, openMinVal, effectiveFactor)

    val hasUnsavedChanges = remember(name, barcode, costPriceText, retailPriceText, smallUnit, enableMajorUnit, largeUnit, factorText, shelfLocation, notes) {
        if (existingProduct == null) {
            name.isNotBlank() || barcode.isNotBlank() || costPriceText.isNotBlank() || retailPriceText.isNotBlank()
        } else {
            name != existingProduct.Name ||
                barcode != (existingProduct.Barcode ?: "") ||
                smallUnit != existingProduct.SmallUnit ||
                costVal != existingProduct.CostPrice ||
                retailVal != existingProduct.RetailPrice
        }
    }

    val isFormValid = name.isNotBlank() &&
        smallUnit.isNotBlank() &&
        costVal >= 0.0 &&
        retailVal > 0.0 &&
        unitValidation.isValid &&
        conflictingProduct == null

    AdaptiveFormHost(
        mode = FormWindowMode.BOTTOM_SHEET,
        title = if (isEditMode) "تعديل بيانات الصنف" else "إضافة صنف جديد",
        subtitle = if (isEditMode) "تحديث الأسعار والوحدات دون المساس بسجل الحركات" else "أدخل البيانات الأساسية، أو افتح التفاصيل المتقدمة للعبوات الكبرى",
        onDismiss = onDismiss,
        primaryButtonText = if (isEditMode) "حفظ التعديلات" else "إضافة الصنف للمخزن",
        isPrimaryEnabled = isFormValid,
        isSaving = isSaving,
        hasUnsavedChanges = hasUnsavedChanges,
        errorMessage = formError ?: unitValidation.errorMessage,
        onPrimaryClick = {
            if (!isFormValid) return@AdaptiveFormHost
            isSaving = true
            formError = null
            scope.launch {
                val wholesale = if (enableMajorUnit) {
                    val parsed = Formatters.parseDoubleSafe(wholesalePriceText, 0.0)
                    if (parsed > 0.0) parsed else (retailVal * effectiveFactor)
                } else retailVal

                val threshold = Formatters.parseDoubleSafe(thresholdText, defaultAlertThreshold)
                val result = viewModel.saveProductAsync(
                    id = existingProduct?.Id ?: 0L,
                    barcode = barcode,
                    name = name,
                    categoryId = existingProduct?.CategoryId,
                    warehouse = warehouse,
                    shelfLocation = shelfLocation,
                    smallUnit = smallUnit,
                    largeUnit = if (enableMajorUnit) largeUnit else null,
                    unitFactor = effectiveFactor,
                    costPrice = costVal,
                    retailPrice = retailVal,
                    wholesalePrice = wholesale,
                    alertThreshold = threshold,
                    openingMajorQty = openMajVal,
                    openingMinorQty = openMinVal,
                    notes = notes
                )
                isSaving = false
                if (result.isSuccess) {
                    onSaved(isEditMode)
                } else {
                    formError = result.exceptionOrNull()?.message ?: "تعذر حفظ الصنف"
                }
            }
        }
    ) {
        SmartTextField(
            value = name,
            onValueChange = { name = it; formError = null },
            label = "اسم الصنف التجاري",
            placeholder = "مثال: حليب بودرة 2.5 كجم",
            isRequired = true,
            focusRequester = nameFocus,
            nextFocusRequester = if (showBarcodeSetting) barcodeFocus else smallUnitFocus
        )

        if (showBarcodeSetting) {
            SmartTextField(
                value = barcode,
                onValueChange = { barcode = Formatters.normalizeDigits(it).trim(); formError = null },
                label = "الباركود (اختياري)",
                placeholder = "امسح بالكاميرا أو اكتب الرمز...",
                focusRequester = barcodeFocus,
                nextFocusRequester = smallUnitFocus,
                trailingIcon = {
                    IconButton(onClick = { showBarcodeScanner = true }) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "مسح الباركود بالكاميرا",
                            tint = SmartLedgerColors.AccentLight
                        )
                    }
                }
            )

            if (conflictingProduct != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.StateWarningSubtle),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(SmartLedgerColors.StateWarningBorder)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "تنبيه تعارض: هذا الباركود مسجل مسبقاً للصنف «${conflictingProduct!!.Name}»",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.StateWarning
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onOpenConflictingProduct(conflictingProduct!!) },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("فتح الصنف الموجود وتعديله", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight)
                            }
                            TextButton(
                                onClick = { barcode = "" },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("مسح الرمز", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        Column {
            SmartTextField(
                value = smallUnit,
                onValueChange = { smallUnit = it },
                label = "الوحدة الأساسية (الصغرى)",
                placeholder = "قطعة، حبة، كجم، علبة...",
                isRequired = true,
                focusRequester = smallUnitFocus,
                nextFocusRequester = costFocus
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("قطعة", "حبة", "علبة", "كجم", "كيس", "متر")) { preset ->
                    val selected = smallUnit.trim() == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) SmartLedgerColors.AccentSubtle else SmartLedgerColors.SurfaceCardElevated)
                            .clickable { smallUnit = preset }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) SmartLedgerColors.AccentLight else SmartLedgerColors.TextMuted,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmartNumberField(
                value = costPriceText,
                onValueChange = { costPriceText = it },
                label = "سعر التكلفة ($smallUnit)",
                suffixText = currencySymbol,
                isRequired = true,
                modifier = Modifier.weight(1f),
                focusRequester = costFocus,
                nextFocusRequester = retailFocus
            )
            SmartNumberField(
                value = retailPriceText,
                onValueChange = { retailPriceText = it },
                label = "سعر البيع ($smallUnit)",
                suffixText = currencySymbol,
                isRequired = true,
                modifier = Modifier.weight(1f),
                focusRequester = retailFocus,
                nextFocusRequester = if (!isEditMode) openingMinorFocus else null,
                imeAction = if (!isEditMode) ImeAction.Next else ImeAction.Done
            )
        }

        if (retailVal > 0 && costVal > 0 && retailVal < costVal) {
            Text(
                text = "تنبيه: سعر البيع المدخل أقل من سعر التكلفة!",
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.StateWarning,
                fontWeight = FontWeight.Bold
            )
        }

        if (!isEditMode) {
            SmartNumberField(
                value = openingMinorText,
                onValueChange = { openingMinorText = it },
                label = "الكمية الافتتاحية المتوفرة ($smallUnit)",
                suffixText = smallUnit,
                focusRequester = openingMinorFocus,
                imeAction = ImeAction.Done,
                helperText = if (totalOpeningMinor > 0) {
                    "سيتم قيد رصيد افتتاحي بمقدار ${Formatters.formatQuantity(totalOpeningMinor)} $smallUnit"
                } else null
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SmartLedgerColors.SurfaceCardElevated)
                .clickable { showAdvancedSection = !showAdvancedSection }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = SmartLedgerColors.AccentLight,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "تفاصيل متقدمة (وحدة كبرى / جملة / حد النفاد / الرف)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary
                    )
                    Text(
                        text = if (enableMajorUnit) {
                            UnitConversionEngine.describeUnitRelation(smallUnit, largeUnit, effectiveFactor)
                        } else {
                            "اختياري للمستخدم المتقدم وتجار الجملة"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted
                    )
                }
            }
            Icon(
                imageVector = if (showAdvancedSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = SmartLedgerColors.AccentLight
            )
        }

        AnimatedVisibility(visible = showAdvancedSection) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SmartLedgerColors.SurfaceCard)
                    .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "دعم البيع والشراء بعبوة كبرى (كرتون / درزن)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        Text(
                            text = "يحسب النظام التحويلات والكميات بالوحدة الصغرى تلقائياً",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }
                    Switch(
                        checked = enableMajorUnit,
                        onCheckedChange = { enableMajorUnit = it }
                    )
                }

                if (enableMajorUnit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartTextField(
                            value = largeUnit,
                            onValueChange = { largeUnit = it },
                            label = "اسم الوحدة الكبرى",
                            placeholder = "كرتون، درزن...",
                            isRequired = true,
                            modifier = Modifier.weight(1f)
                        )
                        SmartNumberField(
                            value = factorText,
                            onValueChange = { factorText = it },
                            label = "كم $smallUnit في الـ $largeUnit؟",
                            isRequired = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SmartLedgerColors.AccentSubtle)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${UnitConversionEngine.describeUnitRelation(smallUnit, largeUnit, rawFactor)} • تكلفة الـ $largeUnit: ${Formatters.formatCurrency(costVal * effectiveFactor, currencySymbol)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.AccentLight
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartNumberField(
                            value = wholesalePriceText,
                            onValueChange = { wholesalePriceText = it },
                            label = "سعر بيع الـ $largeUnit",
                            placeholder = Formatters.formatQuantity(retailVal * effectiveFactor),
                            suffixText = currencySymbol,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isEditMode) {
                            SmartNumberField(
                                value = openingMajorText,
                                onValueChange = { openingMajorText = it },
                                label = "كمية افتتاحية ($largeUnit)",
                                suffixText = largeUnit,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Divider(color = SmartLedgerColors.BorderGridLine)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmartNumberField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it },
                        label = "حد تنبيه النفاد ($smallUnit)",
                        modifier = Modifier.weight(1f)
                    )
                    if (showShelfSetting) {
                        SmartTextField(
                            value = shelfLocation,
                            onValueChange = { shelfLocation = it },
                            label = "موقع الرف / الممر",
                            placeholder = "مثال: رف A-2",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                SmartTextField(
                    value = warehouse,
                    onValueChange = { warehouse = it },
                    label = "المستودع / المخزن",
                    placeholder = "المستودع الرئيسي"
                )

                SmartTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "ملاحظات الصنف أو مواصفاته",
                    singleLine = false,
                    minLines = 2,
                    imeAction = ImeAction.Done
                )
            }
        }
    }

    if (showBarcodeScanner) {
        BarcodeScannerDialog(
            title = "مسح باركود الصنف",
            initialManualValue = barcode,
            onBarcodeResult = { code ->
                showBarcodeScanner = false
                barcode = code
            },
            onDismiss = { showBarcodeScanner = false }
        )
    }
}
