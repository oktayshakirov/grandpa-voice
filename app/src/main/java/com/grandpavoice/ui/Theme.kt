package com.grandpavoice.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Dark, high-contrast palette in the style of Muscle Pal / Drone Pal: near-black background,
 * soft grey cards, one lime accent. A dark screen also glares less at night and suits an
 * always-on phone on a stand. Lime always means "your turn, I'm listening".
 */
object Palette {
    val Background = Color(0xFF0B0B0C)
    val Surface = Color(0xFF1A1A1C)
    val Surface2 = Color(0xFF252528)
    val Surface3 = Color(0xFF323236)
    val Separator = Color(0x14FFFFFF)
    val Ink = Color(0xFFFFFFFF)
    val Secondary = Color(0xB3EBEBF5)
    val Tertiary = Color(0x66EBEBF5)

    val Accent = Color(0xFFC8FA3C)
    val OnAccent = Color(0xFF0B0B0C)

    val Listening = Accent
    val Thinking = Color(0xFFFFB340)
    val Speaking = Color(0xFF5AC8FA)
    val Error = Color(0xFFFF453A)
    val Goodbye = Color(0xFFFF9EB5)
}

@Composable
fun GrandpaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Palette.OnAccent,
            primaryContainer = Palette.Surface3,
            onPrimaryContainer = Palette.Ink,
            secondary = Palette.Accent,
            onSecondary = Palette.OnAccent,
            background = Palette.Background,
            onBackground = Palette.Ink,
            surface = Palette.Surface,
            onSurface = Palette.Ink,
            surfaceVariant = Palette.Surface2,
            onSurfaceVariant = Palette.Secondary,
            surfaceContainerHighest = Palette.Surface3,
            outline = Palette.Surface3,
            outlineVariant = Palette.Separator,
            error = Palette.Error,
        ),
    ) {
        // Text without an explicit colour is white, not Material's default black.
        CompositionLocalProvider(LocalContentColor provides Palette.Ink, content = content)
    }
}
