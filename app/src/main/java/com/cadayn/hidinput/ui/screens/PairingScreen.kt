package com.cadayn.hidinput.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cadayn.hidinput.ui.RelayController
import com.cadayn.hidinput.ui.components.BtnKind
import com.cadayn.hidinput.ui.components.Eyebrow
import com.cadayn.hidinput.ui.components.RelayButton
import com.cadayn.hidinput.ui.components.RelayIcons
import com.cadayn.hidinput.ui.components.TText
import com.cadayn.hidinput.ui.theme.Relay

/**
 * Unified, target-centric connection screen. One list of devices; the transport (Wi-Fi for
 * desktops, Bluetooth for everything else) is chosen automatically and shown as a badge.
 */
@Composable
fun PairingScreen(c: RelayController, onMakeDiscoverable: () -> Unit) {
    val col = Relay.colors
    DisposableEffect(Unit) { c.discovery.start(); onDispose { c.discovery.stop() } }

    // read the states that should refresh the list
    c.registered; c.conn; c.wifiConnected; c.wifiHost; c.discovery.hosts.size
    val devices = c.unifiedDevices()

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Relay · Connections")
                Spacer(Modifier.height(8.dp))
                TText("Your devices", Relay.type.h1, col.text)
            }
            StatusChip(c)
        }

        // ---- banners: never let a failure or a blocked state be silent ----
        val ctx = androidx.compose.ui.platform.LocalContext.current
        if (!c.btPermission) {
            Spacer(Modifier.height(16.dp))
            Banner("Bluetooth permission needed", "Relay can’t pair without it.", "Grant", col.warn) {
                (ctx as? com.cadayn.hidinput.MainActivity)?.reRequestPermissions()
            }
        } else if (!c.bluetoothOn) {
            Spacer(Modifier.height(16.dp))
            Banner("Bluetooth is off", "Turn it on to pair a tablet, phone or TV.", "Settings", col.warn) {
                runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
            }
        }
        c.notice?.let { msg ->
            Spacer(Modifier.height(16.dp))
            Banner("Couldn’t connect", msg, "Dismiss", col.danger) { c.clearNotice() }
        }

        Spacer(Modifier.height(18.dp))
        if (devices.isEmpty()) {
            EmptyState()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 560.dp)) {
                devices.forEach { d -> DeviceRow(c, d) }
            }
        }

        Spacer(Modifier.height(26.dp))
        Eyebrow("Add a device")
        Spacer(Modifier.height(12.dp))
        AddSection(c, onMakeDiscoverable)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Banner(title: String, body: String, action: String, accent: androidx.compose.ui.graphics.Color, onAction: () -> Unit) {
    val col = Relay.colors
    val shape = RoundedCornerShape(13.dp)
    Row(
        Modifier.fillMaxWidth().widthIn(max = 560.dp).clip(shape).background(col.surface)
            .border(1.dp, accent.copy(alpha = 0.5f), shape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Column(Modifier.weight(1f)) {
            Text(title, style = Relay.type.body.copy(color = col.text, fontSize = 13.5.sp), maxLines = 1)
            Text(body, style = Relay.type.sub.copy(fontSize = 11.5.sp), color = col.textFaint)
        }
        Box(
            Modifier.clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.16f))
                .clickable(onClick = onAction).padding(horizontal = 12.dp, vertical = 8.dp),
        ) { Text(action, style = Relay.type.label.copy(color = accent, fontSize = 12.sp)) }
    }
}

@Composable
private fun StatusChip(c: RelayController) {
    val col = Relay.colors
    val t = c.activeTransport
    val (label, color) = when (t) {
        "wifi" -> "Wi-Fi · ${c.wifiHost}" to col.accent
        "bt" -> "Bluetooth · ${c.deviceName ?: "device"}" to col.accent
        else -> "Not connected" to col.textDim
    }
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).background(col.bgDeep).border(1.dp, col.border, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = Relay.type.body.copy(color = color, fontSize = 12.sp), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DeviceRow(c: RelayController, d: RelayController.UiDevice) {
    val col = Relay.colors
    val shape = RoundedCornerShape(16.dp)
    val connected = c.isConnectedDevice(d)
    val activeWifi = connected && c.activeTransport == "wifi"
    val isConnecting = c.connecting != null && (c.connecting == d.wifiHost || c.connecting == d.name)
    var pinOpen by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }

    val subtitle = when {
        d.bt != null && d.wifiHost != null -> "Wi-Fi · ${d.wifiHost}  ·  Bluetooth ready"
        d.wifiHost != null -> "Wi-Fi · ${d.wifiHost}" + if (d.needsPin) "  ·  needs PIN" else ""
        else -> "Bluetooth"
    }

    Column(
        Modifier.fillMaxWidth().clip(shape).background(col.surface, shape)
            .border(1.dp, if (connected) col.accentDim else col.border, shape),
    ) {
        Row(
            Modifier.fillMaxWidth().height(72.dp).clickable {
                when {
                    connected || isConnecting -> {}
                    d.needsPin && d.bt == null -> pinOpen = !pinOpen
                    else -> c.connectBest(d)
                }
            }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (connected) col.accentGhost else col.surface2)
                    .border(1.dp, col.border, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val ic = if (connected) col.accent else col.textDim
                if (d.isDesktop) RelayIcons.Monitor(size = 19.dp, color = ic) else RelayIcons.Bluetooth(size = 18.dp, color = ic)
            }
            Column(Modifier.weight(1f)) {
                Text(d.name, style = Relay.type.h2.copy(color = col.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = Relay.type.mono.copy(color = col.textFaint, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when {
                isConnecting -> Text("connecting…", style = Relay.type.mono.copy(color = col.warn, fontSize = 11.5.sp))
                connected -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (activeWifi) RelayIcons.Wifi(size = 15.dp, color = col.accent) else RelayIcons.Bluetooth(size = 14.dp, color = col.accent)
                    Text("Connected", style = Relay.type.label.copy(color = col.accent, fontSize = 12.sp))
                }
                d.needsPin && d.bt == null -> Text(if (pinOpen) "enter PIN" else "set up", style = Relay.type.mono.copy(color = col.textDim, fontSize = 11.sp))
                else -> RelayIcons.ChevronRight(color = col.textFaint)
            }
        }
        if (connected && c.activeTransport == "wifi") {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RelayButton("Disconnect", { c.wifiDisconnect() }, kind = BtnKind.Secondary)
            }
        }
        if (pinOpen && d.needsPin && d.bt == null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                WifiField(pin, "PIN", Modifier.weight(1f)) { pin = it.take(8) }
                RelayButton("Connect", { if (pin.isNotBlank()) { c.connectBest(d, pin.trim()); pinOpen = false } }, kind = BtnKind.Primary)
            }
        }
    }
}

@Composable
private fun AddSection(c: RelayController, onMakeDiscoverable: () -> Unit) {
    val col = Relay.colors
    var ipOpen by remember { mutableStateOf(false) }
    var ip by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 560.dp)) {
        // Bluetooth: tablets, phones, TVs, consoles
        AddCard(
            title = "Tablet, phone or TV",
            body = "Make this phone discoverable, then pick “Relay” in the other device’s Bluetooth settings.",
            icon = { RelayIcons.Bluetooth(size = 18.dp, color = col.textDim) },
            onClick = onMakeDiscoverable,
        )
        // Wi-Fi: computers
        AddCard(
            title = "Computer (Mac · Windows · Linux)",
            body = "Run the relay-desktop receiver, then scan its QR with your camera — or enter its IP + PIN below.",
            icon = { RelayIcons.Monitor(size = 18.dp, color = col.textDim) },
            onClick = { ipOpen = !ipOpen },
        )
        if (ipOpen) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(col.bgDeep)
                    .border(1.dp, col.border, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WifiField(ip, "192.168.x.x", Modifier.weight(2f)) { ip = it.filter { ch -> ch.isDigit() || ch == '.' } }
                    WifiField(pin, "PIN", Modifier.weight(1f)) { pin = it.take(8) }
                }
                RelayButton("Connect", { if (ip.isNotBlank()) { c.wifiConnect(ip.trim(), 47600, pin.trim()); ipOpen = false } }, kind = BtnKind.Primary)
            }
        }
    }
}

@Composable
private fun AddCard(title: String, body: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    val col = Relay.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(col.surface, shape).border(1.dp, col.border, shape)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(col.surface2).border(1.dp, col.border, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Column(Modifier.weight(1f)) {
            Text(title, style = Relay.type.body.copy(color = col.text, fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(body, style = Relay.type.sub.copy(fontSize = 11.5.sp), color = col.textFaint)
        }
        RelayIcons.Plus(size = 16.dp, color = col.textDim)
    }
}

@Composable
private fun WifiField(value: String, hint: String, modifier: Modifier, onChange: (String) -> Unit) {
    val col = Relay.colors
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(col.surface)
            .border(1.dp, col.border, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = Relay.type.mono.copy(color = col.text, fontSize = 14.sp), cursorBrush = SolidColor(col.accent),
            decorationBox = { inner -> if (value.isEmpty()) Text(hint, style = Relay.type.mono.copy(color = col.textFaint, fontSize = 14.sp)); inner() },
        )
    }
}

@Composable
private fun EmptyState() {
    val col = Relay.colors
    Column(
        Modifier.fillMaxWidth().widthIn(max = 560.dp).clip(RoundedCornerShape(16.dp)).background(col.surface)
            .border(1.dp, col.border, RoundedCornerShape(16.dp)).padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TText("No devices yet", Relay.type.h2, col.textDim)
        TText("Add one below — a tablet or TV over Bluetooth, or a computer over Wi-Fi.",
            Relay.type.sub, col.textFaint)
    }
}
