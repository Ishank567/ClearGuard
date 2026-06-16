package com.clearguard.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Shared "realistic motion" helpers for the whole app.
 *
 * [pressSpring] gives any surface a tactile, slightly bouncy push-in on press (spring physics,
 * not a linear tween) — the difference between a button that feels mechanical and one that feels
 * physical. [shimmer] sweeps a soft highlight across a surface for loading/placeholder states.
 */

/** Tactile springy press feedback for any clickable surface (no ripple; scales in on press). */
fun Modifier.pressSpring(
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressSpring"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/**
 * Tactile 3D press for buttons and surfaces. On press the surface scales in slightly *and* tilts
 * back in perspective (rotationX), so — paired with a Material elevation that drops on press — it
 * reads as physically pushing into the screen rather than a flat scale. Drive [pressed] from the
 * surface's own [MutableInteractionSource] so the transform tracks the real touch state.
 */
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

/**
 * Animated whole-number value — eases from the previous value to [target] so stat counters tick up
 * instead of snapping. Returns the in-flight value to display.
 */
@Composable
fun animatedCount(target: Int, durationMillis: Int = 700): Int {
    val value by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        label = "animatedCount"
    )
    return value
}

/** A soft highlight that sweeps across the surface — drop on skeletons / loading placeholders. */
fun Modifier.shimmer(
    color: Color = Color.White,
    durationMillis: Int = 1400
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress"
    )
    drawWithContent {
        drawContent()
        val band = size.width * 0.45f
        val start = -band + (size.width + band) * progress
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = 0.30f), Color.Transparent),
                startX = start,
                endX = start + band
            )
        )
    }
}
