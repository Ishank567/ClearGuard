package com.clearguard.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Soft animated mesh backdrop: drifting teal blobs + tiny "DNS node" particles.
 * Pure Canvas — no bitmaps, fixed particle count, safe on low-RAM devices.
 */
@Composable
fun ShieldMeshBackground(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    secondary: Color = MaterialTheme.colorScheme.secondary,
    active: Boolean = true,
    animate: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    if (!animate) {
        Box(modifier = modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawMeshOrb(
                    center = Offset(w * 0.15f, h * 0.12f),
                    radius = w * 0.42f,
                    color = accent.copy(alpha = 0.10f)
                )
                drawMeshOrb(
                    center = Offset(w * 0.88f, h * 0.22f),
                    radius = w * 0.34f,
                    color = secondary.copy(alpha = 0.08f)
                )
            }
            content()
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "meshBg")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )

    val particles = remember {
        List(8) {
            Particle(
                seed = Random(it * 31 + 7).nextFloat(),
                orbit = 0.18f + Random(it).nextFloat() * 0.32f,
                speed = 0.4f + Random(it).nextFloat() * 0.9f,
                size = 2f + Random(it).nextFloat() * 3.5f
            )
        }
    }

    val baseAlpha = if (active) 1f else 0.55f

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawMeshOrb(
                center = Offset(w * (0.15f + 0.08f * sin(phase * 6.28f)), h * (0.12f + 0.05f * cos(phase * 6.28f))),
                radius = w * 0.42f * breathe,
                color = accent.copy(alpha = 0.10f * baseAlpha)
            )
            drawMeshOrb(
                center = Offset(w * (0.88f - 0.06f * cos(phase * 6.28f)), h * (0.22f + 0.07f * sin(phase * 6.28f))),
                radius = w * 0.34f * breathe,
                color = secondary.copy(alpha = 0.08f * baseAlpha)
            )
            drawMeshOrb(
                center = Offset(w * 0.5f, h * (0.92f - 0.04f * sin(phase * 6.28f * 0.7f))),
                radius = w * 0.5f,
                color = accent.copy(alpha = 0.05f * baseAlpha)
            )

            particles.forEach { p ->
                val angle = (phase * p.speed + p.seed) * 6.28f * 2f
                val cx = w * (0.5f + p.orbit * cos(angle + p.seed * 6.28f))
                val cy = h * (0.38f + p.orbit * 0.85f * sin(angle * 0.9f + p.seed * 3.14f))
                drawCircle(
                    color = accent.copy(alpha = (0.12f + 0.18f * p.seed) * baseAlpha),
                    radius = p.size,
                    center = Offset(cx, cy)
                )
                if (active && p.seed > 0.55f) {
                    drawCircle(
                        color = accent.copy(alpha = 0.06f * baseAlpha),
                        radius = p.size * 2.8f,
                        center = Offset(cx, cy)
                    )
                }
            }

        }
        content()
    }
}

private data class Particle(val seed: Float, val orbit: Float, val speed: Float, val size: Float)

private fun DrawScope.drawMeshOrb(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}