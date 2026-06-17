package com.clearguard.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Brand palette used by legacy [ColorScheme] extension accessors.
 * New screens should prefer MaterialTheme.colorScheme directly.
 */
object ClearColors {
    val green = Color(0xFFC2410C)
    val blue = Color(0xFFBE185D)
    val text = Color(0xFF431407)
    val muted = Color(0xFF78350F)
    val border = Color(0xFFFDBA74)
    val bg = BubbleGlass.skyMid
    val danger = Color(0xFFDC2626)
    val warning = Color(0xFFD97706)
    val success = Color(0xFF059669)
    val panel = BubbleGlass.surfaceTop
    val glass = BubbleGlass.surfaceTop
    val glassBorder = BubbleGlass.borderHighlight
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