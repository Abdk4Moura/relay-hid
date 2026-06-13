package com.cadayn.hidinput.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cadayn.hidinput.ui.Feel
import com.cadayn.hidinput.ui.RelayController
import com.cadayn.hidinput.ui.components.BtnKind
import com.cadayn.hidinput.ui.components.RelayButton
import com.cadayn.hidinput.ui.components.TText
import com.cadayn.hidinput.ui.theme.Relay

private const val MAX_TRIALS = 6

/** Staircase state for the preference duel: converge on the user's preferred Feel detent. */
private class Duel {
    var current = Feel.NEUTRAL
    var step = 2
    var lastDir = 0
    var reversals = 0
    var trial = 0
    fun aIdx() = (current - step).coerceIn(0, Feel.DETENTS - 1)   // calmer side
    fun bIdx() = (current + step).coerceIn(0, Feel.DETENTS - 1)   // snappier side
    val done get() = trial >= MAX_TRIALS || (reversals >= 2 && step == 1)
    /** dir: -1 = calmer (A) preferred, +1 = snappier (B), 0 = same. */
    fun record(dir: Int) {
        if (dir == 0) { step = (step - 1).coerceAtLeast(1) }
        else {
            current = (current + dir * step).coerceIn(0, Feel.DETENTS - 1)
            if (lastDir != 0 && dir != lastDir) { reversals++; step = (step - 1).coerceAtLeast(1) }
            lastDir = dir
        }
        trial++
    }
}

/**
 * The ~20-second preference duel. A few rounds of "which felt better, A or B?", each compared on the
 * real trackpad, drive a 1-up/1-down staircase that places the user's Feel detent. Forced choice after
 * a real movement is a behavioral read, not gut feel. Design: docs/comfort-calibration-research.md.
 */
@Composable
fun CalibrateScreen(c: RelayController, onDone: () -> Unit) {
    val col = Relay.colors
    val view = LocalView.current
    val original = remember { c.feelPointer }
    val duel = remember { Duel() }
    var phase by remember { mutableStateOf("intro") }   // intro | duel | result
    var showing by remember { mutableStateOf("A") }      // which candidate is loaded
    var roundKey by remember { mutableStateOf(0) }        // bumps each round to reset the A/B toggle

    fun loadCurrentRound(which: String) {
        showing = which
        c.previewFeel(if (which == "A") duel.aIdx() else duel.bIdx())
    }

    Box(Modifier.fillMaxSize().background(col.bg).padding(24.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            when (phase) {
                "intro" -> {
                    Spacer(Modifier.height(20.dp))
                    TText("Find your feel", Relay.type.h1, col.text)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "A quick feel check. You will compare two settings a few times on the trackpad and pick which felt better. About 20 seconds.",
                        style = Relay.type.body.copy(color = col.textDim, fontSize = 15.sp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tip: have a window to scroll and a target to point at on your connected machine.",
                        style = Relay.type.mono.copy(color = col.textFaint, fontSize = 12.sp),
                    )
                    Spacer(Modifier.height(28.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RelayButton("Start", { duel.trial = 0; loadCurrentRound("A"); phase = "duel" }, kind = BtnKind.Primary, large = true)
                        RelayButton("Skip", { c.previewFeel(original); onDone() }, kind = BtnKind.Ghost, large = true)
                    }
                }
                "duel" -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TText("Round ${duel.trial + 1}", Relay.type.h2, col.text)
                        Text("of about $MAX_TRIALS", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 12.sp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Flip between A and B, try each, then pick the one that felt better.",
                        style = Relay.type.body.copy(color = col.textDim, fontSize = 14.sp))
                    Spacer(Modifier.height(12.dp))
                    // A/B toggle
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ABChip("A", showing == "A", Modifier.weight(1f)) { loadCurrentRound("A") }
                        ABChip("B", showing == "B", Modifier.weight(1f)) { loadCurrentRound("B") }
                    }
                    Spacer(Modifier.height(12.dp))
                    // the real trackpad, feeling the loaded candidate live
                    Box(
                        Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))
                            .background(col.bgDeep).border(1.dp, col.border, RoundedCornerShape(16.dp))
                            .trackpadGestures(c, view),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("trying  ·  ${showing}  ·  move & scroll here",
                            style = Relay.type.mono.copy(color = col.textFaint, fontSize = 11.sp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Which felt better?", style = Relay.type.body.copy(color = col.text, fontSize = 15.sp))
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VerdictBtn("A", Modifier.weight(1f)) { advance(c, duel, -1) { phase = it; roundKey++; if (it == "duel") loadCurrentRound("A") } }
                        VerdictBtn("Same", Modifier.weight(1f)) { advance(c, duel, 0) { phase = it; roundKey++; if (it == "duel") loadCurrentRound("A") } }
                        VerdictBtn("B", Modifier.weight(1f)) { advance(c, duel, 1) { phase = it; roundKey++; if (it == "duel") loadCurrentRound("A") } }
                    }
                }
                "result" -> {
                    Spacer(Modifier.height(20.dp))
                    TText("Set to ${Feel.label(c.feelPointer)}", Relay.type.h1, col.text)
                    Spacer(Modifier.height(12.dp))
                    Text("Your trackpad feel is dialed in. You can fine-tune it any time from Settings.",
                        style = Relay.type.body.copy(color = col.textDim, fontSize = 15.sp))
                    Spacer(Modifier.height(28.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RelayButton("Done", onDone, kind = BtnKind.Primary, large = true)
                        RelayButton("Redo", { duel.current = Feel.NEUTRAL; duel.step = 2; duel.lastDir = 0; duel.reversals = 0; duel.trial = 0; loadCurrentRound("A"); phase = "duel" }, kind = BtnKind.Ghost, large = true)
                    }
                }
            }
        }
    }
}

/** Apply a verdict, advance the staircase, and either start the next round or commit the result. */
private fun advance(c: RelayController, duel: Duel, dir: Int, setPhase: (String) -> Unit) {
    duel.record(dir)
    if (duel.done) {
        c.updateFeel(duel.current)     // commit (persists; also sets scroll feel when linked)
        setPhase("result")
    } else {
        setPhase("duel")
    }
}

@Composable
private fun ABChip(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val col = Relay.colors
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (on) col.accentGhost else col.surface)
            .border(1.dp, if (on) col.accentDim else col.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Relay.type.h2.copy(color = if (on) col.accent else col.textDim, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun VerdictBtn(label: String, modifier: Modifier, onClick: () -> Unit) {
    val col = Relay.colors
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(12.dp))
            .background(col.surface2).border(1.dp, col.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Relay.type.body.copy(color = col.text, fontSize = 15.sp))
    }
}
