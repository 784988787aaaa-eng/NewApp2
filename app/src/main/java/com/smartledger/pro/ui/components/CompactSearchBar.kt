package com.smartledger.pro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.util.NumberFormatter

/**
 * شريط بحث مدمج وذكي (CompactSearchBar)
 * يجمع بين البحث الفوري، عداد النتائج، زر المسح السريع، وزر ماسح الباركود المدمج.
 */
@Composable
fun CompactSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    resultCount: Int? = null,
    onBarcodeClick: (() -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(SmartLedgerTokens.inputCorner))
            .background(SmartLedgerColors.SurfaceInput)
            .border(
                width = 1.dp,
                color = if (query.isNotEmpty()) SmartLedgerColors.AccentPrimary else SmartLedgerColors.BorderSubtle,
                shape = RoundedCornerShape(SmartLedgerTokens.inputCorner)
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (query.isNotEmpty()) SmartLedgerColors.AccentSubtle
                    else SmartLedgerColors.SurfaceCardElevated.copy(alpha = 0.6f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "بحث",
                tint = if (query.isNotEmpty()) SmartLedgerColors.AccentLight else SmartLedgerColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SmartLedgerColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = { onQueryChange(NumberFormatter.toWesternDigits(it)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = SmartLedgerColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                ),
                cursorBrush = SolidColor(SmartLedgerColors.AccentLight),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (query.isNotEmpty() && resultCount != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(SmartLedgerTokens.chipCorner))
                    .background(SmartLedgerColors.AccentSubtle)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$resultCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = SmartLedgerColors.AccentLight,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        if (query.isNotEmpty()) {
            IconButton(
                onClick = {
                    onQueryChange("")
                    focusManager.clearFocus()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = "مسح البحث",
                    tint = SmartLedgerColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (onBarcodeClick != null) {
            Spacer(modifier = Modifier.width(2.dp))
            IconButton(
                onClick = onBarcodeClick,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SmartLedgerColors.AccentSubtle)
                    .border(0.5.dp, SmartLedgerColors.AccentPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "مسح باركود",
                    tint = SmartLedgerColors.AccentLight,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        if (trailingAction != null) {
            Spacer(modifier = Modifier.width(4.dp))
            trailingAction()
        }
    }
}

/**
 * أزرار الاختيار المجمّعة الذكية (Segmented Control) مع تدرج لوني ناعم واستجابة لمسية خفيفة
 */
@Composable
fun <T> SegmentedOptionRow(
    options: List<Pair<T, String>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = SmartLedgerColors.AccentPrimary
) {
    val view = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SmartLedgerTokens.buttonCorner))
            .background(SmartLedgerColors.SurfaceCardElevated)
            .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(SmartLedgerTokens.buttonCorner))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selectedOption
            val animatedBg by animateColorAsState(
                targetValue = if (isSelected) activeColor else Color.Transparent,
                label = "segBg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(SmartLedgerTokens.chipCorner))
                    .background(animatedBg)
                    .clickable {
                        if (!isSelected) {
                            AppHaptics.perform(view, AppHapticType.TICK)
                        }
                        onOptionSelected(value)
                    }
                    .padding(vertical = 7.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) SmartLedgerColors.TextInverse else SmartLedgerColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * شرائح التصفية المتراكمة والواضحة (Filter Chips Row)
 */
@Composable
fun <T> FilterChipRow(
    items: List<Pair<T, String>>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    counts: Map<T, Int>? = null
) {
    val view = LocalView.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(items) { (key, label) ->
            val isSelected = key == selectedItem
            val count = counts?.get(key)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(SmartLedgerTokens.chipCorner))
                    .background(
                        if (isSelected) {
                            Brush.horizontalGradient(
                                colors = listOf(
                                    SmartLedgerColors.AccentSubtle,
                                    SmartLedgerColors.AccentPrimary.copy(alpha = 0.25f)
                                )
                            )
                        } else {
                            SolidColor(SmartLedgerColors.SurfaceCardElevated)
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) SmartLedgerColors.AccentPrimary else SmartLedgerColors.BorderSubtle,
                        shape = RoundedCornerShape(SmartLedgerTokens.chipCorner)
                    )
                    .clickable {
                        if (!isSelected) {
                            AppHaptics.perform(view, AppHapticType.TICK)
                        }
                        onItemSelected(key)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(SmartLedgerColors.AccentLight)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) SmartLedgerColors.AccentLight else SmartLedgerColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (count != null) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "($count)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) SmartLedgerColors.AccentLight else SmartLedgerColors.TextMuted
                        )
                    }
                }
            }
        }
    }
}
