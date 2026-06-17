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

    val surfaceTop = Color(0xE8FFF7ED)      // warm frosted white
    val surfaceBottom = Color(0xB8FFFBEB)     // peach-tinted glass
    val borderHighlight = Color(0xCCFFFFFF)
    val borderShadow = Color(0x33C2410C)
    val innerGlow = Color(0x1AFB923C)
    val shadowColor = Color(0x26451A03)

    // Sunset sky gradient — twilight lavender top → coral mid → golden horizon
    val skyTop = Color(0xFFC4B5FD)       // soft periwinkle
    val skyUpper = Color(0xFFF9A8D4)     // pink blush
    val skyMid = Color(0xFFFB923C)       // warm orange
    val skyLower = Color(0xFFFDBA74)     // peach amber
    val skyBottom = Color(0xFFFED7AA)    // golden haze
    val sunCore = Color(0xFFFEF08A)      // pale gold
    val sunGlow = Color(0xFFFB923C)      // orange halo
    val sunHaze = Color(0xFFFECACA)      // rose mist

    val bgTop = skyTop
    val bgMid = skyMid
    val bgBottom = skyBottom

    val bubbleCoral = Color(0x55FB7185)
    val bubblePeach = Color(0x50FDBA74)
    val bubbleMagenta = Color(0x45F472B6)
    val bubbleGold = Color(0x48FDE68A)
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

    fun sunsetSkyBrush(): Brush =
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to skyTop,
                0.22f to skyUpper,
                0.48f to skyMid,
                0.72f to skyLower,
                1.00f to skyBottom
            )
        )

    @Composable
    fun backgroundBrush(): Brush = sunsetSkyBrush()

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