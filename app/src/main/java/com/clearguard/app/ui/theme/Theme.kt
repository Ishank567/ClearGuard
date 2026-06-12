package com.clearguard.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier

/** User-selectable appearance, persisted under PreferenceKeys.KEY_THEME_MODE. */
enum class ThemeMode(val prefValue: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromPref(value: String?): ThemeMode =
            entries.firstOrNull { it.prefValue == value } ?: System
    }
}

private val LightColorScheme = lightColorScheme(
    primary = ClearPalettes.light.green,
    onPrimary = Color.White,
    secondary = ClearPalettes.light.blue,
    onSecondary = Color.White,
    background = ClearPalettes.light.bg,
    onBackground = ClearPalettes.light.text,
    surface = ClearPalettes.light.panel,
    onSurface = ClearPalettes.light.text,
    surfaceVariant = ClearPalettes.light.glass,
    onSurfaceVariant = ClearPalettes.light.muted,
    outline = ClearPalettes.light.border,
    error = ClearPalettes.light.danger
)

private val DarkColorScheme = darkColorScheme(
    primary = ClearPalettes.dark.green,
    onPrimary = Color(0xFF0C0F14),
    secondary = ClearPalettes.dark.blue,
    onSecondary = Color(0xFF0C0F14),
    background = ClearPalettes.dark.bg,
    onBackground = ClearPalettes.dark.text,
    surface = ClearPalettes.dark.panel,
    onSurface = ClearPalettes.dark.text,
    surfaceVariant = ClearPalettes.dark.glass,
    onSurfaceVariant = ClearPalettes.dark.muted,
    outline = ClearPalettes.dark.border,
    error = ClearPalettes.dark.danger
)

@Composable
fun ClearGuardTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val palette = if (darkTheme) ClearPalettes.dark else ClearPalettes.light
    // Swap the palette before children compose so every ClearColors read this pass
    // already sees the right values.
    ClearColors.update(palette)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            if (Build.VERSION.SDK_INT < 35) {
                // Android 15+ enforces edge-to-edge and ignores these; older versions
                // still need the bars tinted to match the app background.
                @Suppress("DEPRECATION")
                window.statusBarColor = palette.bg.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = palette.bg.toArgb()
            }
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = ClearTypography,
        content = content
    )
}

@Composable
fun ClearMeshBackground(darkTheme: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    val xOffset1 by infiniteTransition.animateFloat(
        initialValue = -40f, targetValue = 140f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x1"
    )
    val yOffset1 by infiniteTransition.animateFloat(
        initialValue = -30f, targetValue = 130f,
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y1"
    )
    val xOffset2 by infiniteTransition.animateFloat(
        initialValue = 130f, targetValue = -30f,
        animationSpec = infiniteRepeatable(
            animation = tween(32000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x2"
    )
    val yOffset2 by infiniteTransition.animateFloat(
        initialValue = 90f, targetValue = -20f,
        animationSpec = infiniteRepeatable(
            animation = tween(26000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y2"
    )
    val xOffset3 by infiniteTransition.animateFloat(
        initialValue = -10f, targetValue = 110f,
        animationSpec = infiniteRepeatable(
            animation = tween(36000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x3"
    )
    val yOffset3 by infiniteTransition.animateFloat(
        initialValue = 120f, targetValue = -40f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y3"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Draw deep base background color
        drawRect(color = ClearColors.bg)

        // === Futuristic cyber 3D depth matching reference: hex grid + particles + network lines ===
        if (darkTheme) {
            val hexColor = Color(0xFF0EA5E9).copy(alpha = 0.06f) // cyan-ish like reference
            val particleColor = ClearColors.green.copy(alpha = 0.12f)

            // Subtle glowing hexagons (futuristic tech feel)
            for (hx in 0 until 5) {
                for (hy in 0 until 4) {
                    val hxPos = w * (0.1f + hx * 0.2f + (if (hy % 2 == 0) 0.1f else 0f))
                    val hyPos = h * (0.15f + hy * 0.22f)
                    val hexSize = 38f + (hx + hy) % 3 * 6f

                    // Simple hexagon using 6 lines
                    val points = (0 until 6).map { i ->
                        val ang = (i * 60f - 30f) * (Math.PI / 180f)
                        Offset(
                            (hxPos + hexSize * cos(ang)).toFloat(),
                            (hyPos + hexSize * sin(ang) * 0.9f).toFloat()
                        )
                    }
                    for (i in 0 until 6) {
                        val p1 = points[i]
                        val p2 = points[(i + 1) % 6]
                        drawLine(color = hexColor, start = p1, end = p2, strokeWidth = 1.2f)
                    }
                }
            }

            // Extra small glowing particles / nodes (like the reference background)
            val particleCount = 18
            for (i in 0 until particleCount) {
                val px = w * ((i * 0.37f + (i % 5) * 0.11f) % 1f)
                val py = h * (0.1f + (i * 0.19f) % 0.75f)
                val pr = 1.5f + (i % 3) * 0.8f
                drawCircle(color = particleColor, radius = pr, center = Offset(px, py))
                // faint connection hint
                if (i % 3 == 0) {
                    drawCircle(color = ClearColors.blue.copy(alpha = 0.05f), radius = pr * 2.8f, center = Offset(px, py))
                }
            }
        }

        // Keep the perspective grid as subtle structural lines
        val gridAlpha = if (darkTheme) 0.028f else 0.018f
        val gridColor = if (darkTheme) ClearColors.green else ClearColors.blue
        
        for (i in 1..7) {
            val progress = i / 7f
            val y = h * (0.22f + progress * 0.58f)
            val perspectiveScale = 0.3f + progress * 0.7f
            val leftX = w * (0.5f - 0.42f * perspectiveScale)
            val rightX = w * (0.5f + 0.42f * perspectiveScale)
            drawLine(color = gridColor.copy(alpha = gridAlpha), start = Offset(leftX, y), end = Offset(rightX, y), strokeWidth = 1f)
        }

        // Draw blurry soft mesh neon gradients (original beautiful moving orbs)
        if (darkTheme) {
            // Teal ambient glow — primary brand warmth
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ClearColors.green.copy(alpha = 0.10f), Color.Transparent),
                    radius = w * 0.70f
                ),
                center = Offset(w * (xOffset1 / 100f), h * (yOffset1 / 100f)),
                radius = w * 0.70f
            )
            // Sky blue ambient glow — informational accent
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ClearColors.blue.copy(alpha = 0.08f), Color.Transparent),
                    radius = w * 0.80f
                ),
                center = Offset(w * (xOffset2 / 100f), h * (yOffset2 / 100f)),
                radius = w * 0.80f
            )
            // Deep indigo ambient glow — subtle depth
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF4338CA).copy(alpha = 0.07f), Color.Transparent),
                    radius = w * 0.75f
                ),
                center = Offset(w * (xOffset3 / 100f), h * (yOffset3 / 100f)),
                radius = w * 0.75f
            )
        } else {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ClearColors.green.copy(alpha = 0.08f), Color.Transparent),
                    radius = w * 0.65f
                ),
                center = Offset(w * (xOffset1 / 100f), h * (yOffset1 / 100f)),
                radius = w * 0.65f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ClearColors.blue.copy(alpha = 0.08f), Color.Transparent),
                    radius = w * 0.75f
                ),
                center = Offset(w * (xOffset2 / 100f), h * (yOffset2 / 100f)),
                radius = w * 0.75f
            )
        }
    }
}
