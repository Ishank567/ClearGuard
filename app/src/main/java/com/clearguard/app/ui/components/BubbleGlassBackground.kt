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
 * Sunset bubble-glass canvas: golden-hour sky, glowing sun disc, warm drifting orbs.
 */
@Composable
fun BubbleGlassBackground(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    animate: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val baseAlpha = if (active) 1f else 0.55f
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val transition = if (animate) {
        rememberInfiniteTransition(label = "sunsetBg")
    } else null

    val phase = if (transition != null) {
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart),
            label = "phase"
        )
        p
    } else 0f

    val breathe = if (transition != null) {
        val b by transition.animateFloat(
            initialValue = 0.90f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(6000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breathe"
        )
        b
    } else 1f

    val sunPulse = if (transition != null) {
        val s by transition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "sunPulse"
        )
        s
    } else 1f

    val sunsetBubbles = remember {
        listOf(
            BubbleGlass.bubbleCoral,
            BubbleGlass.bubblePeach,
            BubbleGlass.bubbleMagenta,
            BubbleGlass.bubbleGold,
            primary.copy(alpha = 0.28f),
            secondary.copy(alpha = 0.24f)
        )
    }

    val bubbles = remember {
        List(6) { i ->
            GlassBubble(
                seed = Random(i * 17 + 3).nextFloat(),
                orbitX = 0.12f + Random(i).nextFloat() * 0.28f,
                orbitY = 0.10f + Random(i).nextFloat() * 0.25f,
                radiusFrac = 0.12f + Random(i).nextFloat() * 0.14f,
                speed = 0.30f + Random(i).nextFloat() * 0.65f,
                hue = i % sunsetBubbles.size
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BubbleGlass.sunsetSkyBrush())
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Horizon rose haze
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BubbleGlass.sunHaze.copy(alpha = 0.55f * baseAlpha),
                        BubbleGlass.skyMid.copy(alpha = 0.18f * baseAlpha),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.92f),
                    radius = w * 0.85f
                ),
                radius = w * 0.85f,
                center = Offset(w * 0.5f, h * 0.92f)
            )

            // Setting sun — gentle pulse
            val sunCenter = Offset(
                x = w * (0.72f + 0.02f * sin(phase * 6.28f)),
                y = h * (0.58f + 0.015f * cos(phase * 6.28f))
            )
            val sunRadius = w * 0.19f * sunPulse * breathe

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BubbleGlass.sunGlow.copy(alpha = 0.42f * baseAlpha),
                        BubbleGlass.sunGlow.copy(alpha = 0.14f * baseAlpha),
                        Color.Transparent
                    ),
                    center = sunCenter,
                    radius = sunRadius * 2.4f
                ),
                radius = sunRadius * 2.4f,
                center = sunCenter
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BubbleGlass.sunCore.copy(alpha = 0.95f * baseAlpha),
                        BubbleGlass.sunGlow.copy(alpha = 0.75f * baseAlpha),
                        BubbleGlass.skyMid.copy(alpha = 0.35f * baseAlpha)
                    ),
                    center = sunCenter - Offset(sunRadius * 0.08f, sunRadius * 0.12f),
                    radius = sunRadius
                ),
                radius = sunRadius,
                center = sunCenter
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.55f * baseAlpha),
                radius = sunRadius * 0.14f,
                center = sunCenter - Offset(sunRadius * 0.28f, sunRadius * 0.32f)
            )

            // Twilight cloud wash (upper sky)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BubbleGlass.skyTop.copy(alpha = 0.35f * baseAlpha),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.28f, h * 0.06f),
                    radius = w * 0.45f
                ),
                radius = w * 0.45f,
                center = Offset(w * 0.28f, h * 0.06f)
            )

            bubbles.forEach { bubble ->
                val angle = (phase * bubble.speed + bubble.seed) * 6.28f * 2f
                val cx = w * (0.5f + bubble.orbitX * cos(angle + bubble.seed * 6.28f))
                val cy = h * (0.40f + bubble.orbitY * sin(angle * 0.85f + bubble.seed * 4f))
                val radius = w * bubble.radiusFrac * breathe
                val tint = when (bubble.hue) {
                    0 -> BubbleGlass.bubbleCoral
                    1 -> BubbleGlass.bubblePeach
                    2 -> BubbleGlass.bubbleMagenta
                    3 -> BubbleGlass.bubbleGold
                    4 -> primary
                    else -> tertiary
                }
                drawGlassBubble(center = Offset(cx, cy), radius = radius, tint = tint, alpha = baseAlpha)
            }

            drawGlassBubble(
                center = Offset(
                    w * (0.14f + 0.04f * sin(phase * 6.28f)),
                    h * (0.74f + 0.03f * cos(phase * 6.28f))
                ),
                radius = w * 0.16f * breathe,
                tint = BubbleGlass.bubblePeach,
                alpha = baseAlpha
            )
            drawGlassBubble(
                center = Offset(
                    w * (0.38f - 0.03f * cos(phase * 6.28f)),
                    h * (0.82f + 0.02f * sin(phase * 6.28f))
                ),
                radius = w * 0.12f * breathe,
                tint = BubbleGlass.bubbleMagenta,
                alpha = baseAlpha * 0.85f
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
                Color.White.copy(alpha = 0.50f * alpha),
                tint.copy(alpha = tint.alpha.coerceIn(0f, 1f) * alpha),
                tint.copy(alpha = tint.alpha * 0.35f * alpha),
                Color.Transparent
            ),
            center = center - Offset(radius * 0.22f, radius * 0.28f),
            radius = radius
        ),
        radius = radius,
        center = center
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.38f * alpha),
        radius = radius * 0.16f,
        center = center - Offset(radius * 0.30f, radius * 0.34f)
    )
}