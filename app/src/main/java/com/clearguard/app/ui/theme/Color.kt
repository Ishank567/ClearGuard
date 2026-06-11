package com.clearguard.app.ui.theme

import androidx.compose.ui.graphics.Color

// Original palette from colors.xml, extended for glassmorphism & 3D effects
object ClearColors {
    val bg = Color(0xFFF7F8FA)
    val panel = Color(0xFFFFFFFF)
    val text = Color(0xFF17202A)
    val muted = Color(0xFF5E6B78)
    val green = Color(0xFF167A52)
    val blue = Color(0xFF1E5EFF)
    val border = Color(0xFFD9E0E8)

    // Glassmorphism surface (light frosted glass)
    val glass = Color(0xFFF8FAFC)          // very light base
    val glassBorder = Color.White.copy(alpha = 0.45f)

    // 3D depth helpers
    val glassHighlight = Color.White.copy(alpha = 0.32f)
    val glassShadow = Color.Black.copy(alpha = 0.10f)

    // Accent states
    val success = green
    val danger = Color(0xFFE53935)
    val warning = Color(0xFFFFA726)

    // Darker glass variant for contrast elements
    val glassDark = Color(0xFF0F172A).copy(alpha = 0.65f)
}