package com.smartledger.pro.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.viewmodels.SettingsViewModel
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * منظومة إدارة الصناديق الرئيسية والفرعية والمحافظ والتحويلات الذرية والجرد (FinancialAccountsAndTransfersSheet)
 * - تدعم تعريف صناديق رئيسية وصناديق فرعية مرتبطة بها (`parentGroupId`).
 * - تعرض ملخص السيولة الشامل (إجمالي الداخل، إجمالي الخارج، الصافي الكلي) في أعلى النافذة.
 * - تتيح التحويل الفوري أو المطابقة الجردية لأي صندوق بلمسة واحدة من بطاقته.
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

    // حقول إنشاء صندوق رئيسي أو فرعي / محفظة / بنك
    var accName by rememberSaveable { mutableStateOf("") }
    var accType by rememberSaveable { mutableStateOf("CASH_BOX") }
    var selectedParentId by rememberSaveable { mutableStateOf<Long?>(null) }
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

    val totalInflows = remember(accounts) { accounts.sumOf { it.TotalInflows } }
    val totalOutflows = remember(accounts) { accounts.sumOf { it.TotalOutflows } }
    val totalNet = remember(accounts) { accounts.sumOf { it.NetBalance } }

    val mainAccounts = remember(accounts) {
        accounts.filter { it.ParentGroupId == null || it.ParentGroupId <= 0L }
    }
    val subAccountsByParent = remember(accounts) {
        accounts.filter { it.ParentGroupId != null && it.ParentGroupId > 0L }
            .groupBy { it.ParentGroupId!! }
    }

    FloatingModalDialog(onDismissRequest = onClose) {
        FormSheetContainer(
            title = "منظومة الصناديق الرئيسية والفرعية",
            subtitle = "إدارة الخزائن النقدية والمحافظ والبنوك والتحويل الذري والمطابقة الجردية",
            headerIcon = Icons.Default.AccountBalanceWallet,
            onDismiss = onClose,
            primaryButtonText = when (activeTab) {
                "ACCOUNTS" -> "حفظ الصندوق / الحساب"
                "TRANSFER" -> "تنفيذ التحويل الذري"
                else -> "اعتماد مطابقة الجرد"
            },
            secondaryButtonText = "إغلاق",
            onPrimaryClick = {
                scope.launch {
                    when (activeTab) {
                        "ACCOUNTS" -> {
                            val res = viewModel.saveFinancialAccountAsync(
                                name = accName,
                                accountType = accType,
                                parentGroupId = selectedParentId,
                                openingBalance = NumberFormatter.parseDouble(accOpeningText, 0.0),
                                allowNegativeBalance = accAllowNegative,
                                isDefault = accIsDefault
                            )
                            if (res.isSuccess) {
                                isFeedbackError = false
                                feedbackMsg = "تم حفظ الحساب المالي «$accName» بنجاح."
                                accName = ""
                                accOpeningText = "0"
                                selectedParentId = null
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
                                feedbackMsg = if (abs(delta) < 0.001) {
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

            // شريط ملخص السيولة الشامل (كل ما دخل • كل ما خرج • الصافي الكلي)
            SmartScreenSummaryStrip(
                firstLabel = "إجمالي الداخل (+)",
                firstValue = NumberFormatter.formatCurrency(totalInflows, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                firstColor = SmartLedgerColors.StateSuccess,
                secondLabel = "إجمالي الخارج (-)",
                secondValue = NumberFormatter.formatCurrency(totalOutflows, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                secondColor = SmartLedgerColors.StateDanger,
                thirdLabel = "صافي السيولة الحية",
                thirdValue = NumberFormatter.formatCurrency(totalNet, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                thirdColor = if (totalNet >= 0.0) SmartLedgerColors.AccentLight else SmartLedgerColors.StateDanger
            )

            SegmentedOptionRow(
                options = listOf(
                    "ACCOUNTS" to "الصناديق (${accounts.size})",
                    "TRANSFER" to "تحويل داخلي",
                    "RECONCILE" to "جرد ومطابقة"
                ),
                selectedOption = activeTab,
                onOptionSelected = { activeTab = it }
            )

            when (activeTab) {
                "ACCOUNTS" -> {
                    FormSectionGroup(title = "1. تأسيس صندوق (رئيسي أو فرعي) أو محفظة أو بنك") {
                        FilterChipRow(
                            items = listOf(
                                "CASH_BOX" to "صندوق نقدي",
                                "E_WALLET" to "محفظة إلكترونية",
                                "BANK_ACCOUNT" to "حساب بنكي",
                                "POS_TERMINAL" to "شبكة / كاشير"
                            ),
                            selectedItem = accType,
                            onItemSelected = { accType = it }
                        )

                        if (mainAccounts.isNotEmpty()) {
                            val parentOptions = remember(mainAccounts) {
                                listOf<Pair<Long?, String>>(null to "صندوق رئيسي مستقل") +
                                    mainAccounts.map { it.Id as Long? to "فرعي تابع لـ: ${it.Name}" }
                            }
                            Text(
                                text = "المستوى التنظيمي للصندوق:",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextSecondary
                            )
                            FilterChipRow(
                                items = parentOptions,
                                selectedItem = selectedParentId,
                                onItemSelected = { selectedParentId = it }
                            )
                        }

                        SmartTextField(
                            value = accName,
                            onValueChange = { accName = it },
                            label = if (selectedParentId != null) "اسم الصندوق الفرعي" else "اسم الصندوق الرئيسي أو المحفظة",
                            placeholder = if (selectedParentId != null) "مثال: صندوق كاشير الفترة المسائية..." else "مثال: الخزينة الرئيسية، محفظة جوالي...",
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

                    FormSectionGroup(title = "2. شجرة الصناديق الرئيسية والفرعية وحركتها (${accounts.size})") {
                        if (accounts.isEmpty()) {
                            Text("لم يتم تعريف صناديق أو محافظ بعد.", style = MaterialTheme.typography.bodySmall, color = SmartLedgerColors.TextMuted)
                        } else {
                            val roots = if (mainAccounts.isNotEmpty()) mainAccounts else accounts
                            roots.forEach { acc ->
                                val subList = subAccountsByParent[acc.Id].orEmpty()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(SmartLedgerTokens.cardCorner))
                                        .background(SmartLedgerColors.SurfaceCardElevated)
                                        .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(SmartLedgerTokens.cardCorner))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = acc.Name,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SmartLedgerColors.TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (acc.IsDefault) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(SmartLedgerColors.AccentSubtle)
                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                    ) {
                                                        Text("افتراضي", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Text(
                                                text = "افتتاحي: ${NumberFormatter.formatAmount(acc.OpeningBalance)} • داخل: +${NumberFormatter.formatAmount(acc.TotalInflows)} • خارج: -${NumberFormatter.formatAmount(acc.TotalOutflows)}",
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

                                    // أزرار ذكية مدمجة للتحويل أو الجرد أو إضافة صندوق فرعي
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            modifier = Modifier.clickable {
                                                selectedParentId = acc.Id
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = SmartLedgerColors.SurfaceCard,
                                            border = BorderStroke(0.5.dp, SmartLedgerColors.BorderSubtle)
                                        ) {
                                            Text(
                                                text = "+ صندوق فرعي",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SmartLedgerColors.AccentLight,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Surface(
                                            modifier = Modifier.clickable {
                                                fromAccountId = acc.Id
                                                activeTab = "TRANSFER"
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = SmartLedgerColors.SurfaceCard,
                                            border = BorderStroke(0.5.dp, SmartLedgerColors.BorderSubtle)
                                        ) {
                                            Text(
                                                text = "تحويل منه ⇄",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SmartLedgerColors.TextSecondary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Surface(
                                            modifier = Modifier.clickable {
                                                reconcileAccountId = acc.Id
                                                actualCountedBalanceText = NumberFormatter.formatQuantity(acc.NetBalance)
                                                activeTab = "RECONCILE"
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = SmartLedgerColors.SurfaceCard,
                                            border = BorderStroke(0.5.dp, SmartLedgerColors.BorderSubtle)
                                        ) {
                                            Text(
                                                text = "مطابقة جرد ✔",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SmartLedgerColors.StateWarning,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    // عرض الصناديق الفرعية التابعة لهذا الصندوق الرئيسي
                                    if (subList.isNotEmpty()) {
                                        Divider(color = SmartLedgerColors.BorderSubtle)
                                        subList.forEach { sub ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(SmartLedgerColors.SurfaceCard)
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Icon(
                                                        imageVector = Icons.Default.SubdirectoryArrowLeft,
                                                        contentDescription = null,
                                                        tint = SmartLedgerColors.AccentLight,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Column {
                                                        Text(sub.Name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                                                        Text(
                                                            text = "داخل: +${NumberFormatter.formatAmount(sub.TotalInflows)} • خارج: -${NumberFormatter.formatAmount(sub.TotalOutflows)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = SmartLedgerColors.TextMuted
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = NumberFormatter.formatCurrency(sub.NetBalance, sub.CurrencySymbol, AppDesignState.decimalPlaces),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (sub.NetBalance >= 0.0) SmartLedgerColors.AccentLight else SmartLedgerColors.StateDanger
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "TRANSFER" -> {
                    val accountChips = remember(accounts) {
                        accounts.filter { it.IsActive }.map { it.Id as Long? to "${it.Name} (${NumberFormatter.formatCurrency(it.NetBalance, it.CurrencySymbol)})" }
                    }
                    FormSectionGroup(title = "تحويل داخلي ذري بين الصناديق (رئيسي ⇄ فرعي أو محفظة)") {
                        if (accountChips.size < 2) {
                            Text("يرجى تعريف حسابين ماليين على الأقل (مثلاً صندوق رئيسي وصندوق فرعي أو محفظة) لتنفيذ التحويلات الداخلية.", color = SmartLedgerColors.StateWarning, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("من صندوق / حساب (المصدر):", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                            FilterChipRow(
                                items = accountChips,
                                selectedItem = fromAccountId,
                                onItemSelected = { fromAccountId = it }
                            )
                            Text("إلى صندوق / حساب (الوجهة):", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
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
                                placeholder = "مثال: تغذية الصندوق الفرعي من الخزينة الرئيسية...",
                                imeAction = ImeAction.Done
                            )
                        }
                        if (transfers.isNotEmpty()) {
                            Divider(color = SmartLedgerColors.BorderSubtle)
                            Text("أحدث التحويلات الداخلية (${transfers.size}):", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                            transfers.take(6).forEach { tr ->
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
                            label = "الرصيد الفعلي الموجود حالياً بعد العد",
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
