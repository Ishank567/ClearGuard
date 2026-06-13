package com.clearguard.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Minimal clean spacing / shape tokens for the fresh UI.
 */
object AppDesign {
    val screenPadding = 16.dp
    val cardPadding = 16.dp
    val itemSpacing = 12.dp
    val sectionSpacing = 20.dp
}

/**
 * Compatibility shims for the large amount of existing code that referenced
 * the previous custom design system. This lets the project compile while we
 * have a clean Material 3 UI.
 */
object ClearDesign {
    val screenHPadding = 20.dp
    val cardSpacing = 16.dp
    val cardPadding = 16.dp
    val tabCrossfadeMs = 200
    val screenFadeInMs = 220
    val screenFadeOutMs = 180
    val pressFeedbackMs = 150
    val pillSelectedScale = 1.04f
    val navSelectedScale = 1.08f
    val navCorner = 16.dp
    val cardCorner = 16.dp
    val heroCorner = 20.dp
    val buttonCorner = 16.dp
    val cardGlassAlpha = 0.9f
    val compactGlassAlpha = 0.85f
    val heroGlassAlpha = 0.95f
    val cardElevation = 2.dp
    val compactElevation = 1.dp
    val heroElevation = 3.dp
    val navElevation = 0.dp
    val buttonElevation = 2.dp
    val buttonPressedElevation = 0.dp
    val buttonPressedScale = 0.98f
}
