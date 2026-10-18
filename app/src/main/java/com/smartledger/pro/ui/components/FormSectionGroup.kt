package com.smartledger.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens

/**
 * بطاقة تجميع حقول رشيقة داخل النوافذ العائمة (FormSectionGroup)
 */
@Composable
fun FormSectionGroup(
    title: String,
    badgeText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SmartLedgerTokens.cardCorner))
            .background(SmartLedgerColors.SurfaceCard.copy(alpha = 0.82f))
            .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(SmartLedgerTokens.cardCorner))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(SmartLedgerColors.AccentLight)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = SmartLedgerColors.AccentLight
                )
            }
            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(SmartLedgerTokens.chipCorner))
                        .background(SmartLedgerColors.AccentSubtle)
                        .border(0.5.dp, SmartLedgerColors.AccentPrimary.copy(alpha = 0.4f), RoundedCornerShape(SmartLedgerTokens.chipCorner))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.AccentLight
                    )
                }
            }
        }
        content()
    }
}
