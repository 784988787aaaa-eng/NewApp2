package com.smartledger.pro.ui.screens.operations

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.dao.AccountBalanceSummary
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.navigation.NavigationIntentHolder
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.viewmodels.VouchersViewModel
import com.smartledger.pro.util.AppSettingsManager
import com.smartledger.pro.util.Formatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VouchersScreen(
    onBack: (() -> Unit)? = null,
    viewModel: VouchersViewModel = viewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val recentVouchers by viewModel.recentVouchers.collectAsState()
    val settings by AppSettingsManager.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var isReceiptMode by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var selectedAccount by remember { mutableStateOf<AccountBalanceSummary?>(null) }
    var statementText by remember { mutableStateOf("") }
    var referenceNo by remember { mutableStateOf("") }
    var showOptionalRef by remember { mutableStateOf(false) }
    var showAccountPickerSheet by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val amountFocus = remember { FocusRequester() }
    val statementFocus = remember { FocusRequester() }

    LaunchedEffect(accounts, NavigationIntentHolder.voucherInitialAccountId, NavigationIntentHolder.voucherInitialIsReceipt) {
        NavigationIntentHolder.voucherInitialIsReceipt?.let {
            isReceiptMode = it
            NavigationIntentHolder.voucherInitialIsReceipt = null
        }
        val targetId = NavigationIntentHolder.voucherInitialAccountId
        if (targetId != null && accounts.isNotEmpty()) {
            selectedAccount = accounts.firstOrNull { it.PersonId == targetId }
            NavigationIntentHolder.voucherInitialAccountId = null
        }
    }

    val accountsMap = remember(accounts) { accounts.associate { it.PersonId to it.PersonName } }
    val parsedAmount = Formatters.parseDoubleSafe(amountText, 0.0)
    val isFormValid = parsedAmount > 0.0 && selectedAccount != null

    val projectedBalance = remember(selectedAccount, parsedAmount, isReceiptMode) {
        val current = selectedAccount?.NetBalance ?: 0.0
        if (isReceiptMode) current - parsedAmount else current + parsedAmount
    }

    Scaffold(
        containerColor = SmartLedgerColors.SurfaceWindow,
        topBar = {
            Column(
                modifier = Modifier
                    .background(SmartLedgerColors.SurfacePanel)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
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
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column {
                            Text(
                                text = "السندات والقيود المالية",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            Text(
                                text = "إصدار سندات القبض والصرف مع معاينة الأثر على الرصيد",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                        }
                    }

                    Badge(
                        containerColor = if (isReceiptMode) SmartLedgerColors.StateSuccessSubtle else SmartLedgerColors.StateDangerSubtle,
                        contentColor = if (isReceiptMode) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                    ) {
                        Text(
                            text = if (isReceiptMode) "سند قبض (له)" else "سند صرف (عليه)",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SmartLedgerColors.SurfaceCardElevated)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isReceiptMode) SmartLedgerColors.StateSuccess else Color.Transparent)
                            .clickable { isReceiptMode = true; successMessage = null }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = if (isReceiptMode) SmartLedgerColors.TextInverse else SmartLedgerColors.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "سند قبض / استلام (له)",
                                fontWeight = FontWeight.Bold,
                                color = if (isReceiptMode) SmartLedgerColors.TextInverse else SmartLedgerColors.TextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isReceiptMode) SmartLedgerColors.StateDanger else Color.Transparent)
                            .clickable { isReceiptMode = false; successMessage = null }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = if (!isReceiptMode) SmartLedgerColors.TextInverse else SmartLedgerColors.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "سند صرف / سداد (عليه)",
                                fontWeight = FontWeight.Bold,
                                color = if (!isReceiptMode) SmartLedgerColors.TextInverse else SmartLedgerColors.TextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            if (!successMessage.isNullOrBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SmartLedgerColors.StateSuccessSubtle)
                            .border(1.dp, SmartLedgerColors.StateSuccessBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(successMessage!!, color = SmartLedgerColors.StateSuccessText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { successMessage = null }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = SmartLedgerColors.StateSuccessText, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("الطرف الحسابي (العميل / المورد) *", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SmartLedgerColors.SurfaceInput)
                                .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable { showAccountPickerSheet = true }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedAccount?.PersonName ?: "اضغط لاختيار الحساب من الدفتر ▾",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedAccount != null) SmartLedgerColors.TextPrimary else SmartLedgerColors.TextMuted
                            )
                            if (selectedAccount != null) {
                                val bal = selectedAccount!!.NetBalance
                                val stateLabel = if (bal > 0.001) "عليه" else if (bal < -0.001) "له" else "متزن"
                                Text(
                                    text = "الرصيد: ${Formatters.formatCurrency(kotlin.math.abs(bal), settings.currencySymbol, settings.decimalPlaces)} ($stateLabel)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (bal > 0.001) SmartLedgerColors.StateDanger else if (bal < -0.001) SmartLedgerColors.StateSuccess else SmartLedgerColors.TextMuted
                                )
                            }
                        }

                        SmartNumberField(
                            value = amountText,
                            onValueChange = { amountText = it; successMessage = null },
                            label = if (isReceiptMode) "المبلغ المقبوض نقداً" else "المبلغ المنصرف نقداً",
                            placeholder = "أدخل المبلغ بالأرقام...",
                            suffixText = settings.currencySymbol,
                            isRequired = true,
                            focusRequester = amountFocus,
                            nextFocusRequester = statementFocus
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("1000", "5000", "10000", "25000", "50000").forEach { preset ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SmartLedgerColors.SurfaceCardElevated)
                                        .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(6.dp))
                                        .clickable { amountText = preset }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = Formatters.formatAmountOnly(preset.toDouble(), 0),
                                        color = SmartLedgerColors.AccentLight,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        SmartTextField(
                            value = statementText,
                            onValueChange = { statementText = it },
                            label = "البيان والشرح المحاسبي",
                            placeholder = if (isReceiptMode) "مثال: دفعة من الحساب نقداً" else "مثال: سداد دفعة للمورد نقداً",
                            focusRequester = statementFocus,
                            imeAction = ImeAction.Done
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showOptionalRef = !showOptionalRef }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("إضافة رقم سند ورقي / مرجعي (اختياري)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight)
                            Icon(if (showOptionalRef) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                        }

                        AnimatedVisibility(visible = showOptionalRef) {
                            SmartTextField(
                                value = referenceNo,
                                onValueChange = { referenceNo = it },
                                label = "رقم السند المرجعي",
                                placeholder = "مثال: RV-1042",
                                imeAction = ImeAction.Done
                            )
                        }

                        if (selectedAccount != null && parsedAmount > 0.0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SmartLedgerColors.SurfaceCardElevated)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("رصيد الحساب بعد ترحيل السند:", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                                    val projState = when {
                                        projectedBalance > 0.001 -> "لنا عليه (${Formatters.formatCurrency(projectedBalance, settings.currencySymbol, settings.decimalPlaces)})"
                                        projectedBalance < -0.001 -> "له علينا (${Formatters.formatCurrency(kotlin.math.abs(projectedBalance), settings.currencySymbol, settings.decimalPlaces)})"
                                        else -> "متزن تماماً (0)"
                                    }
                                    Text(projState, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                                }
                            }
                        }

                        Button(
                            onClick = rememberDebouncedClick {
                                if (isFormValid) showConfirmDialog = true
                            },
                            enabled = isFormValid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isReceiptMode) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SmartLedgerColors.TextInverse)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isReceiptMode) "اعتماد وترحيل سند القبض (له)" else "اعتماد وترحيل سند الصرف (عليه)",
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextInverse,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            if (recentVouchers.isNotEmpty()) {
                item {
                    Text(
                        text = "أحدث السندات المرحلة (${Formatters.formatQuantity(recentVouchers.size.toDouble())})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary
                    )
                }

                items(recentVouchers.take(20), key = { it.Id }) { v ->
                    val isRec = v.Type == "Receive"
                    val accName = accountsMap[v.PersonId] ?: "حساب #${v.PersonId}"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SmartLedgerColors.SurfaceCard)
                            .border(1.dp, SmartLedgerColors.BorderGridLine, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(accName, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "${v.Note ?: if (isRec) "سند قبض" else "سند صرف"} • ${v.OccurredAt.take(16)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                        }
                        Text(
                            text = "${if (isRec) "قبض: " else "صرف: "}${Formatters.formatCurrency(v.Amount, settings.currencySymbol, settings.decimalPlaces)}",
                            fontWeight = FontWeight.Bold,
                            color = if (isRec) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (showAccountPickerSheet) {
            var accountSearch by remember { mutableStateOf("") }
            val filteredAccs = remember(accounts, accountSearch) {
                if (accountSearch.isBlank()) accounts
                else accounts.filter {
                    it.PersonName.contains(accountSearch, ignoreCase = true) ||
                        (it.Phone?.contains(accountSearch, ignoreCase = true) == true)
                }
            }

            ModalBottomSheet(
                onDismissRequest = { showAccountPickerSheet = false },
                containerColor = SmartLedgerColors.SurfacePanel
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Text(
                        "اختر الحساب للسند",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CompactSearchBar(
                        query = accountSearch,
                        onQueryChange = { accountSearch = it },
                        placeholder = "ابحث باسم العميل أو المورد..."
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredAccs, key = { it.PersonId }) { acc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SmartLedgerColors.SurfaceCard)
                                    .clickable {
                                        selectedAccount = acc
                                        showAccountPickerSheet = false
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(acc.PersonName, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                                Text(
                                    text = Formatters.formatCurrency(acc.NetBalance, settings.currencySymbol, settings.decimalPlaces),
                                    color = if (acc.NetBalance >= 0) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showConfirmDialog && selectedAccount != null) {
            val voucherTitle = if (isReceiptMode) "تأكيد ترحيل سند قبض (له)" else "تأكيد ترحيل سند صرف (عليه)"
            ConfirmActionDialog(
                title = voucherTitle,
                message = "الحساب: ${selectedAccount!!.PersonName}\n" +
                    "المبلغ: ${Formatters.formatCurrency(parsedAmount, settings.currencySymbol, settings.decimalPlaces)}\n" +
                    "الأثر المحاسبي: ${if (isReceiptMode) "يُخصم من مديونية الحساب (يُقيد له دائن)" else "يُضاف على الحساب (يُقيد عليه مدين)"}\n" +
                    "هل تؤكد حفظ وترحيل السند؟",
                confirmText = "تأكيد وترحيل",
                isDestructive = !isReceiptMode,
                onConfirm = {
                    showConfirmDialog = false
                    scope.launch {
                        val type = if (isReceiptMode) "Receive" else "Payment"
                        val st = statementText.ifBlank { if (isReceiptMode) "سند قبض نقدية" else "سند صرف نقدية" }
                        viewModel.createVoucher(selectedAccount!!.PersonId, type, parsedAmount, st, referenceNo)
                        successMessage = "تم حفظ وترحيل السند بمبلغ ${Formatters.formatCurrency(parsedAmount, settings.currencySymbol, settings.decimalPlaces)} بنجاح."
                        amountText = ""
                        statementText = ""
                        referenceNo = ""
                    }
                },
                onDismiss = { showConfirmDialog = false }
            )
        }
    }
}
