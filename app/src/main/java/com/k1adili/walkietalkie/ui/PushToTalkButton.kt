package com.k1adili.walkietalkie.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PttMode { IDLE, TX, RX, OFF }

/**
 * The big round talk button.
 * - Hold mode: transmits while a finger is down (keeps going if the finger drifts off the button).
 * - Tap mode: tap to start, tap again to stop.
 * Rings pulse with the live audio level; colour tells you the state at a glance.
 */
@Composable
fun PushToTalkButton(
    mode: PttMode,
    tapToggle: Boolean,
    levelState: State<Float>,
    onTalkStart: () -> Unit,
    onTalkStop: () -> Unit,
    onTalkToggle: () -> Unit,
    areaSize: Dp,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

    val baseColor = animateColorAsState(
        targetValue = when (mode) {
            PttMode.TX -> scheme.tertiary
            PttMode.RX -> scheme.secondary
            PttMode.OFF -> scheme.outlineVariant
            PttMode.IDLE -> scheme.primary
        },
        animationSpec = tween(250),
        label = "pttColor",
    )
    val contentColor = when (mode) {
        PttMode.TX -> scheme.onTertiary
        PttMode.RX -> scheme.onSecondary
        PttMode.OFF -> scheme.onSurfaceVariant
        PttMode.IDLE -> scheme.onPrimary
    }
    val pressScale = animateFloatAsState(
        targetValue = if (mode == PttMode.TX) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pttScale",
    )
    val pulse = rememberInfiniteTransition(label = "pttPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "pulse",
    )

    // Read inside the pointer-input coroutine, so the gesture never has to restart.
    val enabled by rememberUpdatedState(mode != PttMode.OFF)
    val tapMode by rememberUpdatedState(tapToggle)
    val start by rememberUpdatedState(onTalkStart)
    val stop by rememberUpdatedState(onTalkStop)
    val toggle by rememberUpdatedState(onTalkToggle)

    val buttonSize = areaSize * 0.68f
    val label = when (mode) {
        PttMode.TX -> "ON AIR"
        PttMode.RX -> "LISTENING"
        PttMode.OFF -> "OFF"
        PttMode.IDLE -> if (tapToggle) "TAP" else "HOLD"
    }

    Box(modifier.size(areaSize), contentAlignment = Alignment.Center) {
        // Rings + glow (state is read in the draw phase only: no recomposition per audio frame)
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val maxR = size.minDimension / 2f
            val buttonR = maxR * 0.68f
            val color = baseColor.value
            val active = mode == PttMode.TX || mode == PttMode.RX
            val lvl = levelState.value.coerceIn(0f, 1f)

            val rings = if (active) 2 else 1
            for (k in 0 until rings) {
                val t = (pulse.value + k * 0.5f) % 1f
                drawCircle(
                    color = color.copy(alpha = (if (active) 0.45f else 0.18f) * (1f - t)),
                    radius = buttonR + (maxR - buttonR) * t,
                    center = c,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
            if (active) {
                drawCircle(
                    color = color.copy(alpha = 0.22f),
                    radius = buttonR + (maxR - buttonR) * 0.45f * lvl,
                    center = c,
                )
            }
            drawCircle(color = color.copy(alpha = 0.16f), radius = buttonR * 1.06f, center = c)
        }

        Box(
            Modifier
                .size(buttonSize)
                .graphicsLayer {
                    scaleX = pressScale.value
                    scaleY = pressScale.value
                }
                .drawBehind {
                    val b = baseColor.value
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(lerp(b, Color.White, 0.25f), b, lerp(b, Color.Black, 0.28f)),
                            center = Offset(size.width * 0.35f, size.height * 0.3f),
                            radius = size.minDimension,
                        ),
                        radius = size.minDimension / 2f,
                    )
                }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "Push to talk"
                    stateDescription = when (mode) {
                        PttMode.TX -> "Transmitting"
                        PttMode.RX -> "Receiving"
                        PttMode.OFF -> "Off"
                        PttMode.IDLE -> "Ready"
                    }
                    // Screen-reader users can't hold a finger down, so double tap toggles talking.
                    onClick(label = if (mode == PttMode.TX) "Stop talking" else "Start talking") {
                        if (enabled) toggle()
                        true
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        if (!enabled) {
                            waitForUpOrCancellation()
                            return@awaitEachGesture
                        }
                        if (tapMode) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            toggle()
                            waitForUpOrCancellation()
                        } else {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            start()
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            } finally {
                                stop()
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(buttonSize * 0.36f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}
