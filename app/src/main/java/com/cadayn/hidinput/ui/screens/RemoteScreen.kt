package com.cadayn.hidinput.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cadayn.hidinput.HidConstants
import com.cadayn.hidinput.ui.RelayController
import com.cadayn.hidinput.ui.components.SectionTitle
import com.cadayn.hidinput.ui.components.TText
import com.cadayn.hidinput.ui.components.repeatingPress
import com.cadayn.hidinput.ui.theme.Relay
import kotlin.math.roundToInt

@Composable
fun RemoteScreen(c: RelayController) {
    val col = Relay.colors
    var mode by remember { mutableStateOf("remote") }
    val portrait = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 900.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TText(if (mode == "remote") "Remote" else "Presenter", Relay.type.h1, col.text)
            Seg2(mode, "remote" to "Remote", "present" to "Presenter") { mode = it }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.widthIn(max = if (portrait) 520.dp else 900.dp).fillMaxWidth().weight(1f)) {
            if (mode == "remote") RemotePad(c, portrait) else PresenterPad(c, portrait)
        }
    }
}

@Composable
private fun RemotePad(c: RelayController, portrait: Boolean) {
    if (portrait) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DPad(c, Modifier.fillMaxWidth().weight(1f))
            BackHomeRow(c, Modifier.fillMaxWidth().height(52.dp))
            MediaRow(c, Modifier.fillMaxWidth().height(52.dp))
            VolRow(c, Modifier.fillMaxWidth().height(52.dp))
        }
    } else {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                DPad(c, Modifier.fillMaxHeight().aspectRatio(1f))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BackHomeRow(c, Modifier.fillMaxWidth().weight(1f))
                MediaRow(c, Modifier.fillMaxWidth().weight(1f))
                VolRow(c, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun DPad(c: RelayController, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f)); RKey("↑", Modifier.weight(1f), repeat = c.keyRepeat) { c.tapKeycode(HidConstants.KEY_UP); c.logEvent("key", "↑") }; Spacer(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RKey("←", Modifier.weight(1f), repeat = c.keyRepeat) { c.tapKeycode(HidConstants.KEY_LEFT); c.logEvent("key", "←") }
            RKey("OK", Modifier.weight(1f), accent = true) { c.tapKeycode(HidConstants.KEY_ENTER); c.logEvent("key", "OK") }
            RKey("→", Modifier.weight(1f), repeat = c.keyRepeat) { c.tapKeycode(HidConstants.KEY_RIGHT); c.logEvent("key", "→") }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f)); RKey("↓", Modifier.weight(1f), repeat = c.keyRepeat) { c.tapKeycode(HidConstants.KEY_DOWN); c.logEvent("key", "↓") }; Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun BackHomeRow(c: RelayController, modifier: Modifier) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    RKey("Back", Modifier.weight(1f)) { c.tapKeycode(HidConstants.KEY_ESC); c.logEvent("key", "back") }
    RKey("Home", Modifier.weight(1f)) { c.consumer(HidConstants.CC_HOME); c.logEvent("key", "home") }
}

@Composable
private fun MediaRow(c: RelayController, modifier: Modifier) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    RKey("⏮", Modifier.weight(1f), repeat = c.keyRepeat) { c.consumer(HidConstants.CC_PREV); c.logEvent("key", "prev") }
    RKey("⏯", Modifier.weight(1f)) { c.consumer(HidConstants.CC_PLAY_PAUSE); c.logEvent("key", "play") }
    RKey("⏭", Modifier.weight(1f), repeat = c.keyRepeat) { c.consumer(HidConstants.CC_NEXT); c.logEvent("key", "next") }
}

@Composable
private fun VolRow(c: RelayController, modifier: Modifier) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    RKey("vol −", Modifier.weight(1f), repeat = c.keyRepeat) { c.consumer(HidConstants.CC_VOL_DOWN); c.logEvent("key", "vol−") }
    RKey("mute", Modifier.weight(1f)) { c.consumer(HidConstants.CC_MUTE); c.logEvent("key", "mute") }
    RKey("vol +", Modifier.weight(1f), repeat = c.keyRepeat) { c.consumer(HidConstants.CC_VOL_UP); c.logEvent("key", "vol+") }
}

@Composable
private fun PresenterPad(c: RelayController, portrait: Boolean) {
    if (portrait) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrevNextRow(c, Modifier.fillMaxWidth().weight(1f))
            PresControls(c, Modifier.fillMaxWidth().height(52.dp))
            PointerPad(c, Modifier.fillMaxWidth().weight(1.1f))
        }
    } else {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrevNextRow(c, Modifier.fillMaxWidth().weight(1f))
                PresControls(c, Modifier.fillMaxWidth().height(54.dp))
            }
            PointerPad(c, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun PrevNextRow(c: RelayController, modifier: Modifier) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    RKey("◀ Prev", Modifier.weight(1f), repeat = c.keyRepeat) { c.tapKeycode(HidConstants.KEY_LEFT); c.logEvent("key", "prev slide") }
    RKey("Next ▶", Modifier.weight(1f), accent = true, repeat = c.keyRepeat) { c.tapKeycode(HidConstants.KEY_RIGHT); c.logEvent("key", "next slide") }
}

@Composable
private fun PresControls(c: RelayController, modifier: Modifier) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    RKey("Start", Modifier.weight(1f)) { c.tapKeycode(HidConstants.KEY_F5); c.logEvent("key", "start (F5)") }
    RKey("Black", Modifier.weight(1f)) { c.tapKeycode(HidConstants.KEY_B); c.logEvent("key", "black (B)") }
    RKey("End", Modifier.weight(1f)) { c.tapKeycode(HidConstants.KEY_ESC); c.logEvent("key", "end") }
}

@Composable
private fun PointerPad(c: RelayController, modifier: Modifier) {
    val col = Relay.colors
    val view = LocalView.current
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(col.bgDeep).border(1.dp, col.border, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectDragGestures { ch, drag -> ch.consume(); val s = c.sensitivity / 5f; c.mouseMove((drag.x * s).roundToInt(), (drag.y * s).roundToInt()) }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { if (c.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); c.click(false) })
            },
        contentAlignment = Alignment.Center,
    ) { Text("POINTER · drag to move · tap to click", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 9.sp)) }
}

@Composable
private fun RowScope.RKey(label: String, modifier: Modifier = Modifier, accent: Boolean = false, repeat: Boolean = false, onTap: () -> Unit) {
    val col = Relay.colors
    val view = LocalView.current
    val shape = RoundedCornerShape(12.dp)
    val bg = if (accent) Modifier.background(Brush.verticalGradient(listOf(col.accent, col.accent2)), shape)
        else Modifier.background(Brush.verticalGradient(listOf(col.keyTop, col.keyBot)), shape)
    Box(
        modifier.fillMaxHeight().clip(shape).then(bg).border(1.dp, if (accent) col.accentDim else col.keyEdge, shape)
            .repeatingPress(enabled = repeat, haptic = true, view = view, onFire = onTap),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Relay.type.body.copy(color = if (accent) col.bgDeep else col.text, fontSize = 18.sp)) }
}

@Composable
private fun Seg2(value: String, a: Pair<String, String>, b: Pair<String, String>, onChange: (String) -> Unit) {
    val col = Relay.colors
    Row(Modifier.clip(RoundedCornerShape(11.dp)).background(col.bgDeep).border(1.dp, col.border, RoundedCornerShape(11.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(a, b).forEach { (v, lbl) ->
            val on = v == value
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) col.surfaceHi else androidx.compose.ui.graphics.Color.Transparent).clickable { onChange(v) }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                Text(lbl, style = Relay.type.body.copy(color = if (on) col.text else col.textDim, fontSize = 12.5.sp))
            }
        }
    }
}
