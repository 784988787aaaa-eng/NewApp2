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
