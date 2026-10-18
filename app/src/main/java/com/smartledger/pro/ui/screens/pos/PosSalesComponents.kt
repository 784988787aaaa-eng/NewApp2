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

@Composable
fun CheckoutSheetContent(
    subtotalAmount: Double,
    selectedCustomer: AccountBalanceSummary?,
    isSaving: Boolean,
    errorMessage: String?,
    financialAccounts: List<com.smartledger.pro.data.local.dao.FinancialAccountBalanceSummary> = emptyList(),
    onSelectCustomerClick: () -> Unit,
    onCommit: (payType: String, discount: Double, paid: Double, notes: String?, financialAccountId: Long?) -> Unit,
    onClose: () -> Unit
) {
    var paymentType by rememberSaveable { mutableStateOf("Cash") }
    var discountText by rememberSaveable { mutableStateOf("") }
    var paidAmountText by rememberSaveable { mutableStateOf("") }
    var receivedCashText by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var selectedFinAccountId by rememberSaveable { mutableStateOf<Long?>(null) }

    val rawDiscount = NumberFormatter.parseDouble(discountText, 0.0)
    val isDiscountExceeding = rawDiscount > subtotalAmount
    val discountVal = NumberFormatter.roundCurrency(rawDiscount.coerceIn(0.0, subtotalAmount))
    val finalTotal = NumberFormatter.roundCurrency((subtotalAmount - discountVal).coerceAtLeast(0.0))

    val rawPaidInput = NumberFormatter.parseDouble(paidAmountText, 0.0)
    val isPartialInvalid = paymentType == "Partial" && (rawPaidInput <= 0.0 || rawPaidInput >= finalTotal)

    val paidAmount = NumberFormatter.roundCurrency(
        when (paymentType) {
            "Cash" -> finalTotal
            "Credit" -> 0.0
            else -> rawPaidInput.coerceIn(0.0, finalTotal)
        }
    )
    val remaining = NumberFormatter.roundCurrency((finalTotal - paidAmount).coerceAtLeast(0.0))

    val receivedCash = NumberFormatter.parseDouble(receivedCashText, finalTotal)
    val changeDue = if (paymentType == "Cash" && receivedCash > finalTotal) NumberFormatter.roundCurrency(receivedCash - finalTotal) else 0.0

    val requiresNamedCustomer = (paymentType == "Credit" || paymentType == "Partial") && selectedCustomer == null
    val exceedsCreditLimit = selectedCustomer != null &&
        selectedCustomer.CreditLimit > 0.0 &&
        (selectedCustomer.NetBalance + remaining) > selectedCustomer.CreditLimit

    val canSubmit = finalTotal > 0.0 && !requiresNamedCustomer && !isDiscountExceeding && !isPartialInvalid

    FormSheetContainer(
        title = "سداد وترحيل الفاتورة",
        subtitle = "العميل: ${selectedCustomer?.PersonName ?: "عميل نقدي عام"}",
        headerIcon = Icons.Default.PointOfSale,
        onDismiss = onClose,
        primaryButtonText = "تأكيد وترحيل الفاتورة",
        isPrimaryEnabled = canSubmit,
        isSaving = isSaving,
        errorMessage = errorMessage,
        hasUnsavedChanges = discountText.isNotBlank() || paidAmountText.isNotBlank() || notes.isNotBlank(),
        onPrimaryClick = {
            if (canSubmit) {
                onCommit(paymentType, discountVal, paidAmount, notes, selectedFinAccountId)
            }
        }
    ) {
        SegmentedOptionRow(
            options = listOf(
                "Cash" to "نقدي كامل",
                "Credit" to "آجل كامل",
                "Partial" to "سداد جزئي"
            ),
            selectedOption = paymentType,
            onOptionSelected = { paymentType = it }
        )

        if (requiresNamedCustomer) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.StateWarningSubtle),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.StateWarningBorder))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "البيع الآجل أو الجزئي يتطلب تحديد حساب عميل لتسجيل المديونية عليه.",
                        style = MaterialTheme.typography.labelMedium,
                        color = SmartLedgerColors.StateWarning,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onSelectCustomerClick,
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.heightIn(min = 32.dp).pressScaleEffect()
                    ) {
                        Text("اختيار عميل", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (exceedsCreditLimit) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.StateDangerSubtle),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.StateDangerBorder))
            ) {
                Text(
                    text = "تنبيه ائتماني: إجمالي مديونية العميل بعد هذه الفاتورة سيتجاوز سقف الائتمان المحدد (${NumberFormatter.formatCurrency(selectedCustomer!!.CreditLimit, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)}).",
                    style = MaterialTheme.typography.labelSmall,
                    color = SmartLedgerColors.StateDangerText,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (paymentType != "Credit" && financialAccounts.isNotEmpty()) {
            FormSectionGroup(title = "الصندوق / المحفظة المستلمة للنقد") {
                FilterChipRow(
                    items = listOf<Pair<Long?, String>>(null to "الصندوق الافتراضي") + financialAccounts.map { it.Id as Long? to it.Name },
                    selectedItem = selectedFinAccountId,
                    onItemSelected = { selectedFinAccountId = it }
                )
            }
        }

        FormSectionGroup(title = "الخصم والمبالغ المستلمة") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmartTextField(
                    value = discountText,
                    onValueChange = { discountText = it },
                    label = "الخصم (${AppDesignState.currencySymbol})",
                    placeholder = "0",
                    errorText = if (isDiscountExceeding) "الخصم يتجاوز إجمالي الفاتورة" else null,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )

                if (paymentType == "Partial") {
                    SmartTextField(
                        value = paidAmountText,
                        onValueChange = { paidAmountText = it },
                        label = "المسدد نقداً الآن",
                        placeholder = "0",
                        isRequired = true,
                        errorText = if (paidAmountText.isNotEmpty() && isPartialInvalid) "أدخل مبلغاً أكبر من 0 وأقل من الصافي" else null,
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f)
                    )
                } else if (paymentType == "Cash") {
                    SmartTextField(
                        value = receivedCashText,
                        onValueChange = { receivedCashText = it },
                        label = "المستلم من العميل",
                        placeholder = NumberFormatter.formatQuantity(finalTotal),
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (paymentType == "Partial" && finalTotal > 0.0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { paidAmountText = NumberFormatter.formatQuantity(NumberFormatter.roundCurrency(finalTotal * 0.5)) },
                        modifier = Modifier.weight(1f).heightIn(min = 30.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("نصف الصافي (50%)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight)
                    }
                    OutlinedButton(
                        onClick = { paidAmountText = NumberFormatter.formatQuantity(NumberFormatter.roundCurrency(finalTotal * 0.25)) },
                        modifier = Modifier.weight(1f).heightIn(min = 30.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ربع الصافي (25%)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الإجمالي قبل الخصم:", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextSecondary)
                    Text(NumberFormatter.formatCurrency(subtotalAmount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces), fontWeight = FontWeight.SemiBold, color = SmartLedgerColors.TextPrimary)
                }
                if (discountVal > 0.0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الخصم الممنوح:", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.StateWarning)
                        Text("-${NumberFormatter.formatCurrency(discountVal, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)}", fontWeight = FontWeight.SemiBold, color = SmartLedgerColors.StateWarning)
                    }
                }
                Divider(color = SmartLedgerColors.BorderSubtle)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الصافي النهائي للفاتورة:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                    Text(NumberFormatter.formatCurrency(finalTotal, AppDesignState.currencySymbol, AppDesignState.decimalPlaces), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateSuccess)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("المسدد نقداً للصندوق:", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.StateSuccessText)
                    Text(NumberFormatter.formatCurrency(paidAmount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces), fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateSuccess)
                }
                if (remaining > 0.0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المتبقي آجل على العميل:", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.StateDangerText)
                        Text(NumberFormatter.formatCurrency(remaining, AppDesignState.currencySymbol, AppDesignState.decimalPlaces), fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateDanger)
                    }
                }
                if (changeDue > 0.0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الباقي المرتجع للعميل:", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.AccentLight)
                        Text(NumberFormatter.formatCurrency(changeDue, AppDesignState.currencySymbol, AppDesignState.decimalPlaces), fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                    }
                }
            }
        }

        SmartTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "ملاحظة على الفاتورة (اختياري)",
            placeholder = "رقم طلب، اسم المستلم، أو بيان إضافي...",
            imeAction = ImeAction.Done
        )
    }
}
