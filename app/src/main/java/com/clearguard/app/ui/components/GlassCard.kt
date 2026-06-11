package com.clearguard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clearguard.app.ui.theme.ClearColors

/**
 * Premium 3D Glassmorphic Card
 * - Frosted glass surface with transparency
 * - Crisp edge highlight (glass refraction)
 * - Strong drop shadow for floating 3D depth
 * - Bevel / specular highlight gradient for realistic 3D glass feel
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    glassAlpha: Float = 0.86f,
    elevation: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            // Main frosted glass layer
            .background(
                color = ClearColors.glass.copy(alpha = glassAlpha),
                shape = shape
            )
            // Glass edge / refraction border
            .border(
                width = 1.25.dp,
                color = ClearColors.glassBorder,
                shape = shape
            )
            // 3D floating shadow (ambient + spot for depth)
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = ClearColors.glassShadow,
                spotColor = Color.Black.copy(alpha = 0.22f)
            )
    ) {
        // Inner 3D bevel / specular highlight layer (top shine + bottom soft shadow)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        0f to ClearColors.glassHighlight,
                        0.18f to Color.White.copy(alpha = 0.08f),
                        0.42f to Color.Transparent,
                        0.78f to Color.Black.copy(alpha = 0.035f),
                        1f to Color.Black.copy(alpha = 0.07f)
                    )
                )
        )

        // Actual content
        Box(
            modifier = Modifier.matchParentSize(),
            content = content
        )
    }
}

/**
 * Slightly more "pressed" or lower elevation glass variant for lists / dense content.
 */
@Composable
fun GlassCardCompact(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        glassAlpha = 0.78f,
        elevation = 8.dp,
        content = content
    )
}

/**
 * Prominent 3D glass surface (used for hero toggle, big stats).
 */
@Composable
fun GlassCardHero(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 32.dp,
        glassAlpha = 0.90f,
        elevation = 26.dp,
        content = content
    )
}