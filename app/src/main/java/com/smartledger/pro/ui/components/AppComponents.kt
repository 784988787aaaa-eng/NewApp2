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
            .clip(RoundedCornerShape(12.dp))
            .background(SmartLedgerColors.SurfaceInput)
            .border(
                width = 1.dp,
                color = if (query.isNotEmpty()) SmartLedgerColors.AccentPrimary else SmartLedgerColors.BorderSubtle,
                shape = RoundedCornerShape(12.dp)
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
                    .clip(RoundedCornerShape(8.dp))
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
 * أزرار الاختيار المجمّعة (Segmented Control) مع استجابة لمسية خفيفة (TICK)
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
            .clip(RoundedCornerShape(12.dp))
            .background(SmartLedgerColors.SurfaceCardElevated)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selectedOption
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) activeColor else Color.Transparent)
                    .clickable {
                        if (!isSelected) {
                            AppHaptics.perform(view, AppHapticType.TICK)
                        }
                        onOptionSelected(value)
                    }
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
    val view = LocalView.current
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
                    .clickable {
                        if (!isSelected) {
                            AppHaptics.perform(view, AppHapticType.TICK)
                        }
                        onItemSelected(key)
                    }
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
                    modifier = Modifier.pressScaleEffect()
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
 * حوار تأكيد العمليات الحساسة أو المدمرة (ConfirmActionDialog) — نافذة قرار قصيرة ومستقلة
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
    var reason by rememberSaveable { mutableStateOf("") }

    FloatingModalDialog(onDismissRequest = onDismiss, maxWidth = 440.dp) {
        FormSheetContainer(
            title = title,
            headerIcon = if (isDestructive) Icons.Default.WarningAmber else Icons.Default.Info,
            onDismiss = onDismiss,
            primaryButtonText = confirmText,
            primaryButtonColor = if (isDestructive) SmartLedgerColors.StateDanger else SmartLedgerColors.AccentPrimary,
            onPrimaryClick = { onConfirm(reason.trim().ifBlank { null }) }
        ) {
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
    }
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

/**
 * شاشة قفل التطبيق الآمنة (AppSecurityLockScreen)
 * - تعيش داخل طبقة `ui.components` لمنع الاعتماديات الدائرية بين `util` و`ui`.
 * - تتحقق من رمز PIN عبر `SecurityCryptoHelper.verifyPin` (مقارنة مملحة PBKDF2 بزمن ثابت).
 * - تفرض تأخيراً متدرجاً (`Progressive Lockout`) عند تكرار المحاولات الفاشلة لمنع التخمين.
 */
@Composable
fun AppSecurityLockScreen(
    expectedPinVerifier: String,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val view = LocalView.current
    val capability = remember { BiometricLockHelper.checkBiometricSupport(context) }

    val scope = rememberCoroutineScope()
    var pinInput by rememberSaveable { mutableStateOf("") }
    var isVerifyingPin by remember { mutableStateOf(false) }
    var failedAttempts by rememberSaveable { mutableStateOf(0) }
    var lockoutRemainingSeconds by rememberSaveable { mutableStateOf(0) }
    var lockoutUntilRealtime by rememberSaveable { mutableStateOf(0L) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(lockoutUntilRealtime) {
        while (lockoutUntilRealtime > SystemClock.elapsedRealtime()) {
            val remaining = ((lockoutUntilRealtime - SystemClock.elapsedRealtime() + 999L) / 1000L).toInt()
            lockoutRemainingSeconds = remaining.coerceAtLeast(0)
            delay(250L)
        }
        lockoutRemainingSeconds = 0
    }

    val verifyEnteredPin: () -> Unit = {
        if (lockoutRemainingSeconds > 0) {
            errorMessage = "تم إيقاف المحاولات مؤقتاً لحماية الحساب. انتظر $lockoutRemainingSeconds ثانية."
            AppHaptics.perform(view, AppHapticType.ERROR)
        } else if (!isVerifyingPin) {
            isVerifyingPin = true
            val candidatePin = pinInput
            scope.launch {
                val matched = withContext(Dispatchers.Default) {
                    SecurityCryptoHelper.verifyPin(candidatePin, expectedPinVerifier)
                }
                isVerifyingPin = false
                if (matched) {
                    failedAttempts = 0
                    lockoutUntilRealtime = 0L
                    lockoutRemainingSeconds = 0
                    AppHaptics.perform(view, AppHapticType.CONFIRM)
                    onUnlocked()
                } else {
                    failedAttempts++
                    val penaltySec = SecurityCryptoHelper.calculateLockoutDurationSeconds(failedAttempts)
                    if (penaltySec > 0) {
                        lockoutUntilRealtime = SystemClock.elapsedRealtime() + (penaltySec * 1000L)
                        lockoutRemainingSeconds = penaltySec
                        errorMessage = "تجاوزت الحد المسموح للمحاولات ($failedAttempts). يرجى الانتظار $penaltySec ثانية."
                    } else {
                        val remainingTries = (5 - failedAttempts).coerceAtLeast(1)
                        errorMessage = "رمز الحماية غير صحيح (متبقي $remainingTries محاولات قبل الإيقاف المؤقت)"
                    }
                    AppHaptics.perform(view, AppHapticType.ERROR)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (activity != null && capability.canUseBiometric) {
            BiometricLockHelper.showBiometricPrompt(
                activity = activity,
                onSuccess = {
                    failedAttempts = 0
                    lockoutUntilRealtime = 0L
                    onUnlocked()
                },
                onError = { err -> errorMessage = err }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SmartLedgerColors.SurfaceWindow)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SmartLedgerColors.AccentSubtle)
                        .border(1.5.dp, SmartLedgerColors.AccentPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = SmartLedgerColors.AccentLight,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "الدفتر الذكي Pro محمي",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SmartLedgerColors.TextPrimary
                )

                Text(
                    text = "لحماية سجلاتك المالية، يرجى تأكيد الهوية بالبصمة أو إدخال رمز المرور.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SmartLedgerColors.TextMuted,
                    textAlign = TextAlign.Center
                )

                if (expectedPinVerifier.isNotBlank()) {
                    SmartTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = NumberFormatter.normalizeNumericInput(it, allowDecimal = false).take(8)
                            if (lockoutRemainingSeconds == 0) {
                                errorMessage = null
                            }
                        },
                        label = "رمز الحماية الشخصي (PIN)",
                        placeholder = "أدخل الرمز الرقمي...",
                        enabled = lockoutRemainingSeconds == 0,
                        errorText = if (lockoutRemainingSeconds > 0) {
                            "يرجى الانتظار $lockoutRemainingSeconds ثانية قبل المحاولة مجدداً"
                        } else errorMessage,
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                        onDoneAction = { verifyEnteredPin() }
                    )

                    Button(
                        onClick = { verifyEnteredPin() },
                        enabled = pinInput.length >= 4 && lockoutRemainingSeconds == 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .pressScaleEffect(enabled = pinInput.length >= 4 && lockoutRemainingSeconds == 0),
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                    ) {
                        Text(
                            text = if (lockoutRemainingSeconds > 0) "مقفل مؤقتاً ($lockoutRemainingSeconds ث)" else "تحقق وفتح القفل",
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextInverse
                        )
                    }
                } else if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SmartLedgerColors.StateDanger,
                        textAlign = TextAlign.Center
                    )
                }

                if (activity != null && capability.canUseBiometric) {
                    OutlinedButton(
                        onClick = {
                            BiometricLockHelper.showBiometricPrompt(
                                activity = activity,
                                onSuccess = {
                                    failedAttempts = 0
                                    lockoutUntilRealtime = 0L
                                    onUnlocked()
                                },
                                onError = { err -> errorMessage = err }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .pressScaleEffect(),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary))
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = SmartLedgerColors.AccentLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("المصادقة ببصمة الإصبع / قفل الجهاز", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                    }
                } else if (expectedPinVerifier.isBlank()) {
                    Text(
                        text = capability.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.StateWarning,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onUnlocked,
                        modifier = Modifier.pressScaleEffect(),
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                    ) {
                        Text("المتابعة إلى التطبيق", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
