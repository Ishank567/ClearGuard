package com.clearguard.app.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** @deprecated Use [BubbleGlassBackground] — kept for compatibility. */
@Composable
fun ShieldMeshBackground(
    modifier: Modifier = Modifier,
    accent: Color = Color.Unspecified,
    secondary: Color = Color.Unspecified,
    active: Boolean = true,
    animate: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) = BubbleGlassBackground(
    modifier = modifier,
    active = active,
    animate = animate,
    content = content
)