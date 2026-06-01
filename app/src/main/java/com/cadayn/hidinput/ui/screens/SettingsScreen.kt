package com.cadayn.hidinput.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.SolidColor
import com.cadayn.hidinput.ui.RelayController
import com.cadayn.hidinput.ui.components.BtnKind
import com.cadayn.hidinput.ui.components.RelayButton
import com.cadayn.hidinput.ui.components.RelaySlider
import com.cadayn.hidinput.ui.components.RelaySwitch
import com.cadayn.hidinput.ui.components.Seg
import com.cadayn.hidinput.ui.components.SettingRow
import com.cadayn.hidinput.ui.components.TText
import com.cadayn.hidinput.ui.theme.Relay

@Composable
fun SettingsScreen(c: RelayController) {
    val portrait = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 620.dp)) {
            TText("Settings", Relay.type.h1, Relay.colors.text)

            Spacer(Modifier.height(6.dp))
            // Target device — always visible up top; the single most useful control.
            TText("TARGET DEVICE", Relay.type.label.copy(fontSize = 11.sp), Relay.colors.textDim)
            Spacer(Modifier.height(8.dp))
            ProfileChips(c.profile, c::updateProfile)
            Spacer(Modifier.height(16.dp))

            // Everything else tucks into collapsible groups so the screen stays calm.
            Group("Keys & typing") {
                SettingRow("Modifier behavior", "Sticky holds the modifier; one-shot clears it after the next key") {
                    Seg(c.behavior, listOf("sticky" to "Sticky", "oneshot" to "One-shot"), c::updateBehavior)
                }
                SettingRow("Caps Lock → Esc") { RelaySwitch(c.capsEsc, c::updateCapsEsc) }
                if (portrait) SettingRow("Key height", "Resting size of the thumb keys (drag the divider to resize live)") { RelaySlider(c.keyHeight, 38, 82, onChange = c::updateKeyHeight) }
                SettingRow("Key spacing", "Lower = tighter, bigger-feeling keys") { RelaySlider(c.keyGap, 0, 12, onChange = c::updateKeyGap) }
                if (!portrait) SettingRow("Show arrow keys (landscape)", "Off = arrows live on the space bar (hold & slide)") {
                    RelaySwitch(c.showArrowKeys, c::updateShowArrowKeys)
                }
                if (portrait) OrientationHint("Rotate to landscape for landscape-only key options")
                SettingRow("Key repeat", "Hold a key (or D-pad/volume on the remote) to repeat it, accelerating") {
                    RelaySwitch(c.keyRepeat, c::updateKeyRepeat)
                }
                SettingRow("Corner-slide animation", "Light up & pop the corner symbol as you flick toward it") {
                    RelaySwitch(c.slideAnim, c::updateSlideAnim)
                }
                SettingRow("Live type-preview", "Show typed text + caret above the thumb keyboard") {
                    RelaySwitch(c.showPreview, c::updateShowPreview)
                }
            }

            Group("Cursor & trackpad") {
                SettingRow("Pointer sensitivity") { RelaySlider(c.sensitivity, 1, 10, onChange = c::updateSensitivity) }
                SettingRow("Cursor acceleration", "How fast the space-cursor & pointer ramp up") {
                    RelaySlider(c.accel, 0, 10, onChange = c::updateAccel)
                }
                SettingRow("Scroll speed", "Two-finger scroll — independent of pointer speed") { RelaySlider(c.scrollSpeed, 1, 10, onChange = c::updateScrollSpeed) }
                SettingRow("Natural scrolling") { RelaySwitch(c.naturalScroll, c::updateNaturalScroll) }
                SettingRow("Tap to click") { RelaySwitch(c.tapClick, c::updateTapClick) }
                SettingRow("Momentum scrolling", "Flick two fingers to keep scrolling") { RelaySwitch(c.momentum, c::updateMomentum) }
                if (!portrait) SettingRow("Trackpad auto-return", "Landscape: seconds the full pad waits (idle) before the keyboard returns. 0 = swipe only") {
                    RelaySlider(c.padTimeout, 0, 8, onChange = c::updatePadTimeout)
                }
                SettingRow("Invert pointer X") { RelaySwitch(c.invertX, c::updateInvertX) }
                SettingRow("Invert pointer Y") { RelaySwitch(c.invertY, c::updateInvertY) }
                SettingRow("Side bias", "Favour left/right over up/down on the space-cursor") {
                    RelaySlider(c.sideBias, 0, 10, onChange = c::updateSideBias)
                }
                SettingRow("Firm-press → right-click", "Press harder on the pad for a secondary click (approximate)") { RelaySwitch(c.firmPress, c::updateFirmPress) }
            }

            Group("Gestures") {
                SettingRow("Two-finger swipe ◀ ▶", "Swipe sideways with two fingers for Back / Forward") {
                    RelaySwitch(c.swipeNav, c::updateSwipeNav)
                }
                SettingRow("Two-finger scroll", "Drag two fingers up / down to scroll") {
                    Text("⇅", style = Relay.type.mono.copy(color = Relay.colors.textDim))
                }
                SettingRow("Three-finger swipe", "Switch apps") {
                    Text("⌘⇥", style = Relay.type.mono.copy(color = Relay.colors.textDim))
                }
                SettingRow("Three-finger tap", "Find pointer — jiggles the cursor so you can spot it") {
                    Text("◎", style = Relay.type.mono.copy(color = Relay.colors.textDim))
                }
            }

            Group("Feedback") {
                SettingRow("Haptics", "Vibrate on each keystroke") { RelaySwitch(c.haptics, c::updateHaptics) }
                SettingRow("HID readout", "Live modifier byte + key stream") { RelaySwitch(c.showReadout, c::updateShowReadout) }
            }

            Group("Hardware & connection") {
                SettingRow("Volume buttons", "Repurpose the volume rocker while Relay is open") {
                    Seg(c.volumeKeys, listOf("off" to "Off", "scroll" to "Scroll", "page" to "Page", "click" to "Click"), c::updateVolumeKeys)
                }
                SettingRow("Auto-reconnect", "Reconnect to the last device automatically") {
                    RelaySwitch(c.autoReconnect, c::updateAutoReconnect)
                }
                SettingRow("Stay awake (keep host awake)", "Imperceptible nudge every 25s so the host won't sleep") {
                    RelaySwitch(c.jiggler, c::updateJiggler)
                }
                SettingRow("Swap ⌘ ↔ ⌃ (advanced)", "Override the profile's modifier mapping") {
                    RelaySwitch(c.swapCmd, c::updateSwapCmd)
                }
                SettingRow("Custom Bluetooth name", "Renames this phone's Bluetooth (all connections). Forget & re-pair the host to see the new name.") {
                    RelaySwitch(c.renameBt, c::updateRenameBt)
                }
                if (c.renameBt) BtNameField(c)
            }

            Group("WiFi & sync") {
                SettingRow("Receive desktop clipboard", "Copies on the desktop set your phone clipboard automatically") {
                    RelaySwitch(c.clipboardAuto, c::updateClipboardAuto)
                }
                SettingRow("Sync notifications", "One self-replacing alert when a clipboard/file syncs — never piles up") {
                    RelaySwitch(c.notifySync, c::updateNotifySync)
                }
                SettingRow("Share to Relay", "Share text or files to “Relay” from any app to send them to the desktop") {
                    Text("share sheet", style = Relay.type.mono.copy(color = Relay.colors.textDim, fontSize = 11.sp))
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun BtNameField(c: RelayController) {
    val col = Relay.colors
    var name by remember { mutableStateOf(c.btName) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(col.bgDeep)
                .border(1.dp, col.border, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = name, onValueChange = { name = it.take(24) }, singleLine = true,
                textStyle = Relay.type.mono.copy(color = col.text, fontSize = 14.sp), cursorBrush = SolidColor(col.accent),
                decorationBox = { inner ->
                    if (name.isEmpty()) Text("Relay", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 14.sp))
                    inner()
                },
            )
        }
        RelayButton("Apply", { c.updateBtName(name.ifBlank { "Relay" }) }, kind = BtnKind.Secondary)
    }
}

@Composable
private fun OrientationHint(text: String) {
    val col = Relay.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("↻", style = Relay.type.body.copy(color = col.accent, fontSize = 15.sp))
        Text(text, style = Relay.type.sub.copy(color = col.textFaint, fontSize = 12.sp))
    }
}

/** Collapsible settings group — collapsed by default to keep the screen calm. */
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val col = Relay.colors
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = Relay.type.h2.copy(color = col.text, fontSize = 14.sp))
            Spacer(Modifier.weight(1f))
            Text(if (open) "▾" else "▸", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 13.sp))
        }
        if (open) Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) { content() }
        Box(Modifier.fillMaxWidth().height(1.dp).background(col.border))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProfileChips(value: String, onChange: (String) -> Unit) {
    val col = Relay.colors
    val opts = listOf(
        "ipad" to "iPad", "mac" to "Mac", "windows" to "Windows", "androidtv" to "Android TV",
        "appletv" to "Apple TV", "linux" to "Linux", "ps" to "PlayStation",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.forEach { (v, lbl) ->
            val on = v == value
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (on) col.accentGhost else col.surface)
                    .border(1.dp, if (on) col.accentDim else col.border, RoundedCornerShape(10.dp))
                    .clickable { onChange(v) }.padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(lbl, style = Relay.type.body.copy(color = if (on) col.accent else col.textDim, fontSize = 13.sp))
            }
        }
    }
}
