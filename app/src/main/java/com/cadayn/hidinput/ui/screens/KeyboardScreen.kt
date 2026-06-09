package com.cadayn.hidinput.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cadayn.hidinput.HidConstants
import com.cadayn.hidinput.HidKeymap
import com.cadayn.hidinput.ui.RelayController
import com.cadayn.hidinput.ui.theme.Relay
import kotlin.math.abs
import kotlin.math.roundToInt

/* ============================ key models ============================ */
private data class K(
    val label: String, val sub: String? = null, val code: String? = null,
    val mod: String? = null, val weight: Float = 1f, val glyph: Boolean = false,
    // Unexpected-Keyboard style corner slides — chars emitted when you flick toward a corner.
    val ne: String? = null, val nw: String? = null, val se: String? = null, val sw: String? = null,
) {
    val hasCorners get() = ne != null || nw != null || se != null || sw != null
}

// A letter key carrying a digit (NE) and its shifted symbol (NW) in the corners.
private fun L(letter: String, ne: String? = null, nw: String? = null, se: String? = null, sw: String? = null) =
    K(letter, ne = ne, nw = nw, se = se, sw = sw)

/* No dedicated number row: digits 1-0 live on the NE corner of the top letter row,
 * their shifted symbols on NW, and the remaining ASCII symbols on SE/SW corners.
 * Slide a key toward a corner to emit it. */
private val ROWS: List<List<K>> = listOf(
    listOf(K("⇥", code = "Tab", weight = 1.4f, glyph = true)) + listOf(
        L("q", ne = "1", nw = "!", sw = "~"), L("w", ne = "2", nw = "@"), L("e", ne = "3", nw = "#"),
        L("r", ne = "4", nw = "$"), L("t", ne = "5", nw = "%"), L("y", ne = "6", nw = "^"),
        L("u", ne = "7", nw = "&", se = "-"), L("i", ne = "8", nw = "*", se = "="),
        L("o", ne = "9", nw = "(", se = "["), L("p", ne = "0", nw = ")", se = "]"),
    ) + listOf(K("⌫", code = "Backspace", weight = 1.5f, glyph = true)),
    listOf(K("⇪", mod = "caps", weight = 1.4f, glyph = true)) + listOf(
        L("a", se = "\\", sw = "`"), L("s", se = "|"), L("d"), L("f"), L("g"), L("h"),
        L("j"), L("k"), L("l", ne = "_", nw = "+"),
    ) + listOf(K(";", ne = ":"), K("'", ne = "\""), K("return", code = "Enter", weight = 1.8f)),
    listOf(K("⇧", mod = "shift", weight = 2f, glyph = true)) + listOf(
        L("z"), L("x"), L("c"), L("v"), L("b"), L("n"), L("m"),
    ) + listOf(K(",", ne = "<"), K(".", ne = ">"), K("/", ne = "?"), K("⇧", mod = "shift", weight = 2f, glyph = true)),
)

/* System row. Keyboard combos (mod+key) for nav/shortcuts; Consumer-Control usages (cc) for media. */
private data class Sys(val label: String, val mod: Int = 0, val key: Int = 0, val cc: Int = 0)

/** System row tailored to the selected target device — only shows shortcuts that work there. */
private fun sysRowFor(profile: String): List<Sys> {
    val esc = Sys("esc", key = HidConstants.KEY_ESC)
    val home = Sys("home", cc = HidConstants.CC_HOME)
    val media = listOf(
        Sys("prev", cc = HidConstants.CC_PREV), Sys("play", cc = HidConstants.CC_PLAY_PAUSE), Sys("next", cc = HidConstants.CC_NEXT),
        Sys("mute", cc = HidConstants.CC_MUTE), Sys("vol−", cc = HidConstants.CC_VOL_DOWN), Sys("vol+", cc = HidConstants.CC_VOL_UP),
    )
    val bri = listOf(Sys("bri−", cc = HidConstants.CC_BRI_DOWN), Sys("bri+", cc = HidConstants.CC_BRI_UP))
    val spot = Sys("spot", HidConstants.MOD_LGUI, HidConstants.KEY_SPACE)
    val appsCmd = Sys("apps", HidConstants.MOD_LGUI, HidConstants.KEY_TAB)
    val appsAlt = Sys("apps", HidConstants.MOD_LALT, HidConstants.KEY_TAB)
    // Lone Super/Win press (key code 0 = modifier only) → Start menu / Activities. Single tap,
    // unambiguous — no arming, no double-tap.
    val start = Sys("Start", HidConstants.MOD_LGUI)
    val superK = Sys("Super", HidConstants.MOD_LGUI)
    return when (profile) {
        "ipad" -> listOf(esc, spot, home, appsCmd) + media + bri
        "mac" -> listOf(esc, spot, appsCmd) + media + bri
        "appletv" -> listOf(Sys("menu", key = HidConstants.KEY_ESC), home) + media
        "androidtv" -> listOf(Sys("back", key = HidConstants.KEY_ESC), home) + media
        "windows" -> listOf(esc, start, Sys("search", HidConstants.MOD_LGUI, HidKeymap.charToKey('s')!!.second), appsAlt) + media
        "linux" -> listOf(esc, superK, appsAlt) + media
        "ps" -> listOf(home, Sys("play", cc = HidConstants.CC_PLAY_PAUSE), Sys("vol−", cc = HidConstants.CC_VOL_DOWN), Sys("mute", cc = HidConstants.CC_MUTE), Sys("vol+", cc = HidConstants.CC_VOL_UP))
        else -> listOf(esc) + media
    }
}

private enum class Layer { BASE, NUM, SYM, FN }
private val F_KEYS = listOf("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12")
private sealed class TK {
    data class Ch(val c: Char, val w: Float = 1f,
        val ne: String? = null, val nw: String? = null, val se: String? = null, val sw: String? = null) : TK()
    data class Act(val id: String, val show: String, val w: Float = 1f) : TK()
}
private fun ch(s: String) = s.map { TK.Ch(it) }

// Top thumb row carries digits (NE) + shifted symbols (NW) in the corners — slide to reach them.
private val THUMB_TOP = listOf(
    TK.Ch('q', ne = "1", nw = "!"), TK.Ch('w', ne = "2", nw = "@"), TK.Ch('e', ne = "3", nw = "#"),
    TK.Ch('r', ne = "4", nw = "$"), TK.Ch('t', ne = "5", nw = "%"), TK.Ch('y', ne = "6", nw = "^"),
    TK.Ch('u', ne = "7", nw = "&"), TK.Ch('i', ne = "8", nw = "*"), TK.Ch('o', ne = "9", nw = "("), TK.Ch('p', ne = "0", nw = ")"),
)

private val THUMB_BASE = listOf(
    THUMB_TOP,
    ch("asdfghjkl"),
    listOf(TK.Act("shift", "⇧", 1.5f)) + ch("zxcvbnm") + TK.Act("bksp", "⌫", 1.5f),
    listOf(TK.Act("l123", "123", 1.6f), TK.Ch(','), TK.Act("space", "space", 5f), TK.Ch('.'), TK.Act("enter", "⏎", 1.6f)),
)
private val THUMB_NUM = listOf(
    ch("1234567890"),
    listOf('-', '/', ':', ';', '(', ')', '$', '&', '@', '"').map { TK.Ch(it) },
    listOf(TK.Act("lSym", "#+=", 1.5f), TK.Ch('.'), TK.Ch(','), TK.Ch('?'), TK.Ch('!'), TK.Ch('\''), TK.Act("bksp", "⌫", 1.5f)),
    listOf(TK.Act("lABC", "ABC", 1.5f), TK.Act("lFn", "Fn", 1.4f), TK.Act("space", "space", 5.4f), TK.Act("enter", "⏎", 1.5f)),
)
// Function-key layer (reached via the Fn key on the number layer). Armed modifiers still apply,
// so e.g. arm Alt then tap F4 → Alt+F4.
private val THUMB_FN = listOf(
    listOf("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10").map { TK.Act(it, it) },
    listOf(TK.Act("F11", "F11"), TK.Act("F12", "F12"), TK.Act("esc", "esc"), TK.Act("Tab", "⇥"), TK.Act("bksp", "⌫")),
    listOf(TK.Act("lABC", "ABC", 1.6f), TK.Act("l123", "123", 1.4f), TK.Act("space", "space", 5f), TK.Act("enter", "⏎", 1.6f)),
)
private val THUMB_SYM = listOf(
    listOf('[', ']', '{', '}', '#', '%', '^', '*', '+', '=').map { TK.Ch(it) },
    listOf('_', '\\', '|', '~', '<', '>', '=', '+', '`', '"').map { TK.Ch(it) },
    listOf(TK.Act("l123", "123", 1.5f), TK.Ch('.'), TK.Ch(','), TK.Ch('?'), TK.Ch('!'), TK.Ch('\''), TK.Act("bksp", "⌫", 1.5f)),
    listOf(TK.Act("lABC", "ABC", 1.6f), TK.Act("space", "space", 7f), TK.Act("enter", "⏎", 1.6f)),
)

/* ============================ screen ============================ */
@Composable
fun KeyboardScreen(c: RelayController, immersive: Boolean, onToggleImmersive: () -> Unit) {
    val col = Relay.colors
    var ctrl by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var gui by remember { mutableStateOf(false) }
    var caps by remember { mutableStateOf(false) }
    var loneMode by remember { mutableStateOf(false) }   // sticky: when on, a modifier chip fires ALONE (lone press)
    var lastShiftTap by remember { mutableLongStateOf(0L) }
    var lastGuiTap by remember { mutableLongStateOf(0L) }
    var lastCombo by remember { mutableStateOf("—") }
    var flashId by remember { mutableStateOf<String?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    var layer by remember { mutableStateOf(Layer.BASE) }
    var buf by remember { mutableStateOf("") }           // local mirror of typed text (portrait preview)
    var caret by remember { mutableIntStateOf(0) }
    var previewTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(flashTick) { if (flashId != null) { delay(140); flashId = null } }
    LaunchedEffect(previewTick) { if (buf.isNotEmpty()) { delay(8000); buf = ""; caret = 0 } }
    fun insertPreview(s: String) { buf = buf.substring(0, caret) + s + buf.substring(caret); caret += s.length; previewTick++ }
    fun backspacePreview() { if (caret > 0) { buf = buf.removeRange(caret - 1, caret); caret-- }; previewTick++ }
    fun moveCaret(d: Int) { caret = (caret + d).coerceIn(0, buf.length); previewTick++ }

    fun modMask(): Int {
        var m = 0
        val ctrlBit = if (c.swapCmd) HidConstants.MOD_LGUI else HidConstants.MOD_LCTRL
        val guiBit = if (c.swapCmd) HidConstants.MOD_LCTRL else HidConstants.MOD_LGUI
        if (ctrl) m = m or ctrlBit
        if (gui) m = m or guiBit
        if (alt) m = m or HidConstants.MOD_LALT
        if (shift) m = m or HidConstants.MOD_LSHIFT
        return m
    }
    fun clearOneShot() { if (c.behavior == "oneshot") { ctrl = false; shift = false; alt = false; gui = false } }
    fun emit(disp: String, extraMod: Int, keycode: Int, flash: String?, keepShift: Boolean = false) {
        c.tapKey(modMask() or extraMod, keycode)
        lastCombo = buildString { if (ctrl) append(c.ctrlKey.first); if (alt) append(c.altKey.first); if (shift) append("⇧"); if (gui) append(c.guiKey.first) } + disp
        c.logEvent("key", lastCombo)
        if (flash != null) { flashId = flash; flashTick++ }
        if (!caps && !keepShift) shift = false   // shift is one-shot on printable keys (phone-style); arrows keep it for shift+select
        clearOneShot()
    }
    // Lone-modifier mode: tapping a modifier chip sends THAT modifier by itself (key code 0 + its bit),
    // e.g. Super alone → Start menu / COSMIC launcher. Mirrors modMask()'s bits (incl. the Cmd/Ctrl swap).
    fun loneBit(which: String): Pair<Int, String> = when (which) {
        "ctrl"  -> (if (c.swapCmd) HidConstants.MOD_LGUI else HidConstants.MOD_LCTRL) to c.ctrlKey.first
        "alt"   -> HidConstants.MOD_LALT to c.altKey.first
        "gui"   -> (if (c.swapCmd) HidConstants.MOD_LCTRL else HidConstants.MOD_LGUI) to c.guiKey.first
        "shift" -> HidConstants.MOD_LSHIFT to "⇧"
        else    -> 0 to ""
    }
    fun tapMod(which: String) {
        if (loneMode && which in setOf("ctrl", "alt", "gui", "shift")) {
            val (bit, label) = loneBit(which)
            c.tapKey(bit, 0); lastCombo = label   // surface it in the SENDING readout
            c.logEvent("key", "$label (alone)")
            return
        }
        when (which) {
            "ctrl" -> ctrl = !ctrl
            "shift" -> {
                val now = System.currentTimeMillis()
                if (now - lastShiftTap < 320L) { caps = !caps; shift = false }   // double-tap Shift → Caps Lock
                else shift = !shift
                lastShiftTap = now
            }
            "alt" -> alt = !alt
            "gui" -> gui = !gui
            "caps" -> if (c.capsEsc) emit("esc", 0, HidConstants.KEY_ESC, null) else caps = !caps
        }
    }
    fun sendKey(k: K, id: String) {
        val kc = baseKeycode(k) ?: return
        val isLetter = k.label.length == 1 && k.label[0] in 'a'..'z'
        val keepShift = k.code == "Up" || k.code == "Down" || k.code == "Left" || k.code == "Right"
        emit(displayLabel(k, shift, caps), if (caps && isLetter) HidConstants.MOD_LSHIFT else 0, kc, id, keepShift)
    }
    fun sendChar(character: Char) {
        val mapped = HidKeymap.charToKey(character) ?: return
        val isLetter = character in 'a'..'z'
        val disp = thumbDisplay(character, shift, caps)
        val command = ctrl || alt || gui   // ⌃/⌥/⌘ → this is a shortcut, not typed text
        emit(disp, mapped.first or (if (caps && isLetter) HidConstants.MOD_LSHIFT else 0), mapped.second, null)
        if (!command) insertPreview(disp)
    }
    fun special(id: String) {
        val command = ctrl || alt || gui   // with a command modifier these are shortcuts, not text edits
        when (id) {
            "space" -> { emit("space", 0, HidConstants.KEY_SPACE, null); if (!command) insertPreview(" ") }
            "enter" -> { emit("⏎", 0, HidConstants.KEY_ENTER, null); if (!command) { buf = ""; caret = 0; previewTick++ } }
            "bksp" -> { emit("⌫", 0, HidConstants.KEY_BACKSPACE, null); if (!command) backspacePreview() }
            "esc" -> emit("esc", 0, HidConstants.KEY_ESC, null)
            "Up" -> emit("↑", 0, HidConstants.KEY_UP, null, keepShift = true)
            "Down" -> emit("↓", 0, HidConstants.KEY_DOWN, null, keepShift = true)
            "Left" -> { emit("←", 0, HidConstants.KEY_LEFT, null, keepShift = true); if (!command) moveCaret(-1) }
            "Right" -> { emit("→", 0, HidConstants.KEY_RIGHT, null, keepShift = true); if (!command) moveCaret(1) }
            "l123" -> layer = Layer.NUM; "lSym" -> layer = Layer.SYM; "lABC" -> layer = Layer.BASE
            "lFn" -> layer = Layer.FN
            "Tab" -> emit("⇥", 0, HidConstants.KEY_TAB, null)
            in F_KEYS -> emit(id, 0, HidConstants.KEY_F1 + (F_KEYS.indexOf(id)), null)  // armed mods apply (e.g. Alt+F4)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(col.bgDeep)) {
        val portrait = maxHeight >= maxWidth
        if (portrait) {
            PortraitThumb(c, layer, shift, caps, ctrl, alt, gui, loneMode, lastCombo, immersive, onToggleImmersive, buf, caret,
                onMod = ::tapMod, onChar = ::sendChar, onSpecial = ::special, onToggleLone = { loneMode = !loneMode },
                onTogglePreview = { c.updateShowPreview(!c.showPreview) })
        } else {
            LandscapeDesktop(c, ctrl, shift, alt, gui, caps, flashId, lastCombo, immersive, onToggleImmersive,
                onMod = ::tapMod, onKey = ::sendKey, onSpecial = ::special, onChar = ::sendChar)
        }
    }
}

/* ============================ portrait thumb keyboard ============================ */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PortraitThumb(
    c: RelayController, layer: Layer, shift: Boolean, caps: Boolean, ctrl: Boolean, alt: Boolean, gui: Boolean,
    loneMode: Boolean, lastCombo: String, immersive: Boolean, onToggleImmersive: () -> Unit, buf: String, caret: Int,
    onMod: (String) -> Unit, onChar: (Char) -> Unit, onSpecial: (String) -> Unit, onToggleLone: () -> Unit,
    onTogglePreview: () -> Unit,
) {
    val col = Relay.colors
    val view = LocalView.current
    val rows = when (layer) { Layer.NUM -> THUMB_NUM; Layer.SYM -> THUMB_SYM; Layer.FN -> THUMB_FN; Layer.BASE -> THUMB_BASE }
    val pl = c.portraitLayout
    val oneHand = pl == "onehanded"
    var padFull by remember { mutableStateOf(false) }
    var demoActive by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    // One resizable layout: trackpad on top, keyboard below; drag the divider to resize the keys
    // (up = bigger keys, down = bigger pad; drag it to the floor = full trackpad).
    val minH = 38f; val maxH = 82f
    var kh by remember { mutableFloatStateOf(c.keyHeight.coerceIn(minH.toInt(), maxH.toInt()).toFloat()) }
    var overshoot by remember { mutableFloatStateOf(0f) }
    val animKh by animateFloatAsState(kh, spring(stiffness = Spring.StiffnessMedium), label = "kh")
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp)) {
      if (padFull) {
        var gyroMode by remember { mutableStateOf(false) }
        ModifierBar(c, ctrl, shift, alt, gui, loneMode, immersive, onToggleImmersive, onMod, onSpecial, c.haptics)
        Spacer(Modifier.height(6.dp))
        val padBase = Modifier.fillMaxWidth().weight(1f).then(trackpadCanvas(c))
        if (gyroMode) {
            GyroPad(c, padBase) { c.mouseMove(0, 0, 0, HidConstants.MOUSE_LEFT); c.mouseMove(0, 0, 0, 0); c.logEvent("click", "primary click") }
        } else {
            Box(padBase.dragSignal(c.dragStyle, dragging).trackpadGestures(c, view, onDrag = { dragging = it })) {
                Text("FULL TRACKPAD · 2-finger scroll · 2-finger tap = right-click",
                    style = Relay.type.mono.copy(color = col.textFaint, fontSize = 9.sp),
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
            }
        }
        DragHandle(onUp = { padFull = false; if (kh < 46f) kh = 52f }, onDown = {}, onTap = { padFull = false; if (kh < 46f) kh = 52f })
        PadButtons(c, gyroMode) { gyroMode = !gyroMode }
      } else {
        // free area is multifunctional: type-preview + trackpad (drag/tap/hold). Long-press the label to demo drag signals.
        if (demoActive) {
          DragStyleDemo(Modifier.fillMaxWidth().weight(1f).then(trackpadCanvas(c))) { c.updateDragStyle(it); demoActive = false }
        } else {
        Box(
            Modifier.fillMaxWidth().weight(1f).then(trackpadCanvas(c)).dragSignal(c.dragStyle, dragging).trackpadGestures(c, view, onDrag = { dragging = it }),
        ) {
            Text("TRACKPAD", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 9.sp),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp).combinedClickable(onClick = {}, onLongClick = { demoActive = true }))
            PreviewChip(c.showPreview, Modifier.align(Alignment.TopEnd).padding(8.dp), onTogglePreview)
            if (c.showPreview && buf.isNotEmpty()) {
                val start = maxOf(0, caret - 28)
                val end = minOf(buf.length, caret + 14)
                val pre = (if (start > 0) "…" else "") + buf.substring(start, caret)
                val post = buf.substring(caret, end) + (if (end < buf.length) "…" else "")
                Row(
                    Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(pre, style = Relay.type.h1.copy(color = col.text, fontSize = 22.sp), maxLines = 1)
                    Box(Modifier.width(2.dp).height(26.dp).background(col.accent))
                    Text(post, style = Relay.type.h1.copy(color = col.textDim, fontSize = 22.sp), maxLines = 1)
                }
            } else {
                Text("tap = click · drag = move · hold = drag & drop", style = Relay.type.sub.copy(color = col.textFaint, fontSize = 12.sp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp))
            }
        }
        }
        // draggable divider — resize the keyboard; drag it all the way down to collapse to a full trackpad
        ResizeHandle(
            onResize = { dyDp ->
                val target = kh - dyDp
                when {
                    target < minH -> { overshoot += (minH - target); kh = minH; if (overshoot > 60f) padFull = true }
                    target > maxH -> kh = maxH
                    else -> { kh = target; overshoot = 0f }
                }
            },
            onSettle = { overshoot = 0f; c.updateKeyHeight(kh.roundToInt()) },
        )
        // Thin strip below the trackpad handle: sticky LONE switch on the left (when on, tapping any
        // modifier chip sends it BY ITSELF → e.g. Super alone = Start/launcher), live readout centered.
        Box(Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 4.dp, vertical = 2.dp)) {
            LoneSwitch(loneMode, c.haptics, Modifier.align(Alignment.CenterStart), onToggleLone)
            ScrollLockSwitch(c, Modifier.align(Alignment.CenterEnd))
            if (c.showReadout) {
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Text("SENDING ", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 10.sp))
                    Text(lastCombo, style = Relay.type.monoSemi.copy(color = col.accent2, fontSize = 12.sp))
                    Text("  →  ${c.deviceName ?: "no device"}", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 10.sp))
                }
            }
        }
        ModifierBar(c, ctrl, shift, alt, gui, loneMode, immersive, onToggleImmersive, onMod, onSpecial, c.haptics)
        Spacer(Modifier.height(8.dp))
        if (oneHand) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = if (c.oneHandSide == "right") Arrangement.Start else Arrangement.End) {
                FlipSideChip(c.oneHandSide) { c.updateOneHandSide(if (c.oneHandSide == "right") "left" else "right") }
            }
        }
        ThumbKeyboard(c, rows, shift, caps, view, rowHeight = animKh.dp,
            oneHandSide = if (oneHand) c.oneHandSide else null, onMod = onMod, onChar = onChar, onSpecial = onSpecial)
      }
    }
}

/** One thumb-keyboard key (letter / corner-slide / space / action). */
@Composable
private fun RowScope.ThumbItem(
    c: RelayController, tk: TK, shift: Boolean, caps: Boolean, view: android.view.View,
    onMod: (String) -> Unit, onChar: (Char) -> Unit, onSpecial: (String) -> Unit,
) {
    when (tk) {
        is TK.Ch -> if (tk.ne != null || tk.nw != null || tk.se != null || tk.sw != null)
            ThumbCharKey(tk, shift, caps, c.haptics, c.slideAnim, view, c.keyRepeat, onChar)
        else ThumbCap(thumbDisplay(tk.c, shift, caps), tk.w, action = false, armed = false, c.haptics, repeat = c.keyRepeat) { onChar(tk.c) }
        is TK.Act -> if (tk.id == "space")
            SpaceKey(tk.w, sculpted = c.keycap == "sculpted", haptic = c.haptics, sensitivity = c.sensitivity, accel = c.accel, sideBias = c.sideBias, onSpace = { onSpecial("space") }, onArrow = onSpecial)
        else ThumbCap(if (tk.id == "shift" && caps) "⇪" else tk.show, tk.w, action = true, armed = tk.id == "shift" && (shift || caps), c.haptics, repeat = tk.id == "bksp") {
            if (tk.id == "shift") onMod("shift") else onSpecial(tk.id)
        }
    }
}

/** The thumb keyboard body — rows at [rowHeight]; optionally side-shifted for one-handed reach. */
@Composable
private fun ThumbKeyboard(
    c: RelayController, rows: List<List<TK>>, shift: Boolean, caps: Boolean, view: android.view.View,
    rowHeight: Dp, oneHandSide: String?,
    onMod: (String) -> Unit, onChar: (Char) -> Unit, onSpecial: (String) -> Unit,
) {
    val gap = c.keyGap.dp
    val body: @Composable ColumnScope.() -> Unit = {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().height(rowHeight), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { ThumbItem(c, it, shift, caps, view, onMod, onChar, onSpecial) }
            }
        }
    }
    if (oneHandSide != null) {
        Row(Modifier.fillMaxWidth()) {
            if (oneHandSide == "right") Spacer(Modifier.weight(0.30f))
            Column(Modifier.weight(0.70f), verticalArrangement = Arrangement.spacedBy(gap), content = body)
            if (oneHandSide == "left") Spacer(Modifier.weight(0.30f))
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap), content = body)
    }
}

/** Draggable divider between trackpad and keyboard — reports vertical drag in dp; lively pill grip. */
@Composable
private fun ResizeHandle(onResize: (Float) -> Unit, onSettle: () -> Unit) {
    val col = Relay.colors
    val density = LocalDensity.current.density
    val infinite = rememberInfiniteTransition(label = "rh")
    val pulse by infinite.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "rhp")
    var pressed by remember { mutableStateOf(false) }
    val w by animateDpAsState(if (pressed) 56.dp else 40.dp, label = "rw")
    Box(
        Modifier.fillMaxWidth().height(26.dp).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true
                while (true) {
                    val e = awaitPointerEvent()
                    val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    onResize(ch.positionChange().y / density)
                    ch.consume()
                }
                pressed = false
                onSettle()
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.width(w).height(if (pressed) 6.dp else 4.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (pressed) col.accent else col.textFaint.copy(alpha = pulse)),
        )
    }
}

@Composable
private fun FlipSideChip(side: String, onFlip: () -> Unit) {
    val col = Relay.colors
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(col.surface).border(1.dp, col.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onFlip).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("⇄", style = Relay.type.body.copy(color = col.accent, fontSize = 13.sp))
        Text(if (side == "right") "right hand" else "left hand", style = Relay.type.mono.copy(color = col.textDim, fontSize = 10.sp))
    }
}

@Composable
private fun PreviewChip(on: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val col = Relay.colors
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(col.surface)
            .border(1.dp, col.border, RoundedCornerShape(20.dp)).clickable(onClick = onTap)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (on) col.accent else col.textFaint))
        Text("preview", style = Relay.type.mono.copy(color = if (on) col.accent else col.textFaint, fontSize = 10.sp))
    }
}

@Composable
private fun RowScope.ThumbCap(label: String, weight: Float, action: Boolean, armed: Boolean, haptic: Boolean, repeat: Boolean = false, onTap: () -> Unit) {
    val col = Relay.colors
    KeyShell(Modifier.weight(weight).fillMaxHeight(), sculpted = !action, action = action, armed = armed, flashed = false, haptic = haptic, repeat = repeat, onDown = onTap) {
        Text(label, style = (if (action) Relay.type.mono else Relay.type.body).copy(
            color = if (armed) col.accent else if (action) col.textDim else col.text,
            fontSize = if (action) 13.sp else 19.sp, fontWeight = FontWeight.Medium))
    }
}

/* shared key shell: fire-on-press-down + haptic + pressed visual + optional hold-repeat.
 * onDown/haptic/repeat are wrapped in rememberUpdatedState so the long-lived pointer
 * coroutine always calls the CURRENT action (fixes stale-lambda bugs across layer switches). */
@Composable
private fun KeyShell(
    modifier: Modifier, sculpted: Boolean, action: Boolean, armed: Boolean, flashed: Boolean, haptic: Boolean,
    corner: Int = 9, repeat: Boolean = false, onLong: (() -> Unit)? = null, onDown: () -> Unit, content: @Composable BoxScope.() -> Unit,
) {
    val col = Relay.colors
    val view = LocalView.current
    val onDownLatest by rememberUpdatedState(onDown)
    val onLongLatest by rememberUpdatedState(onLong)
    val hapticLatest by rememberUpdatedState(haptic)
    val repeatLatest by rememberUpdatedState(repeat)
    var pressed by remember { mutableStateOf(false) }
    val rs = RoundedCornerShape(corner.dp)
    val bg = when {
        flashed -> Modifier.background(col.accent, rs)
        pressed -> Modifier.background(col.surfaceHi, rs)
        armed -> Modifier.background(col.accentGhost, rs)
        action -> Modifier.background(col.surface2, rs)
        sculpted -> Modifier.background(Brush.verticalGradient(listOf(col.keyTop, col.keyBot)), rs)
        else -> Modifier.background(col.surface, rs)
    }
    val border = if (armed) col.accentDim else if (sculpted && !action) col.keyEdge else col.border
    Box(
        modifier.clip(rs).then(bg).border(1.dp, border, rs)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    fun fire() { if (hapticLatest) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onDownLatest() }
                    fire()
                    if (repeatLatest) {
                        var wait = 350L            // initial hold delay…
                        var first = true
                        while (true) {
                            val released = withTimeoutOrNull(wait) {
                                do { val e = awaitPointerEvent() } while (e.changes.any { it.id == down.id && it.pressed })
                                true
                            }
                            if (released == true) break
                            fire()
                            wait = if (first) { first = false; 110L } else maxOf(28L, wait - 12L)  // …then accelerate
                        }
                    } else if (onLongLatest != null) {
                        // hold → fire the long-press action once (e.g. send a lone modifier)
                        val released = withTimeoutOrNull(420L) { waitForUpOrCancellation(); true }
                        if (released == null) {
                            if (hapticLatest) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onLongLatest?.invoke()
                            waitForUpOrCancellation()
                        }
                    } else {
                        waitForUpOrCancellation()
                    }
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center, content = content,
    )
}

/* space bar with iOS-style long-press cursor control */
@Composable
private fun RowScope.SpaceKey(weight: Float, sculpted: Boolean, haptic: Boolean, sensitivity: Int, accel: Int, sideBias: Int, onSpace: () -> Unit, onArrow: (String) -> Unit) {
    val col = Relay.colors
    val view = LocalView.current
    var active by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(Offset.Zero) }   // thumb offset from the long-press origin (a joystick)
    val onArrowLatest by rememberUpdatedState(onArrow)
    val dead = 22f
    val vBias = 1f + sideBias.coerceIn(0, 10) / 10f * 1.6f   // >1 favours horizontal (side) arrows

    // Joystick: snap one arrow on entering a direction, then a long initial delay so a quick
    // nudge sends EXACTLY ONE; only a sustained hold starts repeating (accelerating with push).
    // Returning to centre or changing direction re-arms a fresh single press.
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val gain = sensitivity.coerceIn(1, 10) / 5f
        val a = accel.coerceIn(0, 10) / 10f
        var lastDir = ""
        var firstInDir = true
        while (true) {
            val o = offset
            val dist = hypot(o.x, o.y)
            if (dist > dead) {
                val dir = if (abs(o.y) > abs(o.x) * vBias) (if (o.y > 0) "Down" else "Up") else (if (o.x > 0) "Right" else "Left")
                if (dir != lastDir) { lastDir = dir; firstInDir = true }
                onArrowLatest(dir)
                if (haptic) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if (firstInDir) {
                    firstInDir = false
                    delay(450)   // hold this long to begin repeating; a quick nudge = one arrow only
                } else {
                    val speed = (dist - dead) * (0.7f + a) * gain
                    delay((280f - speed).coerceIn(45f, 280f).toLong())
                }
            } else {
                lastDir = ""; firstInDir = true
                delay(30)
            }
        }
    }

    val rs = RoundedCornerShape(9.dp)
    val bg = when {
        active -> Modifier.background(col.accentGhost, rs)
        sculpted -> Modifier.background(Brush.verticalGradient(listOf(col.keyTop, col.keyBot)), rs)
        else -> Modifier.background(col.surface, rs)
    }
    Box(
        Modifier.weight(weight).fillMaxHeight().clip(rs).then(bg)
            .border(1.dp, if (active) col.accentDim else if (sculpted) col.keyEdge else col.border, rs)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { active = true; offset = Offset.Zero; if (haptic) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) },
                    onDragEnd = { active = false; offset = Offset.Zero },
                    onDragCancel = { active = false; offset = Offset.Zero },
                    onDrag = { _, d -> offset += d },
                )
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { if (haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onSpace() }) },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (active) "↔  cursor" else "space", style = Relay.type.mono.copy(color = if (active) col.accent else col.textFaint, fontSize = 11.sp))
    }
    if (active) CursorJoystick(offset, dead, vBias)
}

/* Joystick pad shown while space-cursor is active: 4 direction guides + a live dot that
 * reflects the thumb offset and snaps to the dominant cardinal direction. */
@Composable
private fun CursorJoystick(offset: Offset, dead: Float, vBias: Float) {
    val col = Relay.colors
    val dist = hypot(offset.x, offset.y)
    val dir = when {
        dist <= dead -> ""
        abs(offset.y) > abs(offset.x) * vBias -> if (offset.y > 0) "D" else "U"
        else -> if (offset.x > 0) "R" else "L"
    }
    // map the thumb offset (px) into the pad (dp), clamped to the pad radius
    val dotX = (offset.x / 2.6f).coerceIn(-52f, 52f)
    val dotY = (offset.y / 2.6f).coerceIn(-52f, 52f)
    androidx.compose.ui.window.Popup(
        alignment = Alignment.Center,
        properties = androidx.compose.ui.window.PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        Box(
            Modifier.size(176.dp).clip(androidx.compose.foundation.shape.CircleShape).background(col.surfaceHi)
                .border(1.5.dp, col.accentDim, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // direction guides
            ArrowGuide("↑", dir == "U", Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
            ArrowGuide("↓", dir == "D", Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
            ArrowGuide("←", dir == "L", Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
            ArrowGuide("→", dir == "R", Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
            // crosshair center
            Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(col.border))
            // live thumb dot
            Box(
                Modifier.offset(dotX.dp, dotY.dp).size(22.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape).background(col.accent),
            )
        }
    }
}

@Composable
private fun ArrowGuide(g: String, active: Boolean, modifier: Modifier) {
    val col = Relay.colors
    Text(g, modifier = modifier, style = Relay.type.body.copy(
        color = if (active) col.accent else col.textFaint, fontSize = 22.sp, fontWeight = FontWeight.Bold))
}

@Composable
private fun ModifierBar(
    c: RelayController, ctrl: Boolean, shift: Boolean, alt: Boolean, gui: Boolean, loneMode: Boolean, immersive: Boolean, onToggleImmersive: () -> Unit,
    onMod: (String) -> Unit, onSpecial: (String) -> Unit, haptic: Boolean,
) {
    // All controls weight-shared so they always fit. In lone mode the modifier chips get an accent
    // outline to signal "a tap fires this key on its own."
    Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        BarChip(c.ctrlKey.first, ctrl, haptic, Modifier.weight(1f), lone = loneMode) { onMod("ctrl") }
        BarChip(c.altKey.first, alt, haptic, Modifier.weight(1f), lone = loneMode) { onMod("alt") }
        BarChip(c.guiKey.first, gui, haptic, Modifier.weight(1f), lone = loneMode) { onMod("gui") }
        BarChip("⇧", shift, haptic, Modifier.weight(1f), lone = loneMode) { onMod("shift") }
        BarChip("esc", false, haptic, Modifier.weight(1f)) { onSpecial("esc") }
        FullscreenToggle(immersive, onToggleImmersive)
    }
}

@Composable
private fun RowScope.BarChip(label: String, armed: Boolean, haptic: Boolean, modifier: Modifier = Modifier, repeat: Boolean = false, lone: Boolean = false, onLong: (() -> Unit)? = null, onTap: () -> Unit) {
    val col = Relay.colors
    val shape = RoundedCornerShape(10.dp)
    val m = if (lone && !armed) modifier.fillMaxHeight().border(1.dp, col.accent.copy(alpha = 0.55f), shape) else modifier.fillMaxHeight()
    KeyShell(m, sculpted = false, action = false, armed = armed, flashed = false, haptic = haptic, repeat = repeat, onLong = onLong, onDown = onTap) {
        Text(label, maxLines = 1, style = Relay.type.mono.copy(color = if (armed || lone) col.accent else col.textDim, fontSize = if (label.length > 2) 11.5.sp else 14.sp))
    }
}

// Sticky LONE switch — when on, tapping any modifier chip sends that modifier BY ITSELF (e.g. Super
// alone → Start / COSMIC launcher), instead of arming it for a combo. Lives in the dead strip below
// the trackpad handle, where the modifier bar starts.
@Composable
private fun LoneSwitch(on: Boolean, haptic: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val col = Relay.colors
    val view = LocalView.current
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier.height(26.dp)
            .clip(shape)
            .background(if (on) col.accent.copy(alpha = 0.18f) else col.surface)
            .border(1.dp, if (on) col.accent.copy(alpha = 0.7f) else col.border, shape)
            .pointerInput(Unit) { detectTapGestures(onTap = { if (haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onToggle() }) }
            .padding(start = 7.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // little LED that lights when armed
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(if (on) col.accent else col.textFaint))
        Text("SOLO", maxLines = 1, style = Relay.type.mono.copy(
            color = if (on) col.accent else col.textDim, fontSize = 10.sp, fontWeight = FontWeight.Bold))
    }
}

/** Sticky whole-pad scroll toggle (one-handed). Same house style as SOLO: tap to latch, the pad
 *  then scrolls instead of moving the pointer. */
@Composable
private fun ScrollLockSwitch(c: RelayController, modifier: Modifier = Modifier) {
    val col = Relay.colors
    val view = LocalView.current
    val on = c.scrollLock
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier.height(26.dp)
            .clip(shape)
            .background(if (on) col.accent.copy(alpha = 0.18f) else col.surface)
            .border(1.dp, if (on) col.accent.copy(alpha = 0.7f) else col.border, shape)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    c.scrollLock = !c.scrollLock
                    if (c.haptics) view.performHapticFeedback(
                        if (c.scrollLock) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP)
                })
            }
            .padding(start = 7.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(if (on) col.accent else col.textFaint))
        Text("SCROLL", maxLines = 1, style = Relay.type.mono.copy(
            color = if (on) col.accent else col.textDim, fontSize = 10.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun FullscreenToggle(immersive: Boolean, onToggle: () -> Unit) {
    val col = Relay.colors
    val tint = if (immersive) col.accent else col.textDim
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(9.dp))
            .background(if (immersive) col.accentGhost else col.surface, RoundedCornerShape(9.dp))
            .border(1.dp, if (immersive) col.accentDim else col.border, RoundedCornerShape(9.dp))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val s = size.minDimension; val w = s * 0.1f; val l = s * 0.28f
            val pts = if (immersive)
                listOf(Offset(0f, l) to Offset(0f, 0f), Offset(0f, 0f) to Offset(l, 0f),
                    Offset(s, s - l) to Offset(s, s), Offset(s, s) to Offset(s - l, s))
            else
                listOf(Offset(0f, l) to Offset(0f, 0f), Offset(0f, 0f) to Offset(l, 0f),
                    Offset(s - l, 0f) to Offset(s, 0f), Offset(s, 0f) to Offset(s, l),
                    Offset(0f, s - l) to Offset(0f, s), Offset(0f, s) to Offset(l, s),
                    Offset(s - l, s) to Offset(s, s), Offset(s, s) to Offset(s, s - l))
            pts.forEach { (a, b) -> drawLine(tint, a, b, w, StrokeCap.Round) }
        }
    }
}

/* ============================ landscape desktop ============================ */
@Composable
private fun LandscapeDesktop(
    c: RelayController, ctrl: Boolean, shift: Boolean, alt: Boolean, gui: Boolean, caps: Boolean,
    flashId: String?, lastCombo: String, immersive: Boolean, onToggleImmersive: () -> Unit,
    onMod: (String) -> Unit, onKey: (K, String) -> Unit, onSpecial: (String) -> Unit, onChar: (Char) -> Unit,
) {
    val col = Relay.colors
    val view = LocalView.current
    var padFull by remember { mutableStateOf(false) }
    var touchActive by remember { mutableStateOf(false) }
    // Idle auto-return: once the full pad sits untouched for padTimeout seconds, bring the keyboard back.
    // The timer only counts while no finger is down (touchActive=false) and restarts on every new touch.
    LaunchedEffect(padFull, touchActive) {
        if (padFull && !touchActive && c.padTimeout > 0) { delay(c.padTimeout * 1000L); padFull = false }
    }
    var mode by remember { mutableStateOf("keys") }   // for the toggle layout
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (c.showReadout) HudBar(c, ctrl, shift, alt, gui, lastCombo)
            if (c.layout == "toggle" && !padFull) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                    SegToggle(mode, listOf("keys" to "⌨  Keyboard", "pad" to "⇡  Trackpad")) { mode = it }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(10.dp)) {
                when {
                    padFull -> Column(Modifier.fillMaxSize()) {
                        DragHandle(onUp = {}, onDown = { padFull = false }, onTap = { padFull = false })
                        Box(
                            Modifier.weight(1f).fillMaxWidth().then(trackpadCanvas(c))
                                .trackpadGestures(c, view, onTouch = { touchActive = it }),
                        ) {
                            Text("FULL TRACKPAD · drag = move · 2-finger = scroll · tap = click · swipe handle ↓ for keyboard",
                                style = Relay.type.mono.copy(color = col.textFaint, fontSize = 9.sp),
                                modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    c.layout == "split" -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KeyboardPane(c, Modifier.weight(1f), ctrl, shift, alt, gui, caps, flashId, onMod, onKey, onSpecial, onChar, onExpandPad = {}, showStrip = false)
                        Box(Modifier.width(280.dp).fillMaxHeight()) { Trackpad(c, Modifier.fillMaxSize()) }
                    }
                    c.layout == "toggle" -> if (mode == "keys")
                        KeyboardPane(c, Modifier.fillMaxSize(), ctrl, shift, alt, gui, caps, flashId, onMod, onKey, onSpecial, onChar, onExpandPad = {}, showStrip = false)
                    else Trackpad(c, Modifier.fillMaxSize(), "TRACKPAD — FULL SURFACE")
                    else -> KeyboardPane(c, Modifier.fillMaxSize(), ctrl, shift, alt, gui, caps, flashId, onMod, onKey, onSpecial, onChar, onExpandPad = { padFull = true })
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { FullscreenToggle(immersive, onToggleImmersive) }
    }
}

@Composable
private fun KeyboardPane(
    c: RelayController, modifier: Modifier, ctrl: Boolean, shift: Boolean, alt: Boolean, gui: Boolean, caps: Boolean,
    flashId: String?, onMod: (String) -> Unit, onKey: (K, String) -> Unit, onSpecial: (String) -> Unit, onChar: (Char) -> Unit,
    onExpandPad: () -> Unit, showStrip: Boolean = true,
) {
    val sculpted = c.keycap == "sculpted"
    val gap = c.keyGap.dp
    val view = LocalView.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        // system shortcuts tailored to the selected target device
        Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            sysRowFor(c.profile).forEach { s ->
                SysChip(s.label, c.haptics, Modifier.weight(1f)) {
                    if (s.cc != 0) c.consumer(s.cc) else c.tapKey(s.mod, s.key)
                    c.logEvent("key", s.label)
                }
            }
        }
        ROWS.forEachIndexed { ri, row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEachIndexed { ki, k ->
                    val id = (k.code ?: k.mod ?: k.label) + "$ri-$ki"
                    if (k.mod != null || k.code != null) {
                        val armed = when (k.mod) { "caps" -> caps; "shift" -> shift; else -> false }
                        KeyCap(k, k.weight, armed, flashId == id, sculpted, shift, caps, c.haptics, keyRepeat = c.keyRepeat) {
                            if (k.mod != null) onMod(k.mod) else onKey(k, id)
                        }
                    } else {
                        SlideKey(k, k.weight, sculpted, shift, caps, c.haptics, c.slideAnim, view, repeat = c.keyRepeat, onCenter = { onKey(k, id) }, onSlide = onChar)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            ModKey(c.ctrlKey.first, c.ctrlKey.second, ctrl, 1f, sculpted, c.haptics) { onMod("ctrl") }
            ModKey(c.altKey.first, c.altKey.second, alt, 1f, sculpted, c.haptics) { onMod("alt") }
            ModKey(c.guiKey.first, c.guiKey.second, gui, 1.3f, sculpted, c.haptics) { onMod("gui") }
            SpaceKey(if (c.showArrowKeys) 5f else 8f, sculpted, c.haptics, c.sensitivity, c.accel, c.sideBias, onSpace = { onSpecial("space") }, onArrow = onSpecial)
            ModKey(c.guiKey.first, c.guiKey.second, gui, 1.3f, sculpted, c.haptics) { onMod("gui") }
            ModKey(c.altKey.first, c.altKey.second, alt, 1f, sculpted, c.haptics) { onMod("alt") }
            if (c.showArrowKeys) ArrowCluster(3f, sculpted, shift, caps, c.haptics, onKey)
        }
        // 2D touchpad strip — swipe the handle UP to let the pad take over the whole screen
        if (showStrip) {
            DragHandle(onUp = onExpandPad, onDown = {}, onTap = onExpandPad)
            Box(Modifier.fillMaxWidth().height(48.dp).then(trackpadCanvas(c)).trackpadGestures(c, view)) {
                Text("TRACKPAD · swipe handle ↑ for full screen · drag = move · tap = click · 2-finger = scroll",
                    style = Relay.type.mono.copy(color = Relay.colors.textFaint, fontSize = 9.sp),
                    modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun RowScope.SysChip(label: String, haptic: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val col = Relay.colors
    KeyShell(modifier.fillMaxHeight(), sculpted = false, action = true, armed = false, flashed = false, haptic = haptic, onDown = onTap) {
        Text(label, style = Relay.type.mono.copy(color = col.textDim, fontSize = 11.sp))
    }
}

/** Which corner a flick of (dx,dy) targets — only if that corner actually holds a value. */
private fun cornerFor(dx: Float, dy: Float, ne: String?, nw: String?, se: String?, sw: String?): String? {
    if (hypot(dx, dy) < 34f) return null
    val name = when { dx >= 0f && dy < 0f -> "NE"; dx >= 0f -> "SE"; dx < 0f && dy < 0f -> "NW"; else -> "SW" }
    val has = when (name) { "NE" -> ne; "NW" -> nw; "SE" -> se; else -> sw }
    return if (has.isNullOrEmpty()) null else name
}

/* Unexpected-Keyboard-style key: tap = letter; flick toward a corner = that corner's number/symbol.
 * The targeted corner lights up in accent and pops (springy scale) while you flick. */
@Composable
private fun RowScope.SlideKey(
    k: K, weight: Float, sculpted: Boolean, shift: Boolean, caps: Boolean, haptic: Boolean, animate: Boolean,
    view: android.view.View, repeat: Boolean = false, onCenter: () -> Unit, onSlide: (Char) -> Unit,
) {
    val col = Relay.colors
    val rs = RoundedCornerShape(9.dp)
    var pressed by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<String?>(null) }   // live targeted corner
    val onCenterLatest by rememberUpdatedState(onCenter)
    val onSlideLatest by rememberUpdatedState(onSlide)
    val highlight = animate && active != null
    val bg = when {
        highlight -> Modifier.background(col.accentGhost, rs)
        pressed -> Modifier.background(col.surfaceHi, rs)
        sculpted -> Modifier.background(Brush.verticalGradient(listOf(col.keyTop, col.keyBot)), rs)
        else -> Modifier.background(col.surface, rs)
    }
    Box(
        Modifier.weight(weight).fillMaxHeight().clip(rs).then(bg)
            .border(1.dp, if (highlight) col.accent else if (sculpted) col.keyEdge else col.border, rs)
            .pointerInput(repeat) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    if (haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val downAt = System.currentTimeMillis()
                    var dx = 0f; var dy = 0f
                    var current: String? = null
                    var reps = 0; var interval = 350L; var first = true
                    // A slide is a quick flick; movement is only read as a corner within a short
                    // window. After that, a sustained touch is a HOLD → auto-repeat (wall-clock
                    // timeout, robust to the stationary jitter that used to be misread as a slide).
                    loop@ while (true) {
                        val canRepeat = repeat && current == null
                        val outcome = withTimeoutOrNull(if (canRepeat) interval else 600000L) {
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull "up"
                                ch.consume()
                                if (!ch.pressed) return@withTimeoutOrNull "up"
                                if (current == null && System.currentTimeMillis() - downAt < 260L) {
                                    dx += ch.positionChange().x; dy += ch.positionChange().y
                                    val corner = cornerFor(dx, dy, k.ne, k.nw, k.se, k.sw)
                                    if (corner != null) {
                                        current = corner; active = corner
                                        if (haptic) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        return@withTimeoutOrNull "recompute"
                                    }
                                }
                            }
                            @Suppress("UNREACHABLE_CODE") "up"
                        }
                        when (outcome) {
                            "up" -> break@loop
                            "recompute" -> {}
                            else -> {   // timed out while held in centre → repeat the letter
                                onCenterLatest(); reps++
                                if (haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                interval = if (first) { first = false; 110L } else maxOf(28L, interval - 12L)
                            }
                        }
                    }
                    pressed = false; active = null
                    val s = when (current) { "NE" -> k.ne; "NW" -> k.nw; "SE" -> k.se; "SW" -> k.sw; else -> null }
                    if (!s.isNullOrEmpty()) onSlideLatest(s[0]) else if (reps == 0) onCenterLatest()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CornerHint(k.ne, "NE", active, animate, Alignment.TopEnd, Modifier.padding(end = 4.dp, top = 2.dp))
        CornerHint(k.nw, "NW", active, animate, Alignment.TopStart, Modifier.padding(start = 4.dp, top = 2.dp))
        CornerHint(k.se, "SE", active, animate, Alignment.BottomEnd, Modifier.padding(end = 4.dp, bottom = 2.dp))
        CornerHint(k.sw, "SW", active, animate, Alignment.BottomStart, Modifier.padding(start = 4.dp, bottom = 2.dp))
        Text(displayLabel(k, shift, caps), style = Relay.type.mono.copy(color = col.text, fontSize = 15.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun BoxScope.CornerHint(text: String?, name: String, active: String?, animate: Boolean, align: Alignment, pad: Modifier) {
    if (text == null) return
    val col = Relay.colors
    val on = animate && active == name
    val scale by animateFloatAsState(if (on) 2.1f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "hint")
    Text(
        text, style = Relay.type.mono.copy(color = if (on) col.accent else col.textFaint, fontSize = 8.sp),
        modifier = Modifier.align(align).then(pad).scale(scale),
    )
}

/* Portrait thumb key with corner slides — tap emits the letter, flick a corner emits its symbol; both go through onChar. */
@Composable
private fun RowScope.ThumbCharKey(
    tk: TK.Ch, shift: Boolean, caps: Boolean, haptic: Boolean, animate: Boolean, view: android.view.View, repeat: Boolean = false, onChar: (Char) -> Unit,
) {
    val col = Relay.colors
    val rs = RoundedCornerShape(9.dp)
    var pressed by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<String?>(null) }
    val onCharLatest by rememberUpdatedState(onChar)
    val highlight = animate && active != null
    val bg = when {
        highlight -> Modifier.background(col.accentGhost, rs)
        pressed -> Modifier.background(col.surfaceHi, rs)
        else -> Modifier.background(Brush.verticalGradient(listOf(col.keyTop, col.keyBot)), rs)
    }
    Box(
        Modifier.weight(tk.w).fillMaxHeight().clip(rs).then(bg)
            .border(1.dp, if (highlight) col.accent else col.keyEdge, rs)
            .pointerInput(repeat) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    if (haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val downAt = System.currentTimeMillis()
                    var dx = 0f; var dy = 0f
                    var current: String? = null
                    var reps = 0; var interval = 350L; var first = true
                    loop@ while (true) {
                        val canRepeat = repeat && current == null
                        val outcome = withTimeoutOrNull(if (canRepeat) interval else 600000L) {
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull "up"
                                ch.consume()
                                if (!ch.pressed) return@withTimeoutOrNull "up"
                                if (current == null && System.currentTimeMillis() - downAt < 260L) {
                                    dx += ch.positionChange().x; dy += ch.positionChange().y
                                    val corner = cornerFor(dx, dy, tk.ne, tk.nw, tk.se, tk.sw)
                                    if (corner != null) {
                                        current = corner; active = corner
                                        if (haptic) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        return@withTimeoutOrNull "recompute"
                                    }
                                }
                            }
                            @Suppress("UNREACHABLE_CODE") "up"
                        }
                        when (outcome) {
                            "up" -> break@loop
                            "recompute" -> {}
                            else -> {
                                onCharLatest(tk.c); reps++
                                if (haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                interval = if (first) { first = false; 110L } else maxOf(28L, interval - 12L)
                            }
                        }
                    }
                    pressed = false; active = null
                    val s = when (current) { "NE" -> tk.ne; "NW" -> tk.nw; "SE" -> tk.se; "SW" -> tk.sw; else -> null }
                    if (!s.isNullOrEmpty()) onCharLatest(s[0]) else if (reps == 0) onCharLatest(tk.c)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CornerHint(tk.ne, "NE", active, animate, Alignment.TopEnd, Modifier.padding(end = 4.dp, top = 2.dp))
        CornerHint(tk.nw, "NW", active, animate, Alignment.TopStart, Modifier.padding(start = 4.dp, top = 2.dp))
        CornerHint(tk.se, "SE", active, animate, Alignment.BottomEnd, Modifier.padding(end = 4.dp, bottom = 2.dp))
        CornerHint(tk.sw, "SW", active, animate, Alignment.BottomStart, Modifier.padding(start = 4.dp, bottom = 2.dp))
        Text(thumbDisplay(tk.c, shift, caps), style = Relay.type.body.copy(color = col.text, fontSize = 19.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun RowScope.ArrowCluster(weight: Float, sculpted: Boolean, shift: Boolean, caps: Boolean, haptic: Boolean, onKey: (K, String) -> Unit) {
    Column(Modifier.weight(weight).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.weight(1f))
            KeyCap(K("↑", code = "Up", glyph = true), 1f, false, false, sculpted, shift, caps, haptic, small = true) { onKey(K("↑", code = "Up"), "up") }
            Spacer(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            KeyCap(K("←", code = "Left", glyph = true), 1f, false, false, sculpted, shift, caps, haptic, small = true) { onKey(K("←", code = "Left"), "left") }
            KeyCap(K("↓", code = "Down", glyph = true), 1f, false, false, sculpted, shift, caps, haptic, small = true) { onKey(K("↓", code = "Down"), "down") }
            KeyCap(K("→", code = "Right", glyph = true), 1f, false, false, sculpted, shift, caps, haptic, small = true) { onKey(K("→", code = "Right"), "right") }
        }
    }
}

@Composable
private fun RowScope.KeyCap(
    k: K, weight: Float, armed: Boolean, flashed: Boolean, sculpted: Boolean, shift: Boolean, caps: Boolean, haptic: Boolean,
    small: Boolean = false, keyRepeat: Boolean = true, onTap: () -> Unit,
) {
    val col = Relay.colors
    val shown = displayLabel(k, shift, caps)
    val fg = if (armed) col.accent else col.text
    val repeat = keyRepeat && (k.code == "Backspace" || k.code == "Up" || k.code == "Down" || k.code == "Left" || k.code == "Right")
    KeyShell(Modifier.weight(weight).fillMaxHeight(), sculpted, action = false, armed = armed, flashed = flashed, haptic = haptic, repeat = repeat, onDown = onTap) {
        if (k.sub != null && !shift) Text(k.sub, style = Relay.type.mono.copy(color = col.textFaint, fontSize = 9.sp),
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 5.dp, top = 3.dp))
        Text(shown, style = (if (k.glyph) Relay.type.body else Relay.type.mono).copy(
            color = if (flashed) col.bgDeep else fg, fontSize = if (small) 11.sp else if (k.glyph) 16.sp else 14.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun RowScope.ModKey(glyph: String, word: String, armed: Boolean, weight: Float, sculpted: Boolean, haptic: Boolean, onLong: (() -> Unit)? = null, onTap: () -> Unit) {
    val col = Relay.colors
    KeyShell(Modifier.weight(weight).fillMaxHeight(), sculpted, action = false, armed = armed, flashed = false, haptic = haptic, onLong = onLong, onDown = onTap) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val textLabel = glyph.length > 2   // word-style labels (Ctrl/Alt/Win/Super) vs single glyphs (⌘/⌥/⌃)
            Text(glyph, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
                style = Relay.type.body.copy(color = if (armed) col.accent else col.textDim, fontSize = if (glyph.length >= 5) 10.sp else if (textLabel) 12.sp else 15.sp))
            if (!textLabel) Text(word, style = Relay.type.mono.copy(color = if (armed) col.accent else col.textFaint, fontSize = 9.sp))
        }
    }
}

@Composable
private fun HudBar(c: RelayController, ctrl: Boolean, shift: Boolean, alt: Boolean, gui: Boolean, combo: String) {
    val col = Relay.colors
    val bits = listOf(ctrl to "⌃", shift to "⇧", alt to "⌥", gui to "⌘", false to "⌃", false to "⇧", false to "⌥", false to "⌘")
    val hex = bits.foldIndexed(0) { i, acc, (on, _) -> acc or (if (on) (1 shl i) else 0) }
    Row(Modifier.fillMaxWidth().height(38.dp).background(col.bg).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(col.accent))
            Text("HID LINK", style = Relay.type.mono.copy(color = col.accent, fontSize = 11.sp))
        }
        Text("MOD", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 11.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            bits.forEach { (on, g) ->
                Box(Modifier.size(15.dp, 17.dp).clip(RoundedCornerShape(3.dp)).background(if (on) col.accent else col.surface)
                    .border(1.dp, if (on) Color.Transparent else col.border, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                    Text(g, style = Relay.type.mono.copy(color = if (on) col.bgDeep else col.textFaint, fontSize = 9.sp))
                }
            }
        }
        Text("0x%02X".format(hex), style = Relay.type.mono.copy(color = col.textFaint, fontSize = 11.sp))
        Spacer(Modifier.weight(1f))
        Text("SENDING", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 11.sp))
        Text(combo, style = Relay.type.monoSemi.copy(color = col.accent2, fontSize = 12.sp))
        Text("→ ${c.deviceName ?: "no device"}", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 11.sp))
    }
}

/* ============================ trackpad ============================ */
/** Unified, user-selectable trackpad canvas styling (gradient / dots / plain). */
@Composable
private fun trackpadCanvas(c: RelayController): Modifier {
    val col = Relay.colors
    val shape = RoundedCornerShape(14.dp)
    var m: Modifier = Modifier.clip(shape)
    m = if (c.padStyle == "gradient") m.background(Brush.radialGradient(listOf(col.surface, col.bgDeep)), shape)
        else m.background(col.bgDeep, shape)
    if (c.padStyle == "dots") {
        m = m.drawBehind {
            val gap = 22.dp.toPx(); val r = 1.1.dp.toPx(); var y = gap
            while (y < size.height) { var x = gap; while (x < size.width) { drawCircle(col.border, r, Offset(x, y)); x += gap }; y += gap }
        }
    }
    return m.border(1.dp, col.border, shape)
}

/** Visual feedback while drag-locked. 0=green border · 1=glow · 2=fill · 3=corner brackets. */
@Composable
private fun Modifier.dragSignal(style: Int, on: Boolean): Modifier {
    if (!on) return this
    val col = Relay.colors
    val shape = RoundedCornerShape(14.dp)
    return when (style) {
        1 -> this.shadow(22.dp, shape, clip = false, ambientColor = col.accent, spotColor = col.accent).border(2.dp, col.accent, shape)
        2 -> this.background(col.accentGhost, shape).border(1.5.dp, col.accentDim, shape)
        3 -> this.border(2.dp, col.accent, shape).drawBehind {
            val l = 26.dp.toPx(); val w = 3.5.dp.toPx(); val o = 9.dp.toPx()
            val c1 = Offset(o, o); val c2 = Offset(size.width - o, o); val c3 = Offset(o, size.height - o); val c4 = Offset(size.width - o, size.height - o)
            drawLine(col.accent, c1, c1.copy(x = c1.x + l), w, StrokeCap.Round); drawLine(col.accent, c1, c1.copy(y = c1.y + l), w, StrokeCap.Round)
            drawLine(col.accent, c2, c2.copy(x = c2.x - l), w, StrokeCap.Round); drawLine(col.accent, c2, c2.copy(y = c2.y + l), w, StrokeCap.Round)
            drawLine(col.accent, c3, c3.copy(x = c3.x + l), w, StrokeCap.Round); drawLine(col.accent, c3, c3.copy(y = c3.y - l), w, StrokeCap.Round)
            drawLine(col.accent, c4, c4.copy(x = c4.x - l), w, StrokeCap.Round); drawLine(col.accent, c4, c4.copy(y = c4.y - l), w, StrokeCap.Round)
        }
        else -> this.border(2.5.dp, col.accent, shape)
    }
}

/** Temporary demo: cycles the drag-feedback styles every 5s; tap to pick. Logs to "DRAGDEMO". */
@Composable
private fun DragStyleDemo(modifier: Modifier, onPick: (Int) -> Unit) {
    val col = Relay.colors
    val names = listOf("Green border", "Green glow", "Accent fill", "Corner brackets")
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            android.util.Log.i("DRAGDEMO", "showing style ${i + 1}/4 — ${names[i]}")
            delay(5000); i = (i + 1) % names.size
        }
    }
    Box(
        modifier.dragSignal(i, true).pointerInput(Unit) {
            detectTapGestures { android.util.Log.i("DRAGDEMO", "PICKED style ${i + 1} — ${names[i]}"); onPick(i) }
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("DRAG SIGNAL  ${i + 1}/4", style = Relay.type.mono.copy(color = col.accent, fontSize = 11.sp, letterSpacing = 1.sp))
            Text(names[i], style = Relay.type.h1.copy(color = col.text, fontSize = 22.sp))
            Text("changes every 5s · tap to pick this one", style = Relay.type.sub.copy(color = col.textFaint, fontSize = 11.sp))
        }
    }
}

/** Slim grabber. Swipe direction is wired by the caller (geometry differs portrait vs landscape). */
@Composable
private fun DragHandle(onUp: () -> Unit, onDown: () -> Unit, onTap: () -> Unit) {
    val col = Relay.colors
    var pressed by remember { mutableStateOf(false) }
    // gentle idle "breathing" so it reads as interactive…
    val infinite = rememberInfiniteTransition(label = "handle")
    val pulse by infinite.animateFloat(0.4f, 0.85f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "pulse")
    // …and a lively grab response
    val pillW by animateDpAsState(if (pressed) 58.dp else 44.dp, label = "w")
    val pillH by animateDpAsState(if (pressed) 6.dp else 4.dp, label = "h")
    val pillColor = if (pressed) col.accent else col.textFaint.copy(alpha = pulse)
    Box(
        Modifier.fillMaxWidth().height(24.dp).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true
                var acc = 0f; var fired = false
                while (true) {
                    val e = awaitPointerEvent()
                    val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    acc += ch.positionChange().y
                    if (!fired) {
                        if (acc > 22f) { fired = true; onDown() } else if (acc < -22f) { fired = true; onUp() }
                    }
                    ch.consume()
                }
                if (!fired && abs(acc) < 10f) onTap()
                pressed = false
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(pillW).height(pillH).clip(androidx.compose.foundation.shape.CircleShape).background(pillColor))
    }
}

// Scroll is carried in 1/120-of-a-detent units (REL_WHEEL_HI_RES / Windows WHEEL_DELTA), so the
// pad streams smooth, sub-line scroll. One finger-pixel maps to this many hi-res units at scrollSpeed 5.
private const val HR_PER_PX = 21.6f      // 0.18 lines/px * 120
private const val HR_DETENT = 120f
private const val HR_CLAMP = 3000        // per-message safety cap (~25 lines)

/** Adaptive pointer-acceleration gain (libinput-style): precision floor when slow, ~1:1 in the
 *  middle, ramping to a ceiling for fast flicks. `sens` (the slider) multiplies on top. */
private fun accelGain(speedDpPerMs: Float, profile: String, sens: Float, accel: Int): Float {
    if (profile == "flat") return sens
    val gMin = 0.4f
    val gMax = 1.8f + (accel / 10f) * 2.4f       // accel 0→1.8, 5→3.0, 10→4.2
    val sMin = 0.2f; val sMax = 2.6f             // dp/ms thresholds
    val t = ((speedDpPerMs - sMin) / (sMax - sMin)).coerceIn(0f, 1f)
    val ramp = t * t * (3f - 2f * t)             // smoothstep
    return (gMin + (gMax - gMin) * ramp) * sens
}

/** Very faint scroll-detent tick: a low-amplitude composition primitive where supported, else a
 *  system clock-tick. Kept subtle because it fires often. */
private fun subtleTick(view: android.view.View) {
    val ctx = view.context
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        val vib = (ctx.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
            as? android.os.VibratorManager)?.defaultVibrator
        if (vib != null && vib.areAllPrimitivesSupported(android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
            vib.vibrate(
                android.os.VibrationEffect.startComposition()
                    .addPrimitive(android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.22f)
                    .compose()
            )
            return
        }
    }
    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
}

/**
 * Multi-touch trackpad engine:
 *  • 1 finger drag → move pointer (adaptive acceleration); right-edge gutter or sticky Scroll lock → scroll
 *  • 2 finger drag → smooth hi-res scroll (vertical + side), with momentum (respects natural-scroll)
 *  • tap → left click ·  2-finger tap / firm tap → right click ·  long-press → drag-lock
 *  • 3-finger swipe → app switch · 3-finger tap → find pointer
 */
private fun Modifier.trackpadGestures(c: RelayController, view: android.view.View, onDrag: (Boolean) -> Unit = {}, onTouch: (Boolean) -> Unit = {}): Modifier = pointerInput(Unit) {
    fun haptic(type: Int) { if (c.haptics) view.performHapticFeedback(type) }
    val dens = density.coerceAtLeast(0.5f)
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        onTouch(true)
        val padW = size.width.toFloat().coerceAtLeast(1f)
        val downTime = System.currentTimeMillis()
        // one-handed scroll: sticky whole-pad lock, or a touch that begins in the right-edge gutter
        val gutter = c.edgeScroll && first.position.x > padW * 0.86f
        val scrollModeOne = c.scrollLock || gutter
        var lastOne = first.position
        var lastT = downTime
        var lastCentroid: Offset? = null
        var maxPointers = 1
        var moved = false
        var dragging = false        // long-press drag-lock: left button held while moving
        var scrollAcc = 0f          // hi-res vertical carry
        var scrollAccX = 0f         // hi-res horizontal (side-scroll) carry
        var scrollVel = 0f          // hi-res units/event, for momentum
        var detentCarry = 0f        // accumulates emitted hi-res units → one tick per detent
        var lastTick = 0L
        var maxPressure = first.pressure
        var threeStart: Offset? = null
        var appFired = false        // 3-finger swipe → ⌘Tab (once per gesture)
        var twoStart: Offset? = null
        var twoMode = 0             // 0 = undecided, 1 = scroll (both axes), 2 = nav (back/forward)

        fun detent(units: Int) {
            if (!c.haptics) return
            detentCarry += abs(units).toFloat()
            if (detentCarry >= HR_DETENT) {
                detentCarry -= HR_DETENT * (detentCarry / HR_DETENT).toInt()
                val now = System.currentTimeMillis()
                if (now - lastTick > 28L) { lastTick = now; subtleTick(view) }   // rate-limit so fast flings don't buzz
            }
        }
        // Emit a vertical hi-res scroll step from a finger delta (used by 2-finger and one-handed scroll).
        fun scrollStep(dyPx: Float, dxPx: Float) {
            moved = true
            val dir = if (c.naturalScroll) -1 else 1
            val gainHr = (c.scrollSpeed / 5f) * HR_PER_PX
            val stepY = dyPx * dir * gainHr
            scrollAcc += stepY; scrollVel = scrollVel * 0.6f + stepY * 0.4f
            val sy = scrollAcc.roundToInt()
            if (sy != 0) { c.scrollHires(sy.coerceIn(-HR_CLAMP, HR_CLAMP), 0); scrollAcc -= sy; detent(sy) }
            if (dxPx != 0f) {
                scrollAccX += dxPx * dir * gainHr
                val sx = scrollAccX.roundToInt()
                if (sx != 0) { c.scrollHires(0, sx.coerceIn(-HR_CLAMP, HR_CLAMP)); scrollAccX -= sx }
            }
        }

        while (true) {
            val ev = withTimeoutOrNull(55) { awaitPointerEvent() }   // poll so a stationary long-press registers
            if (ev == null) {
                if (!dragging && !moved && !scrollModeOne && maxPointers == 1 && System.currentTimeMillis() - downTime > 350L) {
                    dragging = true; onDrag(true); haptic(android.view.HapticFeedbackConstants.LONG_PRESS)
                    c.mouseMove(0, 0, 0, HidConstants.MOUSE_LEFT)   // grab (press & hold)
                    c.logEvent("click", "drag start")
                }
                continue
            }
            val pressed = ev.changes.filter { it.pressed }
            if (pressed.isEmpty()) { onTouch(false); break }
            maxPointers = maxOf(maxPointers, pressed.size)
            pressed.forEach { maxPressure = maxOf(maxPressure, it.pressure) }
            if (pressed.size >= 3) {
                val cen = pressed.fold(Offset.Zero) { a, ch -> a + ch.position } / pressed.size.toFloat()
                if (threeStart == null) threeStart = cen
                if (!appFired && abs(cen.x - threeStart!!.x) > 70f) {
                    appFired = true; moved = true
                    c.tapKey(if (c.isApple) HidConstants.MOD_LGUI else HidConstants.MOD_LALT, HidConstants.KEY_TAB)   // ⌘Tab / Alt+Tab → switch apps
                    haptic(android.view.HapticFeedbackConstants.LONG_PRESS)
                    c.logEvent("key", "⌘⇥")
                }
                pressed.forEach { it.consume() }
            } else if (pressed.size == 2) {
                threeStart = null
                val cen = pressed.fold(Offset.Zero) { a, ch -> a + ch.position } / pressed.size.toFloat()
                if (twoStart == null) twoStart = cen
                lastCentroid?.let { prev ->
                    val dx = cen.x - prev.x; val dy = cen.y - prev.y
                    // Decide once: a quick, horizontal-dominant flick = back/forward nav; anything
                    // else = a 2-finger scroll (omnidirectional — vertical AND side-scroll).
                    if (twoMode == 0) {
                        if (c.swipeNav && abs(dx) > 22f && abs(dx) > abs(dy) * 1.6f) {
                            twoMode = 2; moved = true
                            val back = (cen.x - twoStart!!.x) > 0
                            if (c.isApple) c.tapKey(HidConstants.MOD_LGUI, if (back) 0x2F else 0x30)
                            else c.tapKey(HidConstants.MOD_LALT, if (back) HidConstants.KEY_LEFT else HidConstants.KEY_RIGHT)
                            haptic(android.view.HapticFeedbackConstants.LONG_PRESS)
                            c.logEvent("key", if (back) "back" else "forward")
                        } else if (abs(dx) > 1f || abs(dy) > 1f) {
                            twoMode = 1
                        }
                    }
                    if (twoMode == 1) scrollStep(dy, dx)
                }
                lastCentroid = cen
                pressed.forEach { it.consume() }
            } else {
                lastCentroid = null
                threeStart = null
                twoStart = null
                twoMode = 0
                val ch = pressed.first()
                val d = ch.position - lastOne
                lastOne = ch.position
                val now = System.currentTimeMillis()
                val dt = (now - lastT).coerceAtLeast(1)
                lastT = now
                val dist = d.getDistance()
                if (dist > 0.4f) {
                    if (dist > 6f) moved = true
                    if (scrollModeOne) {
                        scrollStep(d.y, 0f)             // one-handed: vertical finger → scroll
                    } else {
                        val speedDp = (dist / dens) / dt    // dp/ms
                        val g = accelGain(speedDp, c.accelProfile, c.sensitivity / 5f, c.accel)
                        val btn = if (dragging) HidConstants.MOUSE_LEFT else 0   // hold button while drag-locked
                        val ix = if (c.invertX) -1 else 1; val iy = if (c.invertY) -1 else 1
                        c.mouseMove((ix * d.x * g).roundToInt(), (iy * d.y * g).roundToInt(), 0, btn)
                    }
                }
                ch.consume()
            }
        }
        when {
            dragging -> { onDrag(false); c.mouseMove(0, 0, 0, 0); haptic(android.view.HapticFeedbackConstants.VIRTUAL_KEY); c.logEvent("click", "drop") }
            maxPointers >= 3 && !moved && !appFired -> {
                haptic(android.view.HapticFeedbackConstants.LONG_PRESS)   // 3-finger tap → jiggle to locate the pointer
                c.findPointer()
            }
            !moved -> {
                val right = maxPointers >= 2 || (c.firmPress && maxPressure > 0.5f)
                c.mouseMove(0, 0, 0, if (right) HidConstants.MOUSE_RIGHT else HidConstants.MOUSE_LEFT); c.mouseMove(0, 0, 0, 0)
                haptic(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                c.logEvent("click", if (right) "secondary click" else "primary click")
            }
            else -> {
                // momentum scroll: fling decays exponentially (iOS-style), streaming hi-res deltas,
                // until it falls below threshold or a new touch lands.
                if (c.momentum && abs(scrollVel) > 60f) {     // ~0.5 line
                    var v = scrollVel * 1.4f
                    while (abs(v) > 12f) {                     // stop ~0.1 line
                        val nd = withTimeoutOrNull(16) { awaitFirstDown(requireUnconsumed = false) }
                        if (nd != null) break
                        c.scrollHires(v.roundToInt().coerceIn(-HR_CLAMP, HR_CLAMP), 0)
                        v *= 0.94f
                    }
                }
            }
        }
    }
}

@Composable
private fun ClickButtons(c: RelayController) {
    Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TpButton("CLICK", Modifier.weight(1f)) { c.mouseMove(0, 0, 0, HidConstants.MOUSE_LEFT); c.mouseMove(0, 0, 0, 0); c.logEvent("click", "primary click") }
        TpButton("SECONDARY", Modifier.weight(1f)) { c.mouseMove(0, 0, 0, HidConstants.MOUSE_RIGHT); c.mouseMove(0, 0, 0, 0); c.logEvent("click", "secondary click") }
    }
}

@Composable
private fun PadButtons(c: RelayController, gyroOn: Boolean, onToggleGyro: () -> Unit) {
    val col = Relay.colors
    Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TpButton("CLICK", Modifier.weight(1f)) { c.mouseMove(0, 0, 0, HidConstants.MOUSE_LEFT); c.mouseMove(0, 0, 0, 0); c.logEvent("click", "primary click") }
        TpButton("SECONDARY", Modifier.weight(1f)) { c.mouseMove(0, 0, 0, HidConstants.MOUSE_RIGHT); c.mouseMove(0, 0, 0, 0); c.logEvent("click", "secondary click") }
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(11.dp))
                .background(if (gyroOn) col.accentGhost else col.surface2)
                .border(1.dp, if (gyroOn) col.accentDim else col.border, RoundedCornerShape(11.dp))
                .clickable(onClick = onToggleGyro),
            contentAlignment = Alignment.Center,
        ) { Text("GYRO", style = Relay.type.mono.copy(color = if (gyroOn) col.accent else col.textDim, fontSize = 11.sp, letterSpacing = 1.sp)) }
    }
}

/** Air-mouse: hold the pad (clutch) and move the phone in space to aim; tap = click. */
@Composable
private fun GyroPad(c: RelayController, modifier: Modifier, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val col = Relay.colors
    var engaged by remember { mutableStateOf(false) }
    val engagedNow = rememberUpdatedState(engaged)
    DisposableEffect(Unit) {
        val sm = context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val gyro = sm.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE)
        var biasX = 0f; var biasY = 0f      // resting drift, auto-tracked while idle
        var smX = 0f; var smY = 0f          // smoothed angular rate (kills jitter)
        var accX = 0f; var accY = 0f        // sub-pixel carry (keeps slow aim precise)
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val rawX = e.values[1]; val rawY = e.values[0]      // yaw, pitch (portrait device frame)
                if (!engagedNow.value) {
                    biasX += (rawX - biasX) * 0.03f                  // calibrate drift while the phone rests
                    biasY += (rawY - biasY) * 0.03f
                    smX = 0f; smY = 0f
                    return
                }
                val dead = 0.030f                                    // wider rest deadzone — ignore hand tremor / sensor noise
                var ox = rawX - biasX; var oy = rawY - biasY
                ox = if (abs(ox) < dead) 0f else ox - dead * (if (ox > 0) 1f else -1f)
                oy = if (abs(oy) < dead) 0f else oy - dead * (if (oy > 0) 1f else -1f)
                smX = smX * 0.6f + ox * 0.4f                         // heavier low-pass → calmer, less jitter
                smY = smY * 0.6f + oy * 0.4f
                // post-smoothing noise gate: residual jitter must not crawl the cursor
                val floor = 0.012f
                val nx = if (abs(smX) < floor) 0f else smX
                val ny = if (abs(smY) < floor) 0f else smY
                val sens = c.sensitivity * 4f + 10f                  // calmer base gain (was *5 + 22)
                val acc = c.accel / 10f
                val gx = sens * (1f + abs(nx) * acc * 1.5f)          // gentler ballistic gain
                val gy = sens * (1f + abs(ny) * acc * 1.5f)
                val ix = if (c.invertX) 1f else -1f
                val iy = if (c.invertY) 1f else -1f
                accX += ix * nx * gx; accY += iy * ny * gy
                val dx = accX.roundToInt(); accX -= dx
                val dy = accY.roundToInt(); accY -= dy
                if (dx != 0 || dy != 0) c.mouseMove(dx, dy)
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        if (gyro != null) sm.registerListener(listener, gyro, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                engaged = true
                val quick = withTimeoutOrNull(220) { waitForUpOrCancellation() } != null
                if (quick) { engaged = false; onClick() } else { waitForUpOrCancellation(); engaged = false }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (engaged) "● AIMING — move the phone" else "GYRO · hold & move the phone to aim · tap to click",
            style = Relay.type.mono.copy(color = if (engaged) col.accent else col.textFaint, fontSize = 11.sp),
        )
    }
}

@Composable
private fun Trackpad(c: RelayController, modifier: Modifier, label: String = "TRACKPAD") {
    val col = Relay.colors
    val view = LocalView.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().then(trackpadCanvas(c)).trackpadGestures(c, view)) {
            Text(label, style = Relay.type.mono.copy(color = col.textFaint, fontSize = 9.5.sp), modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
        }
        Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TpButton("CLICK", Modifier.weight(1f)) { c.mouseMove(0, 0, 0, HidConstants.MOUSE_LEFT); c.mouseMove(0, 0, 0, 0); c.logEvent("click", "primary click") }
            TpButton("SECONDARY", Modifier.weight(1f)) { c.mouseMove(0, 0, 0, HidConstants.MOUSE_RIGHT); c.mouseMove(0, 0, 0, 0); c.logEvent("click", "secondary click") }
        }
    }
}

@Composable
private fun RowScope.TpButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val col = Relay.colors
    val shape = RoundedCornerShape(11.dp)
    Box(modifier.fillMaxHeight().clip(shape).background(Brush.verticalGradient(listOf(col.keyTop, col.keyBot))).border(1.dp, col.keyEdge, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Text(label, style = Relay.type.mono.copy(color = col.textDim, fontSize = 11.sp)) }
}

@Composable
private fun GestureBar(c: RelayController) {
    val col = Relay.colors
    Box(Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 10.dp).clip(RoundedCornerShape(9.dp))
        .background(Brush.horizontalGradient(listOf(col.surface, col.surface2))).border(1.dp, col.border, RoundedCornerShape(9.dp))
        .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); val s = c.sensitivity / 5f; if (abs(drag.x) > 0) c.mouseMove((drag.x * s).roundToInt(), 0) } }
        .pointerInput(Unit) { detectTapGestures(onTap = { c.mouseMove(0, 0, 0, HidConstants.MOUSE_LEFT); c.mouseMove(0, 0, 0, 0); c.logEvent("click", "primary click") }) }) {
        Text("Trackpad strip — drag to move · tap to click", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 9.sp), modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
    }
}

@Composable
private fun SegToggle(value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit) {
    val col = Relay.colors
    Row(Modifier.clip(RoundedCornerShape(11.dp)).background(col.bgDeep).border(1.dp, col.border, RoundedCornerShape(11.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (v, lbl) ->
            val on = v == value
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) col.surfaceHi else Color.Transparent).clickable { onChange(v) }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                Text(lbl, style = Relay.type.body.copy(color = if (on) col.text else col.textDim, fontSize = 12.5.sp))
            }
        }
    }
}

/* ============================ helpers ============================ */
private fun baseKeycode(k: K): Int? = when (k.code) {
    "Backspace" -> HidConstants.KEY_BACKSPACE; "Tab" -> HidConstants.KEY_TAB; "Enter" -> HidConstants.KEY_ENTER
    "Space" -> HidConstants.KEY_SPACE; "Esc" -> HidConstants.KEY_ESC
    "Up" -> HidConstants.KEY_UP; "Down" -> HidConstants.KEY_DOWN; "Left" -> HidConstants.KEY_LEFT; "Right" -> HidConstants.KEY_RIGHT
    else -> if (k.label.length == 1) HidKeymap.charToKey(k.label[0])?.second else null
}

/** Label to show on a key given shift/caps state (letters uppercase; symbol keys show their shifted glyph). */
private fun displayLabel(k: K, shift: Boolean, caps: Boolean): String = when {
    k.code != null -> k.label
    k.label.length == 1 && k.label[0] in 'a'..'z' -> if (shift || caps) k.label.uppercase() else k.label
    shift && k.sub != null -> k.sub
    else -> k.label
}

private val SHIFT_SYM = mapOf(
    ',' to '<', '.' to '>', '/' to '?', ';' to ':', '\'' to '"', '[' to '{', ']' to '}',
    '-' to '_', '=' to '+', '`' to '~', '\\' to '|',
    '1' to '!', '2' to '@', '3' to '#', '4' to '$', '5' to '%', '6' to '^', '7' to '&', '8' to '*', '9' to '(', '0' to ')',
)

/** Thumb-key face honouring shift/caps: letters uppercase, symbols show their shifted glyph. */
private fun thumbDisplay(c: Char, shift: Boolean, caps: Boolean): String = when {
    c in 'a'..'z' -> if (shift || caps) c.uppercaseChar().toString() else c.toString()
    shift -> (SHIFT_SYM[c] ?: c).toString()
    else -> c.toString()
}
