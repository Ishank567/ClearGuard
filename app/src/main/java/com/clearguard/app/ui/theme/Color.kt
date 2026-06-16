package com.clearguard.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Brand palette used by legacy [ColorScheme] extension accessors.
 * New screens should prefer MaterialTheme.colorScheme directly.
 */
object ClearColors {
    val green = Color(0xFF0F766E)
    val blue = Color(0xFF0369A1)
    val text = Color(0xFF0F172A)
    val muted = Color(0xFF475569)
    val border = Color(0xFFCBD5E1)
    val bg = Color(0xFFF8FAFC)
    val danger = Color(0xFFDC2626)
    val warning = Color(0xFFD97706)
    val success = Color(0xFF059669)
    val panel = Color(0xFFFFFFFF)
}

val ColorScheme.green: Color get() = ClearColors.green
val ColorScheme.blue: Color get() = ClearColors.blue
val ColorScheme.text: Color get() = ClearColors.text
val ColorScheme.muted: Color get() = ClearColors.muted
val ColorScheme.danger: Color get() = ClearColors.danger
val ColorScheme.warning: Color get() = ClearColors.warning
val ColorScheme.success: Color get() = ClearColors.success
val ColorScheme.border: Color get() = ClearColors.border
val ColorScheme.panel: Color get() = ClearColors.panel
val ColorScheme.bg: Color get() = ClearColors.bg