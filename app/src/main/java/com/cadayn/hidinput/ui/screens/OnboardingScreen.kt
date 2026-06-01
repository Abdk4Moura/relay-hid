package com.cadayn.hidinput.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cadayn.hidinput.ui.RelayController
import com.cadayn.hidinput.ui.components.BtnKind
import com.cadayn.hidinput.ui.components.Eyebrow
import com.cadayn.hidinput.ui.components.RelayButton
import com.cadayn.hidinput.ui.components.TText
import com.cadayn.hidinput.ui.theme.Relay
import com.cadayn.hidinput.ui.theme.SpaceGrotesk

private data class Panel(val eyebrow: String, val title: String, val body: String, val art: String)

private val PANELS = listOf(
    Panel(
        "No app required on the tablet",
        "Your phone is the keyboard.",
        "Relay turns your Android phone into a full Bluetooth keyboard and trackpad — for your iPad, Mac, smart TV, or anything that accepts a keyboard.",
        "kbd",
    ),
    Panel(
        "Standard Bluetooth · HID",
        "Pairs like any real keyboard.",
        "Relay presents itself as a standard Human Interface Device. The other device just “discovers” it in Bluetooth settings — system-wide, nothing to install on the other end.",
        "pair",
    ),
    Panel(
        "Every key · every modifier",
        "A complete replacement.",
        "⌘ Command, ⌥ Option, ⌃ Control, ⇧ Shift, arrows, function keys, trackpad gestures — the full desktop layout, in your pocket.",
        "mods",
    ),
)

@Composable
fun OnboardingScreen(ctrl: RelayController, onDone: () -> Unit) {
    val c = Relay.colors
    var step by remember { mutableIntStateOf(0) }
    val last = PANELS.size                 // the device picker is the final step
    val onPicker = step == last

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding(),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val d = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(320)) { w -> d * w / 3 } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(240)) { w -> -d * w / 3 } + fadeOut(tween(200)))
                },
                label = "panel",
            ) { s ->
                if (s == PANELS.size) DevicePicker(ctrl) else PanelBody(PANELS[s])
            }
        }

        // footer
        Row(
            Modifier.fillMaxWidth().border(0.dp, c.border).background(c.surface).padding(horizontal = 40.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(PANELS.size + 1) { i ->
                    val w by animateDpAsState(if (i == step) 22.dp else 7.dp, label = "dot")
                    Box(
                        Modifier
                            .height(7.dp)
                            .width(w)
                            .clip(CircleShape)
                            .background(if (i == step) c.accent else c.borderHi),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (step > 0) {
                RelayButton("Back", { step-- }, kind = BtnKind.Ghost)
                Spacer(Modifier.width(8.dp))
            }
            RelayButton(
                if (onPicker) "Set up Relay" else "Next",
                { if (onPicker) onDone() else step++ },
                kind = BtnKind.Primary,
                large = true,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DevicePicker(ctrl: RelayController) {
    val c = Relay.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Eyebrow("One last thing")
        Spacer(Modifier.height(14.dp))
        Text("What are you controlling?",
            style = Relay.type.h1.copy(color = c.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(10.dp))
        TText("Sets the right modifier keys and shortcuts for your host. You can change it anytime in Settings.",
            Relay.type.sub.copy(fontSize = 14.sp), c.textDim, Modifier.widthIn(max = 460.dp), align = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        val opts = listOf(
            "ipad" to "iPad", "mac" to "Mac", "windows" to "Windows", "androidtv" to "Android TV",
            "appletv" to "Apple TV", "linux" to "Linux", "ps" to "PlayStation",
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.widthIn(max = 540.dp),
        ) {
            opts.forEach { (v, lbl) ->
                val on = ctrl.profile == v
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (on) c.accentGhost else c.surface)
                        .border(if (on) 1.5.dp else 1.dp, if (on) c.accent else c.border, RoundedCornerShape(12.dp))
                        .clickable { ctrl.updateProfile(v) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Text(lbl, style = Relay.type.body.copy(color = if (on) c.accent else c.textDim, fontSize = 15.sp))
                }
            }
        }
    }
}

/** Responsive intro panel: art-banner-on-top in portrait, side-by-side in landscape. */
@Composable
private fun PanelBody(p: Panel) {
    val c = Relay.colors
    val portrait = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    @Composable fun Copy(modifier: Modifier) = Column(modifier, verticalArrangement = Arrangement.Center) {
        Eyebrow(p.eyebrow)
        Spacer(Modifier.height(16.dp))
        Text(p.title, style = Relay.type.h1.copy(color = c.text, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
            modifier = if (portrait) Modifier.fillMaxWidth() else Modifier.widthIn(max = 360.dp))
        Spacer(Modifier.height(14.dp))
        TText(p.body, Relay.type.sub.copy(fontSize = 14.5.sp), c.textDim, if (portrait) Modifier.fillMaxWidth() else Modifier.widthIn(max = 360.dp))
    }
    @Composable fun Art(modifier: Modifier) = Box(
        modifier.background(Brush.radialGradient(listOf(c.surface, c.bg))).border(1.dp, c.border),
        contentAlignment = Alignment.Center,
    ) { OnboardArt(p.art) }
    if (portrait) {
        Column(Modifier.fillMaxSize()) {
            Art(Modifier.fillMaxWidth().weight(0.42f))
            Copy(Modifier.fillMaxWidth().weight(0.58f).padding(horizontal = 34.dp))
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            Copy(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 40.dp))
            Art(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun OnboardArt(kind: String) {
    val c = Relay.colors
    val t = rememberInfiniteTransition(label = "art")
    when (kind) {
        // keys with a diagonal highlight wave travelling across them
        "kbd" -> {
            val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = androidx.compose.animation.core.LinearEasing)), label = "kbd")
            Column(Modifier.rotate(-4f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                listOf(10, 11, 9).forEachIndexed { row, n ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(n) { col ->
                            val pos = (col + row).toFloat() / 13f
                            val d = abs(((phase - pos + 1f) % 1f) - 0f)
                            val glow = (1f - d * 5f).coerceIn(0f, 1f)
                            Box(
                                Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                                    .background(Brush.verticalGradient(listOf(lerp(c.keyTop, c.accent, glow * 0.5f), lerp(c.keyBot, c.accent, glow * 0.25f))))
                                    .border(1.dp, lerp(c.keyEdge, c.accent, glow), RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
            }
        }
        // signal pulse flowing phone → tablet
        "pair" -> {
            val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = androidx.compose.animation.core.LinearEasing)), label = "pair")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                Box(Modifier.size(72.dp, 110.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, c.borderHi, RoundedCornerShape(14.dp)), Alignment.Center) {
                    com.cadayn.hidinput.ui.components.RelayLogo(size = 28, corner = 9)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { i ->
                        val d = abs(((phase - i / 3f + 1f) % 1f))
                        val a = (1f - d * 3f).coerceIn(0.12f, 1f)
                        Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent.copy(alpha = a)))
                    }
                }
                val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = androidx.compose.animation.core.LinearEasing)), label = "pulseb")
                Box(Modifier.size(120.dp, 90.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, lerp(c.borderHi, c.accent, (1f - abs(pulse - 0.5f) * 2f) * 0.7f), RoundedCornerShape(12.dp)))
            }
        }
        // four modifier tiles lighting up in sequence
        else -> {
            val phase by t.animateFloat(0f, 4f, infiniteRepeatable(tween(3600, easing = androidx.compose.animation.core.LinearEasing)), label = "mods")
            val active = phase.toInt().coerceIn(0, 3)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(listOf("⌘", "⌥"), listOf("⌃", "⇧")).forEachIndexed { r, rowG ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        rowG.forEachIndexed { col, g ->
                            val idx = r * 2 + col
                            val on = idx == active
                            Box(
                                Modifier.size(74.dp).clip(RoundedCornerShape(16.dp))
                                    .background(if (on) c.accentGhost else c.surface)
                                    .border(if (on) 1.5.dp else 1.dp, if (on) c.accent else c.border, RoundedCornerShape(16.dp)),
                                Alignment.Center,
                            ) { Text(g, style = Relay.type.mono.copy(color = c.accent, fontSize = 28.sp)) }
                        }
                    }
                }
            }
        }
    }
}
