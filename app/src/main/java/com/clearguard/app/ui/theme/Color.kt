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
    val glassDark: Color,
    /** Whether to use translucent glass layers or solid panels. */
    val useGlass: Boolean
)

object ClearPalettes {
    /** The light "liquid glass" look. Background sits a touch deeper than the white
     *  cards so frosted surfaces read with real separation and float. */
    val light = ClearPalette(
        bg = Color(0xFFE9EEF6),
        panel = Color(0xFFFFFFFF),
        text = Color(0xFF0E1626),
        muted = Color(0xFF566379),
        green = Color(0xFF0EA88F),
        blue = Color(0xFF2BA9E0),
        border = Color(0xFFDCE3EE),
        glass = Color(0xFFFFFFFF).copy(alpha = 0.86f),
        glassBorder = Color.White.copy(alpha = 0.70f),
        glassHighlight = Color.White.copy(alpha = 0.50f),
        glassShadow = Color(0xFF1E293B).copy(alpha = 0.10f),
        success = Color(0xFF0D9488),
        danger = Color(0xFFE11D48),
        warning = Color(0xFFE08A0B),
        glassDark = Color(0xFF0F172A).copy(alpha = 0.70f),
        useGlass = true
    )

    /**
     * Modern dark theme — warm dark navy surfaces with frosted glassmorphism.
     * Inspired by Linear, Arc, Vercel, and iOS 26 liquid glass aesthetics.
     * Uses teal/cyan accents unified with the light theme for brand consistency.
     */
    val dark = ClearPalette(
        bg = Color(0xFF0A0E1A),
        panel = Color(0xFF141B2E),
        text = Color(0xFFF2F6FF),
        muted = Color(0xFF9AA9C6),
        green = Color(0xFF2DD4BF),
        blue = Color(0xFF67E8F9),
        border = Color(0xFF243049),
        glass = Color(0xFFFFFFFF).copy(alpha = 0.09f),
        glassBorder = Color.White.copy(alpha = 0.15f),
        glassHighlight = Color.White.copy(alpha = 0.09f),
        glassShadow = Color(0xFF000000).copy(alpha = 0.45f),
        success = Color(0xFF34D399),
        danger = Color(0xFFFB5071),
        warning = Color(0xFFFBBF24),
        glassDark = Color(0xFF0A0D12).copy(alpha = 0.85f),
        useGlass = true
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
    val useGlass: Boolean get() = palette.value.useGlass
}
