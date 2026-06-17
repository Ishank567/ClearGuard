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

// Sunset bubble-glass theme — golden-hour backdrop, warm frosted surfaces.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFC2410C),           // burnt orange
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = Color(0xFF431407),
    secondary = Color(0xFFBE185D),         // sunset rose
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE7F3),
    onSecondaryContainer = Color(0xFF500724),
    tertiary = Color(0xFFD97706),          // amber gold
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    background = BubbleGlass.skyMid,
    onBackground = Color(0xFF431407),
    surface = BubbleGlass.surfaceTop,
    onSurface = Color(0xFF431407),
    surfaceVariant = Color(0xCCFFF7ED),
    onSurfaceVariant = Color(0xFF78350F),
    outline = Color(0x66FFFFFF),
    outlineVariant = Color(0x33C2410C),
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