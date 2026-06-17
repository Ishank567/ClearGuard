package com.clearguard.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
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

/** Staggered entrance for dashboard sections and cards. */
@Composable
fun StaggeredEntrance(
    index: Int,
    delayPerItemMs: Int = 70,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index * delayPerItemMs).toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 5 }
    ) {
        content()
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