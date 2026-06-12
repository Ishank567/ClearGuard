package com.clearguard.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Non-color design tokens for the ClearGuard "liquid glass" system: corner radii,
 * glass opacities, elevations, motion durations, and the spacing scale.
 *
 * Components read their defaults from here, so adjusting a value in this file
 * restyles the whole app consistently. Each token is documented in
 * docs/UI-CONFIGURATION.md together with where it is used.
 */
object ClearDesign {
    // --- Corner radii ---
    /** Standard GlassCard corner. */
    val cardCorner = 28.dp
    /** Dense list / compact card corner. */
    val compactCorner = 22.dp
    /** Hero surfaces (big toggle, headline stats). */
    val heroCorner = 36.dp
    /** LiquidGlassButton corner. */
    val buttonCorner = 22.dp
    /** Floating bottom navigation corner. */
    val navCorner = 28.dp

    // --- Glass opacity (0f transparent .. 1f opaque) ---
    /** Standard card frost. */
    const val cardGlassAlpha = 0.75f
    /** Compact card frost (slightly more see-through for dense lists). */
    const val compactGlassAlpha = 0.68f
    /** Hero card frost (most opaque, carries the most content). */
    const val heroGlassAlpha = 0.80f

    // --- Elevation / depth ---
    val cardElevation = 18.dp
    val compactElevation = 8.dp
    val heroElevation = 26.dp
    val navElevation = 14.dp
    /** Button resting / pressed elevations. */
    val buttonElevation = 12.dp
    val buttonPressedElevation = 4.dp

    // --- Motion (milliseconds) ---
    /** Screen-to-screen crossfade in/out. */
    const val screenFadeInMs = 220
    const val screenFadeOutMs = 180
    /** Press feedback (scale, glow, elevation) on buttons and nav items. */
    const val pressFeedbackMs = 150
    /** Pressed button scale. */
    const val buttonPressedScale = 0.955f
    /** Selected bottom-nav icon scale. */
    const val navSelectedScale = 1.08f

    // --- Spacing scale ---
    /** Horizontal screen margin. */
    val screenHPadding = 20.dp
    /** Vertical gap between stacked cards. */
    val cardSpacing = 16.dp
    /** Inner padding of card content. */
    val cardPadding = 18.dp
}
