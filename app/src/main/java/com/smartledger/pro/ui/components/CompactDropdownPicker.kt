package com.smartledger.pro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens

/**
 * Compact, searchable-by-tap option list for fields with known choices.
 * It keeps the form short and prevents long rows of chips when there are many options.
 */
@Composable
fun <T> CompactDropdownPicker(
    label: String,
    options: List<Pair<T, String>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedOption }?.second
        ?: options.firstOrNull()?.second.orEmpty()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = SmartLedgerColors.TextSecondary
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled && options.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = SolidColor(SmartLedgerColors.BorderSubtle)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = SmartLedgerColors.TextPrimary
                )
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = SmartLedgerColors.AccentLight
                        )
                    }
                    Text(
                        text = selectedLabel.ifBlank { "اختر" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = SmartLedgerColors.TextSecondary
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 200.dp, max = 340.dp)
            ) {
                options.forEach { (value, title) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onOptionSelected(value)
                            expanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
