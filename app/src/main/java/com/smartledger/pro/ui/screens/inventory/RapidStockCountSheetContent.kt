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
