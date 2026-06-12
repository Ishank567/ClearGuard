package com.clearguard.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
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
    onPrimary = Color(0xFF06281A),
    secondary = ClearPalettes.dark.blue,
    onSecondary = Color(0xFF0A1A3A),
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
        typography = Typography(),
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

        // Draw blurry soft mesh neon gradients
        if (darkTheme) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ClearColors.green.copy(alpha = 0.16f), Color.Transparent),
                    radius = w * 0.70f
                ),
                center = Offset(w * (xOffset1 / 100f), h * (yOffset1 / 100f)),
                radius = w * 0.70f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ClearColors.blue.copy(alpha = 0.14f), Color.Transparent),
                    radius = w * 0.80f
                ),
                center = Offset(w * (xOffset2 / 100f), h * (yOffset2 / 100f)),
                radius = w * 0.80f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.12f), Color.Transparent),
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
