package com.smartledger.pro.ui.components

import android.os.SystemClock
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * تفضيلات الحركات والاهتزازات الحسية المركزية القابلة للتعديل من شاشة الإعدادات
 */
object AppSensoryConfig {
    var enableHaptics by mutableStateOf(true)
    var reduceMotion by mutableStateOf(false)
}

/**
 * تأثير ارتداد فيزيائي لمسي (Tactile Bounce Click) مع حماية مدمجة ضد النقر المكرر (Debounce)
 * - يمنع تكرار تنفيذ الفواتير أو السندات عند الضغط السريع المتتالي (450ms).
 * - يحترم إعدادات المستخدم (تفعيل/تعطيل الاهتزاز الحسي وتقليل الحركة).
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.97f,
    debounceMs: Long = 450L,
    enabled: Boolean = true,
    onClick: () -> Unit
) = composed {
    var buttonState by remember { mutableStateOf(ButtonState.Idle) }
    var lastClickTimestamp by remember { mutableStateOf(0L) }
    val haptic = LocalHapticFeedback.current

    val targetScale = if (enabled && !AppSensoryConfig.reduceMotion && buttonState == ButtonState.Pressed) {
        scaleDown
    } else {
        1f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 550f),
        label = "BounceAnimation"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                val now = SystemClock.elapsedRealtime()
                if (now - lastClickTimestamp >= debounceMs) {
                    lastClickTimestamp = now
                    if (AppSensoryConfig.enableHaptics) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                }
            }
        )
        .pointerInput(buttonState, enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                buttonState = if (buttonState == ButtonState.Pressed) {
                    waitForUpOrCancellation()
                    ButtonState.Idle
                } else {
                    awaitFirstDown(false)
                    ButtonState.Pressed
                }
            }
        }
}

enum class ButtonState { Pressed, Idle }
