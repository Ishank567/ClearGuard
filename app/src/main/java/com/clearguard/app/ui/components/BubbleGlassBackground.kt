package com.clearguard.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.clearguard.app.ui.theme.BubbleGlass
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * App-wide bubble-glass canvas: soft gradient sky + drifting translucent orbs with specular highlights.
 */
@Composable
fun BubbleGlassBackground(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    animate: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val baseAlpha = if (active) 1f else 0.6f
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val transition = if (animate) {
        rememberInfiniteTransition(label = "bubbleGlass")
    } else null

    val phase = if (transition != null) {
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
            label = "phase"
        )
        p
    } else 0f

    val breathe = if (transition != null) {
        val b by transition.animateFloat(
            initialValue = 0.88f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breathe"
        )
        b
    } else 1f

    val bubbles = remember {
        List(6) { i ->
            GlassBubble(
                seed = Random(i * 17 + 3).nextFloat(),
                orbitX = 0.12f + Random(i).nextFloat() * 0.28f,
                orbitY = 0.10f + Random(i).nextFloat() * 0.25f,
                radiusFrac = 0.14f + Random(i).nextFloat() * 0.16f,
                speed = 0.35f + Random(i).nextFloat() * 0.7f,
                hue = i % 3
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BubbleGlass.backgroundBrush())
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(BubbleGlass.bubbleMint, Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.08f),
                    radius = w * 0.55f
                ),
                radius = w * 0.55f,
                center = Offset(w * 0.5f, h * 0.08f)
            )

            bubbles.forEach { bubble ->
                val angle = (phase * bubble.speed + bubble.seed) * 6.28f * 2f
                val cx = w * (0.5f + bubble.orbitX * cos(angle + bubble.seed * 6.28f))
                val cy = h * (0.42f + bubble.orbitY * sin(angle * 0.85f + bubble.seed * 4f))
                val radius = w * bubble.radiusFrac * breathe
                val base = when (bubble.hue) {
                    0 -> primary
                    1 -> secondary
                    else -> BubbleGlass.bubbleMint
                }
                drawGlassBubble(center = Offset(cx, cy), radius = radius, tint = base, alpha = baseAlpha)
            }

            drawGlassBubble(
                center = Offset(
                    w * (0.18f + 0.04f * sin(phase * 6.28f)),
                    h * (0.78f + 0.03f * cos(phase * 6.28f))
                ),
                radius = w * 0.22f * breathe,
                tint = primary,
                alpha = baseAlpha
            )
            drawGlassBubble(
                center = Offset(
                    w * (0.82f - 0.05f * cos(phase * 6.28f)),
                    h * (0.68f + 0.04f * sin(phase * 6.28f))
                ),
                radius = w * 0.18f * breathe,
                tint = secondary,
                alpha = baseAlpha
            )
        }
        content()
    }
}

private data class GlassBubble(
    val seed: Float,
    val orbitX: Float,
    val orbitY: Float,
    val radiusFrac: Float,
    val speed: Float,
    val hue: Int
)

private fun DrawScope.drawGlassBubble(
    center: Offset,
    radius: Float,
    tint: Color,
    alpha: Float = 1f
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.55f * alpha),
                tint.copy(alpha = 0.22f * alpha),
                tint.copy(alpha = 0.06f * alpha),
                Color.Transparent
            ),
            center = center - Offset(radius * 0.22f, radius * 0.28f),
            radius = radius
        ),
        radius = radius,
        center = center
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.42f * alpha),
        radius = radius * 0.18f,
        center = center - Offset(radius * 0.32f, radius * 0.36f)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.12f * alpha),
        radius = radius * 0.55f,
        center = center + Offset(radius * 0.08f, radius * 0.12f)
    )
}