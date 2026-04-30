package com.inknironapps.bagger.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary       = InkTeal,
    onPrimary     = InkPaper,
    secondary     = InkTealBright,
    background    = InkBackground,
    onBackground  = InkPaper,
    surface       = InkBackground,
    onSurface     = InkPaper,
    surfaceVariant = Color(0xFF1A1F26),
    onSurfaceVariant = InkMuted
)

private val LightColors = lightColorScheme(
    primary       = InkTeal,
    onPrimary     = InkPaper,
    secondary     = InkTealBright,
    background    = InkPaper,
    onBackground  = InkBackground,
    surface       = InkPaper,
    onSurface     = InkBackground,
    surfaceVariant = Color(0xFFD7D2C5),
    onSurfaceVariant = Color(0xFF4B5563)
)

@Composable
fun BaggerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = BaggerTypography, content = content)
}
