package com.meditation.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Night = Color(0xFF0E1116)
private val Surface = Color(0xFF161B22)
private val SurfaceHi = Color(0xFF1E2530)
private val Accent = Color(0xFF8AB4C8)
private val AccentDim = Color(0xFF5C7A8A)
private val OnDark = Color(0xFFDCE7ED)
private val OnDim = Color(0xFF9AA7B0)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Night,
    secondary = AccentDim,
    background = Night,
    onBackground = OnDark,
    surface = Surface,
    onSurface = OnDark,
    surfaceVariant = SurfaceHi,
    onSurfaceVariant = OnDim,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3C6478),
    background = Color(0xFFF3F5F7),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun MeditationTheme(
    forceDark: Boolean = true, // the app's meditative aesthetic is dark-first
    content: @Composable () -> Unit,
) {
    val scheme = if (forceDark || isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        content = content,
    )
}
