package com.meditation.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Appearance system (brief §16: custom timer faces/themes). A small set of calm palettes, each with
 * a dark-first and a light variant. The active palette + theme mode ("system" | "light" | "dark")
 * come from user preferences; [MeditationTheme] resolves them to a Material 3 [ColorScheme].
 */
data class Palette(
    val key: String,
    val title: String,
    val dark: ColorScheme,
    val light: ColorScheme,
    /** A representative swatch for the settings picker. */
    val swatch: Color,
)

private fun palette(
    key: String,
    title: String,
    night: Color,
    surface: Color,
    surfaceHi: Color,
    accent: Color,
    accentDim: Color,
    onDark: Color,
    onDim: Color,
    lightBg: Color,
    lightPrimary: Color,
) = Palette(
    key = key,
    title = title,
    swatch = accent,
    dark = darkColorScheme(
        primary = accent,
        onPrimary = night,
        secondary = accentDim,
        background = night,
        onBackground = onDark,
        surface = surface,
        onSurface = onDark,
        surfaceVariant = surfaceHi,
        onSurfaceVariant = onDim,
    ),
    light = lightColorScheme(
        primary = lightPrimary,
        background = lightBg,
        surface = Color(0xFFFFFFFF),
    ),
)

object Palettes {
    val Twilight = palette(
        "twilight", "Twilight",
        night = Color(0xFF0E1116), surface = Color(0xFF161B22), surfaceHi = Color(0xFF1E2530),
        accent = Color(0xFF8AB4C8), accentDim = Color(0xFF5C7A8A),
        onDark = Color(0xFFDCE7ED), onDim = Color(0xFF9AA7B0),
        lightBg = Color(0xFFF3F5F7), lightPrimary = Color(0xFF3C6478),
    )
    val Forest = palette(
        "forest", "Forest",
        night = Color(0xFF0D1310), surface = Color(0xFF151E18), surfaceHi = Color(0xFF1E2A22),
        accent = Color(0xFF8CC0A0), accentDim = Color(0xFF5E876E),
        onDark = Color(0xFFDCEAE0), onDim = Color(0xFF97A99D),
        lightBg = Color(0xFFF1F6F2), lightPrimary = Color(0xFF3A6B4C),
    )
    val Sand = palette(
        "sand", "Sand",
        night = Color(0xFF17130E), surface = Color(0xFF221C14), surfaceHi = Color(0xFF2E2519),
        accent = Color(0xFFD9B892), accentDim = Color(0xFF9C815F),
        onDark = Color(0xFFEDE3D6), onDim = Color(0xFFB0A492),
        lightBg = Color(0xFFF7F3EC), lightPrimary = Color(0xFF7A5C34),
    )
    val Ink = palette(
        "ink", "Ink",
        night = Color(0xFF0F0F11), surface = Color(0xFF17181B), surfaceHi = Color(0xFF212327),
        accent = Color(0xFFB8BCC4), accentDim = Color(0xFF7C808A),
        onDark = Color(0xFFE4E6EA), onDim = Color(0xFF9DA0A8),
        lightBg = Color(0xFFF4F4F6), lightPrimary = Color(0xFF454850),
    )

    val all = listOf(Twilight, Forest, Sand, Ink)
    fun byKey(key: String): Palette = all.firstOrNull { it.key == key } ?: Twilight
}

@Composable
fun MeditationTheme(
    themeMode: String = "dark",
    paletteKey: String = "twilight",
    content: @Composable () -> Unit,
) {
    val palette = Palettes.byKey(paletteKey)
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) palette.dark else palette.light,
        typography = Typography(),
        content = content,
    )
}
