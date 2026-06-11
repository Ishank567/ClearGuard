package com.clearguard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ClearColors.green,
    onPrimary = Color.White,
    secondary = ClearColors.blue,
    onSecondary = Color.White,
    background = ClearColors.bg,
    onBackground = ClearColors.text,
    surface = ClearColors.panel,
    onSurface = ClearColors.text,
    surfaceVariant = ClearColors.glass,
    onSurfaceVariant = ClearColors.muted,
    outline = ClearColors.border
)

@Composable
fun ClearGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // For now we focus on beautiful light glassmorphism.
    // Dark theme can be added later with adjusted glass alphas.
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}