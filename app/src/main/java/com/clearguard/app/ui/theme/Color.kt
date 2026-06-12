package com.clearguard.app.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * Every color token used by the ClearGuard design system. A palette is one complete
 * look; the active palette is swapped by [ClearGuardTheme] based on the theme mode.
 *
 * Token roles are documented in docs/UI-CONFIGURATION.md.
 */
data class ClearPalette(
    /** Window / screen background behind all cards. */
    val bg: Color,
    /** Opaque surface, used as the Material surface color. */
    val panel: Color,
    /** Primary text. */
    val text: Color,
    /** Secondary text: captions, descriptions, inactive icons. */
    val muted: Color,
    /** Brand accent: protection state, buttons, selected items. */
    val green: Color,
    /** Secondary accent: informational stats. */
    val blue: Color,
    /** Hairline dividers and outlines. */
    val border: Color,
    /** Frosted glass base for GlassCard surfaces (alpha applied per card). */
    val glass: Color,
    /** Edge highlight stroke around glass surfaces. */
    val glassBorder: Color,
    /** Specular top shine inside glass surfaces. */
    val glassHighlight: Color,
    /** Ambient shadow color under glass surfaces. */
    val glassShadow: Color,
    /** Positive state (defaults to the green accent). */
    val success: Color,
    /** Errors, destructive actions, "paused" indicator. */
    val danger: Color,
    /** Warnings and medium threat scores. */
    val warning: Color,
    /** Dark translucent glass for contrast elements. */
    val glassDark: Color
)

object ClearPalettes {
    /** The original light "liquid glass" look. */
    val light = ClearPalette(
        bg = Color(0xFFF0F3F6),
        panel = Color(0xFFFFFFFF),
        text = Color(0xFF0F172A),
        muted = Color(0xFF64748B),
        green = Color(0xFF0D9488),
        blue = Color(0xFF0EA5E9),
        border = Color(0xFFE2E8F0),
        glass = Color(0xFFFFFFFF).copy(alpha = 0.85f),
        glassBorder = Color.White.copy(alpha = 0.60f),
        glassHighlight = Color.White.copy(alpha = 0.40f),
        glassShadow = Color(0xFF0F172A).copy(alpha = 0.05f),
        success = Color(0xFF0D9488),
        danger = Color(0xFFF43F5E),
        warning = Color(0xFFF59E0B),
        glassDark = Color(0xFF0F172A).copy(alpha = 0.70f)
    )

    /** Deep navy dark variant; accents brightened to keep contrast on dark glass. */
    val dark = ClearPalette(
        bg = Color(0xFF030712),
        panel = Color(0xFF090D16),
        text = Color(0xFFF3F4F6),
        muted = Color(0xFF9CA3AF),
        green = Color(0xFF10B981),
        blue = Color(0xFF06B6D4),
        border = Color(0xFF1F2937),
        glass = Color(0xFF0B132B).copy(alpha = 0.65f),
        glassBorder = Color.White.copy(alpha = 0.08f),
        glassHighlight = Color.White.copy(alpha = 0.05f),
        glassShadow = Color.Black.copy(alpha = 0.60f),
        success = Color(0xFF10B981),
        danger = Color(0xFFEF4444),
        warning = Color(0xFFF59E0B),
        glassDark = Color(0xFF030712).copy(alpha = 0.85f)
    )
}

/**
 * Theme-aware accessor used throughout the app as `ClearColors.bg`, `ClearColors.text`,
 * etc. The backing palette is Compose state, so composables that read these values
 * recompose automatically when the theme mode changes.
 */
object ClearColors {
    private val palette = mutableStateOf(ClearPalettes.light)

    internal fun update(next: ClearPalette) {
        if (palette.value !== next) {
            palette.value = next
        }
    }

    val bg: Color get() = palette.value.bg
    val panel: Color get() = palette.value.panel
    val text: Color get() = palette.value.text
    val muted: Color get() = palette.value.muted
    val green: Color get() = palette.value.green
    val blue: Color get() = palette.value.blue
    val border: Color get() = palette.value.border
    val glass: Color get() = palette.value.glass
    val glassBorder: Color get() = palette.value.glassBorder
    val glassHighlight: Color get() = palette.value.glassHighlight
    val glassShadow: Color get() = palette.value.glassShadow
    val success: Color get() = palette.value.success
    val danger: Color get() = palette.value.danger
    val warning: Color get() = palette.value.warning
    val glassDark: Color get() = palette.value.glassDark
}
