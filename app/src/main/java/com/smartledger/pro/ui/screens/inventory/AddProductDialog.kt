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
        title = if (isEditing) "تعديل الصنف" else "صنف جديد",
        headerIcon = Icons.Default.Inventory2,
        onDismiss = onDismiss,
        primaryButtonText = if (isEditing) "حفظ التعديلات" else "حفظ",
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

                val availableWarehouses = remember(warehouseSuggestions) {
                    warehouseSuggestions.map(String::trim).filter(String::isNotBlank).distinct()
                }
                var warehouseMenuExpanded by rememberSaveable(stateKey) { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = warehouseMenuExpanded,
                    onExpandedChange = { warehouseMenuExpanded = it && availableWarehouses.isNotEmpty() }
                ) {
                    OutlinedTextField(
                        value = warehouse,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("المخزن") },
                        leadingIcon = { Icon(Icons.Default.Warehouse, contentDescription = null) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = warehouseMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SmartLedgerColors.AccentPrimary,
                            unfocusedBorderColor = SmartLedgerColors.BorderSubtle
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = warehouseMenuExpanded,
                        onDismissRequest = { warehouseMenuExpanded = false }
                    ) {
                        availableWarehouses.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, maxLines = 1) },
                                onClick = {
                                    warehouse = option
                                    warehouseMenuExpanded = false
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

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
