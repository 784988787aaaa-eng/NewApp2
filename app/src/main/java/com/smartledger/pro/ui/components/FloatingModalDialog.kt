package com.smartledger.pro.ui.components

import android.os.SystemClock
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartledger.pro.ui.theme.SmartLedgerColors

/**
 * نموذج الحالات الصريح لدورة حياة نافذة الإدخال (Explicit Window Lifecycle State Machine)
 * التدفق الأساسي:
 * - Open -> EDITING -> VALIDATING -> SAVING -> SAVE_SUCCESS -> Close
 * - عند فشل التحقق: EDITING -> VALIDATION_ERROR (لا تُغلق النافذة ولا تضيع القيم)
 * - عند فشل الحفظ: SAVING -> SAVE_ERROR -> EDITING (لا تُغلق النافذة وتُعرض الرسالة مع بقاء القيم)
 * - عند طلب الإلغاء أو الرجوع مع وجود تعديلات: EDITING -> CONFIRMING_DISCARD
 */
@Composable
fun FloatingModalDialog(
    onDismissRequest: () -> Unit,
    maxWidth: Dp = 540.dp,
    allowOutsideClickDismiss: Boolean = false,
    content: @Composable () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var childCloseHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }

    val safeAttemptDismiss = {
        val intercepted = childCloseHandler?.invoke() ?: false
        if (!intercepted) {
            keyboardController?.hide()
            onDismissRequest()
        }
    }

    Dialog(
        onDismissRequest = safeAttemptDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = allowOutsideClickDismiss
        )
    ) {
        // اعتراض صريح لزر الرجوع في أندرويد لضمان المرور عبر حارس التعديلات غير المحفوظة وحارس الحفظ الجاري
        BackHandler(enabled = true) {
            safeAttemptDismiss()
        }

        CompositionLocalProvider(
            LocalModalCloseInterceptor provides { handler -> childCloseHandler = handler }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SmartLedgerColors.SurfaceOverlay)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (allowOutsideClickDismiss) {
                            safeAttemptDismiss()
                        }
                    }
                    .imePadding()
                    .systemBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = maxWidth)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* منع انتقال النقرة إلى الخلفية */ }
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
}

/**
 * حاوية النماذج العائمة الموحدة (FormSheetContainer)
 * تطبق دورة حياة FormWindowLifecycleState الصارمة:
 * 1. تمنع الإغلاق التلقائي عند وجود خطأ تحقق أو فشل في الحفظ.
 * 2. تمنع الإغلاق العرضي أثناء تنفيذ الحفظ (isSaving == true).
 * 3. تحمي التعديلات غير المحفوظة عند الضغط على زر الإغلاق أو زر الإلغاء أو زر الرجوع في أندرويد.
 * 4. تفصل التأثير البصري للزر عن استدعاء onPrimaryClick لمنع تكرار العمليات المالية، مع قفل زمني ضد النقر السريع.
 */
