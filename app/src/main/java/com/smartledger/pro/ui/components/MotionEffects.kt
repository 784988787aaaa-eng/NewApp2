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

object AppSensoryConfig {
    var enableHaptics by mutableStateOf(true)
    var reduceMotion by mutableStateOf(false)
    var compactDensity by mutableStateOf(false)
    var currencySymbol by mutableStateOf("ر.ي")
    var decimalPlaces by mutableStateOf(2)
}

@Composable
fun rememberDebouncedClick(
    debounceMs: Long = 450L,
    onClick: () -> Unit
): () -> Unit {
    var lastClickTimestamp by remember { mutableStateOf(0L) }
    val haptic = LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onClick)

    return remember(debounceMs) {
        {
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickTimestamp >= debounceMs) {
                lastClickTimestamp = now
                if (AppSensoryConfig.enableHaptics) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                currentOnClick()
            }
        }
    }
}

fun Modifier.bounceClick(
    scaleDown: Float = 0.97f,
    debounceMs: Long = 450L,
    onClick: (() -> Unit)? = null
) = composed {
    var buttonState by remember { mutableStateOf(ButtonState.Idle) }
    var lastClickTimestamp by remember { mutableStateOf(0L) }
    val haptic = LocalHapticFeedback.current

    val targetScale = if (!AppSensoryConfig.reduceMotion && buttonState == ButtonState.Pressed) scaleDown else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "BounceAnimation"
    )

    val baseModifier = this.graphicsLayer { scaleX = scale; scaleY = scale }
    val clickableModifier = if (onClick != null) {
        baseModifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                val now = SystemClock.elapsedRealtime()
                if (now - lastClickTimestamp >= debounceMs) {
                    lastClickTimestamp = now
                    if (AppSensoryConfig.enableHaptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            }
        )
    } else baseModifier

    clickableModifier.pointerInput(buttonState) {
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
