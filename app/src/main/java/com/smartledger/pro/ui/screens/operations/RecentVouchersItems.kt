package com.smartledger.pro.ui.screens.operations

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartledger.pro.data.local.dao.RecentOperationWithPerson
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.util.NumberFormatter
import com.smartledger.pro.util.ThermalPrinterHelper

/**
 * Adds the latest posted receipts and payments to the parent LazyColumn.
 * This UI-only extraction leaves voucher creation and posting in the screen/ViewModel.
 */
internal fun LazyListScope.recentVouchersItems(
    recentVouchers: List<RecentOperationWithPerson>,
    context: Context
) {
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
}
