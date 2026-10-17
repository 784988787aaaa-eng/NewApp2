package com.smartledger.pro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.util.NumberFormatter

/**
 * حقل إدخال ذكي موحد (SmartTextField)
 * - يدعم التسمية الدائمة الواضحة وعلامة الإلزام (*).
 * - يحول الأرقام الشرقية (٠-٩) تلقائياً إلى أرقام غربية (0-9).
 * - يدعم التنقل الذكي عبر لوحة المفاتيح (ImeAction.Next -> الحقل التالي، ImeAction.Done -> إغلاق آمن للوحة المفاتيح).
 * - يعرض رسائل الخطأ السياقية والنصوص المساعدة بجوار الحقل مباشرة.
 */
@Composable
fun SmartTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    isRequired: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    onDoneAction: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isError = !errorText.isNullOrBlank()

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { raw ->
                val normalized = when (keyboardType) {
                    KeyboardType.Decimal -> NumberFormatter.normalizeNumericInput(raw, allowDecimal = true)
                    KeyboardType.Number, KeyboardType.NumberPassword -> NumberFormatter.normalizeNumericInput(raw, allowDecimal = false)
                    KeyboardType.Phone -> NumberFormatter.toWesternDigits(raw).filter { it.isDigit() || it == '+' || it == '-' || it == ' ' }
                    else -> NumberFormatter.toWesternDigits(raw)
                }
                onValueChange(normalized)
            },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = label, style = MaterialTheme.typography.labelMedium)
                    if (isRequired) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "*", color = SmartLedgerColors.StateDanger, fontWeight = FontWeight.Bold)
                    }
                }
            },
            placeholder = if (!placeholder.isNullOrBlank()) {
                { Text(text = placeholder, style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextMuted) }
            } else null,
            leadingIcon = if (leadingIcon != null) {
                { Icon(leadingIcon, contentDescription = null, tint = SmartLedgerColors.TextMuted, modifier = Modifier.size(18.dp)) }
            } else null,
            trailingIcon = trailingContent,
            enabled = enabled,
            readOnly = readOnly,
            isError = isError,
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    if (nextFocusRequester != null) {
                        try {
                            nextFocusRequester.requestFocus()
                        } catch (_: Exception) {
                            focusManager.moveFocus(FocusDirection.Down)
                        }
                    } else {
                        if (!focusManager.moveFocus(FocusDirection.Down)) {
                            focusManager.moveFocus(FocusDirection.Next)
                        }
                    }
                },
                onDone = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onDoneAction?.invoke()
                },
                onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onDoneAction?.invoke()
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SmartLedgerColors.SurfaceInputFocus,
                unfocusedContainerColor = SmartLedgerColors.SurfaceInput,
                disabledContainerColor = SmartLedgerColors.SurfaceCardElevated.copy(alpha = 0.4f),
                focusedBorderColor = if (isError) SmartLedgerColors.StateDanger else SmartLedgerColors.AccentLight,
                unfocusedBorderColor = if (isError) SmartLedgerColors.StateDangerBorder else SmartLedgerColors.BorderSubtle,
                focusedTextColor = SmartLedgerColors.TextPrimary,
                unfocusedTextColor = SmartLedgerColors.TextPrimary,
                focusedLabelColor = if (isError) SmartLedgerColors.StateDanger else SmartLedgerColors.AccentLight,
                unfocusedLabelColor = SmartLedgerColors.TextMuted
            ),
            shape = RoundedCornerShape(SmartLedgerTokens.inputCorner),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        )

        if (isError) {
            Text(
                text = errorText.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.StateDanger,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 6.dp, top = 3.dp)
            )
        } else if (!helperText.isNullOrBlank()) {
            Text(
                text = helperText,
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.TextMuted,
                modifier = Modifier.padding(start = 6.dp, top = 3.dp)
            )
        }
    }
}

/**
 * شريط بحث مدمج وذكي (CompactSearchBar)
 * لا يستهلك ارتفاعاً مفرطاً، ويدعم المسح السريع، مسح الباركود، وعرض عدد النتائج الفعلي.
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
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "بحث",
            tint = if (query.isNotEmpty()) SmartLedgerColors.AccentLight else SmartLedgerColors.TextMuted,
            modifier = Modifier.size(18.dp)
        )

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
                    fontWeight = FontWeight.Medium
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
                    .clip(RoundedCornerShape(6.dp))
                    .background(SmartLedgerColors.AccentSubtle)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
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
            IconButton(
                onClick = onBarcodeClick,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SmartLedgerColors.AccentSubtle)
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
 * أزرار الاختيار المجمّعة (Segmented Control)
 */
@Composable
fun <T> SegmentedOptionRow(
    options: List<Pair<T, String>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = SmartLedgerColors.AccentPrimary
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SmartLedgerTokens.inputCorner))
            .background(SmartLedgerColors.SurfaceCardElevated)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selectedOption
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) activeColor else Color.Transparent)
                    .clickable { onOptionSelected(value) }
                    .padding(vertical = 8.dp, horizontal = 6.dp),
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
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { (key, label) ->
            val isSelected = key == selectedItem
            val count = counts?.get(key)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(SmartLedgerTokens.chipCorner))
                    .background(if (isSelected) SmartLedgerColors.AccentSubtle else SmartLedgerColors.SurfaceCardElevated)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) SmartLedgerColors.AccentPrimary else SmartLedgerColors.BorderSubtle,
                        shape = RoundedCornerShape(SmartLedgerTokens.chipCorner)
                    )
                    .clickable { onItemSelected(key) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) SmartLedgerColors.AccentLight else SmartLedgerColors.TextSecondary
                    )
                    if (count != null) {
                        Spacer(modifier = Modifier.width(6.dp))
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

/**
 * حالة فارغة ذكية وسياقية (SmartEmptyState)
 * تفرّق بين عدم وجود بيانات أصلية وبين عدم وجود نتائج مطابقة للبحث أو التصفية.
 */
@Composable
fun SmartEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    isFiltered: Boolean = false,
    onClearFilters: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
        shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(SmartLedgerColors.SurfaceCardElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFiltered) Icons.Default.SearchOff else icon,
                    contentDescription = null,
                    tint = SmartLedgerColors.AccentLight,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isFiltered) "لا توجد نتائج مطابقة" else title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SmartLedgerColors.TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isFiltered) "جرب تعديل كلمات البحث أو مسح الفلاتر النشطة لعرض كافة السجلات." else subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SmartLedgerColors.TextMuted,
                textAlign = TextAlign.Center
            )

            if (isFiltered && onClearFilters != null) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onClearFilters,
                    shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary))
                ) {
                    Icon(Icons.Default.FilterAltOff, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مسح البحث والفلاتر", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                }
            } else if (!actionText.isNullOrBlank() && onActionClick != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onActionClick,
                    colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                    shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                    modifier = Modifier.bounceClick { onActionClick() }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionText, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextInverse)
                }
            }
        }
    }
}

/**
 * حوار تأكيد العمليات الحساسة أو المدمرة (ConfirmActionDialog)
 */
@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: (reason: String?) -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = true,
    requireReason: Boolean = false,
    reasonLabel: String = "سبب العملية (اختياري)"
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SmartLedgerColors.SurfacePanel,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isDestructive) Icons.Default.WarningAmber else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isDestructive) SmartLedgerColors.StateDanger else SmartLedgerColors.AccentLight,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SmartLedgerColors.TextPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SmartLedgerColors.TextSecondary
                )
                if (requireReason) {
                    SmartTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = reasonLabel,
                        placeholder = "توثيق السبب في سجل المراجعة...",
                        imeAction = ImeAction.Done
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.trim().ifBlank { null }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) SmartLedgerColors.StateDanger else SmartLedgerColors.AccentPrimary
                ),
                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextInverse)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = SmartLedgerColors.TextSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

/**
 * شريط رسائل الحالة والتراجع (StatusFeedbackBanner)
 */
@Composable
fun StatusFeedbackBanner(
    message: String?,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isError) SmartLedgerColors.StateDangerSubtle else SmartLedgerColors.StateSuccessSubtle
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = SolidColor(if (isError) SmartLedgerColors.StateDangerBorder else SmartLedgerColors.StateSuccessBorder)
            ),
            shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        tint = if (isError) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isError) SmartLedgerColors.StateDangerText else SmartLedgerColors.StateSuccessText
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!actionLabel.isNullOrBlank() && onAction != null) {
                        TextButton(
                            onClick = onAction,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = actionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.AccentLight
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = if (isError) SmartLedgerColors.StateDangerText else SmartLedgerColors.StateSuccessText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
