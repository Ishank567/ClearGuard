package com.clearguard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Clean, fresh color definitions for the new UI.
 */
object AppColors {
    val primary = Color(0xFF0F766E)
    val primaryContainer = Color(0xFFCCFBF1)
    val secondary = Color(0xFF0369A1)
    val success = Color(0xFF059669)
    val warning = Color(0xFFD97706)
    val danger = Color(0xFFDC2626)
    val mutedLight = Color(0xFF64748B)
    val mutedDark = Color(0xFF94A3B8)
}

/**
 * Compatibility shim so the large amount of legacy UI code continues to compile.
 * These now point to the fresh classy palette. New screens use MaterialTheme directly.
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
    val glass = Color(0xFFFFFFFF)
    val glassBorder = Color(0xFFE2E8F0)
    val glassHighlight = Color.White.copy(alpha = 0.08f)
    val glassShadow = Color.Black.copy(alpha = 0.12f)
    val glassDark = Color(0xFF1E2937)
    val useGlass = false
    val panel = Color(0xFFFFFFFF)
}
