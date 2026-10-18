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
fun AccountStatementSheetContent(
    account: AccountBalanceSummary,
    bookName: String,
    privacyMode: Boolean,
    viewModel: AccountsViewModel,
    onQuickVoucher: (isReceipt: Boolean) -> Unit,
    onOperationFeedback: (message: String, isError: Boolean) -> Unit = { _, _ -> },
    onShareStatement: () -> Unit,
    onCall: () -> Unit,
    onClose: () -> Unit
) {
    val operationsFlow = remember(account.PersonId) { viewModel.getOperationsForAccount(account.PersonId) }
    val allOperations by operationsFlow.collectAsState(initial = emptyList())
    var periodFilter by rememberSaveable { mutableStateOf("ALL") }
    var typeFilter by rememberSaveable { mutableStateOf("ALL") }
    var operationIdToDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val operationToDelete = remember(allOperations, operationIdToDelete) { operationIdToDelete?.let { id -> allOperations.firstOrNull { it.Id == id } } }

    val operationsWithRunningBalance = remember(allOperations) {
        val chronological = allOperations.sortedWith(compareBy<OperationEntity> { it.OccurredAt }.thenBy { it.Id })
        var running = 0.0
        chronological.map { op ->
            running += LedgerDirectionPolicy.signedOperationDelta(op.Type, op.Amount)
            Pair(op, running)
        }.reversed()
    }

    val filteredOperations = remember(operationsWithRunningBalance, periodFilter, typeFilter) {
        val today = NumberFormatter.todayDate()
        val monthPrefix = today.take(7)
        operationsWithRunningBalance.filter { (op, _) ->
            val matchesPeriod = when (periodFilter) {
                "TODAY" -> op.OccurredAt.startsWith(today)
                "MONTH" -> op.OccurredAt.startsWith(monthPrefix)
                else -> true
            }
            val isCredit = LedgerDirectionPolicy.isCreditOperation(op.Type)
            val matchesType = when (typeFilter) {
                "DEBIT" -> !isCredit
                "CREDIT" -> isCredit
                else -> true
            }
            matchesPeriod && matchesType
        }
    }

    FormSheetContainer(
        title = account.PersonName,
        subtitle = "$bookName • الهاتف: ${account.Phone ?: "غير مسجل"}",
        headerIcon = Icons.Default.AccountBalanceWallet,
        onDismiss = onClose,
        primaryButtonText = "إرسال كشف واتساب / مشاركة",
        secondaryButtonText = "إغلاق",
        onPrimaryClick = onShareStatement
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي مدين (عليه)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateDangerText)
                    Text(
                        NumberFormatter.formatCurrency(account.TotalDebit, AppDesignState.currencySymbol, AppDesignState.decimalPlaces, privacyMode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.StateDanger
                    )
                }
                Box(modifier = Modifier.width(1.dp).heightIn(min = 32.dp).background(SmartLedgerColors.BorderSubtle))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي دائن (له)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateSuccessText)
                    Text(
                        NumberFormatter.formatCurrency(account.TotalCredit, AppDesignState.currencySymbol, AppDesignState.decimalPlaces, privacyMode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.StateSuccess
                    )
                }
                Box(modifier = Modifier.width(1.dp).heightIn(min = 32.dp).background(SmartLedgerColors.BorderSubtle))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (account.NetBalance >= 0) "الصافي لنا (عليه)" else "الصافي علينا (له)",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted
                    )
                    Text(
                        NumberFormatter.formatCurrency(abs(account.NetBalance), AppDesignState.currencySymbol, AppDesignState.decimalPlaces, privacyMode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (account.NetBalance >= 0) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onQuickVoucher(true) },
                modifier = Modifier.weight(1f).heightIn(min = 38.dp).pressScaleEffect(),
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.StateSuccess),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = SmartLedgerColors.TextInverse, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("سند قبض (له)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextInverse, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { onQuickVoucher(false) },
                modifier = Modifier.weight(1f).heightIn(min = 38.dp).pressScaleEffect(),
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.StateDanger),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = SmartLedgerColors.TextInverse, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("سند صرف (عليه)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextInverse, fontWeight = FontWeight.Bold)
            }
            if (!account.Phone.isNullOrBlank()) {
                OutlinedButton(
                    onClick = onCall,
                    modifier = Modifier.heightIn(min = 38.dp).pressScaleEffect(),
                    shape = RoundedCornerShape(10.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "اتصال", tint = SmartLedgerColors.TextSecondary, modifier = Modifier.size(15.dp))
                }
            }
        }

        FilterChipRow(
            items = listOf(
                "ALL" to "كل الفترات (${allOperations.size})",
                "TODAY" to "اليوم",
                "MONTH" to "هذا الشهر"
            ),
            selectedItem = periodFilter,
            onItemSelected = { periodFilter = it }
        )

        SegmentedOptionRow(
            options = listOf(
                "ALL" to "جميع الحركات",
                "DEBIT" to "مدين (عليه +)",
                "CREDIT" to "دائن (له -)"
            ),
            selectedOption = typeFilter,
            onOptionSelected = { typeFilter = it }
        )

        if (filteredOperations.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                Text("لا توجد حركات مطابقة للفترة أو النوع المحدد.", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredOperations, key = { it.first.Id }) { (op, runningBalance) ->
                    val isCredit = LedgerDirectionPolicy.isCreditOperation(op.Type)
                    val isAlreadyReversed = op.ReferenceNumber?.startsWith("REV-OP-") == true ||
                        allOperations.any { it.ReferenceNumber == "REV-OP-${op.Id}" }
                    val typeTitle = when (op.Type) {
                        "Receive" -> "سند قبض"
                        "Payment" -> "سند صرف"
                        "Sale" -> "فاتورة مبيعات آجل"
                        "Purchase" -> "فاتورة مشتريات آجل"
                        "SaleReturn" -> "مرتجع مبيعات"
                        "PurchaseReturn" -> "مرتجع مشتريات"
                        "DebtOnUs" -> "رصيد افتتاحي (مدين)"
                        "DebtToUs" -> "رصيد افتتاحي (دائن)"
                        else -> op.Type
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
                                text = op.Note ?: typeTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            Text(
                                text = "$typeTitle • ${op.OccurredAt.take(16)}" +
                                    if (!op.ReferenceNumber.isNullOrBlank()) " • #${op.ReferenceNumber}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${if (isCredit) "-" else "+"}${NumberFormatter.formatCurrency(op.Amount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces, privacyMode)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCredit) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                                )
                                Text(
                                    text = "الرصيد: ${NumberFormatter.formatCurrency(abs(runningBalance), AppDesignState.currencySymbol, AppDesignState.decimalPlaces, privacyMode)} ${if (runningBalance >= 0) "لنا" else "علينا"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextMuted
                                )
                            }
                            if (!isAlreadyReversed) {
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { operationIdToDelete = op.Id },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Undo, contentDescription = "إلغاء بقيد عكسي", tint = SmartLedgerColors.StateWarning, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (operationToDelete != null) {
        val targetOp = operationToDelete!!
        ConfirmActionDialog(
            title = "إلغاء الحركة بقيد عكسي محاسبي؟",
            message = "حفاظاً على سلامة السجل المحاسبي، لا يتم مسح الحركة الأصلية، بل سيتم إدراج قيد عكسي موثق بقيمة ${NumberFormatter.formatCurrency(targetOp.Amount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} لتصحيح رصيد الحساب فوراً مع توثيق سبب الإلغاء.",
            confirmText = "اعتماد القيد العكسي",
            requireReason = true,
            reasonLabel = "سبب الإلغاء المحاسبي",
            onConfirm = { reason ->
                viewModel.deleteOperation(targetOp.Id, reason) { res ->
                    operationIdToDelete = null
                    if (res.isSuccess) {
                        onOperationFeedback("تم إدراج القيد العكسي للحركة #${targetOp.Id} وتصحيح رصيد الحساب بنجاح.", false)
                    } else {
                        onOperationFeedback(res.exceptionOrNull()?.message ?: "تعذر عكس الحركة المالية.", true)
                    }
                }
            },
            onDismiss = { operationIdToDelete = null }
        )
    }
}
