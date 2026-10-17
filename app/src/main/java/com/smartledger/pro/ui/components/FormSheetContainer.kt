package com.smartledger.pro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens

/**
 * نافذة عائمة حديثة وذكية (FloatingModalDialog)
 * - تحل محل اللوحات الملتصقة التقليدية (ModalBottomSheet) لتعطي مظهراً عائماً أنيقاً ومنفصلاً عن حواف الشاشة.
 * - تستخدم `decorFitsSystemWindows = false` مع `imePadding()` و`systemBarsPadding()` بحيث ترتفع النافذة
 *   تلقائياً وبانسيابية فوق لوحة المفاتيح دون أن تختفي أزرار الحفظ أو الحقول النشطة.
 */
@Composable
fun FloatingModalDialog(
    onDismissRequest: () -> Unit,
    maxWidth: Dp = 540.dp,
    content: @Composable () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Dialog(
        onDismissRequest = {
            keyboardController?.hide()
            onDismissRequest()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SmartLedgerColors.SurfaceOverlay)
                .imePadding()
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = maxWidth)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color.Black,
                        spotColor = SmartLedgerColors.AccentPrimary.copy(alpha = 0.25f)
                    )
                    .clip(RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = SmartLedgerColors.SurfacePanel,
                tonalElevation = 12.dp,
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            SmartLedgerColors.AccentLight.copy(alpha = 0.35f),
                            SmartLedgerColors.BorderSubtle
                        )
                    )
                )
            ) {
                content()
            }
        }
    }
}

/**
 * حاوية النماذج العائمة الاحترافية (FormSheetContainer)
 * - مصممة لتكون رشيقة وغير ملتصقة بحواف الشاشة، مع مسافات مريحة تمنع تشابك الحقول.
 * - ترفع المحتوى وشريط الأزرار السفلي تلقائياً فوق لوحة المفاتيح.
 * - تحمي المستخدم من فقدان التعديلات غير المحفوظة وتمنع النقر المزدوج أثناء الحفظ.
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
    onPrimaryClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var showDiscardWarning by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val handleDismissRequest = {
        if (hasUnsavedChanges && !isSaving) {
            showDiscardWarning = true
        } else {
            keyboardController?.hide()
            onDismiss()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp)
    ) {
        // 1. ترويسة النافذة العائمة (Floating Window Header)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SmartLedgerColors.SurfaceCardElevated.copy(alpha = 0.45f))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // مؤشر علوي جمالي رشيق
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 10.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(SmartLedgerColors.BorderStrong.copy(alpha = 0.6f))
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
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(SmartLedgerColors.AccentSubtle)
                                .border(1.dp, SmartLedgerColors.AccentPrimary.copy(alpha = 0.4f), RoundedCornerShape(11.dp)),
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
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
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
                    onClick = handleDismissRequest,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(SmartLedgerColors.SurfaceCard)
                        .border(1.dp, SmartLedgerColors.BorderSubtle, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = SmartLedgerColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // تنبيه فقدان التعديلات غير المحفوظة
            AnimatedVisibility(
                visible = showDiscardWarning,
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
                                onClick = { showDiscardWarning = false },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("متابعة", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    showDiscardWarning = false
                                    keyboardController?.hide()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.StateDanger),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("تجاهل", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextInverse, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // رسالة خطأ سياقية إن وجدت
            AnimatedVisibility(
                visible = !errorMessage.isNullOrBlank(),
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
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.StateDangerText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Divider(color = SmartLedgerColors.BorderSubtle)

        // 2. جسم النموذج القابل للتمرير المريح بمسافات متوازنة غير متشابكة
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

        // 3. شريط الإجراءات السفلي الثابت (يظهر دائماً فوق لوحة المفاتيح مباشرة)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SmartLedgerColors.SurfaceCardElevated.copy(alpha = 0.35f))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    if (isPrimaryEnabled && !isSaving) {
                        keyboardController?.hide()
                        onPrimaryClick()
                    }
                },
                enabled = isPrimaryEnabled && !isSaving,
                modifier = Modifier
                    .weight(1.65f)
                    .height(46.dp)
                    .bounceClick(enabled = isPrimaryEnabled && !isSaving) {
                        keyboardController?.hide()
                        onPrimaryClick()
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryButtonColor,
                    disabledContainerColor = SmartLedgerColors.SurfaceCardElevated
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
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
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    Text(
                        text = primaryButtonText,
                        fontWeight = FontWeight.Bold,
                        color = if (isPrimaryEnabled) SmartLedgerColors.TextInverse else SmartLedgerColors.TextDisabled,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            OutlinedButton(
                onClick = handleDismissRequest,
                enabled = !isSaving,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartLedgerColors.TextSecondary),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = SolidColor(SmartLedgerColors.BorderSubtle)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = secondaryButtonText,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.TextSecondary
                )
            }
        }
    }
}

/**
 * بطاقة تجميع حقول رشيقة داخل النوافذ العائمة (FormSectionGroup)
 * تفصل مجموعات الحقول بصرياً بمسافات مدروسة تمنع الالتصاق والتشابك.
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
            .clip(RoundedCornerShape(14.dp))
            .background(SmartLedgerColors.SurfaceCard.copy(alpha = 0.75f))
            .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SmartLedgerColors.AccentLight
            )
            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SmartLedgerColors.AccentSubtle)
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

/**
 * قسم الإفصاح التدريجي الاختياري (ExpandableAdvancedSection)
 */
@Composable
fun ExpandableAdvancedSection(
    title: String = "خيارات وتفاصيل إضافية",
    subtitle: String? = null,
    initiallyExpanded: Boolean = false,
    badgeText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SmartLedgerColors.SurfaceCard.copy(alpha = 0.6f))
            .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = SmartLedgerColors.AccentLight,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.TextPrimary
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!badgeText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SmartLedgerColors.AccentSubtle)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.AccentLight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = SmartLedgerColors.TextMuted
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Divider(color = SmartLedgerColors.BorderSubtle)
                content()
            }
        }
    }
}
