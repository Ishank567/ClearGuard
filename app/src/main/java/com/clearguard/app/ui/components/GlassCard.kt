package com.clearguard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clearguard.app.ui.theme.ClearColors
import com.clearguard.app.ui.theme.ClearDesign

/**
 * Premium 3D Glassmorphic Card
 * - Frosted glass surface with transparency
 * - Crisp edge highlight (glass refraction)
 * - Strong drop shadow for floating 3D depth
 * - Bevel / specular highlight gradient for realistic 3D glass feel
 *
 * All defaults come from [ClearDesign]; colors follow the active [ClearColors]
 * palette, so the card adapts to light and dark themes automatically.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = ClearDesign.cardCorner,
    glassAlpha: Float = ClearDesign.cardGlassAlpha,
    elevation: Dp = ClearDesign.cardElevation,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            // 3D floating shadow (ambient + spot for depth); applied before the clip
            // so it renders fully outside the rounded outline.
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = ClearColors.glassShadow,
                spotColor = if (ClearColors.useGlass) Color.Black.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.45f)
            )
            .clip(shape)
            // Main frosted glass layer (or solid dark panel if useGlass is false)
            .background(
                color = if (ClearColors.useGlass) ClearColors.glass.copy(alpha = glassAlpha) else ClearColors.glass,
                shape = shape
            )
            // Glass edge / refraction border (or 3D red aura border)
            .border(
                width = if (ClearColors.useGlass) 1.25.dp else 1.5.dp,
                brush = if (ClearColors.useGlass) {
                    if (ClearColors.glassBorder.alpha > 0.2f) {
                        Brush.verticalGradient(
                            0f to ClearColors.glassBorder,
                            0.4f to ClearColors.glassBorder.copy(alpha = ClearColors.glassBorder.alpha * 0.5f),
                            1f to Color.Black.copy(alpha = 0.03f)
                        )
                    } else {
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.15f),
                            0.5f to ClearColors.green.copy(alpha = 0.18f),
                            1f to Color.Black.copy(alpha = 0.35f)
                        )
                    }
                } else {
                    Brush.verticalGradient(
                        0f to ClearColors.green.copy(alpha = 0.40f),
                        0.25f to ClearColors.green.copy(alpha = 0.10f),
                        0.75f to Color.Transparent,
                        1f to ClearColors.green.copy(alpha = 0.05f)
                    )
                },
                shape = shape
            )
    ) {
        // Inner 3D bevel / specular highlight layer (top shine + bottom soft shadow)
        if (ClearColors.useGlass) {
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
        } else {
            // Physical 3D dark bevel highlights
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.03f),
                            0.10f to Color.Transparent,
                            0.90f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.45f)
                        )
                    )
            )
        }

        // Actual content. Text and icons that do not set an explicit color inherit
        // the palette's text color, so cards stay readable in both themes.
        CompositionLocalProvider(LocalContentColor provides ClearColors.text) {
            Box(content = content)
        }
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
        cornerRadius = ClearDesign.compactCorner,
        glassAlpha = ClearDesign.compactGlassAlpha,
        elevation = ClearDesign.compactElevation,
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
        cornerRadius = ClearDesign.heroCorner,
        glassAlpha = ClearDesign.heroGlassAlpha,
        elevation = ClearDesign.heroElevation,
        content = content
    )
}
