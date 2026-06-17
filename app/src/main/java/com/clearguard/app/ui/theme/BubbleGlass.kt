package com.clearguard.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Frosted bubble-glass surface tokens — light theme only. */
object BubbleGlass {
    val cardRadius = 24.dp
    val compactRadius = 18.dp
    val pillRadius = 999.dp
    val navRadius = 28.dp

    val surfaceTop = Color(0xE6FFFFFF)      // ~90% white
    val surfaceBottom = Color(0xB8FFFFFF)     // ~72% white
    val borderHighlight = Color(0xCCFFFFFF)
    val borderShadow = Color(0x330F766E)
    val innerGlow = Color(0x1A0F766E)
    val shadowColor = Color(0x1A0F172A)

    val bgTop = Color(0xFFE8F4F8)
    val bgMid = Color(0xFFF0F9FF)
    val bgBottom = Color(0xFFEFF6FF)

    val bubbleTeal = Color(0x400F766E)
    val bubbleBlue = Color(0x351E40AF)
    val bubbleMint = Color(0x45CCFBF1)
    val bubbleWhite = Color(0x55FFFFFF)

    @Composable
    fun surfaceBrush(tint: Color = MaterialTheme.colorScheme.primary): Brush =
        Brush.linearGradient(
            colors = listOf(
                surfaceTop,
                surfaceBottom,
                tint.copy(alpha = 0.06f)
            )
        )

    @Composable
    fun borderBrush(tint: Color = MaterialTheme.colorScheme.primary): Brush =
        Brush.linearGradient(
            colors = listOf(
                borderHighlight,
                tint.copy(alpha = 0.18f),
                borderShadow.copy(alpha = 0.12f)
            )
        )

    @Composable
    fun backgroundBrush(): Brush =
        Brush.verticalGradient(
            colors = listOf(bgTop, bgMid, bgBottom)
        )

    @Composable
    fun buttonBrush(accent: Color = MaterialTheme.colorScheme.primary): Brush =
        Brush.linearGradient(
            colors = listOf(
                accent.copy(alpha = 0.92f),
                accent,
                accent.copy(alpha = 0.78f)
            )
        )
}

/**
 * Frosted bubble-glass panel: soft gradient fill, luminous edge, gentle drop shadow.
 */
fun Modifier.bubbleGlass(
    shape: Shape = RoundedCornerShape(BubbleGlass.cardRadius),
    tint: Color = Color.Unspecified,
    elevation: Dp = 6.dp,
    borderWidth: Dp = 1.dp
): Modifier = this.then(
    Modifier
        .shadow(elevation, shape, ambientColor = BubbleGlass.shadowColor, spotColor = BubbleGlass.shadowColor)
        .clip(shape)
        .background(
            if (tint == Color.Unspecified) {
                Brush.linearGradient(
                    colors = listOf(BubbleGlass.surfaceTop, BubbleGlass.surfaceBottom)
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        BubbleGlass.surfaceTop,
                        BubbleGlass.surfaceBottom,
                        tint.copy(alpha = 0.07f)
                    )
                )
            }
        )
        .border(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(
                    BubbleGlass.borderHighlight,
                    if (tint == Color.Unspecified) BubbleGlass.innerGlow else tint.copy(alpha = 0.16f),
                    BubbleGlass.borderShadow.copy(alpha = 0.10f)
                )
            ),
            shape = shape
        )
)