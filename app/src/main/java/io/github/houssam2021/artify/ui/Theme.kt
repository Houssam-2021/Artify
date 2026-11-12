package io.github.houssam2021.artify.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF8B6CFF)
val PanelBg = Color(0xFF222226)
val ChromeBg = Color(0xF21A1A1D)
val ChromeFg = Color(0xFFECECF1)
val Muted = Color(0xFF9A9AA6)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFFFF6B9A),
    background = Color(0xFF121214),
    surface = Color(0xFF1C1C20),
    surfaceVariant = Color(0xFF2A2A30),
    onSurface = ChromeFg,
    onBackground = ChromeFg,
    onSurfaceVariant = Muted,
)

@Composable
fun ArtifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme) {
        // Without a Surface, Text defaults to black; the whole app sits on dark chrome.
        CompositionLocalProvider(LocalContentColor provides ChromeFg, content = content)
    }
}
