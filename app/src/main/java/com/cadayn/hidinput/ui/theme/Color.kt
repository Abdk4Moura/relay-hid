package com.cadayn.hidinput.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Full Relay token palette (mirrors the prototype's oklch CSS variables, converted to sRGB). */
@Immutable
data class RelayPalette(
    val bg: Color,
    val bgDeep: Color,
    val bgStudio: Color,
    val surface: Color,
    val surface2: Color,
    val surfaceHi: Color,
    val border: Color,
    val borderHi: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val accent: Color,
    val accent2: Color,
    val accentDim: Color,
    val accentGhost: Color,
    val warn: Color,
    val danger: Color,
    val keyTop: Color,
    val keyBot: Color,
    val keyEdge: Color,
    val isDark: Boolean,
)

private val DarkBase = RelayPalette(
    bg = Color(0xFF0D0E11),
    bgDeep = Color(0xFF060609),
    bgStudio = Color(0xFF030304),
    surface = Color(0xFF18191D),
    surface2 = Color(0xFF212327),
    surfaceHi = Color(0xFF2B2E32),
    border = Color(0xFF313337),
    borderHi = Color(0xFF52555C),
    text = Color(0xFFF0F2F4),
    textDim = Color(0xFF9C9EA2),
    textFaint = Color(0xFF67696C),
    accent = Color(0xFF65EA92),
    accent2 = Color(0xFF4FB772),
    accentDim = Color(0xFF28633C),
    accentGhost = Color(0x1F65EA92),
    warn = Color(0xFFF2B95A),
    danger = Color(0xFFEF6567),
    keyTop = Color(0xFF26282C),
    keyBot = Color(0xFF16171A),
    keyEdge = Color(0xFF07080B),
    isDark = true,
)

private val LightBase = RelayPalette(
    bg = Color(0xFFF4F5F8),
    bgDeep = Color(0xFFFBFCFE),
    bgStudio = Color(0xFFDCDEE1),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF5F7F9),
    surfaceHi = Color(0xFFEDEEF2),
    border = Color(0xFFD5D7DB),
    borderHi = Color(0xFFB5B7BD),
    text = Color(0xFF181B1F),
    textDim = Color(0xFF505357),
    textFaint = Color(0xFF7E8084),
    accent = Color(0xFF0EA053),
    accent2 = Color(0xFF008942),
    accentDim = Color(0xFF85CA98),
    accentGhost = Color(0x1F0EA053),
    warn = Color(0xFFBB7400),
    danger = Color(0xFFD33944),
    keyTop = Color(0xFFFFFFFF),
    keyBot = Color(0xFFEFF0F3),
    keyEdge = Color(0xFFC8CACE),
    isDark = false,
)

/** Accent hue id -> (accent, accent2, accentDim) for dark and light. */
enum class AccentHue(val id: String, val label: String) {
    GREEN("152", "Signal green"),
    CYAN("200", "Cyan"),
    VIOLET("290", "Violet"),
    AMBER("78", "Amber");

    companion object {
        fun from(id: String): AccentHue = entries.firstOrNull { it.id == id } ?: GREEN
    }
}

private data class AccentSet(val accent: Color, val accent2: Color, val accentDim: Color)

private val DARK_ACCENTS = mapOf(
    AccentHue.GREEN to AccentSet(Color(0xFF65EA92), Color(0xFF4FB772), Color(0xFF28633C)),
    AccentHue.CYAN to AccentSet(Color(0xFF00EAF6), Color(0xFF00B7C1), Color(0xFF006469)),
    AccentHue.VIOLET to AccentSet(Color(0xFFCAB5FF), Color(0xFF9E8EEF), Color(0xFF554C84)),
    AccentHue.AMBER to AccentSet(Color(0xFFFFBA1D), Color(0xFFCD9219), Color(0xFF704E09)),
)
private val LIGHT_ACCENTS = mapOf(
    AccentHue.GREEN to AccentSet(Color(0xFF009A4D), Color(0xFF008039), Color(0xFF8CD19E)),
    AccentHue.CYAN to AccentSet(Color(0xFF009AA6), Color(0xFF00808B), Color(0xFF64D1D7)),
    AccentHue.VIOLET to AccentSet(Color(0xFF816BD8), Color(0xFF6A55B8), Color(0xFFBCB3FA)),
    AccentHue.AMBER to AccentSet(Color(0xFFB37000), Color(0xFF975A00), Color(0xFFE1B671)),
)

/** The representative swatch color for an accent hue (for the Customize picker). */
fun accentSwatch(hue: AccentHue, dark: Boolean): Color =
    (if (dark) DARK_ACCENTS else LIGHT_ACCENTS).getValue(hue).accent

/** Build the active palette from theme darkness + accent hue. */
fun relayPalette(dark: Boolean, hue: AccentHue): RelayPalette {
    val base = if (dark) DarkBase else LightBase
    val a = (if (dark) DARK_ACCENTS else LIGHT_ACCENTS).getValue(hue)
    return base.copy(
        accent = a.accent,
        accent2 = a.accent2,
        accentDim = a.accentDim,
        accentGhost = a.accent.copy(alpha = 0.12f),
    )
}
