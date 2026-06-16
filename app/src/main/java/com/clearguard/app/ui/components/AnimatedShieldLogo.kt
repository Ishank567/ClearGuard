package com.clearguard.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clearguard.app.R

/**
 * The ShieldDNS mark presented with realistic, physics-flavoured motion:
 *  - a soft radial glow that breathes,
 *  - a slow conic "radar sweep" ring (only while [active]),
 *  - the logo itself gently breathing and floating for depth.
 *
 * Lightweight: a handful of looping float animations + one Canvas, no bitmaps allocated per frame.
 * When [active] is false (protection paused) the sweep stops and the glow dims, so the animation
 * communicates real state instead of being decoration.
 */
@Composable
fun AnimatedShieldLogo(
    modifier: Modifier = Modifier,
    diameter: Dp = 104.dp,
    accent: Color = MaterialTheme.colorScheme.primary,
    active: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "shieldLogo")

    val breathe by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val glowPulse by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    val float by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float"
    )

    // Smoothly fade the glow toward a dim resting level when protection is paused.
    val glow by animateFloatAsState(
        targetValue = if (active) glowPulse else 0.12f,
        animationSpec = tween(600),
        label = "glowState"
    )

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Soft radial glow halo.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = glow), accent.copy(alpha = 0f)),
                    center = center,
                    radius = r
                ),
                radius = r,
                center = center
            )

            // Rotating radar sweep ring — only while active.
            if (active) {
                val ringR = r * 0.82f
                rotate(degrees = sweep, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                accent.copy(alpha = 0f),
                                accent.copy(alpha = 0f),
                                accent.copy(alpha = 0.18f),
                                accent.copy(alpha = 0.55f)
                            ),
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(center.x - ringR, center.y - ringR),
                        size = Size(ringR * 2f, ringR * 2f),
                        style = Stroke(width = r * 0.05f, cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Vector launcher mark — scales cleanly and avoids large bitmap decode failures on low-RAM devices.
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "ShieldDNS",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize(0.64f)
                .graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
                    translationY = float
                }
        )
    }
}
