package com.smartledger.pro.ui.screens.accounts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.dao.AccountBalanceSummary
import com.smartledger.pro.data.local.entity.BookEntity
import com.smartledger.pro.data.local.entity.OperationEntity
import com.smartledger.pro.domain.usecase.LedgerDirectionPolicy
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.viewmodels.AccountsViewModel
import com.smartledger.pro.util.NumberFormatter
import com.smartledger.pro.util.ThermalPrinterHelper
import com.smartledger.pro.util.WhatsAppPreviewDialog
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun QuickAccountVoucherSheet(
    account: AccountBalanceSummary,
    initialIsReceipt: Boolean,
    financialAccounts: List<com.smartledger.pro.data.local.dao.FinancialAccountBalanceSummary> = emptyList(),
    openInvoices: List<com.smartledger.pro.data.local.entity.InvoiceEntity> = emptyList(),
    isSaving: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (isReceipt: Boolean, amount: Double, statement: String, refNumber: String?, financialAccountId: Long?, targetInvoiceId: Long?) -> Unit
) {
    var isReceipt by rememberSaveable(account.PersonId) { mutableStateOf(initialIsReceipt) }
    var amountText by rememberSaveable(account.PersonId) { mutableStateOf("") }
    var statement by rememberSaveable(account.PersonId) { mutableStateOf("") }
    var refNumber by rememberSaveable(account.PersonId) { mutableStateOf("") }
    var selectedFinAccountId by rememberSaveable(account.PersonId) { mutableStateOf<Long?>(null) }
    var selectedTargetInvoiceId by rememberSaveable(account.PersonId) { mutableStateOf<Long?>(null) }

    val focusStatement = remember { FocusRequester() }
    val focusRef = remember { FocusRequester() }

    val amountVal = NumberFormatter.parseDouble(amountText, 0.0)
    val isValid = amountVal > 0.0
    val currentNet = account.NetBalance
    val projectedNet = if (isReceipt) (currentNet - amountVal) else (currentNet + amountVal)

    FormSheetContainer(
        title = if (isReceipt) "سند قبض سريع (له)" else "سند صرف سريع (عليه)",
        subtitle = "الحساب: ${account.PersonName} • الرصيد الحالي: ${NumberFormatter.formatCurrency(abs(currentNet), AppDesignState.currencySymbol, AppDesignState.decimalPlaces)}",
        headerIcon = Icons.Default.ReceiptLong,
        onDismiss = onDismiss,
        primaryButtonText = if (isReceipt) "ترحيل سند القبض" else "ترحيل سند الصرف",
        primaryButtonColor = if (isReceipt) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger,
        isPrimaryEnabled = isValid,
        isSaving = isSaving,
        errorMessage = errorMessage,
        hasUnsavedChanges = amountText.isNotBlank() || statement.isNotBlank() || refNumber.isNotBlank(),
        onPrimaryClick = {
            if (isValid) {
                val finalStatement = statement.trim().ifBlank {
                    if (isReceipt) "سند قبض نقدية من ${account.PersonName}" else "سند صرف نقدية إلى ${account.PersonName}"
                }
                onConfirm(isReceipt, amountVal, finalStatement, refNumber.trim().ifBlank { null }, selectedFinAccountId, selectedTargetInvoiceId)
            }
        }
    ) {
        SegmentedOptionRow(
            options = listOf(
                true to "سند قبض / استلام (له)",
                false to "سند صرف / سداد (عليه)"
            ),
            selectedOption = isReceipt,
            onOptionSelected = { isReceipt = it },
            activeColor = if (isReceipt) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
        )

        // بطاقة المعاينة المالية الحية للرصيد قبل وبعد السند (UX-002 Financial Trust)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("الرصيد قبل السند", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                        Text(
                            text = "${NumberFormatter.formatCurrency(abs(currentNet), AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} ${
                                when {
                                    currentNet > 0.001 -> "(عليه)"
                                    currentNet < -0.001 -> "(له)"
                                    else -> "(متزن)"
                                }
                            }",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (currentNet >= 0) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess
                        )
                    }
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = SmartLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("الرصيد المتوقع بعد الترحيل", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                        Text(
                            text = "${NumberFormatter.formatCurrency(abs(projectedNet), AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} ${
                                when {
                                    projectedNet > 0.001 -> "(عليه)"
                                    projectedNet < -0.001 -> "(له)"
                                    else -> "(مسدد بالكامل)"
                                }
                            }",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                projectedNet > 0.001 -> SmartLedgerColors.StateDanger
                                projectedNet < -0.001 -> SmartLedgerColors.StateSuccess
                                else -> SmartLedgerColors.AccentLight
                            }
                        )
                    }
                }

                if (abs(currentNet) > 0.001) {
                    OutlinedButton(
                        onClick = {
                            isReceipt = currentNet > 0.0
                            amountText = NumberFormatter.formatQuantity(abs(currentNet))
                            if (statement.isBlank()) {
                                statement = if (currentNet > 0.0) {
                                    "تصفية وسداد كامل الرصيد المستحق من ${account.PersonName}"
                                } else {
                                    "تصفية وسداد كامل الرصيد الدائن إلى ${account.PersonName}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp).pressScaleEffect(),
                        shape = RoundedCornerShape(8.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تعبئة وتصفية كامل الرصيد (${NumberFormatter.formatCurrency(abs(currentNet), AppDesignState.currencySymbol, AppDesignState.decimalPlaces)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.AccentLight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        FormSectionGroup(title = "تفاصيل السند المالي") {
            if (financialAccounts.isNotEmpty()) {
                CompactDropdownPicker(
                    label = "الصندوق / المحفظة",
                    options = listOf<Pair<Long?, String>>(null to "الصندوق الافتراضي") + financialAccounts.map { it.Id as Long? to it.Name },
                    selectedOption = selectedFinAccountId,
                    onOptionSelected = { selectedFinAccountId = it },
                    leadingIcon = Icons.Default.AccountBalanceWallet
                )
            }
            if (openInvoices.isNotEmpty()) {
                FilterChipRow(
                    items = listOf<Pair<Long?, String>>(null to "دفعة عامة على الحساب") +
                        openInvoices.map { it.Id as Long? to "${it.InvoiceNumber} (${NumberFormatter.formatCurrency(it.RemainingAmount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)})" },
                    selectedItem = selectedTargetInvoiceId,
                    onItemSelected = { invId ->
                        selectedTargetInvoiceId = invId
                        val chosen = openInvoices.firstOrNull { it.Id == invId }
                        if (chosen != null && amountText.isBlank()) {
                            amountText = NumberFormatter.formatQuantity(chosen.RemainingAmount)
                            if (statement.isBlank()) statement = "سداد فاتورة #${chosen.InvoiceNumber}"
                        }
                    }
                )
            }
            SmartTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = "مبلغ السند (${AppDesignState.currencySymbol})",
                placeholder = "0",
                isRequired = true,
                keyboardType = KeyboardType.Decimal,
                autoFocusOnAppear = amountText.isBlank(),
                imeAction = ImeAction.Next,
                nextFocusRequester = focusStatement
            )

            SmartTextField(
                value = statement,
                onValueChange = { statement = it },
                label = "البيان والشرح المحاسبي",
                placeholder = if (isReceipt) "دفعة نقدية من الحساب..." else "دفعة مسددة للحساب...",
                focusRequester = focusStatement,
                nextFocusRequester = focusRef,
                imeAction = ImeAction.Next
            )

            SmartTextField(
                value = refNumber,
                onValueChange = { refNumber = it },
                label = "رقم السند الورقي / الحوالة (اختياري)",
                placeholder = "مثال: RV-1042",
                focusRequester = focusRef,
                imeAction = ImeAction.Done
            )
        }
    }
}
