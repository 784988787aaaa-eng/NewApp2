package com.smartledger.pro.ui.screens.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartledger.pro.data.local.dao.AccountBalanceSummary
import com.smartledger.pro.ui.components.bounceClick
import com.smartledger.pro.ui.components.pressScaleEffect
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.util.NumberFormatter
import kotlin.math.abs

@Composable
fun AccountCardItem(
    account: AccountBalanceSummary,
    bookName: String,
    privacyMode: Boolean,
    showInlineActions: Boolean = true,
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
        isDebit -> "عليه / لنا (مدين)"
        isCredit -> "له / علينا (دائن)"
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
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        SmartLedgerColors.AccentPrimary.copy(alpha = 0.25f),
                                        SmartLedgerColors.AccentSubtle
                                    )
                                )
                            )
                            .border(1.dp, SmartLedgerColors.AccentLight.copy(alpha = 0.4f), RoundedCornerShape(13.dp)),
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
                                    color = SmartLedgerColors.AccentLight,
                                    fontWeight = FontWeight.SemiBold
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
                        if (account.TotalDebit > 0.0 || account.TotalCredit > 0.0) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "إجمالي عليه: ${if (privacyMode) "•••" else NumberFormatter.formatAmount(account.TotalDebit, AppDesignState.decimalPlaces)} • إجمالي له: ${if (privacyMode) "•••" else NumberFormatter.formatAmount(account.TotalCredit, AppDesignState.decimalPlaces)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
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
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        isDebit -> SmartLedgerColors.StateDangerSubtle
                                        isCredit -> SmartLedgerColors.StateSuccessSubtle
                                        else -> SmartLedgerColors.SurfaceCardElevated
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = balanceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = balanceColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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
                                text = { Text("سند قبض سريع (له)", color = SmartLedgerColors.StateSuccess) },
                                leadingIcon = { Icon(Icons.Default.SouthWest, null, tint = SmartLedgerColors.StateSuccess) },
                                onClick = {
                                    menuExpanded = false
                                    onQuickReceipt()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("سند صرف سريع (عليه)", color = SmartLedgerColors.StateDanger) },
                                leadingIcon = { Icon(Icons.Default.NorthEast, null, tint = SmartLedgerColors.StateDanger) },
                                onClick = {
                                    menuExpanded = false
                                    onQuickPayment()
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

            // شريط الإجراءات الذكية المدمجة على البطاقة (يظهر أو يختفي حسب إعدادات المستخدم)
            if (showInlineActions) {
                Spacer(modifier = Modifier.height(9.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onQuickReceipt,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 33.dp)
                            .pressScaleEffect(),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.StateSuccessBorder)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.SouthWest, contentDescription = null, tint = SmartLedgerColors.StateSuccess, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("قبض (له)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateSuccess, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onQuickPayment,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 33.dp)
                            .pressScaleEffect(),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.StateDangerBorder)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.NorthEast, contentDescription = null, tint = SmartLedgerColors.StateDanger, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("صرف (عليه)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateDanger, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onOpenStatement,
                        modifier = Modifier
                            .heightIn(min = 33.dp)
                            .pressScaleEffect(),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("الكشف", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    }

                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(33.dp)
                            .clip(RoundedCornerShape(SmartLedgerTokens.buttonCorner))
                            .background(SmartLedgerColors.StateSuccessSubtle)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة واتساب", tint = SmartLedgerColors.StateSuccess, modifier = Modifier.size(14.dp))
                    }

                    if (AppDesignState.showPhoneInAccounts && !account.Phone.isNullOrBlank()) {
                        IconButton(
                            onClick = onCall,
                            modifier = Modifier
                                .size(33.dp)
                                .clip(RoundedCornerShape(SmartLedgerTokens.buttonCorner))
                                .background(SmartLedgerColors.SurfaceCardElevated)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "اتصال بالحساب", tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}
