package com.cadayn.hidinput.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle

private val LocalRelayPalette = staticCompositionLocalOf { relayPalette(true, AccentHue.GREEN) }

/** Accessor object: `Relay.colors.accent`, `Relay.type.h1`. */
object Relay {
    val colors: RelayPalette
        @Composable @ReadOnlyComposable get() = LocalRelayPalette.current
    val type get() = RelayType
}

@Composable
fun RelayTheme(
    dark: Boolean = true,
    hue: AccentHue = AccentHue.GREEN,
    content: @Composable () -> Unit,
) {
    val palette = relayPalette(dark, hue)
    CompositionLocalProvider(LocalRelayPalette provides palette, content = content)
}

/** Convenience to copy a TextStyle with a color in one call. */
fun TextStyle.c(color: androidx.compose.ui.graphics.Color): TextStyle = this.copy(color = color)
