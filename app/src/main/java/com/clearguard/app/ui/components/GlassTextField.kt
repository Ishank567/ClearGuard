package com.clearguard.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.ui.theme.ClearColors

/**
 * Frosted-glass text field that matches the ClearGuard liquid-glass system instead
 * of the default Material outline. The edge highlight warms to the accent color on
 * focus, mirroring [GlassCard] / [LiquidGlassButton]. Multiline-friendly.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    accent: Color = ClearColors.green,
    minHeight: Dp = 56.dp,
    singleLine: Boolean = false,
    cornerRadius: Dp = 16.dp,
    textStyle: TextStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = ClearColors.text),
    trailing: (@Composable BoxScope.() -> Unit)? = null
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = if (focused) accent.copy(alpha = 0.85f) else ClearColors.glassBorder.copy(alpha = 0.55f),
        animationSpec = tween(180),
        label = "glassFieldBorder"
    )
    val fill = if (ClearColors.useGlass) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.18f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(fill, shape)
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                brush = Brush.verticalGradient(
                    0f to borderColor,
                    1f to borderColor.copy(alpha = borderColor.alpha * 0.5f)
                ),
                shape = shape
            )
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            textStyle = textStyle,
            singleLine = singleLine,
            cursorBrush = SolidColor(accent),
            interactionSource = interaction,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle.copy(color = ClearColors.muted, fontWeight = FontWeight.Normal)
                        )
                    }
                    inner()
                }
            }
        )
        if (trailing != null) trailing()
    }
}
