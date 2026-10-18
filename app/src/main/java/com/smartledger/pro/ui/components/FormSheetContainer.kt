package com.smartledger.pro.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens

/**
 * حاوية نوافذ الإدخال الفاخرة (FormSheetContainer)
 * - ترويسة زجاجية متدرجة راقية مع مقبض علوي أنيق وأيقونة مضيئة.
 * - حماية صارمة للتعديلات غير المحفوظة ومنع الإغلاق الصامت عند الخطأ.
 * - شريط إجراءات سفلي مدمج بأزرار ذكية متوهجة.
 */
@Composable
fun FormSheetContainer(
    title: String,
    subtitle: String? = null,
    headerIcon: ImageVector? = null,
    onDismiss: () -> Unit,
    primaryButtonText: String = "حفظ",
    secondaryButtonText: String = "إلغاء",
    primaryButtonColor: Color = SmartLedgerColors.AccentPrimary,
    isPrimaryEnabled: Boolean = true,
    isSaving: Boolean = false,
    hasUnsavedChanges: Boolean = false,
    errorMessage: String? = null,
    onValidationFailedMessage: String = "يرجى استكمال الحقول المطلوبة بشكل صحيح قبل الحفظ",
    onPrimaryClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var lifecycleState by rememberSaveable { mutableStateOf(FormWindowLifecycleState.EDITING) }
    var localValidationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var lastSubmitTimestamp by remember { mutableStateOf(0L) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val registerInterceptor = LocalModalCloseInterceptor.current

    LaunchedEffect(isSaving, errorMessage) {
        when {
            isSaving -> {
                lifecycleState = FormWindowLifecycleState.SAVING
                localValidationMessage = null
            }
            !errorMessage.isNullOrBlank() -> {
                lifecycleState = FormWindowLifecycleState.SAVE_ERROR
                AppHaptics.perform(view, AppHapticType.ERROR)
            }
            lifecycleState == FormWindowLifecycleState.SAVING -> {
                lifecycleState = FormWindowLifecycleState.EDITING
            }
        }
    }

    val requestWindowClose: () -> Boolean = {
        when {
            isSaving || lifecycleState == FormWindowLifecycleState.SAVING -> true
            lifecycleState == FormWindowLifecycleState.CONFIRMING_DISCARD -> {
                lifecycleState = FormWindowLifecycleState.EDITING
                true
            }
            hasUnsavedChanges -> {
                lifecycleState = FormWindowLifecycleState.CONFIRMING_DISCARD
                AppHaptics.perform(view, AppHapticType.TICK)
                true
            }
            else -> {
                lifecycleState = FormWindowLifecycleState.CLOSED
                keyboardController?.hide()
                focusManager.clearFocus()
                onDismiss()
                true
            }
        }
    }

    DisposableEffect(registerInterceptor, hasUnsavedChanges, isSaving, lifecycleState) {
        registerInterceptor?.invoke(requestWindowClose)
        onDispose { }
    }

    val activeBannerError = errorMessage?.takeIf { it.isNotBlank() } ?: localValidationMessage
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = minOf(700.dp, screenHeight * 0.86f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
    ) {
        // 1. ترويسة النافذة الفاخرة (Luxury Modal Header)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            primaryButtonColor.copy(alpha = 0.14f),
                            SmartLedgerColors.SurfaceCardElevated.copy(alpha = 0.35f)
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 13.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 9.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(primaryButtonColor.copy(alpha = 0.55f))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (headerIcon != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            primaryButtonColor.copy(alpha = 0.25f),
                                            SmartLedgerColors.AccentSubtle
                                        )
                                    )
                                )
                                .border(1.dp, primaryButtonColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = headerIcon,
                                contentDescription = null,
                                tint = SmartLedgerColors.AccentLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            if (hasUnsavedChanges) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SmartLedgerColors.AccentSubtle)
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "قيد التحرير",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SmartLedgerColors.AccentLight,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (!subtitle.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { requestWindowClose() },
                    enabled = !isSaving,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(SmartLedgerColors.SurfaceCard)
                        .border(1.dp, SmartLedgerColors.BorderSubtle, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق النافذة",
                        tint = SmartLedgerColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // حالة CONFIRMING_DISCARD
            AnimatedVisibility(
                visible = lifecycleState == FormWindowLifecycleState.CONFIRMING_DISCARD,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.StateWarningSubtle),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.StateWarningBorder)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "لديك بيانات مدخلة لم تُحفظ، هل تريد إغلاق النافذة؟",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SmartLedgerColors.StateWarning,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = { lifecycleState = FormWindowLifecycleState.EDITING },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("متابعة التحرير", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    lifecycleState = FormWindowLifecycleState.CLOSED
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.StateDanger),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("تجاهل وإغلاق", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextInverse, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // حالة VALIDATION_ERROR / SAVE_ERROR
            AnimatedVisibility(
                visible = !activeBannerError.isNullOrBlank(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.StateDangerSubtle),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.StateDangerBorder)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = SmartLedgerColors.StateDanger,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeBannerError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.StateDangerText,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Divider(color = SmartLedgerColors.BorderSubtle)

        // 2. جسم النموذج القابل للتمرير المريح
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }

        Divider(color = SmartLedgerColors.BorderSubtle)

        // 3. شريط الإجراءات السفلي الذكي الثابت
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SmartLedgerColors.SurfaceCardElevated.copy(alpha = 0.45f))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastSubmitTimestamp < 500L || isSaving || lifecycleState == FormWindowLifecycleState.SAVING) {
                        return@Button
                    }
                    lastSubmitTimestamp = now
                    lifecycleState = FormWindowLifecycleState.VALIDATING

                    if (!isPrimaryEnabled) {
                        lifecycleState = FormWindowLifecycleState.VALIDATION_ERROR
                        localValidationMessage = onValidationFailedMessage
                        AppHaptics.perform(view, AppHapticType.ERROR)
                    } else {
                        localValidationMessage = null
                        lifecycleState = FormWindowLifecycleState.EDITING
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        AppHaptics.perform(view, AppHapticType.CONFIRM)
                        onPrimaryClick()
                    }
                },
                enabled = !isSaving && lifecycleState != FormWindowLifecycleState.SAVING,
                modifier = Modifier
                    .weight(1.75f)
                    .heightIn(min = 46.dp)
                    .pressScaleEffect(
                        enabled = !isSaving && lifecycleState != FormWindowLifecycleState.SAVING,
                        hapticType = null
                    ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPrimaryEnabled) primaryButtonColor else SmartLedgerColors.SurfaceCardElevated,
                    disabledContainerColor = SmartLedgerColors.SurfaceCardElevated
                ),
                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
            ) {
                if (isSaving || lifecycleState == FormWindowLifecycleState.SAVING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = SmartLedgerColors.TextInverse
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "جارٍ الحفظ...",
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextInverse,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    if (isPrimaryEnabled) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SmartLedgerColors.TextInverse,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = primaryButtonText,
                        fontWeight = FontWeight.Bold,
                        color = if (isPrimaryEnabled) SmartLedgerColors.TextInverse else SmartLedgerColors.TextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(
                onClick = { requestWindowClose() },
                enabled = !isSaving && lifecycleState != FormWindowLifecycleState.SAVING,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .pressScaleEffect(
                        enabled = !isSaving && lifecycleState != FormWindowLifecycleState.SAVING,
                        hapticType = AppHapticType.CLICK
                    ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartLedgerColors.TextSecondary),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = SolidColor(SmartLedgerColors.BorderSubtle)
                ),
                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
            ) {
                Text(
                    text = secondaryButtonText,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
