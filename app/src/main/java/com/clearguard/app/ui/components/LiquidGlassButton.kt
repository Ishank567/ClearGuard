package com.clearguard.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clearguard.app.ui.theme.ClearColors
import com.clearguard.app.ui.theme.ClearDesign

/**
 * iOS-style liquid glass button. Shape and motion defaults come from [ClearDesign];
 * the accent color follows the active [ClearColors] palette.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = ClearColors.green,
    contentColor: Color = ClearColors.green,
    cornerRadius: Dp = ClearDesign.buttonCorner,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = enabled && pressed
    val resolvedContentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.55f)

    val scale by animateFloatAsState(
        targetValue = if (active) ClearDesign.buttonPressedScale else 1f,
        animationSpec = tween(ClearDesign.pressFeedbackMs),
        label = "liquidScale"
    )
    val glow by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(ClearDesign.pressFeedbackMs),
        label = "liquidGlow"
    )
    val elevation by animateDpAsState(
        targetValue = if (active) ClearDesign.buttonPressedElevation else ClearDesign.buttonElevation,
        animationSpec = tween(ClearDesign.pressFeedbackMs),
        label = "liquidElevation"
    )

    val bodyAlpha = if (enabled) 1f else 0.5f

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = bodyAlpha
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = if (ClearColors.useGlass) accent.copy(alpha = 0.25f) else ClearColors.green.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = if (ClearColors.useGlass) 0.28f else 0.50f)
            )
            .clip(shape)
            // Frosted, lightly accent-tinted glass body (or solid dark body)
            .background(
                brush = if (ClearColors.useGlass) {
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.55f),
                        0.55f to accent.copy(alpha = 0.16f),
                        1f to accent.copy(alpha = 0.10f)
                    )
                } else {
                    Brush.verticalGradient(
                        0f to accent.copy(alpha = 0.20f),
                        0.50f to ClearColors.panel,
                        1f to ClearColors.bg
                    )
                },
                shape = shape
            )
            // Bright rim that catches light along the top edge (liquid glass refraction or glowing aura)
            .border(
                width = 1.5.dp,
                brush = if (ClearColors.useGlass) {
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.9f),
                        0.5f to Color.White.copy(alpha = 0.35f),
                        1f to accent.copy(alpha = 0.28f)
                    )
                } else {
                    Brush.verticalGradient(
                        0f to ClearColors.green.copy(alpha = 0.60f),
                        0.5f to ClearColors.green.copy(alpha = 0.25f),
                        1f to Color.Transparent
                    )
                },
                shape = shape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        if (ClearColors.useGlass) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.55f + 0.30f * glow),
                            0.35f to Color.White.copy(alpha = 0.0f),
                            0.85f to Color.Black.copy(alpha = 0.02f),
                            1f to Color.Black.copy(alpha = 0.07f)
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(1.dp)
                    .clip(shape)
                    .background(
                        brush = Brush.horizontalGradient(
                            0f to Color.White.copy(alpha = 0.36f),
                            0.38f to Color.Transparent,
                            0.68f to Color.White.copy(alpha = 0.18f),
                            1f to Color.Transparent
                        )
                    )
            )
        } else {
            // Physical 3D button details: top highlight and bottom shadow, responsive to glow / press
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.06f + 0.08f * glow),
                            0.15f to Color.Transparent,
                            0.80f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.35f)
                        )
                    )
            )
        }

        CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                content = content
            )
        }
    }
}

@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = ClearColors.green,
    contentColor: Color = accent,
    size: Dp = 40.dp,
    content: @Composable RowScope.() -> Unit
) {
    LiquidGlassButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        accent = accent,
        contentColor = contentColor,
        cornerRadius = size / 2f,
        contentPadding = PaddingValues(0.dp),
        content = content
    )
}
