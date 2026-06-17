package com.clearguard.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clearguard.app.ui.theme.BubbleGlass
import com.clearguard.app.ui.theme.bubbleGlass

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = BubbleGlass.cardRadius,
    elevation: Dp = 6.dp,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.bubbleGlass(
            shape = RoundedCornerShape(cornerRadius),
            tint = tint,
            elevation = elevation
        ),
        content = content
    )
}

@Composable
fun GlassCardCompact(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) = GlassCard(modifier = modifier, cornerRadius = BubbleGlass.compactRadius, content = content)

@Composable
fun GlassCardInteractive(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = BubbleGlass.cardRadius,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val tint = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .scale(if (isPressed) 0.98f else 1f)
            .press3D(isPressed, pressedScale = 1f, tiltDegrees = 4f)
            .bubbleGlass(
                shape = RoundedCornerShape(cornerRadius),
                tint = tint,
                elevation = if (isPressed) 2.dp else 8.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        content = content
    )
}

@Composable
fun AppSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}