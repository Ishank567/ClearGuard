package com.clearguard.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Bubble-glass light theme — airy gradient backdrop, frosted translucent surfaces.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF1E40AF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF001D36),
    tertiary = Color(0xFF0891B2),
    onTertiary = Color.White,
    background = BubbleGlass.bgMid,
    onBackground = Color(0xFF0F172A),
    surface = BubbleGlass.surfaceTop,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xCCFFFFFF),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0x66FFFFFF),
    outlineVariant = Color(0x330F766E),
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun ShieldDNSTheme(content: @Composable () -> Unit) {
    val colorScheme = LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            if (Build.VERSION.SDK_INT < 35) {
                @Suppress("DEPRECATION")
                window.statusBarColor = BubbleGlass.bgTop.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = BubbleGlass.bgBottom.toArgb()
            }
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

@Composable
fun ClearGuardTheme(content: @Composable () -> Unit) = ShieldDNSTheme(content = content)