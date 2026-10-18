package com.smartledger.pro.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.util.BiometricLockHelper
import com.smartledger.pro.util.NumberFormatter
import com.smartledger.pro.util.SecurityCryptoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * حقل إدخال عصري وذكي (SmartTextField)
 * - يفصل التسمية العلوية (Label) فوق حقل الإدخال لمنع تشابك أو اقتصاص التسميات العربية.
 * - يدعم `BringIntoViewRequester` بحيث يتم تمرير الحقل النشط تلقائياً ليظهر كاملاً فوق لوحة المفاتيح وشريط الأزرار.
 * - يدعم التركيز التلقائي الاختياري (`autoFocusOnAppear`) للنماذج السريعة فقط دون إرباك المستخدم في الشاشات الأخرى.
 * - يضبط `ImeAction.Done` لإخفاء لوحة المفاتيح بأمان دون تنفيذ عملية حفظ مالية غير مقصودة.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    autoFocusOnAppear: Boolean = false,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    onDoneAction: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val internalFocusRequester = remember { FocusRequester() }
    val activeFocusRequester = focusRequester ?: internalFocusRequester
    val coroutineScope = rememberCoroutineScope()

    val isError = !errorText.isNullOrBlank()
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(autoFocusOnAppear) {
        if (autoFocusOnAppear && enabled && !readOnly) {
            delay(120)
            runCatching { activeFocusRequester.requestFocus() }
        }
    }

    Column(
        modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                color = when {
                    isError -> SmartLedgerColors.StateDanger
                    isFocused -> SmartLedgerColors.AccentLight
                    else -> SmartLedgerColors.TextSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isRequired) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "*",
                    style = MaterialTheme.typography.labelMedium,
                    color = SmartLedgerColors.StateDanger,
                    fontWeight = FontWeight.Bold
                )
            }
        }

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
            placeholder = if (!placeholder.isNullOrBlank()) {
                {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SmartLedgerColors.TextMuted.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else null,
            leadingIcon = if (leadingIcon != null) {
                {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = if (isFocused) SmartLedgerColors.AccentLight else SmartLedgerColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else null,
            trailingIcon = trailingContent,
            enabled = enabled,
            readOnly = readOnly,
            isError = isError,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                color = SmartLedgerColors.TextPrimary
            ),
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
                unfocusedTextColor = SmartLedgerColors.TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(activeFocusRequester)
                .onFocusChanged { state ->
                    isFocused = state.isFocused
                    if (state.isFocused) {
                        coroutineScope.launch {
                            delay(80)
                            runCatching { bringIntoViewRequester.bringIntoView() }
                        }
                    }
                }
        )

        if (isError) {
            Text(
                text = errorText.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.StateDanger,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp)
            )
        } else if (!helperText.isNullOrBlank()) {
            Text(
                text = helperText,
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.AccentLight,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * شريط بحث مدمج وذكي (CompactSearchBar)
 */
