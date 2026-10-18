package com.smartledger.pro.ui.screens.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartledger.pro.data.local.dao.ProductStockItem
import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.ui.components.bounceClick
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.util.NumberFormatter

@Composable
fun ProductCardItem(
    product: ProductStockItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onQuickStocktake: () -> Unit,
    onDelete: () -> Unit,
    showBarcodeField: Boolean = AppDesignState.showBarcodeField,
    showShelfLocationField: Boolean = AppDesignState.showShelfLocation,
    showCostAndMargin: Boolean = true,
    showWarehouseBadge: Boolean = true,
    showQuickStocktakeButton: Boolean = true
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

    val unitProfit = product.RetailPrice - product.CostPrice
    val marginPct = if (product.CostPrice > 0.0 && product.RetailPrice > 0.0) {
        ((unitProfit / product.CostPrice) * 100.0)
    } else 0.0

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

                    Spacer(modifier = Modifier.height(4.dp))

                    // شارات المخزن، الرف، الباركود، ومعامل التحويل
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showWarehouseBadge && product.Warehouse.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SmartLedgerColors.SurfaceCardElevated)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = product.Warehouse,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.AccentLight,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (showShelfLocationField && !product.ShelfLocation.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SmartLedgerColors.SurfaceCardElevated)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "رف: ${product.ShelfLocation}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextSecondary
                                )
                            }
                        }
                        if (showBarcodeField && !product.Barcode.isNullOrBlank()) {
                            Text(
                                text = "#${product.Barcode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                        }
                    }

                    if (!product.LargeUnit.isNullOrBlank() && product.UnitFactor > 1.0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "1 ${product.LargeUnit} = ${NumberFormatter.formatQuantity(product.UnitFactor)} ${product.SmallUnit}",
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
                                text = if (isOutOfStock) "نافد" else "قارب النفاد",
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
                        text = "الرصيد المتاح بالمخزن",
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

            // صف التكلفة وهامش الربح + أزرار الحركة السريعة المدمجة
            if (showCostAndMargin || showQuickStocktakeButton) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = SmartLedgerColors.BorderSubtle.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showCostAndMargin) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "التكلفة: ${NumberFormatter.formatCurrency(product.CostPrice, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextSecondary
                            )
                            if (marginPct > 0.0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SmartLedgerColors.StateSuccessSubtle)
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "ربح +${NumberFormatter.formatQuantity(marginPct)}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SmartLedgerColors.StateSuccessText,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (showQuickStocktakeButton) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                modifier = Modifier.clickable { onClick() },
                                shape = RoundedCornerShape(6.dp),
                                color = SmartLedgerColors.SurfaceCardElevated,
                                border = BorderStroke(0.5.dp, SmartLedgerColors.BorderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(12.dp))
                                    Text("الحركات", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(
                                modifier = Modifier.clickable { onQuickStocktake() },
                                shape = RoundedCornerShape(6.dp),
                                color = SmartLedgerColors.StateWarningSubtle.copy(alpha = 0.65f),
                                border = BorderStroke(0.5.dp, SmartLedgerColors.StateWarningBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Default.FactCheck, contentDescription = null, tint = SmartLedgerColors.StateWarning, modifier = Modifier.size(12.dp))
                                    Text("جرد سريع", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateWarningText, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
