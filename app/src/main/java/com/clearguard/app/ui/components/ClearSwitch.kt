package com.clearguard.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.clearguard.app.ui.theme.ClearColors

@Composable
fun ClearSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val activeTrackColor = ClearColors.green
    val inactiveTrackColor = ClearColors.border.copy(alpha = 0.65f)
    
    val trackColor by animateColorAsState(
        targetValue = if (checked) activeTrackColor else inactiveTrackColor,
        animationSpec = tween(220),
        label = "trackColor"
    )
    
    val width = 50.dp
    val height = 30.dp
    val thumbSize = 24.dp
    val padding = 3.dp
    
    val targetOffset = if (checked) (width - thumbSize - padding) else padding
    val animatedOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 450f),
        label = "thumbOffset"
    )
    
    val thumbWidthMultiplier = if (isPressed) 1.25f else 1.0f
    val animatedThumbWidth by animateDpAsState(
        targetValue = thumbSize * thumbWidthMultiplier,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 500f),
        label = "thumbWidth"
    )
    
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColor)
            .border(
                width = 1.dp,
                color = if (checked) ClearColors.green.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(15.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = padding)
                .offset(x = if (checked && isPressed) animatedOffset - (thumbSize * 0.25f) else animatedOffset)
                .size(width = animatedThumbWidth, height = thumbSize)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.22f)
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(Color.White, Color(0xFFF1F5F9))
                    )
                )
                .border(
                    width = 0.5.dp,
                    color = Color.Black.copy(alpha = 0.08f),
                    shape = CircleShape
                )
        )
    }
}
