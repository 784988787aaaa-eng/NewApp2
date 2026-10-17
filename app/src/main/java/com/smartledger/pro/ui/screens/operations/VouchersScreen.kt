package com.smartledger.pro.ui.screens.operations

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
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.viewmodels.VouchersViewModel
import com.smartledger.pro.util.NumberFormatter
import com.smartledger.pro.util.ThermalPrinterHelper
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun VouchersScreen(
    onBack: (() -> Unit)? = null,
    viewModel: VouchersViewModel = viewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val financialAccounts by viewModel.financialAccounts.collectAsState()
    val recentVouchers by viewModel.recentVouchers.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isReceiptMode by rememberSaveable { mutableStateOf(true) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var selectedAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var statementText by rememberSaveable { mutableStateOf("") }
    var refNumberText by rememberSaveable { mutableStateOf("") }
    var selectedFinancialAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedTargetInvoiceId by rememberSaveable { mutableStateOf<Long?>(null) }

    var showAccountPickerSheet by rememberSaveable { mutableStateOf(false) }
    var showConfirmPostDialog by rememberSaveable { mutableStateOf(false) }
    var isPostingVoucher by remember { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isStatusError by rememberSaveable { mutableStateOf(false) }

    val selectedAccount = remember(accounts, selectedAccountId) {
        selectedAccountId?.let { id -> accounts.firstOrNull { it.PersonId == id } }
    }
    val openInvoicesFlow = remember(selectedAccountId) {
        if (selectedAccountId != null && selectedAccountId!! > 0L) viewModel.getOpenInvoicesForPerson(selectedAccountId!!) else kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val openInvoices by openInvoicesFlow.collectAsState(initial = emptyList())

    val focusStatement = remember { FocusRequester() }
    val amountVal = NumberFormatter.parseDouble(amountText, 0.0)
    val isFormValid = amountVal > 0.0 && selectedAccount != null && !isPostingVoucher

    val currentNet = selectedAccount?.NetBalance ?: 0.0
    val projectedNet = if (isReceiptMode) (currentNet - amountVal) else (currentNet + amountVal)

    Scaffold(
        containerColor = SmartLedgerColors.SurfaceWindow,
        topBar = {
            Column(
                modifier = Modifier
                    .background(SmartLedgerColors.SurfacePanel)
                    .padding(horizontal = SmartLedgerTokens.screenHorizontalPadding, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(34.dp).clip(CircleShape).background(SmartLedgerColors.SurfaceCardElevated)
                            ) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "رجوع", tint = SmartLedgerColors.TextPrimary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column {
                            Text(
                                text = "سندات القبض والصرف",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            Text(
                                text = "توثيق الدفعات النقدية وتحديث أرصدة العملاء والموردين",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isReceiptMode) SmartLedgerColors.StateSuccessSubtle else SmartLedgerColors.StateDangerSubtle)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isReceiptMode) "سند قبض (له)" else "سند صرف (عليه)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isReceiptMode) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                        )
                    }
                }

                SegmentedOptionRow(
                    options = listOf(
                        true to "سند قبض / استلام (له)",
                        false to "سند صرف / سداد (عليه)"
                    ),
                    selectedOption = isReceiptMode,
                    onOptionSelected = { isReceiptMode = it },
                    activeColor = if (isReceiptMode) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = SmartLedgerTokens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                StatusFeedbackBanner(
                    message = statusMessage,
                    isError = isStatusError,
                    onDismiss = { statusMessage = null }
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(
                            if (isReceiptMode) SmartLedgerColors.StateSuccessBorder else SmartLedgerColors.StateDangerBorder
                        )
                    ),
                    shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SmartTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = if (isReceiptMode) "المبلغ المقبوض نقداً (${AppDesignState.currencySymbol})" else "المبلغ المنصرف نقداً (${AppDesignState.currencySymbol})",
                            placeholder = "أدخل قيمة السند...",
                            isRequired = true,
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                            nextFocusRequester = focusStatement,
                            leadingIcon = Icons.Default.Payments
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("5000" to "5,000", "10000" to "10,000", "25000" to "25,000", "50000" to "50,000", "100000" to "100,000").forEach { (raw, label) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SmartLedgerColors.SurfaceCardElevated)
                                        .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(8.dp))
                                        .bounceClick { amountText = raw }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = SmartLedgerColors.AccentLight, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                    shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("الطرف الحسابي (العميل / المورد) *", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.TextSecondary)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SmartLedgerColors.SurfaceInput)
                                .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(12.dp))
                                .clickable { showAccountPickerSheet = true }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = selectedAccount?.PersonName ?: "اضغط لاختيار الحساب من الدفتر ▾",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedAccount != null) SmartLedgerColors.TextPrimary else SmartLedgerColors.TextMuted
                                )
                            }
                            if (selectedAccount != null) {
                                Text(
                                    text = "الحالي: ${NumberFormatter.formatCurrency(abs(currentNet), AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} ${if (currentNet >= 0) "عليه" else "له"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (currentNet >= 0) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (selectedAccount != null && amountVal > 0.0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SmartLedgerColors.SurfaceCardElevated)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("الرصيد المتوقع بعد ترحيل السند:", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                                Text(
                                    text = "${NumberFormatter.formatCurrency(abs(projectedNet), AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} ${when { projectedNet > 0.001 -> "عليه (لنا)"; projectedNet < -0.001 -> "له (علينا)"; else -> "متزن" }}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (projectedNet >= 0) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess
                                )
                            }
                        }

                        if (financialAccounts.isNotEmpty()) {
                            Text("الصندوق / المحفظة المالية:", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                            val accOptions = remember(financialAccounts) {
                                listOf<Pair<Long?, String>>(null to "الصندوق الافتراضي") +
                                    financialAccounts.map { it.Id as Long? to it.Name }
                            }
                            FilterChipRow(
                                items = accOptions,
                                selectedItem = selectedFinancialAccountId,
                                onItemSelected = { selectedFinancialAccountId = it }
                            )
                        }

                        if (openInvoices.isNotEmpty()) {
                            Text("تخصيص الدفعة لفاتورة مفتوحة (اختياري):", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                            val invOptions = remember(openInvoices) {
                                listOf<Pair<Long?, String>>(null to "دفعة عامة على الحساب / مقدمة") +
                                    openInvoices.map { it.Id as Long? to "${it.InvoiceNumber} (متبقي: ${NumberFormatter.formatCurrency(it.RemainingAmount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)})" }
                            }
                            FilterChipRow(
                                items = invOptions,
                                selectedItem = selectedTargetInvoiceId,
                                onItemSelected = { invId ->
                                    selectedTargetInvoiceId = invId
                                    val chosen = openInvoices.firstOrNull { it.Id == invId }
                                    if (chosen != null && amountText.isBlank()) {
                                        amountText = NumberFormatter.formatQuantity(chosen.RemainingAmount)
                                        if (statementText.isBlank()) {
                                            statementText = "سداد فاتورة #${chosen.InvoiceNumber}"
                                        }
                                    }
                                }
                            )
                        }

                        SmartTextField(
                            value = statementText,
                            onValueChange = { statementText = it },
                            label = "البيان والشرح المحاسبي",
                            placeholder = if (isReceiptMode) "مثال: دفعة نقدية من الحساب..." else "مثال: سداد دفعة نقدية للمورد...",
                            focusRequester = focusStatement,
                            imeAction = ImeAction.Next
                        )

                        SmartTextField(
                            value = refNumberText,
                            onValueChange = { refNumberText = it },
                            label = "رقم المرجع / السند الورقي (اختياري)",
                            placeholder = "يُولّد تلقائياً إن تُرك فارغاً",
                            imeAction = ImeAction.Done
                        )

                        Button(
                            onClick = { if (isFormValid) showConfirmPostDialog = true },
                            enabled = isFormValid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp)
                                .pressScaleEffect(enabled = isFormValid),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isReceiptMode) SmartLedgerColors.StateSuccess else SmartLedgerColors.AccentPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SmartLedgerColors.TextInverse, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPostingVoucher) "جارٍ ترحيل السند..." else if (isReceiptMode) "اعتماد وترحيل سند القبض (له)" else "اعتماد وترحيل سند الصرف (عليه)",
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextInverse,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "أحدث السندات المرحلة (${recentVouchers.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SmartLedgerColors.TextPrimary
                )
            }

            if (recentVouchers.isEmpty()) {
                item {
                    Text("لا توجد سندات مرحلة بعد.", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextMuted)
                }
            } else {
                items(recentVouchers, key = { it.Id }) { v ->
                    val isRec = v.Type == "Receive"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${v.PersonName ?: "حساب"} • ${if (isRec) "سند قبض" else "سند صرف"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SmartLedgerColors.TextPrimary
                                )
                                Text(
                                    text = "${v.Note ?: ""} • ${v.OccurredAt.take(16)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextMuted
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = NumberFormatter.formatCurrency(v.Amount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRec) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                                )
                                IconButton(
                                    onClick = {
                                        val shareMsg = "${if (isRec) "إيصال سند قبض" else "إيصال سند صرف"}\nالحساب: ${v.PersonName ?: ""}\nالمبلغ: ${NumberFormatter.formatCurrency(v.Amount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)}\nالبيان: ${v.Note ?: ""}\nالتاريخ: ${v.OccurredAt.take(16)}"
                                        ThermalPrinterHelper.shareTextSafely(context, "إيصال سند", shareMsg)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "مشاركة السند", tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }

        // نافذة اختيار الحساب العائمة فوق لوحة المفاتيح
        if (showAccountPickerSheet) {
            FloatingModalDialog(onDismissRequest = { showAccountPickerSheet = false }) {
                var accSearch by rememberSaveable { mutableStateOf("") }
                val filtered = remember(accounts, accSearch) {
                    if (accSearch.isBlank()) accounts
                    else accounts.filter {
                        it.PersonName.contains(accSearch, ignoreCase = true) ||
                            (it.Phone?.contains(accSearch) == true)
                    }
                }

                FormSheetContainer(
                    title = "اختر الطرف الحسابي للسند",
                    subtitle = "ابحث واختر العميل أو المورد المرتبط بالسند",
                    headerIcon = Icons.Default.PersonSearch,
                    onDismiss = { showAccountPickerSheet = false },
                    primaryButtonText = "إغلاق",
                    onPrimaryClick = { showAccountPickerSheet = false }
                ) {
                    CompactSearchBar(
                        query = accSearch,
                        onQueryChange = { accSearch = it },
                        placeholder = "ابحث باسم العميل أو المورد...",
                        resultCount = filtered.size
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.PersonId }) { acc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SmartLedgerColors.SurfaceCard)
                                    .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedAccountId = acc.PersonId
                                        showAccountPickerSheet = false
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(acc.PersonName, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                                Text(
                                    text = "${NumberFormatter.formatCurrency(abs(acc.NetBalance), AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} ${if (acc.NetBalance >= 0) "عليه" else "له"}",
                                    color = if (acc.NetBalance >= 0) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showConfirmPostDialog && selectedAccount != null) {
            val acc = selectedAccount
            ConfirmActionDialog(
                title = if (isReceiptMode) "تأكيد ترحيل سند القبض؟" else "تأكيد ترحيل سند الصرف؟",
                message = "سيتم قيد ${if (isReceiptMode) "سند قبض (له)" else "سند صرف (عليه)"} بمبلغ ${NumberFormatter.formatCurrency(amountVal, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} على حساب «${acc.PersonName}».",
                confirmText = "تأكيد وترحيل",
                isDestructive = false,
                onConfirm = {
                    showConfirmPostDialog = false
                    isPostingVoucher = true
                    scope.launch {
                        val type = if (isReceiptMode) "Receive" else "Payment"
                        val st = statementText.trim().ifBlank { if (isReceiptMode) "سند قبض نقدية" else "سند صرف نقدية" }
                        val res = viewModel.createVoucher(acc.PersonId, type, amountVal, st, refNumberText.trim().ifBlank { null }, selectedFinancialAccountId, selectedTargetInvoiceId)
                        isPostingVoucher = false
                        if (res.isSuccess) {
                            isStatusError = false
                            statusMessage = "تم حفظ وترحيل السند لحساب «${acc.PersonName}» بنجاح."
                            amountText = ""
                            statementText = ""
                            refNumberText = ""
                        } else {
                            isStatusError = true
                            statusMessage = res.exceptionOrNull()?.message ?: "تعذر حفظ السند؛ تم الاحتفاظ بالبيانات المدخلة."
                        }
                    }
                },
                onDismiss = { showConfirmPostDialog = false }
            )
        }
    }
}
