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
