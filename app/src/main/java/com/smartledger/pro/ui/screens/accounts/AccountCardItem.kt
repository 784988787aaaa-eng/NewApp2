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
fun AccountCardItem(
    account: AccountBalanceSummary,
    bookName: String,
    privacyMode: Boolean,
    onOpenStatement: () -> Unit,
    onQuickReceipt: () -> Unit,
    onQuickPayment: () -> Unit,
    onEdit: () -> Unit,
    onCall: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isDebit = account.NetBalance > 0.001
    val isCredit = account.NetBalance < -0.001
    val overCreditLimit = AppDesignState.warnOnCreditLimit && account.CreditLimit > 0.0 && account.NetBalance > account.CreditLimit

    val balanceColor = when {
        isDebit -> SmartLedgerColors.StateDanger
        isCredit -> SmartLedgerColors.StateSuccess
        else -> SmartLedgerColors.TextMuted
    }
    val balanceLabel = when {
        isDebit -> "عليه / مستحق لنا (مدين)"
        isCredit -> "له / مستحق علينا (دائن)"
        else -> "رصيد متزن"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onOpenStatement() },
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (overCreditLimit) SmartLedgerColors.StateWarningBorder else SmartLedgerColors.BorderSubtle
            )
        ),
        shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
    ) {
        Column(modifier = Modifier.padding(SmartLedgerTokens.cardInternalPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SmartLedgerColors.AccentSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = account.PersonName.trim().take(1).ifBlank { "ح" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.AccentLight
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = account.PersonName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary,
                            maxLines = 1
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SmartLedgerColors.SurfaceCardElevated)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = bookName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.AccentLight
                                )
                            }
                            if (AppDesignState.showPhoneInAccounts && !account.Phone.isNullOrBlank()) {
                                Text(
                                    text = account.Phone,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextMuted
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = NumberFormatter.formatCurrency(
                                abs(account.NetBalance),
                                AppDesignState.currencySymbol,
                                AppDesignState.decimalPlaces,
                                privacyMode
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = balanceColor
                        )
                        Text(
                            text = balanceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = balanceColor
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "خيارات الحساب",
                                tint = SmartLedgerColors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(SmartLedgerColors.SurfacePanel)
                        ) {
                            DropdownMenuItem(
                                text = { Text("كشف الحساب التفصيلي", color = SmartLedgerColors.TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.ReceiptLong, null, tint = SmartLedgerColors.AccentLight) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenStatement()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("تعديل بيانات الحساب", color = SmartLedgerColors.TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Edit, null, tint = SmartLedgerColors.TextSecondary) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("مشاركة الرصيد عبر واتساب", color = SmartLedgerColors.TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Share, null, tint = SmartLedgerColors.StateSuccess) },
                                onClick = {
                                    menuExpanded = false
                                    onShare()
                                }
                            )
                            if (!account.Phone.isNullOrBlank()) {
                                DropdownMenuItem(
                                    text = { Text("اتصال هاتفي", color = SmartLedgerColors.TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Phone, null, tint = SmartLedgerColors.AccentLight) },
                                    onClick = {
                                        menuExpanded = false
                                        onCall()
                                    }
                                )
                            }
                            Divider(color = SmartLedgerColors.BorderSubtle)
                            DropdownMenuItem(
                                text = { Text("أرشفة الحساب", color = SmartLedgerColors.StateDanger) },
                                leadingIcon = { Icon(Icons.Default.Archive, null, tint = SmartLedgerColors.StateDanger) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            if (overCreditLimit) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SmartLedgerColors.StateWarningSubtle)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = SmartLedgerColors.StateWarning, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تجاوز سقف الائتمان المسموح (${NumberFormatter.formatCurrency(account.CreditLimit, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.StateWarningText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // شريط الإجراءات السريعة المباشرة على البطاقة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onQuickReceipt,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp)
                        .pressScaleEffect(),
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.StateSuccessBorder)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = SmartLedgerColors.StateSuccess, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("قبض (له)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateSuccess, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onQuickPayment,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp)
                        .pressScaleEffect(),
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.StateDangerBorder)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = SmartLedgerColors.StateDanger, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("صرف (عليه)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateDanger, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onOpenStatement,
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .pressScaleEffect(),
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("الكشف", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextPrimary)
                }

                if (AppDesignState.showPhoneInAccounts && !account.Phone.isNullOrBlank()) {
                    IconButton(
                        onClick = onCall,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SmartLedgerColors.SurfaceCardElevated)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "اتصال بالحساب", tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}
