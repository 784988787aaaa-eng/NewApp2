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
