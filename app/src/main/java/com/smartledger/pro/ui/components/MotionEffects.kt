package com.smartledger.pro.ui.components

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView

/**
 * تفضيلات الحركات والاهتزازات الحسية المركزية القابلة للتعديل من شاشة الإعدادات
 */
object AppSensoryConfig {
    var enableHaptics by mutableStateOf(true)
    var reduceMotion by mutableStateOf(false)
}

/**
 * أنواع الاستجابة اللمسية الدلالية بحسب طبيعة الحدث (بدلاً من استخدام TextHandleMove عشوائياً)
 */
enum class AppHapticType {
    CLICK,   // نقرة خفيفة للأزرار العادية
    TICK,    // نبضة خفيفة لعدادات الكمية (+/-) والتبديل بين الخيارات
    CONFIRM, // استجابة نجاح واضحة عند إتمام حفظ فاتورة أو سند
    ERROR    // تنبيه لمسي عند رفض الإدخال أو وجود خطأ تحقق
}

/**
 * مدير الاستجابة اللمسية الآمن والمتوافق مع قدرات الجهاز وإعدادات المستخدم
 */
object AppHaptics {
    fun perform(view: View, type: AppHapticType) {
        if (!AppSensoryConfig.enableHaptics) return
        runCatching {
            val constant = when (type) {
                AppHapticType.CLICK -> HapticFeedbackConstants.KEYBOARD_TAP
                AppHapticType.TICK -> HapticFeedbackConstants.CLOCK_TICK
                AppHapticType.CONFIRM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
                AppHapticType.ERROR -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
            }
            view.performHapticFeedback(constant)
        }
    }
}

/**
 * مؤثر بصري وحسي بحت للضغط (pressScaleEffect)
 * - لا ينفذ أي منطق أعمال ولا يضيف clickable مزدوجاً فوق مكونات Button أو OutlinedButton.
 * - يحقق فصل الحركة البصرية عن تنفيذ العمليات المالية بنسبة 100%.
 */
fun Modifier.pressScaleEffect(
    enabled: Boolean = true,
    scaleDown: Float = 0.97f,
    hapticType: AppHapticType? = AppHapticType.CLICK
) = composed {
    var buttonState by remember { mutableStateOf(ButtonState.Idle) }
    val view = LocalView.current

    val targetScale = if (enabled && !AppSensoryConfig.reduceMotion && buttonState == ButtonState.Pressed) {
        scaleDown
    } else {
        1f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 550f),
        label = "PressScaleAnimation"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    buttonState = ButtonState.Pressed
                    if (hapticType != null) {
                        AppHaptics.perform(view, hapticType)
                    }
                    waitForUpOrCancellation()
                    buttonState = ButtonState.Idle
                }
            }
        }
}

/**
 * تفاعل نقر محمي بمانع تكرار (Debounce) ومؤثر ضغط بصري
 * - إذا استُخدم على عنصر غير زر (مثل Card أو Box)، يضيف clickable محمياً بـ debounceMs.
 * - ملاحظة هندسية هامة: عند استخدامه على Button له onClick خاص به، استخدم pressScaleEffect أو
 *   لا تمرر الإجراء المالي مرتين. ولحماية جميع الاستدعاءات الحالية، إذا تم تمرير onClick فارغ `{}`
 *   فإنه يعمل كمؤثر بصري فقط دون اعتراض نقرة الزر الأساسي!
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.97f,
    debounceMs: Long = 450L,
    enabled: Boolean = true,
    hapticType: AppHapticType = AppHapticType.CLICK,
    onClick: (() -> Unit)? = null
) = composed {
    var buttonState by remember { mutableStateOf(ButtonState.Idle) }
    var lastClickTimestamp by remember { mutableStateOf(0L) }
    val view = LocalView.current

    val targetScale = if (enabled && !AppSensoryConfig.reduceMotion && buttonState == ButtonState.Pressed) {
        scaleDown
    } else {
        1f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 550f),
        label = "BounceAnimation"
    )

    val baseModifier = this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    buttonState = ButtonState.Pressed
                    waitForUpOrCancellation()
                    buttonState = ButtonState.Idle
                }
            }
        }

    if (onClick != null) {
        baseModifier.clickable(
            enabled = enabled,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                val now = SystemClock.elapsedRealtime()
                if (now - lastClickTimestamp >= debounceMs) {
                    lastClickTimestamp = now
                    AppHaptics.perform(view, hapticType)
                    onClick()
                }
            }
        )
    } else {
        baseModifier
    }
}

enum class ButtonState { Pressed, Idle }
