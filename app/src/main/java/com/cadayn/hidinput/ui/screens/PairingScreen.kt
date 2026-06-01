package com.cadayn.hidinput.ui.screens

import android.bluetooth.BluetoothDevice
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cadayn.hidinput.ui.ConnState
import com.cadayn.hidinput.ui.RelayController
import com.cadayn.hidinput.ui.components.BtnKind
import com.cadayn.hidinput.ui.components.Eyebrow
import com.cadayn.hidinput.ui.components.RelayButton
import com.cadayn.hidinput.ui.components.RelayIcons
import com.cadayn.hidinput.ui.components.TText
import com.cadayn.hidinput.ui.theme.Relay

@Composable
fun PairingScreen(c: RelayController, onMakeDiscoverable: () -> Unit, onPickFile: () -> Unit = {}) {
    val col = Relay.colors
    val devices = remember(c.registered, c.conn) { c.pairedDevices }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 26.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Bluetooth · HID peripheral")
                Spacer(Modifier.height(8.dp))
                TText("Connect a device", Relay.type.h1, col.text)
            }
            AdvertisingChip(registered = c.registered)
        }

        Spacer(Modifier.height(14.dp))
        TText(
            "Open Settings → Bluetooth on your other device and pick “Relay”, or tap a paired device below.",
            Relay.type.sub, col.textDim, Modifier.widthIn(max = 540.dp),
        )
        Spacer(Modifier.height(16.dp))
        RelayButton(
            "Make discoverable", onMakeDiscoverable, kind = BtnKind.Primary, large = true,
            leading = { RelayIcons.Bluetooth(size = 16.dp, color = col.bgDeep) },
        )

        Spacer(Modifier.height(20.dp))
        val portrait = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        when {
            devices.isEmpty() -> EmptyDevices()
            portrait -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                devices.forEach { d -> DeviceEntry(c, d) }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().height((((devices.size + 1) / 2) * 84).dp),
                userScrollEnabled = false,
            ) {
                items(devices) { d -> DeviceEntry(c, d) }
            }
        }

        Spacer(Modifier.height(22.dp))
        WifiPanel(c, onPickFile)
    }
}

@Composable
private fun WifiPanel(c: RelayController, onPickFile: () -> Unit) {
    val col = Relay.colors
    var ip by remember { mutableStateOf(c.wifiHost ?: "") }
    var pin by remember { mutableStateOf("") }
    DisposableEffect(Unit) { c.discovery.start(); onDispose { c.discovery.stop() } }
    Column(
        Modifier.fillMaxWidth().widthIn(max = 540.dp).clip(RoundedCornerShape(16.dp)).background(col.surface)
            .border(1.dp, if (c.wifiConnected) col.accentDim else col.border, RoundedCornerShape(16.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("WiFi · Desktop (Linux)")
            if (c.wifiConnected) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(col.accent))
                Text("connected", style = Relay.type.label.copy(color = col.accent, fontSize = 12.sp))
            }
        }
        TText("Run the relay-desktop receiver on your computer, then enter its LAN IP + PIN.",
            Relay.type.sub.copy(fontSize = 12.sp), col.textFaint)
        if (!c.wifiConnected) {
            if (c.discovery.hosts.isNotEmpty()) {
                TText("FOUND ON YOUR NETWORK", Relay.type.label.copy(fontSize = 10.sp), col.textFaint)
                c.discovery.hosts.forEach { h ->
                    val saved = c.wifiPinFor(h.ip)
                    FoundHost(h.name, h.ip, saved.isNotEmpty()) {
                        if (saved.isNotEmpty()) c.wifiConnect(h.ip, h.port, saved)
                        else { ip = h.ip }   // fill IP, just type the PIN once
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WifiField(ip, "192.168.x.x", Modifier.weight(2f)) { ip = it.filter { ch -> ch.isDigit() || ch == '.' } }
                WifiField(pin, "PIN", Modifier.weight(1f)) { pin = it.take(8) }
            }
            RelayButton("Connect", { if (ip.isNotBlank()) c.wifiConnect(ip.trim(), 47600, pin.trim()) }, kind = BtnKind.Primary)
        } else {
            Text("→ ${c.wifiHost}  ·  input now goes over WiFi", style = Relay.type.mono.copy(color = col.textDim, fontSize = 12.5.sp))
            RelayButton("Disconnect", { c.wifiDisconnect() }, kind = BtnKind.Secondary)
            TText("Share text or files to “Relay” from any app to send them to the desktop. Desktop copies arrive on your phone automatically.",
                Relay.type.sub.copy(fontSize = 11.5.sp), col.textFaint)
        }
    }
}

@Composable
private fun FoundHost(name: String, ip: String, saved: Boolean, onClick: () -> Unit) {
    val col = Relay.colors
    val shape = RoundedCornerShape(11.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(col.bgDeep).border(1.dp, col.border, shape)
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(col.accent))
        Column(Modifier.weight(1f)) {
            Text(name, style = Relay.type.body.copy(color = col.text, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ip, style = Relay.type.mono.copy(color = col.textFaint, fontSize = 10.5.sp))
        }
        Text(if (saved) "tap to connect" else "enter PIN ↓", style = Relay.type.mono.copy(color = if (saved) col.accent else col.textDim, fontSize = 10.5.sp))
    }
}

@Composable
private fun WifiField(value: String, hint: String, modifier: Modifier, onChange: (String) -> Unit) {
    val col = Relay.colors
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(col.bgDeep)
            .border(1.dp, col.border, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = Relay.type.mono.copy(color = col.text, fontSize = 14.sp), cursorBrush = SolidColor(col.accent),
            decorationBox = { inner -> if (value.isEmpty()) Text(hint, style = Relay.type.mono.copy(color = col.textFaint, fontSize = 14.sp)); inner() },
        )
    }
}

@Composable
private fun DeviceEntry(c: RelayController, d: BluetoothDevice) {
    val name = remember(d) { runCatching { d.name }.getOrNull() ?: d.address }
    val isConn = c.conn == ConnState.CONNECTED && c.deviceName == name
    val isPairing = c.conn == ConnState.PAIRING && c.deviceName == name
    DeviceCard(name, d.address, isConn, isPairing) { if (!isConn) c.connectTo(d) }
}

@Composable
private fun AdvertisingChip(registered: Boolean) {
    val col = Relay.colors
    val dotColor = if (registered) col.accent else col.warn
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).background(col.bgDeep).border(1.dp, col.border, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Text(
            if (registered) "Advertising as “Relay”" else "Starting…",
            style = Relay.type.body.copy(color = if (registered) col.accent else col.warn, fontSize = 12.5.sp),
            maxLines = 1, softWrap = false,
        )
    }
}

@Composable
private fun DeviceCard(name: String, address: String, connected: Boolean, pairing: Boolean, onClick: () -> Unit) {
    val col = Relay.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().height(72.dp).clip(shape)
            .background(col.surface, shape)
            .border(1.dp, if (connected) col.accentDim else col.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                .background(if (connected) col.accentGhost else col.surface2)
                .border(1.dp, col.border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { RelayIcons.Bluetooth(size = 18.dp, color = if (connected) col.accent else col.textDim) }

        Column(Modifier.weight(1f)) {
            Text(name, style = Relay.type.h2.copy(color = col.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(address, style = Relay.type.mono.copy(color = col.textFaint, fontSize = 11.sp), maxLines = 1)
        }
        when {
            pairing -> Text("pairing…", style = Relay.type.mono.copy(color = col.warn, fontSize = 11.sp))
            connected -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RelayIcons.Check(size = 16.dp, color = col.accent)
                Text("Connected", style = Relay.type.label.copy(color = col.accent, fontSize = 12.sp))
            }
            else -> RelayIcons.ChevronRight(color = col.textFaint)
        }
    }
}

@Composable
private fun EmptyDevices() {
    val col = Relay.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(col.surface)
            .border(1.dp, col.border, RoundedCornerShape(16.dp)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TText("No paired devices yet", Relay.type.h2, col.textDim)
        TText("Tap “Make discoverable”, then pick Relay from the other device’s Bluetooth settings.",
            Relay.type.sub, col.textFaint)
    }
}
