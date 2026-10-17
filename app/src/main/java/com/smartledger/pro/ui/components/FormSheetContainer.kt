package com.smartledger.pro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens

/**
 * حاوية نموذج ذكية موحدة (Smart Form Sheet Container)
 * - تدعم حماية التعديلات غير المحفوظة (hasUnsavedChanges) عبر طلب تأكيد قبل الإغلاق.
 * - تدعم حالات الحفظ والتحميل (isSaving) ومنع الإرسال المكرر.
 * - تعرض رسائل الخطأ السياقية دون مسح القيم المدخلة.
 * - تثبت شريط الإجراءات السفلي فوق لوحة المفاتيح عبر imePadding() و navigationBarsPadding().
 */
@Composable
fun FormSheetContainer(
    title: String,
    subtitle: String? = null,
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
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 1. ترويسة النافذة المدمجة والأنيقة
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            IconButton(
                onClick = handleDismissRequest,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SmartLedgerColors.SurfaceCardElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "إغلاق",
                    tint = SmartLedgerColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // تنبيه فقدان التعديلات غير المحفوظة (يظهر فقط عند محاولة الإغلاق مع وجود تعديلات)
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
                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "لديك تعديلات غير محفوظة، هل تريد تجاهلها؟",
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
                            Text("متابعة التحرير", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextPrimary, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                showDiscardWarning = false
                                keyboardController?.hide()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.StateDanger),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
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
                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
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

        Spacer(modifier = Modifier.height(12.dp))

        // 2. جسم النموذج القابل للتمرير السلس
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. شريط الأزرار السفلي المثبت (Sticky Action Bar)
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    .weight(1.6f)
                    .height(46.dp)
                    .bounceClick(enabled = isPrimaryEnabled && !isSaving) {
                        keyboardController?.hide()
                        onPrimaryClick()
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryButtonColor,
                    disabledContainerColor = SmartLedgerColors.SurfaceCardElevated
                ),
                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
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
                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
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
 * قسم الإفصاح التدريجي (Progressive Disclosure Section)
 * يتيح إبقاء النموذج الأساسي بسيطاً وسريعاً للمستخدم العادي،
 * مع توفير الحقول المتقدمة (مثل الوحدات الكبرى، موقع الرف، حد الائتمان، الملاحظات) عند الطلب.
 */
@Composable
fun ExpandableAdvancedSection(
    title: String = "خيارات وتفاصيل متقدمة",
    subtitle: String? = null,
    initiallyExpanded: Boolean = false,
    badgeText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SmartLedgerTokens.inputCorner))
            .background(SmartLedgerColors.SurfaceCardElevated.copy(alpha = 0.55f))
            .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(SmartLedgerTokens.inputCorner))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Divider(color = SmartLedgerColors.BorderSubtle)
                content()
            }
        }
    }
}
