package com.cadayn.hidinput.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cadayn.hidinput.ui.components.Eyebrow
import com.cadayn.hidinput.ui.components.RelayLogo
import com.cadayn.hidinput.ui.components.TText
import com.cadayn.hidinput.ui.screens.OnboardingScreen
import com.cadayn.hidinput.ui.theme.Relay

@Composable
fun RelayApp(
    c: RelayController,
    permsGranted: Boolean,
    onMakeDiscoverable: () -> Unit,
    onRequestPerms: () -> Unit,
) {
    val colors = Relay.colors
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        if (!c.onboarded) {
            OnboardingScreen(c, onDone = { c.finishOnboarding() })
            return@Box
        }

        var screen by remember { mutableStateOf(if (c.isConnected) "dashboard" else "pairing") }
        var immersive by remember { mutableStateOf(false) }
        var debugMode by remember { mutableStateOf(false) }
        val chromeHidden = immersive && screen == "keyboard"

        // Immersive keyboard: hide the system bars (top bar + rail are hidden below).
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.LaunchedEffect(chromeHidden) {
            val window = view.context.findActivity()?.window ?: return@LaunchedEffect
            val ctl = androidx.core.view.WindowCompat.getInsetsController(window, view)
            if (chromeHidden) {
                ctl.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                ctl.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                ctl.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }

        Column(Modifier.fillMaxSize().then(if (chromeHidden) Modifier else Modifier.safeDrawingPadding())) {
            if (!chromeHidden) {
                TopBar(c, onSettings = { screen = if (screen == "settings") "dashboard" else "settings" },
                    onConnClick = { screen = "pairing" }, onLongPressLogo = { debugMode = true })
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (!chromeHidden) Rail(active = screen, connected = c.isConnected, onNav = { screen = it })
                Box(Modifier.weight(1f).fillMaxHeight().background(colors.bg)) {
                    ScreenContent(
                        screen, c, permsGranted, onMakeDiscoverable, onRequestPerms,
                        onNav = { screen = it },
                        immersive = immersive,
                        onToggleImmersive = { immersive = !immersive },
                    )
                }
            }
        }

        if (debugMode) DebugOverlay(onClose = { debugMode = false })
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun ScreenContent(
    screen: String,
    c: RelayController,
    permsGranted: Boolean,
    onMakeDiscoverable: () -> Unit,
    onRequestPerms: () -> Unit,
    onNav: (String) -> Unit,
    immersive: Boolean,
    onToggleImmersive: () -> Unit,
) {
    when (screen) {
        "pairing" -> com.cadayn.hidinput.ui.screens.PairingScreen(c, onMakeDiscoverable)
        "keyboard" -> com.cadayn.hidinput.ui.screens.KeyboardScreen(c, immersive, onToggleImmersive)
        "send" -> com.cadayn.hidinput.ui.screens.SendScreen(c)
        "remote" -> com.cadayn.hidinput.ui.screens.RemoteScreen(c)
        "dashboard" -> com.cadayn.hidinput.ui.screens.DashboardScreen(
            c, onOpenKeyboard = { onNav("keyboard") }, onConnect = { onNav("pairing") })
        "settings" -> com.cadayn.hidinput.ui.screens.SettingsScreen(c)
        "customize" -> com.cadayn.hidinput.ui.screens.CustomizeScreen(c)
        else -> Unit
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TopBar(c: RelayController, onSettings: () -> Unit, onConnClick: () -> Unit, onLongPressLogo: () -> Unit) {
    val colors = Relay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.clip(RoundedCornerShape(7.dp)).combinedClickable(onClick = {}, onLongClick = onLongPressLogo)) { RelayLogo() }
        Text("RELAY", style = Relay.type.h2.copy(color = colors.text, fontSize = 15.sp, letterSpacing = 0.14.em))
        Box(
            Modifier.clip(RoundedCornerShape(5.dp)).background(colors.bgDeep)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) { Text("v1.0", style = Relay.type.mono.copy(color = colors.textFaint, fontSize = 10.sp)) }

        Spacer(Modifier.weight(1f))

        // connection chip
        Row(
            Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(colors.bgDeep)
                .clickable(onClick = onConnClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            val dot = if (c.online) colors.accent else colors.textFaint
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dot))
            if (c.online) {
                Text(c.activeName ?: "Host", style = Relay.type.body.copy(color = colors.text, fontSize = 12.5.sp), maxLines = 1)
                Text(if (c.wifiConnected) "WiFi" else "${c.latency}ms", style = Relay.type.mono.copy(color = colors.accent2, fontSize = 11.sp))
            } else {
                Text("Not connected", style = Relay.type.body.copy(color = colors.textFaint, fontSize = 12.5.sp))
            }
        }

        // settings gear
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) { GearIcon(colors.textDim) }
    }
}

@Composable
private fun Rail(active: String, connected: Boolean, onNav: (String) -> Unit) {
    val colors = Relay.colors
    Column(
        Modifier.width(62.dp).fillMaxHeight().background(colors.surface).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val items = listOf("dashboard" to "HUB", "keyboard" to "KEYS", "send" to "SEND", "remote" to "REMOTE", "customize" to "STYLE", "settings" to "SET")
        items.forEach { (id, label) ->
            val isActive = active == id
            val enabled = true   // keyboard view is reachable even before connecting
            val fg = when { isActive -> colors.accent; !enabled -> colors.textFaint.copy(alpha = 0.4f); else -> colors.textFaint }
            Column(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) colors.accentGhost else Color.Transparent)
                    .clickable(enabled = enabled) { onNav(id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                RailIcon(id, fg)
                Spacer(Modifier.height(3.dp))
                Text(label, style = Relay.type.label.copy(color = fg, fontSize = 8.5.sp))
            }
        }
    }
}

/* ---- minimal Canvas icons ---- */
@Composable
private fun RailIcon(id: String, color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        when (id) {
            "dashboard" -> { // 2x2 grid
                val g = s * 0.34f; val gap = s * 0.16f
                listOf(Offset(0f, 0f), Offset(g + gap, 0f), Offset(0f, g + gap), Offset(g + gap, g + gap)).forEach {
                    drawRoundRect(color, topLeft = it, size = androidx.compose.ui.geometry.Size(g, g),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.06f), style = Stroke(s * 0.09f))
                }
            }
            "keyboard" -> {
                drawRoundRect(color, topLeft = Offset(0f, s * 0.2f),
                    size = androidx.compose.ui.geometry.Size(s, s * 0.6f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.12f), style = Stroke(s * 0.09f))
                listOf(0.28f, 0.5f, 0.72f).forEach { drawCircle(color, s * 0.03f, Offset(s * it, s * 0.42f)) }
                drawLine(color, Offset(s * 0.3f, s * 0.62f), Offset(s * 0.7f, s * 0.62f), s * 0.08f)
            }
            "customize" -> { // sliders
                drawLine(color, Offset(0f, s * 0.3f), Offset(s, s * 0.3f), s * 0.08f)
                drawLine(color, Offset(0f, s * 0.7f), Offset(s, s * 0.7f), s * 0.08f)
                drawCircle(color, s * 0.12f, Offset(s * 0.66f, s * 0.3f))
                drawCircle(color, s * 0.12f, Offset(s * 0.34f, s * 0.7f))
            }
            "send" -> { // paper-plane arrow
                drawLine(color, Offset(s * 0.08f, s * 0.5f), Offset(s * 0.92f, s * 0.5f), s * 0.09f)
                drawLine(color, Offset(s * 0.55f, s * 0.2f), Offset(s * 0.92f, s * 0.5f), s * 0.09f)
                drawLine(color, Offset(s * 0.55f, s * 0.8f), Offset(s * 0.92f, s * 0.5f), s * 0.09f)
            }
            "remote" -> { // d-pad: body + center
                drawRoundRect(color, topLeft = Offset(s * 0.28f, 0f),
                    size = androidx.compose.ui.geometry.Size(s * 0.44f, s),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.14f), style = Stroke(s * 0.09f))
                drawCircle(color, s * 0.1f, Offset(s * 0.5f, s * 0.32f))
            }
            else -> GearOnCanvas(this, color)
        }
    }
}

@Composable
private fun GearIcon(color: Color) {
    Canvas(Modifier.size(18.dp)) { GearOnCanvas(this, color) }
}

private fun GearOnCanvas(scope: DrawScope, color: Color) = with(scope) {
    val s = size.minDimension
    drawCircle(color, s * 0.22f, center = androidx.compose.ui.geometry.Offset(s / 2, s / 2), style = Stroke(s * 0.09f))
    for (i in 0 until 8) {
        val a = Math.toRadians((i * 45).toDouble())
        val cx = s / 2 + (s * 0.38f) * Math.cos(a).toFloat()
        val cy = s / 2 + (s * 0.38f) * Math.sin(a).toFloat()
        drawCircle(color, s * 0.05f, androidx.compose.ui.geometry.Offset(cx, cy))
    }
}
