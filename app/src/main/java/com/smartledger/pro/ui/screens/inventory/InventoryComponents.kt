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
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.viewmodels.InventoryViewModel
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.launch

@Composable
fun ProductCardItem(
    product: ProductStockItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onQuickStocktake: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() },
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                when {
                    isOutOfStock -> SmartLedgerColors.StateDangerBorder
                    isLowStock -> SmartLedgerColors.StateWarningBorder
                    else -> SmartLedgerColors.BorderSubtle
                }
            )
        ),
        shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
    ) {
        Column(modifier = Modifier.padding(SmartLedgerTokens.cardInternalPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.Name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary
                    )

                    val metaParts = buildList {
                        if (AppDesignState.showBarcodeField && !product.Barcode.isNullOrBlank()) {
                            add("باركود: ${product.Barcode}")
                        }
                        if (AppDesignState.showShelfLocation && !product.ShelfLocation.isNullOrBlank()) {
                            add("رف: ${product.ShelfLocation}")
                        }
                        if (!product.LargeUnit.isNullOrBlank() && product.UnitFactor > 1.0) {
                            add("1 ${product.LargeUnit} = ${NumberFormatter.formatQuantity(product.UnitFactor)} ${product.SmallUnit}")
                        }
                    }

                    if (metaParts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = metaParts.joinToString("  •  "),
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isOutOfStock || isLowStock) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isOutOfStock) SmartLedgerColors.StateDangerSubtle else SmartLedgerColors.StateWarningSubtle)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isOutOfStock) "نافد" else "حد النفاد!",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isOutOfStock) SmartLedgerColors.StateDanger else SmartLedgerColors.StateWarning
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "خيارات الصنف",
                                tint = SmartLedgerColors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(SmartLedgerColors.SurfacePanel)
                        ) {
                            DropdownMenuItem(
                                text = { Text("تفاصيل وسجل الحركات", color = SmartLedgerColors.TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null, tint = SmartLedgerColors.AccentLight) },
                                onClick = {
                                    menuExpanded = false
                                    onClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("تعديل بيانات الصنف", color = SmartLedgerColors.TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = SmartLedgerColors.AccentLight) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("تسوية جرد سريعة", color = SmartLedgerColors.TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.FactCheck, contentDescription = null, tint = SmartLedgerColors.StateWarning) },
                                onClick = {
                                    menuExpanded = false
                                    onQuickStocktake()
                                }
                            )
                            Divider(color = SmartLedgerColors.BorderSubtle)
                            DropdownMenuItem(
                                text = { Text("أرشفة الصنف", color = SmartLedgerColors.StateDanger) },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = SmartLedgerColors.StateDanger) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "الرصيد المتاح",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted
                    )
                    Text(
                        text = breakdown.formattedText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isOutOfStock -> SmartLedgerColors.StateDanger
                            isLowStock -> SmartLedgerColors.StateWarning
                            else -> SmartLedgerColors.AccentLight
                        }
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val hasMajor = !product.LargeUnit.isNullOrBlank() && product.UnitFactor > 1.0
                    val majorPrice = if (product.WholesalePrice > 0.0) product.WholesalePrice else (product.RetailPrice * product.UnitFactor)
                    Text(
                        text = if (hasMajor) "سعر ${product.SmallUnit}  |  سعر ${product.LargeUnit}" else "سعر بيع ${product.SmallUnit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted
                    )
                    Text(
                        text = NumberFormatter.formatCurrency(product.RetailPrice, AppDesignState.currencySymbol, AppDesignState.decimalPlaces) +
                            if (hasMajor) "  |  " + NumberFormatter.formatCurrency(majorPrice, AppDesignState.currencySymbol, AppDesignState.decimalPlaces) else "",
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
    viewModel: InventoryViewModel,
    onEdit: () -> Unit,
    onAdjustStock: () -> Unit,
    onTransferWarehouse: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    val movementsFlow = remember(product.Id) { viewModel.getMovementsForProduct(product.Id) }
    val movements by movementsFlow.collectAsState(initial = emptyList())
    val breakdown = remember(product.CurrentStockMinor, product.SmallUnit, product.LargeUnit, product.UnitFactor) {
        UnitConversionEngine.breakdownStock(
            totalMinorQty = product.CurrentStockMinor,
            smallUnit = product.SmallUnit,
            largeUnit = product.LargeUnit,
            unitFactor = product.UnitFactor
        )
    }
    val totalValuation = product.CurrentStockMinor.coerceAtLeast(0.0) * product.CostPrice

    FormSheetContainer(
        title = product.Name,
        subtitle = "باركود: ${product.Barcode ?: "غير محدد"} • ${product.Warehouse}" +
            if (!product.ShelfLocation.isNullOrBlank()) " • رف: ${product.ShelfLocation}" else "",
        headerIcon = Icons.Default.Inventory2,
        onDismiss = onClose,
        primaryButtonText = "تعديل بيانات الصنف",
        secondaryButtonText = "إغلاق",
        onPrimaryClick = onEdit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("الرصيد الفعلي", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                    Text(breakdown.formattedText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("تكلفة ${product.SmallUnit}", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                    Text(
                        NumberFormatter.formatCurrency(product.CostPrice, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("قيمة المخزون بالتكلفة", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                    Text(
                        NumberFormatter.formatCurrency(totalValuation, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.StateSuccess
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onAdjustStock,
                modifier = Modifier.weight(1f).heightIn(min = 40.dp).pressScaleEffect(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.StateWarning))
            ) {
                Icon(Icons.Default.FactCheck, contentDescription = null, tint = SmartLedgerColors.StateWarning, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تسوية جرد الصنف", color = SmartLedgerColors.StateWarning, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            if (onTransferWarehouse != null) {
                OutlinedButton(
                    onClick = onTransferWarehouse,
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp).pressScaleEffect(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary))
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تحويل بين المخازن", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }

        Text(
            text = "سجل حركات الصنف (${movements.size}):",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = SmartLedgerColors.TextPrimary
        )

        if (movements.isEmpty()) {
            Text("لا توجد حركات مسجلة لهذا الصنف بعد.", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextMuted)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(movements, key = { it.Id }) { mov ->
                    val isIn = mov.Type in listOf("In", "Purchase", "ReturnIn", "AdjustmentIn")
                    val typeLabel = when (mov.Type) {
                        "In" -> "رصيد افتتاحي / توريد"
                        "Purchase" -> "فاتورة مشتريات"
                        "Sale" -> "فاتورة مبيعات"
                        "ReturnIn" -> "مرتجع مبيعات وارد"
                        "ReturnOut" -> "مرتجع مشتريات صادر"
                        "AdjustmentIn" -> "تسوية جرد (فائض +)"
                        "AdjustmentOut" -> "تسوية جرد (عجز -)"
                        else -> mov.Type
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SmartLedgerColors.SurfaceCard)
                            .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(10.dp))
                            .padding(10.dp),
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
                            text = "${if (isIn) "+" else "-"}${NumberFormatter.formatQuantity(mov.QuantityMinor)} ${product.SmallUnit}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isIn) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RapidStockCountSheetContent(
    products: List<ProductStockItem>,
    initialProductId: Long? = null,
    isSaving: Boolean = false,
    errorMessage: String? = null,
    onCommitAdjustment: (productId: Long, actual: Double, current: Double, notes: String?, onSuccessStep: () -> Unit) -> Unit,
    onClose: () -> Unit
) {
    val startIndex = remember(products, initialProductId) {
        if (initialProductId != null) {
            products.indexOfFirst { it.Id == initialProductId }.coerceAtLeast(0)
        } else 0
    }
    var selectedIndex by rememberSaveable { mutableStateOf(startIndex) }
    val currentProduct = products.getOrNull(selectedIndex)

    var actualCountText by rememberSaveable(currentProduct?.Id) {
        mutableStateOf(NumberFormatter.formatQuantity(currentProduct?.CurrentStockMinor ?: 0.0))
    }
    var adjustmentReason by rememberSaveable(currentProduct?.Id) { mutableStateOf("") }

    val actualCount = NumberFormatter.parseDouble(actualCountText, currentProduct?.CurrentStockMinor ?: 0.0).coerceAtLeast(0.0)
    val bookCount = currentProduct?.CurrentStockMinor ?: 0.0
    val delta = actualCount - bookCount

    FormSheetContainer(
        title = "أداة الجرد السريع وتسوية الفروق",
        subtitle = if (currentProduct != null) "الصنف (${selectedIndex + 1} من ${products.size}) — يحفظ أثر التسوية في سجل المخزون" else "لا توجد أصناف للجرد",
        headerIcon = Icons.Default.FactCheck,
        onDismiss = onClose,
        primaryButtonText = if (selectedIndex < products.size - 1) "اعتماد التسوية والتالي ↵" else "اعتماد التسوية وإنهاء الجرد ✔",
        isPrimaryEnabled = currentProduct != null,
        isSaving = isSaving,
        errorMessage = errorMessage,
        hasUnsavedChanges = kotlin.math.abs(delta) > 0.001 || adjustmentReason.isNotBlank(),
        onPrimaryClick = {
            if (currentProduct != null) {
                onCommitAdjustment(currentProduct.Id, actualCount, bookCount, adjustmentReason) {
                    if (selectedIndex < products.size - 1) {
                        selectedIndex++
                    } else {
                        onClose()
                    }
                }
            }
        }
    ) {
        if (currentProduct == null) {
            Text("لا توجد أصناف مسجلة في المخزن لإجراء الجرد.", color = SmartLedgerColors.TextMuted)
        } else {
            if (products.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(products.size) { idx ->
                        val p = products[idx]
                        val isCurrent = idx == selectedIndex
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) SmartLedgerColors.AccentSubtle else SmartLedgerColors.SurfaceCardElevated)
                                .border(1.dp, if (isCurrent) SmartLedgerColors.AccentPrimary else SmartLedgerColors.BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable { selectedIndex = idx }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = p.Name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCurrent) SmartLedgerColors.AccentLight else SmartLedgerColors.TextSecondary,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(currentProduct.Name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("الرصيد الدفتري", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                            Text(
                                "${NumberFormatter.formatQuantity(bookCount)} ${currentProduct.SmallUnit}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val nextVal = (actualCount - 1.0).coerceAtLeast(0.0)
                                    actualCountText = NumberFormatter.formatQuantity(nextVal)
                                },
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(SmartLedgerColors.SurfacePanel)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "إنقاص", tint = SmartLedgerColors.TextPrimary)
                            }
                            Text(
                                text = NumberFormatter.formatQuantity(actualCount),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.AccentLight,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                            IconButton(
                                onClick = {
                                    val nextVal = actualCount + 1.0
                                    actualCountText = NumberFormatter.formatQuantity(nextVal)
                                },
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(SmartLedgerColors.AccentPrimary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "زيادة", tint = SmartLedgerColors.TextInverse)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("فارق الجرد", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                            Text(
                                text = when {
                                    delta > 0.001 -> "+${NumberFormatter.formatQuantity(delta)} (فائض)"
                                    delta < -0.001 -> "${NumberFormatter.formatQuantity(delta)} (عجز)"
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
                }
            }

            SmartTextField(
                value = actualCountText,
                onValueChange = { actualCountText = it },
                label = "الكمية الفعلية المعدودة (${currentProduct.SmallUnit})",
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            )

            SmartTextField(
                value = adjustmentReason,
                onValueChange = { adjustmentReason = it },
                label = "ملاحظة أو سبب الفارق الجردي (اختياري)",
                placeholder = "مثال: جرد دوري لنهاية الأسبوع / تالف...",
                imeAction = ImeAction.Done
            )
        }
    }
}

/**
 * نافذة تعريف أو تعديل صنف بالمخزن (AddProductDialog)
 * - تستخدم القيم الافتراضية الذكية المحددة في الإعدادات (`AppDesignState.defaultSmallUnit` إلخ - `UX-005`)
 * - تعرض معاينة حية لهامش الربح وتنبيهاً فورياً عند إدخال سعر بيع أقل من التكلفة (`UX-003`)
 * - تحفظ حالة المدخلات عبر rememberSaveable لمنع ضياع البيانات عند دوران الشاشة أو ظهور لوحة المفاتيح أو حدوث خطأ حفظ
 */
@Composable
fun AddProductDialog(
    existingProduct: ProductStockItem?,
    initialBarcode: String?,
    allProducts: List<ProductStockItem>,
    categories: List<CategoryEntity> = emptyList(),
    unitSuggestions: List<String> = emptyList(),
    warehouseSuggestions: List<String> = emptyList(),
    isSaving: Boolean = false,
    saveErrorMessage: String? = null,
    onOpenExistingProduct: (ProductStockItem) -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        barcode: String?,
        name: String,
        categoryId: Long?,
        warehouse: String,
        shelfLocation: String?,
        smallUnit: String,
        largeUnit: String?,
        unitFactor: Double,
        costPrice: Double,
        retailPrice: Double,
        wholesalePrice: Double,
        alertThreshold: Double,
        openingMajorQty: Double,
        openingMinorQty: Double
    ) -> Unit
) {
    val isEditing = existingProduct != null
    val stateKey = existingProduct?.Id ?: -1L

    var name by rememberSaveable(stateKey) { mutableStateOf(existingProduct?.Name ?: "") }
    var barcode by rememberSaveable(stateKey) { mutableStateOf(existingProduct?.Barcode ?: initialBarcode ?: "") }
    var smallUnit by rememberSaveable(stateKey) {
        mutableStateOf(existingProduct?.SmallUnit ?: AppDesignState.defaultSmallUnit.ifBlank { "قطعة" })
    }
    var largeUnit by rememberSaveable(stateKey) {
        mutableStateOf(existingProduct?.LargeUnit ?: AppDesignState.defaultLargeUnit)
    }
    var factorText by rememberSaveable(stateKey) {
        mutableStateOf(
            NumberFormatter.formatQuantity(existingProduct?.UnitFactor ?: AppDesignState.defaultUnitFactor.coerceAtLeast(1.0))
        )
    }
    var costPriceText by rememberSaveable(stateKey) {
        mutableStateOf(if (existingProduct != null) NumberFormatter.formatQuantity(existingProduct.CostPrice) else "")
    }
    var retailPriceText by rememberSaveable(stateKey) {
        mutableStateOf(if (existingProduct != null) NumberFormatter.formatQuantity(existingProduct.RetailPrice) else "")
    }
    var wholesalePriceText by rememberSaveable(stateKey) {
        mutableStateOf(
            if (existingProduct != null && existingProduct.WholesalePrice > 0.0) NumberFormatter.formatQuantity(existingProduct.WholesalePrice) else ""
        )
    }
    var openingMajorText by rememberSaveable(stateKey) { mutableStateOf("0") }
    var openingMinorText by rememberSaveable(stateKey) { mutableStateOf("0") }
    var thresholdText by rememberSaveable(stateKey) {
        mutableStateOf(NumberFormatter.formatQuantity(existingProduct?.AlertThreshold ?: AppDesignState.defaultAlertThreshold.coerceAtLeast(0.0)))
    }
    var selectedCategoryId by rememberSaveable(stateKey) { mutableStateOf<Long?>(existingProduct?.CategoryId) }
    var warehouse by rememberSaveable(stateKey) { mutableStateOf(existingProduct?.Warehouse ?: warehouseSuggestions.firstOrNull() ?: "المستودع الرئيسي") }
    var shelfLocation by rememberSaveable(stateKey) { mutableStateOf(existingProduct?.ShelfLocation ?: "") }

    // مراجع انتقال التركيز السلس عبر لوحة المفاتيح (Next -> Next -> Done)
    val focusBarcode = remember { FocusRequester() }
    val focusSmallUnit = remember { FocusRequester() }
    val focusLargeUnit = remember { FocusRequester() }
    val focusFactor = remember { FocusRequester() }
    val focusCost = remember { FocusRequester() }
    val focusRetail = remember { FocusRequester() }
    val focusWholesale = remember { FocusRequester() }
    val focusOpenMajor = remember { FocusRequester() }
    val focusOpenMinor = remember { FocusRequester() }
    val focusThreshold = remember { FocusRequester() }
    val focusShelf = remember { FocusRequester() }

    val duplicateBarcodeProduct = remember(barcode, allProducts, existingProduct) {
        val clean = barcode.trim()
        if (clean.isEmpty()) null
        else allProducts.firstOrNull { it.Id != (existingProduct?.Id ?: 0L) && it.Barcode.equals(clean, ignoreCase = true) }
    }

    val costVal = NumberFormatter.parseDouble(costPriceText, 0.0)
    val retailVal = NumberFormatter.parseDouble(retailPriceText, 0.0)
    val factorVal = NumberFormatter.parseDouble(factorText, 1.0).coerceAtLeast(1.0)
    val openMajVal = NumberFormatter.parseDouble(openingMajorText, 0.0)
    val openMinVal = NumberFormatter.parseDouble(openingMinorText, 0.0)
    val totalOpeningMinor = UnitConversionEngine.toMinorQuantity(openMajVal, openMinVal, factorVal)
    val defaultWholesale = retailVal * factorVal
    val isBelowCost = retailVal > 0.0 && costVal > 0.0 && retailVal < costVal
    val unitProfit = retailVal - costVal
    val marginPct = if (costVal > 0.0 && retailVal > 0.0) ((unitProfit / costVal) * 100.0) else 0.0

    val isFormValid = name.trim().isNotEmpty() && costVal >= 0.0 && retailVal > 0.0 && duplicateBarcodeProduct == null
    val hasUnsavedChanges = name != (existingProduct?.Name ?: "") ||
        barcode != (existingProduct?.Barcode ?: initialBarcode ?: "") ||
        costPriceText != (if (existingProduct != null) NumberFormatter.formatQuantity(existingProduct.CostPrice) else "") ||
        retailPriceText != (if (existingProduct != null) NumberFormatter.formatQuantity(existingProduct.RetailPrice) else "")

    FormSheetContainer(
        title = if (isEditing) "تعديل بيانات الصنف بالمخزن" else "تعريف صنف جديد بالمخزن",
        subtitle = "ضبط الوحدات الكبرى والصغرى ومعامل التحويل وأسعار البيع",
        headerIcon = Icons.Default.Inventory2,
        onDismiss = onDismiss,
        primaryButtonText = if (isEditing) "حفظ التعديلات" else "تأكيد وحفظ الصنف",
        isPrimaryEnabled = isFormValid,
        isSaving = isSaving,
        hasUnsavedChanges = hasUnsavedChanges,
        errorMessage = saveErrorMessage,
        onPrimaryClick = {
            if (isFormValid) {
                val wholesale = NumberFormatter.parseDouble(wholesalePriceText, defaultWholesale)
                val threshold = NumberFormatter.parseDouble(thresholdText, AppDesignState.defaultAlertThreshold)
                onSave(
                    existingProduct?.Id ?: 0L,
                    barcode.trim().ifBlank { null },
                    name.trim(),
                    selectedCategoryId,
                    warehouse.trim().ifBlank { "المستودع الرئيسي" },
                    shelfLocation.trim().ifBlank { null },
                    smallUnit.trim().ifBlank { AppDesignState.defaultSmallUnit.ifBlank { "قطعة" } },
                    largeUnit.trim().ifBlank { null },
                    factorVal,
                    costVal,
                    retailVal,
                    wholesale,
                    threshold,
                    openMajVal,
                    openMinVal
                )
            }
        }
    ) {
        // المجموعة 1: هوية الصنف والباركود
        FormSectionGroup(
            title = "1. بيانات الصنف والباركود"
        ) {
            SmartTextField(
                value = name,
                onValueChange = { name = it },
                label = "اسم الصنف التجاري",
                placeholder = "مثال: حليب الممتاز بودرة 2.5 كجم",
                isRequired = true,
                leadingIcon = Icons.Default.ShoppingBag,
                autoFocusOnAppear = !isEditing && name.isBlank(),
                nextFocusRequester = if (AppDesignState.showBarcodeField) focusBarcode else focusSmallUnit
            )

            if (AppDesignState.showBarcodeField) {
                SmartTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = "الباركود الدولي / المحلي",
                    placeholder = "أدخل الرمز أو اضغط توليد تلقائي",
                    leadingIcon = Icons.Default.QrCode,
                    errorText = if (duplicateBarcodeProduct != null) "هذا الباركود مسجل مسبقاً للصنف «${duplicateBarcodeProduct.Name}»" else null,
                    focusRequester = focusBarcode,
                    nextFocusRequester = focusSmallUnit,
                    trailingContent = {
                        TextButton(
                            onClick = {
                                barcode = "629" + (System.currentTimeMillis() % 1000000000L).toString().padStart(9, '0')
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("توليد", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                        }
                    }
                )

                if (categories.isNotEmpty()) {
                    FilterChipRow(
                        items = listOf<Pair<Long?, String>>(null to "بدون تصنيف") + categories.map { it.Id as Long? to it.Name },
                        selectedItem = selectedCategoryId,
                        onItemSelected = { selectedCategoryId = it }
                    )
                }

                SmartAutocompleteField(
                    value = warehouse,
                    onValueChange = { warehouse = it },
                    label = "المخزن / المستودع",
                    placeholder = "اختر أو اكتب اسم المخزن...",
                    suggestions = warehouseSuggestions,
                    leadingIcon = Icons.Default.Warehouse
                )

                if (duplicateBarcodeProduct != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SmartLedgerColors.StateWarningSubtle)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الصنف الموجود: ${duplicateBarcodeProduct.Name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.StateWarning
                        )
                        TextButton(onClick = { onOpenExistingProduct(duplicateBarcodeProduct) }) {
                            Text("فتح الصنف", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                        }
                    }
                }
            }
        }

        // المجموعة 2: هيكلة الوحدات الصغرى والكبرى ومعامل التحويل
        FormSectionGroup(
            title = "2. الوحدات ومعامل التحويل",
            badgeText = if (largeUnit.isNotBlank() && factorVal > 1.0) "1 $largeUnit = ${NumberFormatter.formatQuantity(factorVal)} $smallUnit" else "وحدة مفردة"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmartAutocompleteField(
                    value = smallUnit,
                    onValueChange = { smallUnit = it },
                    label = "الوحدة الصغرى (الأساسية)",
                    placeholder = "قطعة / حبة",
                    isRequired = true,
                    suggestions = unitSuggestions,
                    focusRequester = focusSmallUnit,
                    nextFocusRequester = focusLargeUnit,
                    modifier = Modifier.weight(1f)
                )
                SmartAutocompleteField(
                    value = largeUnit,
                    onValueChange = { largeUnit = it },
                    label = "الوحدة الكبرى (العبوة)",
                    placeholder = "كرتون / درزن",
                    suggestions = unitSuggestions,
                    focusRequester = focusLargeUnit,
                    nextFocusRequester = focusFactor,
                    modifier = Modifier.weight(1f)
                )
            }

            SmartTextField(
                value = factorText,
                onValueChange = { factorText = it },
                label = "معامل التحويل (كم $smallUnit داخل الـ ${largeUnit.ifBlank { "عبوة" }})",
                placeholder = "24",
                isRequired = true,
                keyboardType = KeyboardType.Decimal,
                focusRequester = focusFactor,
                nextFocusRequester = focusCost
            )
        }

        // المجموعة 3: التكلفة وأسعار البيع (التجزئة والجملة) مع منع البيع بخسارة غير مقصود (UX-003)
        FormSectionGroup(
            title = "3. التكلفة وأسعار البيع (${AppDesignState.currencySymbol})",
            badgeText = if (retailVal > 0.0 && costVal > 0.0 && !isBelowCost) {
                "هامش الربح: +${NumberFormatter.formatCurrency(unitProfit, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} (${NumberFormatter.formatQuantity(marginPct)}%)"
            } else null
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmartTextField(
                    value = costPriceText,
                    onValueChange = { costPriceText = it },
                    label = "تكلفة الـ $smallUnit",
                    placeholder = "0",
                    isRequired = true,
                    keyboardType = KeyboardType.Decimal,
                    focusRequester = focusCost,
                    nextFocusRequester = focusRetail,
                    modifier = Modifier.weight(1f)
                )
                SmartTextField(
                    value = retailPriceText,
                    onValueChange = { retailPriceText = it },
                    label = "سعر بيع الـ $smallUnit",
                    placeholder = "0",
                    isRequired = true,
                    keyboardType = KeyboardType.Decimal,
                    focusRequester = focusRetail,
                    nextFocusRequester = focusWholesale,
                    modifier = Modifier.weight(1f)
                )
            }

            if (isBelowCost) {
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
                        text = "تنبيه هام: سعر البيع المدخل (${NumberFormatter.formatCurrency(retailVal, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)}) أقل من سعر التكلفة (${NumberFormatter.formatCurrency(costVal, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)})! سيؤدي ذلك لبيع الصنف بخسارة.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.StateDangerText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            SmartTextField(
                value = wholesalePriceText,
                onValueChange = { wholesalePriceText = it },
                label = "سعر بيع الـ ${largeUnit.ifBlank { "عبوة" }} (اختياري)",
                placeholder = "الافتراضي التلقائي: ${NumberFormatter.formatQuantity(defaultWholesale)}",
                keyboardType = KeyboardType.Decimal,
                focusRequester = focusWholesale,
                nextFocusRequester = if (!isEditing) focusOpenMajor else focusThreshold
            )
        }

        // المجموعة 4: الرصيد الافتتاحي وحد التنبيه وموقع الرف
        FormSectionGroup(
            title = if (isEditing) "4. حد التنبيه وموقع التخزين" else "4. الرصيد الافتتاحي وحد التنبيه",
            badgeText = if (!isEditing && totalOpeningMinor > 0) "الإجمالي: ${NumberFormatter.formatQuantity(totalOpeningMinor)} $smallUnit" else null
        ) {
            if (!isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmartTextField(
                        value = openingMajorText,
                        onValueChange = { openingMajorText = it },
                        label = "رصيد افتتاحي (${largeUnit.ifBlank { "كبرى" }})",
                        placeholder = "0",
                        keyboardType = KeyboardType.Decimal,
                        focusRequester = focusOpenMajor,
                        nextFocusRequester = focusOpenMinor,
                        modifier = Modifier.weight(1f)
                    )
                    SmartTextField(
                        value = openingMinorText,
                        onValueChange = { openingMinorText = it },
                        label = "رصيد افتتاحي ($smallUnit)",
                        placeholder = "0",
                        keyboardType = KeyboardType.Decimal,
                        focusRequester = focusOpenMinor,
                        nextFocusRequester = focusThreshold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmartTextField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it },
                    label = "حد تنبيه النفاد ($smallUnit)",
                    placeholder = "5",
                    keyboardType = KeyboardType.Decimal,
                    focusRequester = focusThreshold,
                    nextFocusRequester = if (AppDesignState.showShelfLocation) focusShelf else null,
                    imeAction = if (AppDesignState.showShelfLocation) ImeAction.Next else ImeAction.Done,
                    modifier = Modifier.weight(1f)
                )

                if (AppDesignState.showShelfLocation) {
                    SmartTextField(
                        value = shelfLocation,
                        onValueChange = { shelfLocation = it },
                        label = "موقع الرف (اختياري)",
                        placeholder = "مثال: A-12",
                        focusRequester = focusShelf,
                        imeAction = ImeAction.Done,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
