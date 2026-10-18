package com.smartledger.pro.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens

@Composable
fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    statusSummary: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
        shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
    ) {
        Column(
            modifier = Modifier.padding(SmartLedgerTokens.cardInternalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SmartLedgerColors.AccentSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary
                    )
                    Text(
                        text = statusSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.AccentLight
                    )
                }
            }
            Divider(color = SmartLedgerColors.BorderSubtle)
            content()
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = SmartLedgerColors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.TextMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SmartLedgerColors.TextInverse,
                checkedTrackColor = SmartLedgerColors.AccentPrimary,
                uncheckedThumbColor = SmartLedgerColors.TextMuted,
                uncheckedTrackColor = SmartLedgerColors.SurfaceCardElevated
            )
        )
    }
}
