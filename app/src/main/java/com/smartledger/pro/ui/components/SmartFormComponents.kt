package com.smartledger.pro.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmartTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isRequired: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    errorMessage: String? = null,
    helperText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    onImeDone: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = modifier) {
        val displayLabel = if (isRequired && !label.endsWith("*")) "$label *" else label
        OutlinedTextField(
            value = value,
            onValueChange = { raw ->
                val cleaned = if (keyboardType in listOf(KeyboardType.Number, KeyboardType.Decimal, KeyboardType.Phone)) {
                    Formatters.normalizeDigits(raw)
                } else raw
                onValueChange(cleaned)
            },
            label = { Text(displayLabel) },
            placeholder = if (!placeholder.isNullOrBlank()) { { Text(placeholder, color = SmartLedgerColors.TextMuted, fontSize = 12.sp) } } else null,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            isError = !errorMessage.isNullOrBlank(),
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, capitalization = KeyboardCapitalization.Sentences),
            keyboardActions = KeyboardActions(
                onNext = {
                    if (nextFocusRequester != null) {
                        try { nextFocusRequester.requestFocus() } catch (_: Exception) { focusManager.moveFocus(FocusDirection.Down) }
                    } else focusManager.moveFocus(FocusDirection.Down)
                },
                onDone = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onImeDone?.invoke()
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SmartLedgerColors.SurfaceInputFocus,
                unfocusedContainerColor = SmartLedgerColors.SurfaceInput,
                disabledContainerColor = SmartLedgerColors.SurfaceCardElevated.copy(alpha = 0.5f),
                focusedBorderColor = SmartLedgerColors.BorderFocusRing,
                unfocusedBorderColor = SmartLedgerColors.BorderSubtle,
                errorBorderColor = SmartLedgerColors.StateDanger,
                focusedTextColor = SmartLedgerColors.TextPrimary,
                unfocusedTextColor = SmartLedgerColors.TextPrimary,
                focusedLabelColor = SmartLedgerColors.AccentLight,
                unfocusedLabelColor = SmartLedgerColors.TextMuted
            ),
            shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton),
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusEvent { state ->
                    if (state.isFocused) {
                        coroutineScope.launch {
                            delay(120)
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                }
        )

        if (!errorMessage.isNullOrBlank()) {
            Text(errorMessage, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateDanger, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
        } else if (!helperText.isNullOrBlank()) {
            Text(helperText, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
        }
    }
}

@Composable
fun SmartNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    suffixText: String? = null,
    isRequired: Boolean = false,
    allowDecimals: Boolean = true,
    allowNegative: Boolean = false,
    enabled: Boolean = true,
    errorMessage: String? = null,
    helperText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    onImeDone: (() -> Unit)? = null
) {
    SmartTextField(
        value = value,
        onValueChange = { raw ->
            val sanitized = if (allowDecimals) Formatters.sanitizeDecimalInput(raw, allowNegative) else Formatters.sanitizeIntegerInput(raw)
            onValueChange(sanitized)
        },
        label = label,
        modifier = modifier,
        placeholder = placeholder,
        isRequired = isRequired,
        enabled = enabled,
        singleLine = true,
        errorMessage = errorMessage,
        helperText = helperText,
        keyboardType = if (allowDecimals) KeyboardType.Decimal else KeyboardType.Number,
        imeAction = imeAction,
        focusRequester = focusRequester,
        nextFocusRequester = nextFocusRequester,
        onImeDone = onImeDone,
        trailingIcon = if (!suffixText.isNullOrBlank()) {
            { Text(suffixText, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 8.dp)) }
        } else null
    )
}

@Composable
fun CompactSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onScanBarcodeClick: (() -> Unit)? = null,
    trailingActionContent: (@Composable RowScope.() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SmartLedgerTokens.SearchBarHeight)
            .clip(RoundedCornerShape(SmartLedgerTokens.RadiusButton))
            .background(SmartLedgerColors.SurfaceInput)
            .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(SmartLedgerTokens.RadiusButton))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = "بحث", tint = SmartLedgerColors.TextMuted, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextMuted, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = { onQueryChange(Formatters.normalizeDigits(it)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = SmartLedgerColors.TextPrimary, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(SmartLedgerColors.AccentLight),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide(); focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Close, contentDescription = "مسح البحث", tint = SmartLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
            }
        }
        if (onScanBarcodeClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onScanBarcodeClick, modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(SmartLedgerColors.AccentSubtle)) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "مسح الباركود", tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
            }
        }
        if (trailingActionContent != null) {
            Spacer(modifier = Modifier.width(4.dp))
            trailingActionContent()
        }
    }
}

@Composable
fun <T> SegmentedButtonBar(
    options: List<Pair<T, String>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = SmartLedgerColors.AccentPrimary
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SmartLedgerTokens.RadiusButton))
            .background(SmartLedgerColors.SurfaceCardElevated)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (id, label) ->
            val selected = id == selectedOption
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(SmartLedgerTokens.RadiusSmall))
                    .background(if (selected) activeColor else Color.Transparent)
                    .clickable { onOptionSelected(id) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) SmartLedgerColors.TextInverse else SmartLedgerColors.TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

data class FilterTabItem<T>(val id: T, val label: String, val badgeCount: Int? = null)

@Composable
fun <T> ScrollableFilterTabs(
    tabs: List<FilterTabItem<T>>,
    selectedTabId: T,
    onTabSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(tabs) { tab ->
            val isSelected = tab.id == selectedTabId
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(SmartLedgerTokens.RadiusButton))
                    .background(if (isSelected) SmartLedgerColors.AccentSubtle else SmartLedgerColors.SurfaceCardElevated)
                    .border(if (isSelected) 1.dp else 0.dp, if (isSelected) SmartLedgerColors.AccentPrimary else Color.Transparent, RoundedCornerShape(SmartLedgerTokens.RadiusButton))
                    .clickable { onTabSelected(tab.id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tab.label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) SmartLedgerColors.AccentLight else SmartLedgerColors.TextSecondary, maxLines = 1)
                if (tab.badgeCount != null && tab.badgeCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier.clip(CircleShape).background(if (isSelected) SmartLedgerColors.AccentPrimary else SmartLedgerColors.SurfacePanel).padding(horizontal = 5.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(Formatters.formatQuantity(tab.badgeCount.toDouble()), style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSelected) SmartLedgerColors.TextInverse else SmartLedgerColors.TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun SmartEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SmartLedgerTokens.RadiusCard))
            .background(SmartLedgerColors.SurfaceCard)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(SmartLedgerColors.SurfaceCardElevated), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = SmartLedgerColors.TextMuted, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextMuted)
        if (!actionText.isNullOrBlank() && onActionClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = rememberDebouncedClick(onClick = onActionClick),
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(actionText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String = "تأكيد التنفيذ",
    dismissText: String = "إلغاء",
    isDestructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SmartLedgerColors.SurfaceDialog,
        shape = RoundedCornerShape(SmartLedgerTokens.RadiusSheet),
        title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextSecondary) },
        confirmButton = {
            Button(
                onClick = rememberDebouncedClick { onConfirm() },
                colors = ButtonDefaults.buttonColors(containerColor = if (isDestructive) SmartLedgerColors.StateDanger else SmartLedgerColors.AccentPrimary),
                shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
            ) { Text(confirmText, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextInverse) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText, color = SmartLedgerColors.TextMuted, fontWeight = FontWeight.SemiBold) }
        }
    )
}
