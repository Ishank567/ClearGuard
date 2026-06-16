package com.clearguard.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/** Tactile 3D press for buttons and interactive cards. */
fun Modifier.press3D(
    pressed: Boolean,
    pressedScale: Float = 0.96f,
    tiltDegrees: Float = 7f
): Modifier = composed {
    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = springSpec,
        label = "press3DScale"
    )
    val tilt by animateFloatAsState(
        targetValue = if (pressed) tiltDegrees else 0f,
        animationSpec = springSpec,
        label = "press3DTilt"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
        rotationX = tilt
        cameraDistance = 12f * density
        transformOrigin = TransformOrigin(0.5f, 0.5f)
    }
}

@Composable
fun animatedCount(target: Int, durationMillis: Int = 700): Int {
    val value by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        label = "animatedCount"
    )
    return value
}