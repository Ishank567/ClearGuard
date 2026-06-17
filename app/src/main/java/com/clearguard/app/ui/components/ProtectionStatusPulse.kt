package com.clearguard.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Live status label with pulsing dot and expanding rings when protection is active.
 */
@Composable
fun ProtectionStatusPulse(
    isProtected: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    animate: Boolean = true
) {
    if (!animate || !isProtected) {
        val label = if (isProtected) "Protected" else "Protection Paused"
        val color = if (isProtected) accent else MaterialTheme.colorScheme.onSurfaceVariant
        val inactiveDot = MaterialTheme.colorScheme.outline
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(10.dp)) {
                    drawCircle(
                        color = if (isProtected) accent else inactiveDot,
                        radius = size.minDimension / 2f
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "statusPulse")
    val ring by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ring"
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ringAlpha"
    )
    val dotPulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot"
    )

    val label = if (isProtected) "Protected" else "Protection Paused"
    val color = if (isProtected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val inactiveDot = MaterialTheme.colorScheme.outline

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (isProtected) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val expand = r * (0.6f + ring * 1.1f)
                    drawCircle(
                        color = accent.copy(alpha = ringAlpha),
                        radius = expand,
                        center = center,
                        style = Stroke(width = 2f)
                    )
                }
            }
            Canvas(
                Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        scaleX = dotPulse
                        scaleY = dotPulse
                    }
            ) {
                drawCircle(
                    color = if (isProtected) accent else inactiveDot,
                    radius = size.minDimension / 2f
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}