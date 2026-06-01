package com.cadayn.hidinput.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cadayn.hidinput.ui.RelayController
import com.cadayn.hidinput.ui.components.BtnKind
import com.cadayn.hidinput.ui.components.Eyebrow
import com.cadayn.hidinput.ui.components.RelayButton
import com.cadayn.hidinput.ui.components.RelayIcons
import com.cadayn.hidinput.ui.components.RelayLogo
import com.cadayn.hidinput.ui.components.TText
import com.cadayn.hidinput.ui.theme.Relay

@Composable
fun DashboardScreen(c: RelayController, onOpenKeyboard: () -> Unit, onConnect: () -> Unit) {
    if (!c.isConnected) EmptyHub(onConnect) else ConnectedHub(c, onOpenKeyboard)
}

@Composable
private fun EmptyHub(onConnect: () -> Unit) {
    val col = Relay.colors
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RelayLogo(size = 54, corner = 16)
            TText("No device connected", Relay.type.h1, col.text)
            TText("Pair with an iPad, Mac or TV to start using your phone as its keyboard and trackpad.",
                Relay.type.sub, col.textDim)
            Spacer(Modifier.height(2.dp))
            RelayButton("Connect a device", onConnect, kind = BtnKind.Primary, large = true,
                leading = { RelayIcons.Bluetooth(size = 16.dp, color = col.bgDeep) })
        }
    }
}

@Composable
private fun ConnectedHub(c: RelayController, onOpenKeyboard: () -> Unit) {
    val portrait = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    if (portrait) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HeroCard(c, onOpenKeyboard, Modifier.fillMaxWidth(), compact = true)
            ActivityCard(c, Modifier.fillMaxWidth().weight(1f))
        }
    } else {
        Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HeroCard(c, onOpenKeyboard, Modifier.weight(1.35f).fillMaxHeight(), compact = false)
            ActivityCard(c, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun HeroCard(c: RelayController, onOpenKeyboard: () -> Unit, modifier: Modifier, compact: Boolean) {
    val col = Relay.colors
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(col.surface, col.bg)))
            .border(1.dp, col.border, RoundedCornerShape(16.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(col.accentGhost)
                    .border(1.dp, col.accentDim, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { RelayIcons.Bluetooth(size = 22.dp, color = col.accent) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    TText(c.deviceName ?: "Host", Relay.type.h1.copy(fontSize = 22.sp), col.text, maxLines = 1)
                    LiveBadge()
                }
                TText("Connected as keyboard + trackpad · HID", Relay.type.sub.copy(fontSize = 12.5.sp), col.textDim)
            }
        }
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Latency", "${c.latency} ms", true, Modifier.weight(1f))
                    Stat("Link", "BT 5.3", false, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Phone", "${c.batteryPct()}%", false, Modifier.weight(1f))
                    Stat("Encryption", "AES", false, Modifier.weight(1f))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Latency", "${c.latency} ms", true, Modifier.weight(1f))
                Stat("Link", "BT 5.3", false, Modifier.weight(1f))
                Stat("Phone", "${c.batteryPct()}%", false, Modifier.weight(1f))
                Stat("Encryption", "AES", false, Modifier.weight(1f))
            }
        }
        if (!compact) Spacer(Modifier.weight(1f))
        RelayButton("Open keyboard & trackpad", onOpenKeyboard, kind = BtnKind.Primary, large = true,
            modifier = Modifier.fillMaxWidth(),
            leading = { RelayIcons.Keyboard(size = 18.dp, color = col.bgDeep) },
            trailing = { RelayIcons.ChevronRight(color = col.bgDeep) })
    }
}

@Composable
private fun ActivityCard(c: RelayController, modifier: Modifier) {
    val col = Relay.colors
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(col.surface)
            .border(1.dp, col.border, RoundedCornerShape(16.dp)).padding(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Live input")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(col.accent))
                Text("STREAMING", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 10.sp))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (c.log.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TText("Keys and gestures you send will appear here.", Relay.type.sub.copy(fontSize = 12.5.sp), col.textFaint)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(c.log, key = { it.id }) { e -> LogRow(e) }
            }
        }
    }
}

@Composable
private fun LiveBadge() {
    val col = Relay.colors
    Row(
        Modifier.clip(CircleShape).background(col.accentGhost).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(col.accent))
        Text("LIVE", style = Relay.type.label.copy(color = col.accent, fontSize = 11.sp))
    }
}

@Composable
private fun Stat(label: String, value: String, accent: Boolean, modifier: Modifier) {
    val col = Relay.colors
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(col.bgDeep)
            .border(1.dp, col.border, RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        Text(value, style = Relay.type.monoSemi.copy(color = if (accent) col.accent else col.text, fontSize = 17.sp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = Relay.type.sub.copy(color = col.textFaint, fontSize = 11.sp))
    }
}

@Composable
private fun LogRow(e: com.cadayn.hidinput.ui.ActivityEvent) {
    val col = Relay.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(col.bgDeep)
            .border(1.dp, col.border, RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(e.time, style = Relay.type.mono.copy(color = col.textFaint, fontSize = 9.sp), modifier = Modifier.width(46.dp))
        val (txt, c2) = when (e.type) {
            "key" -> e.text to col.accent2
            "click" -> e.text to col.textDim
            else -> e.text to col.textFaint
        }
        Text(txt, style = Relay.type.monoSemi.copy(color = c2, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
