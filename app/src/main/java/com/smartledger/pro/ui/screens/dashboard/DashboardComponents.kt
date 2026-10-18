package com.smartledger.pro.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartledger.pro.data.local.dao.FinancialAccountBalanceSummary
import com.smartledger.pro.data.local.dao.RecentOperationWithPerson
import com.smartledger.pro.domain.usecase.LedgerDirectionPolicy
import com.smartledger.pro.ui.components.bounceClick
import com.smartledger.pro.ui.components.pressScaleEffect
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.util.NumberFormatter
import kotlin.math.abs

@Composable
fun TodayMiniMetric(
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
        modifier = modifier.bounceClick { onClick() },
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

/**
 * بطاقة منظومة الصناديق الرئيسية والفرعية والحركات النقدية (DashboardTreasuriesCard)
 * تعرض الصناديق الرئيسية وتحتها الصناديق الفرعية مع الوارد والصادر والصافي لكل صندوق.
 */
@Composable
fun DashboardTreasuriesCard(
    financialAccounts: List<FinancialAccountBalanceSummary>,
    showSubTreasuriesTree: Boolean,
    privacyMode: Boolean,
    onManageClick: () -> Unit
) {
    val totalBalance = financialAccounts.sumOf { it.NetBalance }
    val totalIn = financialAccounts.sumOf { it.TotalInflows }
    val totalOut = financialAccounts.sumOf { it.TotalOutflows }

    val mainAccounts = financialAccounts.filter { it.ParentGroupId == null || it.ParentGroupId <= 0L }
    val subAccountsMap = financialAccounts
        .filter { it.ParentGroupId != null && it.ParentGroupId > 0L }
        .groupBy { it.ParentGroupId!! }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
        shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
    ) {
        Column(
            modifier = Modifier.padding(SmartLedgerTokens.cardInternalPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SmartLedgerColors.AccentSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = SmartLedgerColors.AccentLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "الصناديق الرئيسية والفرعية (${financialAccounts.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        Text(
                            text = "وارد: ${NumberFormatter.formatAmount(totalIn, AppDesignState.decimalPlaces)} • صادر: ${NumberFormatter.formatAmount(totalOut, AppDesignState.decimalPlaces)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = NumberFormatter.formatCurrency(
                            totalBalance,
                            AppDesignState.currencySymbol,
                            AppDesignState.decimalPlaces,
                            privacyMode
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (totalBalance >= 0.0) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                    )
                    Text(
                        text = "إدارة وتحويل ◂",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.AccentLight,
                        modifier = Modifier.clickable { onManageClick() }
                    )
                }
            }

            if (financialAccounts.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick { onManageClick() },
                    shape = RoundedCornerShape(10.dp),
                    color = SmartLedgerColors.SurfaceCardElevated,
                    border = BorderStroke(1.dp, SmartLedgerColors.BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "لم يتم تعريف صناديق نقدية أو فرعية بعد. اضغط لتأسيس الصناديق والمحافظ.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                val rootsToShow = if (mainAccounts.isNotEmpty()) mainAccounts else financialAccounts
                rootsToShow.take(4).forEach { mainAcc ->
                    val children = subAccountsMap[mainAcc.Id].orEmpty()
                    val typeLabel = when (mainAcc.AccountType) {
                        "E_WALLET" -> "محفظة"
                        "BANK_ACCOUNT" -> "بنك"
                        "POS_TERMINAL" -> "شبكة"
                        else -> if (children.isNotEmpty()) "صندوق رئيسي" else "صندوق"
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(SmartLedgerColors.SurfaceCardElevated.copy(alpha = 0.7f))
                            .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(11.dp))
                            .clickable { onManageClick() }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SmartLedgerColors.AccentSubtle)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SmartLedgerColors.AccentLight,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = mainAcc.Name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SmartLedgerColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = NumberFormatter.formatCurrency(
                                    mainAcc.NetBalance,
                                    mainAcc.CurrencySymbol,
                                    AppDesignState.decimalPlaces,
                                    privacyMode
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (mainAcc.NetBalance >= 0.0) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "داخل: +${if (privacyMode) "•••" else NumberFormatter.formatAmount(mainAcc.TotalInflows, AppDesignState.decimalPlaces)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.StateSuccessText
                            )
                            Text(
                                text = "خارج: -${if (privacyMode) "•••" else NumberFormatter.formatAmount(mainAcc.TotalOutflows, AppDesignState.decimalPlaces)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.StateDangerText
                            )
                            if (children.isNotEmpty()) {
                                Text(
                                    text = "${children.size} صناديق فرعية",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.AccentLight,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // عرض الصناديق الفرعية التابعة لهذا الصندوق الرئيسي
                        if (showSubTreasuriesTree && children.isNotEmpty()) {
                            Divider(color = SmartLedgerColors.BorderSubtle.copy(alpha = 0.6f))
                            children.forEach { sub ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp, top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.SubdirectoryArrowLeft,
                                            contentDescription = null,
                                            tint = SmartLedgerColors.AccentLight,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = sub.Name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SmartLedgerColors.TextSecondary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = NumberFormatter.formatCurrency(
                                            sub.NetBalance,
                                            sub.CurrencySymbol,
                                            AppDesignState.decimalPlaces,
                                            privacyMode
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
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
}

/**
 * بطاقة رادار المخزن وحركته التفصيلية (DashboardWarehouseRadarCard)
 */
@Composable
fun DashboardWarehouseRadarCard(
    totalProductsCount: Int,
    lowStockCount: Int,
    outOfStockCount: Int,
    inventoryValuation: Double,
    privacyMode: Boolean,
    onOpenInventory: () -> Unit,
    onQuickAddProduct: () -> Unit,
    onQuickPurchase: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (lowStockCount > 0 || outOfStockCount > 0) SmartLedgerColors.StateWarningBorder else SmartLedgerColors.BorderSubtle
            )
        ),
        shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
    ) {
        Column(
            modifier = Modifier.padding(SmartLedgerTokens.cardInternalPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (lowStockCount > 0) SmartLedgerColors.StateWarningSubtle else SmartLedgerColors.AccentSubtle
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = if (lowStockCount > 0) SmartLedgerColors.StateWarning else SmartLedgerColors.AccentLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "رادار المخزن وحركة البضاعة",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        Text(
                            text = "$totalProductsCount صنف نشط • تقييم التكلفة",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = NumberFormatter.formatCurrency(
                            inventoryValuation,
                            AppDesignState.currencySymbol,
                            AppDesignState.decimalPlaces,
                            privacyMode
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.AccentLight
                    )
                    Text(
                        text = "قيمة المخزون",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // متوفر
                val healthyCount = (totalProductsCount - lowStockCount).coerceAtLeast(0)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(SmartLedgerColors.SurfaceCardElevated)
                        .clickable { onOpenInventory() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("متوفر آمن", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                        Text("$healthyCount", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateSuccess)
                    }
                }
                // قارب النفاد
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (lowStockCount > 0) SmartLedgerColors.StateWarningSubtle else SmartLedgerColors.SurfaceCardElevated)
                        .clickable { onOpenInventory() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("قارب النفاد", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                        Text("$lowStockCount", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateWarning)
                    }
                }
                // نافد
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (outOfStockCount > 0) SmartLedgerColors.StateDangerSubtle else SmartLedgerColors.SurfaceCardElevated)
                        .clickable { onOpenInventory() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("نافد (0)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                        Text("$outOfStockCount", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateDanger)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenInventory,
                    modifier = Modifier.weight(1f).heightIn(min = 34.dp).pressScaleEffect(),
                    shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Inventory, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("المخزن والجرد", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextPrimary, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onQuickAddProduct,
                    modifier = Modifier.weight(1f).heightIn(min = 34.dp).pressScaleEffect(),
                    shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary.copy(alpha = 0.6f))),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("صنف جديد", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onQuickPurchase,
                    modifier = Modifier.weight(1f).heightIn(min = 34.dp).pressScaleEffect(),
                    shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.StateSuccessBorder)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.LocalShipping, contentDescription = null, tint = SmartLedgerColors.StateSuccess, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("توريد شراء", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateSuccess, fontWeight = FontWeight.Bold)
                }
            }
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
        shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = if (isCredit) {
                                    listOf(SmartLedgerColors.StateSuccessSubtle, SmartLedgerColors.StateSuccess.copy(alpha = 0.15f))
                                } else {
                                    listOf(SmartLedgerColors.StateDangerSubtle, SmartLedgerColors.StateDanger.copy(alpha = 0.15f))
                                }
                            )
                        )
                        .border(
                            0.5.dp,
                            if (isCredit) SmartLedgerColors.StateSuccessBorder else SmartLedgerColors.StateDangerBorder,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCredit) Icons.Default.SouthWest else Icons.Default.NorthEast,
                        contentDescription = null,
                        tint = if (isCredit) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!op.PersonName.isNullOrBlank()) "${op.PersonName} — ${op.Note ?: typeLabel}" else (op.Note ?: typeLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$typeLabel • ${op.OccurredAt.take(16)}" +
                            if (!op.ReferenceNumber.isNullOrBlank()) " • #${op.ReferenceNumber}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted,
                        maxLines = 1
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isCredit) SmartLedgerColors.StateSuccessSubtle else SmartLedgerColors.StateDangerSubtle)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (isCredit) "دائن / وارد" else "مدين / صادر",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCredit) SmartLedgerColors.StateSuccessText else SmartLedgerColors.StateDangerText,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
