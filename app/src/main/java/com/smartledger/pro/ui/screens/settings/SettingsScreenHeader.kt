package com.smartledger.pro.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartledger.pro.ui.components.CompactSearchBar
import com.smartledger.pro.ui.components.pressScaleEffect
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens

/**
 * Presentation-only header for settings. Search and reset actions remain owned by the screen.
 */
@Composable
internal fun SettingsScreenHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onResetDefaults: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(SmartLedgerColors.SurfacePanel)
            .padding(horizontal = SmartLedgerTokens.screenHorizontalPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "مركز التحكم والإعدادات",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SmartLedgerColors.TextPrimary
                )
                Text(
                    text = "الدفتر الذكي Pro — الإصدار 3.0.0 • حفظ دائم ومشفر",
                    style = MaterialTheme.typography.labelSmall,
                    color = SmartLedgerColors.TextMuted
                )
            }

            OutlinedButton(
                onClick = onResetDefaults,
                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(SmartLedgerColors.BorderSubtle)
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.heightIn(min = 34.dp).pressScaleEffect()
            ) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = SmartLedgerColors.TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "الافتراضيات",
                    style = MaterialTheme.typography.labelSmall,
                    color = SmartLedgerColors.TextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        CompactSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = "ابحث في الإعدادات (منشأة، عملة، مظهر، نسخ، طابعة، بصمة، محذوفات)..."
        )
    }
}
