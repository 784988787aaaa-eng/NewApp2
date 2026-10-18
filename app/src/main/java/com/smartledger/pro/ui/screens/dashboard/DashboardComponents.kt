package com.smartledger.pro.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartledger.pro.data.local.dao.RecentOperationWithPerson
import com.smartledger.pro.domain.usecase.LedgerDirectionPolicy
import com.smartledger.pro.ui.components.bounceClick
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.util.NumberFormatter

@Composable
private fun TodayMiniMetric(
    label: String,
    amount: Double,
    color: Color,
    privacyMode: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (privacyMode) "••••" else NumberFormatter.formatAmount(amount, AppDesignState.decimalPlaces),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun QuickActionTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .bounceClick { onClick() },
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
        shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SmartLedgerColors.TextPrimary,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.TextMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
fun RealOperationRow(
    op: RecentOperationWithPerson,
    privacyMode: Boolean,
    onClick: () -> Unit
) {
    val isCredit = LedgerDirectionPolicy.isCreditOperation(op.Type)
    val typeLabel = when (op.Type) {
        "Receive" -> "سند قبض نقدية"
        "Payment" -> "سند صرف نقدية"
        "Sale" -> "فاتورة مبيعات آجل"
        "Purchase" -> "فاتورة مشتريات آجل"
        "SaleReturn" -> "مرتجع مبيعات"
        "PurchaseReturn" -> "مرتجع مشتريات"
        "DebtOnUs" -> "رصيد افتتاحي (عليه)"
        "DebtToUs" -> "رصيد افتتاحي (له)"
        else -> op.Type
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() },
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCredit) SmartLedgerColors.StateSuccessSubtle else SmartLedgerColors.StateDangerSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isCredit) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (!op.PersonName.isNullOrBlank()) "${op.PersonName} — ${op.Note ?: typeLabel}" else (op.Note ?: typeLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "$typeLabel • ${op.OccurredAt.take(16)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = NumberFormatter.formatCurrency(op.Amount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces, privacyMode),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isCredit) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                )
                Text(
                    text = if (isCredit) "دائن (له)" else "مدين (عليه)",
                    style = MaterialTheme.typography.labelSmall,
                    color = SmartLedgerColors.TextMuted
                )
            }
        }
    }
}
