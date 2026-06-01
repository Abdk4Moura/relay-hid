package com.cadayn.hidinput.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cadayn.hidinput.ui.RelayController
import com.cadayn.hidinput.ui.components.BtnKind
import com.cadayn.hidinput.ui.components.Eyebrow
import com.cadayn.hidinput.ui.components.RelayButton
import com.cadayn.hidinput.ui.components.RelaySlider
import com.cadayn.hidinput.ui.components.TText
import com.cadayn.hidinput.ui.theme.AccentHue
import com.cadayn.hidinput.ui.theme.Relay
import com.cadayn.hidinput.ui.theme.accentSwatch

@Composable
fun CustomizeScreen(c: RelayController) {
    val portrait = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    if (portrait) {
        Column(Modifier.fillMaxSize()) {
            KeyboardPreview(c, Modifier.fillMaxWidth().height(220.dp).padding(16.dp))
            Controls(c, Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp))
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            Controls(c, Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp))
            KeyboardPreview(c, Modifier.weight(1f).fillMaxHeight().padding(20.dp))
        }
    }
}

@Composable
private fun Controls(c: RelayController, modifier: Modifier) {
    val col = Relay.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TText("Customize", Relay.type.h1, col.text)
            TText("Make Relay yours. Changes apply instantly — preview alongside.", Relay.type.sub.copy(fontSize = 13.sp), col.textDim)
        }

        Section("Theme") {
            OptionCard(c.dark, "Dark", "Default", { c.updateDark(true) }) { Swatch(col.bgDeep) }
            OptionCard(!c.dark, "Light", "Bright rooms", { c.updateDark(false) }) { Swatch(Color(0xFFE9ECEF)) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow("Signal color")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentHue.entries.forEach { hue ->
                    val on = c.hue == hue
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(accentSwatch(hue, c.dark))
                            .border(2.dp, if (on) col.text else Color.Transparent, CircleShape)
                            .clickable { c.updateHue(hue) },
                    )
                }
            }
            TText(c.hue.label, Relay.type.sub.copy(fontSize = 11.5.sp), col.textFaint)
        }

        // Layout controls are orientation-specific — show only the relevant one + a hint for the other.
        val portrait = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        if (!portrait) {
            Section("Landscape layout") {
                OptionCard(c.layout == "gesturebar", "Gesture bar", "Trackpad strip", { c.updateLayout("gesturebar") }) { LayoutGlyph("gesturebar") }
                OptionCard(c.layout == "split", "Split", "Keys + pad", { c.updateLayout("split") }) { LayoutGlyph("split") }
                OptionCard(c.layout == "toggle", "Toggle", "Switch modes", { c.updateLayout("toggle") }) { LayoutGlyph("toggle") }
            }
        } else {
            RotateHint("Rotate to landscape to choose the landscape layout")
        }

        if (portrait) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyebrow("Portrait keyboard")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OptionCard(c.portraitLayout != "onehanded", "Standard", "Resizable split", { c.updatePortraitLayout("standard") }) { PortraitGlyph("thumb") }
                    OptionCard(c.portraitLayout == "onehanded", "One-handed", "Side-shifted", { c.updatePortraitLayout("onehanded") }) { PortraitGlyph("onehanded") }
                }
                if (c.portraitLayout == "onehanded") {
                    com.cadayn.hidinput.ui.components.Seg(c.oneHandSide, listOf("left" to "Left hand", "right" to "Right hand"), c::updateOneHandSide)
                }
                TText("Drag the divider above the keyboard to resize it — all the way down for a full trackpad.",
                    Relay.type.sub.copy(fontSize = 11.5.sp), Relay.colors.textFaint)
            }
        } else {
            RotateHint("Rotate to portrait to change the portrait keyboard")
        }

        Section("Keycaps") {
            OptionCard(c.keycap == "sculpted", "Sculpted", "Raised, tactile", { c.updateKeycap("sculpted") }) { CapSample(true) }
            OptionCard(c.keycap == "flat", "Flat", "Minimal, modern", { c.updateKeycap("flat") }) { CapSample(false) }
        }

        Section("Modifiers") {
            OptionCard(c.behavior == "sticky", "Sticky", "Held until tapped off", { c.updateBehavior("sticky") }, null)
            OptionCard(c.behavior == "oneshot", "One-shot", "Clears after next key", { c.updateBehavior("oneshot") }, null)
        }

        Section("Trackpad canvas") {
            OptionCard(c.padStyle == "gradient", "Gradient", null, { c.updatePadStyle("gradient") }, null)
            OptionCard(c.padStyle == "dots", "Dots", null, { c.updatePadStyle("dots") }, null)
            OptionCard(c.padStyle == "plain", "Plain", null, { c.updatePadStyle("plain") }, null)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow("Pointer speed")
            RelaySlider(c.sensitivity, 1, 10, onChange = c::updateSensitivity)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TText("Reset to defaults", Relay.type.body.copy(fontSize = 14.sp), col.text)
                TText("Restore Relay's original look", Relay.type.sub.copy(fontSize = 11.5.sp), col.textFaint)
            }
            RelayButton("Reset", { c.resetDefaults() }, kind = BtnKind.Secondary)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Section(title: String, cards: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow(title)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = cards)
    }
}

@Composable
private fun RowScope.OptionCard(selected: Boolean, title: String, subtitle: String?, onClick: () -> Unit, preview: (@Composable () -> Unit)? = null) {
    val c = Relay.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.weight(1f).clip(shape).background(if (selected) c.accentGhost else c.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) c.accent else c.border, shape)
            .clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        preview?.invoke()
        Text(title, style = Relay.type.h2.copy(color = if (selected) c.accent else c.text, fontSize = 14.sp))
        if (subtitle != null) Text(subtitle, style = Relay.type.sub.copy(color = c.textFaint, fontSize = 11.sp))
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(Modifier.size(34.dp, 22.dp).clip(RoundedCornerShape(6.dp)).background(color).border(1.dp, Relay.colors.border, RoundedCornerShape(6.dp)))
}

@Composable
private fun CapSample(sculpted: Boolean) {
    val c = Relay.colors
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf("A", "S", "D").forEach { g ->
            val shape = RoundedCornerShape(6.dp)
            val bg = if (sculpted) Modifier.background(Brush.verticalGradient(listOf(c.keyTop, c.keyBot)), shape) else Modifier.background(c.surface2, shape)
            Box(Modifier.size(26.dp).clip(shape).then(bg).border(1.dp, if (sculpted) c.keyEdge else c.border, shape), Alignment.Center) {
                Text(g, style = Relay.type.mono.copy(color = c.textDim, fontSize = 11.sp))
            }
        }
    }
}

@Composable
private fun LayoutGlyph(kind: String) {
    val c = Relay.colors
    val shape = RoundedCornerShape(5.dp)
    Box(Modifier.fillMaxWidth().height(30.dp).clip(shape).background(c.bgDeep).border(1.dp, c.border, shape).padding(4.dp)) {
        when (kind) {
            "gesturebar" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(2.dp)).background(c.surface2))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.dp)).background(c.accentDim))
            }
            "split" -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.weight(1.6f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.surface2))
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.accentDim))
            }
            else -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).background(c.surface2), Alignment.Center) {
                Box(Modifier.size(16.dp, 5.dp).clip(RoundedCornerShape(2.dp)).background(c.accentDim))
            }
        }
    }
}

@Composable
private fun RotateHint(text: String) {
    val c = Relay.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("↻", style = Relay.type.h2.copy(color = c.accent, fontSize = 18.sp))
        Text(text, style = Relay.type.sub.copy(color = c.textDim, fontSize = 12.5.sp))
    }
}

@Composable
private fun PortraitGlyph(kind: String) {
    val c = Relay.colors
    val shape = RoundedCornerShape(4.dp)
    val keys = c.accentDim
    val pad = c.surface2
    Box(Modifier.size(26.dp, 34.dp).clip(shape).background(c.bgDeep).border(1.dp, c.border, shape).padding(3.dp)) {
        when (kind) {
            "thumb" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(2.dp)).background(pad))
                repeat(2) { Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.dp)).background(keys)) }
            }
            "split" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(2.dp)).background(pad))
                Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(1.dp)).background(keys))
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(1.dp)).background(keys))
                }
            }
            "padfirst" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.fillMaxWidth().weight(3f).clip(RoundedCornerShape(2.dp)).background(pad))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(1.dp)).background(keys))
            }
            "onehanded" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(2.dp)).background(pad))
                Row(Modifier.fillMaxWidth().height(8.dp)) {
                    Spacer(Modifier.weight(0.4f))
                    Box(Modifier.weight(0.6f).fillMaxHeight().clip(RoundedCornerShape(1.dp)).background(keys))
                }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(1.dp)).background(keys))
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(2.dp)).background(pad))
            }
        }
    }
}

/* ===================== live keyboard preview ===================== */
private val PREVIEW_ROWS = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

@Composable
private fun KeyboardPreview(c: RelayController, modifier: Modifier) {
    val col = Relay.colors
    val sculpted = c.keycap == "sculpted"
    val layoutName = when (c.layout) { "split" -> "split"; "toggle" -> "toggle"; else -> "gesturebar" }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Live preview")
            Text("${c.hue.label} · $layoutName · ${c.keycap}", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 10.sp))
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)).background(col.bgDeep)
                .border(1.dp, col.border, RoundedCornerShape(14.dp)).padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (c.layout) {
                // keyboard beside a side trackpad pane
                "split" -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1.9f), verticalArrangement = Arrangement.spacedBy(5.dp)) { PreviewKeys(sculpted) }
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(col.surface).border(1.dp, col.border, RoundedCornerShape(8.dp)), Alignment.Center) {
                        Text("PAD", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 9.sp))
                    }
                }
                // switch between keyboard & trackpad — show the mode tabs
                "toggle" -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { MiniTab("⌨ Keys", true); MiniTab("⇡ Pad", false) }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) { PreviewKeys(sculpted) }
                }
                // gesture bar — keyboard with the trackpad strip beneath
                else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    PreviewKeys(sculpted)
                    Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(6.dp)).background(col.surface).border(1.dp, col.border, RoundedCornerShape(6.dp)), Alignment.Center) {
                        Text("trackpad strip", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 8.sp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        TText("This is exactly what you'll see when typing. Open Keys to use it.",
            Relay.type.sub.copy(fontSize = 11.5.sp), col.textFaint, Modifier.fillMaxWidth(), align = TextAlign.Center)
    }
}

@Composable
private fun ColumnScope.PreviewKeys(sculpted: Boolean) {
    val col = Relay.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(col.accent))
        Text("RELAY · HID", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 8.sp))
    }
    Row(Modifier.fillMaxWidth().height(13.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(12) { PreviewKey("", 1f, false, sculpted) }
    }
    PREVIEW_ROWS.forEach { r ->
        Row(Modifier.fillMaxWidth().height(24.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            r.forEach { ch -> PreviewKey(ch.toString(), 1f, false, sculpted) }
        }
    }
    Row(Modifier.fillMaxWidth().height(24.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PreviewKey("⌃", 1f, true, sculpted)
        PreviewKey("⌥", 1f, false, sculpted)
        PreviewKey("space", 6f, false, sculpted)
        PreviewKey("◀", 1f, false, sculpted)
        PreviewKey("▶", 1f, false, sculpted)
    }
}

@Composable
private fun MiniTab(label: String, on: Boolean) {
    val c = Relay.colors
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(if (on) c.surfaceHi else c.bgDeep)
            .border(1.dp, if (on) c.accentDim else c.border, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    ) { Text(label, style = Relay.type.mono.copy(color = if (on) c.accent else c.textFaint, fontSize = 8.sp)) }
}

@Composable
private fun RowScope.PreviewKey(label: String, weight: Float, accent: Boolean, sculpted: Boolean) {
    val c = Relay.colors
    val shape = RoundedCornerShape(5.dp)
    val bg = when {
        accent -> Modifier.background(c.accentGhost, shape)
        sculpted -> Modifier.background(Brush.verticalGradient(listOf(c.keyTop, c.keyBot)), shape)
        else -> Modifier.background(c.surface, shape)
    }
    Box(
        Modifier.weight(weight).fillMaxHeight().clip(shape).then(bg)
            .border(1.dp, if (accent) c.accentDim else if (sculpted) c.keyEdge else c.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) Text(label, maxLines = 1, style = Relay.type.mono.copy(color = if (accent) c.accent else c.textDim, fontSize = 8.sp))
    }
}
