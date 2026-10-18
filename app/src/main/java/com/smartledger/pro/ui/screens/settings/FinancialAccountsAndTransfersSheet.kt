package com.smartledger.pro.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartledger.pro.domain.usecase.HierarchicalCategoryValidator
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.viewmodels.SettingsViewModel
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.launch

/**
 * معالج الإعداد الأولي الرشيق (FirstRunOnboardingDialog) — خطوة واحدة اختيارية دون فرض أي بيانات تجارية وهمية.
 */
@Composable
fun FinancialAccountsAndTransfersSheet(
    viewModel: SettingsViewModel,
    onClose: () -> Unit
) {
    val accounts by viewModel.financialAccounts.collectAsState()
    val transfers by viewModel.recentTransfers.collectAsState()
    val scope = rememberCoroutineScope()

    var activeTab by rememberSaveable { mutableStateOf("ACCOUNTS") } // ACCOUNTS, TRANSFER, RECONCILE
    var feedbackMsg by rememberSaveable { mutableStateOf<String?>(null) }
    var isFeedbackError by rememberSaveable { mutableStateOf(false) }

    // حقول إنشاء صندوق/محفظة/بنك
    var accName by rememberSaveable { mutableStateOf("") }
    var accType by rememberSaveable { mutableStateOf("CASH_BOX") }
    var accOpeningText by rememberSaveable { mutableStateOf("0") }
    var accAllowNegative by rememberSaveable { mutableStateOf(false) }
    var accIsDefault by rememberSaveable { mutableStateOf(accounts.isEmpty()) }

    // حقول التحويل الداخلي الذري
    var fromAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var toAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var transferAmountText by rememberSaveable { mutableStateOf("") }
    var transferFeeText by rememberSaveable { mutableStateOf("0") }
    var transferNotes by rememberSaveable { mutableStateOf("") }

    // حقول الجرد والمطابقة النقدية
    var reconcileAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var actualCountedBalanceText by rememberSaveable { mutableStateOf("") }
    var reconcileNotes by rememberSaveable { mutableStateOf("") }

    FloatingModalDialog(onDismissRequest = onClose) {
        FormSheetContainer(
            title = "الصناديق والمحافظ والتحويلات المالية",
            subtitle = "إدارة الخزائن النقدية والمحافظ الإلكترونية والحسابات البنكية والتحويل الذري بينها",
            headerIcon = Icons.Default.AccountBalanceWallet,
            onDismiss = onClose,
            primaryButtonText = when (activeTab) {
                "ACCOUNTS" -> "حفظ الحساب المالي"
                "TRANSFER" -> "تنفيذ التحويل الداخلي"
                else -> "اعتماد المطابقة"
            },
            secondaryButtonText = "إغلاق",
            onPrimaryClick = {
                scope.launch {
                    when (activeTab) {
                        "ACCOUNTS" -> {
                            val res = viewModel.saveFinancialAccountAsync(
                                name = accName,
                                accountType = accType,
                                openingBalance = NumberFormatter.parseDouble(accOpeningText, 0.0),
                                allowNegativeBalance = accAllowNegative,
                                isDefault = accIsDefault
                            )
                            if (res.isSuccess) {
                                isFeedbackError = false
                                feedbackMsg = "تم حفظ الحساب المالي «$accName» بنجاح."
                                accName = ""
                                accOpeningText = "0"
                            } else {
                                isFeedbackError = true
                                feedbackMsg = res.exceptionOrNull()?.message
                            }
                        }
                        "TRANSFER" -> {
                            val res = viewModel.createInternalTransferAsync(
                                fromAccountId = fromAccountId ?: 0L,
                                toAccountId = toAccountId ?: 0L,
                                amount = NumberFormatter.parseDouble(transferAmountText, 0.0),
                                feeAmount = NumberFormatter.parseDouble(transferFeeText, 0.0),
                                notes = transferNotes
                            )
                            if (res.isSuccess) {
                                isFeedbackError = false
                                feedbackMsg = "تم تنفيذ التحويل الداخلي الذري بنجاح دون التأثير على قائمة الدخل."
                                transferAmountText = ""
                                transferFeeText = "0"
                                transferNotes = ""
                            } else {
                                isFeedbackError = true
                                feedbackMsg = res.exceptionOrNull()?.message
                            }
                        }
                        "RECONCILE" -> {
                            val res = viewModel.reconcileFinancialAccountAsync(
                                accountId = reconcileAccountId ?: 0L,
                                actualBalance = NumberFormatter.parseDouble(actualCountedBalanceText, 0.0),
                                notes = reconcileNotes
                            )
                            if (res.isSuccess) {
                                val delta = res.getOrDefault(0.0)
                                isFeedbackError = false
                                feedbackMsg = if (kotlin.math.abs(delta) < 0.001) {
                                    "الرصيد الفعلي مطابق تماماً للرصيد الدفتري (لا يوجد فارق)."
                                } else {
                                    "تم تسجيل قيد تسوية المطابقة بفارق (${NumberFormatter.formatCurrency(delta, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)})."
                                }
                                actualCountedBalanceText = ""
                                reconcileNotes = ""
                            } else {
                                isFeedbackError = true
                                feedbackMsg = res.exceptionOrNull()?.message
                            }
                        }
                    }
                }
            }
        ) {
            StatusFeedbackBanner(
                message = feedbackMsg,
                isError = isFeedbackError,
                onDismiss = { feedbackMsg = null }
            )

            SegmentedOptionRow(
                options = listOf(
                    "ACCOUNTS" to "الصناديق والمحافظ",
                    "TRANSFER" to "تحويل داخلي",
                    "RECONCILE" to "جرد ومطابقة"
                ),
                selectedOption = activeTab,
                onOptionSelected = { activeTab = it }
            )

            when (activeTab) {
                "ACCOUNTS" -> {
                    FormSectionGroup(title = "1. تعريف صندوق أو محفظة أو حساب بنكي جديد") {
                        FilterChipRow(
                            items = listOf(
                                "CASH_BOX" to "صندوق نقدي",
                                "E_WALLET" to "محفظة إلكترونية",
                                "BANK_ACCOUNT" to "حساب بنكي",
                                "POS_TERMINAL" to "شبكة / نقطة بيع"
                            ),
                            selectedItem = accType,
                            onItemSelected = { accType = it }
                        )
                        SmartTextField(
                            value = accName,
                            onValueChange = { accName = it },
                            label = "اسم الصندوق أو المحفظة أو البنك",
                            placeholder = "أدخل الاسم الذي تختاره...",
                            isRequired = true
                        )
                        SmartTextField(
                            value = accOpeningText,
                            onValueChange = { accOpeningText = it },
                            label = "الرصيد الافتتاحي الموثق (اختياري)",
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        )
                    }

                    FormSectionGroup(title = "2. أرصدة الصناديق والمحافظ الحية (${accounts.size})") {
                        if (accounts.isEmpty()) {
                            Text("لم يتم تعريف صناديق أو محافظ بعد.", style = MaterialTheme.typography.bodySmall, color = SmartLedgerColors.TextMuted)
                        } else {
                            accounts.forEach { acc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SmartLedgerColors.SurfaceCardElevated)
                                        .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(acc.Name, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = "وارد: ${NumberFormatter.formatCurrency(acc.TotalInflows, acc.CurrencySymbol)} • صادر: ${NumberFormatter.formatCurrency(acc.TotalOutflows, acc.CurrencySymbol)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SmartLedgerColors.TextMuted
                                        )
                                    }
                                    Text(
                                        text = NumberFormatter.formatCurrency(acc.NetBalance, acc.CurrencySymbol, AppDesignState.decimalPlaces),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (acc.NetBalance >= 0.0) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                                    )
                                }
                            }
                        }
                    }
                }

                "TRANSFER" -> {
                    val accountChips = remember(accounts) {
                        accounts.filter { it.IsActive }.map { it.Id as Long? to "${it.Name} (${NumberFormatter.formatCurrency(it.NetBalance, it.CurrencySymbol)})" }
                    }
                    FormSectionGroup(title = "تحويل داخلي ذري بين حسابين ماليين") {
                        if (accountChips.size < 2) {
                            Text("يرجى تعريف حسابين ماليين على الأقل (مثلاً صندوق ومحفظة) لتنفيذ التحويلات الداخلية.", color = SmartLedgerColors.StateWarning, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("من حساب (المصدر):", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                            FilterChipRow(
                                items = accountChips,
                                selectedItem = fromAccountId,
                                onItemSelected = { fromAccountId = it }
                            )
                            Text("إلى حساب (الوجهة):", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                            FilterChipRow(
                                items = accountChips,
                                selectedItem = toAccountId,
                                onItemSelected = { toAccountId = it }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SmartTextField(
                                    value = transferAmountText,
                                    onValueChange = { transferAmountText = it },
                                    label = "مبلغ التحويل",
                                    isRequired = true,
                                    keyboardType = KeyboardType.Decimal,
                                    modifier = Modifier.weight(1f)
                                )
                                SmartTextField(
                                    value = transferFeeText,
                                    onValueChange = { transferFeeText = it },
                                    label = "عمولة/رسوم التحويل",
                                    keyboardType = KeyboardType.Decimal,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            SmartTextField(
                                value = transferNotes,
                                onValueChange = { transferNotes = it },
                                label = "بيان التحويل (اختياري)",
                                placeholder = "مثال: إيداع نقدي من الصندوق إلى المحفظة...",
                                imeAction = ImeAction.Done
                            )
                        }
                        if (transfers.isNotEmpty()) {
                            Divider(color = SmartLedgerColors.BorderSubtle)
                            Text("أحدث التحويلات الداخلية (${transfers.size}):", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                            transfers.take(5).forEach { tr ->
                                Text(
                                    text = "• ${tr.Notes ?: "تحويل داخلي"}: ${NumberFormatter.formatCurrency(tr.Amount, AppDesignState.currencySymbol)} (${tr.OccurredAt.take(16)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextMuted
                                )
                            }
                        }
                    }
                }

                "RECONCILE" -> {
                    val accountChips = remember(accounts) {
                        accounts.filter { it.IsActive }.map { it.Id as Long? to "${it.Name} (الدفتري: ${NumberFormatter.formatCurrency(it.NetBalance, it.CurrencySymbol)})" }
                    }
                    FormSectionGroup(title = "جرد ومطابقة الرصيد الفعلي للصندوق أو المحفظة") {
                        FilterChipRow(
                            items = accountChips,
                            selectedItem = reconcileAccountId,
                            onItemSelected = { reconcileAccountId = it }
                        )
                        SmartTextField(
                            value = actualCountedBalanceText,
                            onValueChange = { actualCountedBalanceText = it },
                            label = "الرصيد الفعلي الموجود حالياً بعد الجرد",
                            isRequired = true,
                            keyboardType = KeyboardType.Decimal
                        )
                        SmartTextField(
                            value = reconcileNotes,
                            onValueChange = { reconcileNotes = it },
                            label = "سبب الفارق أو ملاحظة المطابقة (اختياري)",
                            imeAction = ImeAction.Done
                        )
                    }
                }
            }
        }
    }
}

/**
 * نافذة إدارة الوحدات المرنة والمخازن المتعددة والدفاتر الحسابية (UnitsAndWarehousesManagerSheet)
 */
