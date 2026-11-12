package io.github.houssam2021.artify.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF8B6CFF)
val PanelBg = Color(0xF0222226)
val ChromeBg = Color(0xE61A1A1D)
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
    MaterialTheme(colorScheme = scheme, content = content)
}
