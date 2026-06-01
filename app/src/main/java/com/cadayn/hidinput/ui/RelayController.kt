package com.cadayn.hidinput.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cadayn.hidinput.HidConstants
import com.cadayn.hidinput.HidPeripheralManager
import com.cadayn.hidinput.WifiLink
import com.cadayn.hidinput.ui.theme.AccentHue

/** Connection lifecycle the UI cares about. */
enum class ConnState { IDLE, PAIRING, CONNECTED }

/** One entry in the live input log. */
data class ActivityEvent(val id: Long, val time: String, val type: String, val text: String)

/**
 * Single source of truth for Relay's UI: wraps [HidPeripheralManager], exposes Compose
 * state, and persists user settings. HID callbacks are marshalled onto the main thread.
 */
class RelayController(private val context: Context) : HidPeripheralManager.Listener {

    private val prefs = context.getSharedPreferences("relay", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val hid = HidPeripheralManager.getInstance(context).also { it.listener = this }
    private val wifi = WifiLink()
    val discovery = com.cadayn.hidinput.WifiDiscovery(context)
    private var seq = 0L

    // ---- transport (bt = HID, wifi = desktop receiver) ----
    var transport by mutableStateOf("bt"); private set
    var wifiConnected by mutableStateOf(false); private set
    var wifiHost by mutableStateOf<String?>(null); private set
    private var wifiBtn = 0
    private val useWifi get() = transport == "wifi" && wifi.connected

    init {
        // desktop clipboard pushes arrive here → set the phone clipboard
        wifi.onClip = { text -> main.post { setPhoneClipboard(text) } }
    }

    private fun setPhoneClipboard(text: String) {
        if (text.isEmpty()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Relay", text))
        logEvent("key", "clipboard ← desktop (${text.length})")
    }

    /** Push the phone's clipboard to the desktop (Android only lets us read it while focused). */
    fun wifiSendClipboard() {
        val text = clipboardText()
        if (text.isNotEmpty() && useWifi) { wifi.clip(text); logEvent("key", "clipboard → desktop (${text.length})") }
    }

    fun wifiConnect(ip: String, port: Int, pin: String) {
        wifi.connect(ip, port, pin) { ok ->
            main.post {
                wifiConnected = ok
                transport = if (ok) "wifi" else "bt"
                if (ok) { wifiHost = ip; saveWifiPin(ip, pin); logEvent("click", "WiFi → $ip") }
            }
        }
    }
    fun wifiDisconnect() { wifi.disconnect(); wifiConnected = false; wifiHost = null; transport = "bt"; wifiBtn = 0 }

    /** Remember the PIN per host so a discovered desktop reconnects in one tap. */
    fun wifiPinFor(ip: String): String = prefs.getString("wifipin_$ip", "") ?: ""
    private fun saveWifiPin(ip: String, pin: String) { if (pin.isNotEmpty()) prefs.edit().putString("wifipin_$ip", pin).apply() }

    // ---- navigation / onboarding ----
    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", false)); private set

    // ---- connection state ----
    var registered by mutableStateOf(hid.isRegistered); private set
    var conn by mutableStateOf(if (hid.isConnected) ConnState.CONNECTED else ConnState.IDLE); private set
    var deviceName by mutableStateOf(hid.hostName); private set
    var latency by mutableStateOf(if (hid.isConnected) 8 else 0); private set

    val isConnected get() = conn == ConnState.CONNECTED

    // ---- settings (persisted) ----
    var dark by mutableStateOf(prefs.getBoolean("dark", true)); private set
    var hue by mutableStateOf(AccentHue.from(prefs.getString("hue", "152")!!)); private set
    var behavior by mutableStateOf(prefs.getString("behavior", "sticky")!!); private set      // sticky | oneshot
    var keycap by mutableStateOf(prefs.getString("keycap", "sculpted")!!); private set         // sculpted | flat
    var layout by mutableStateOf(prefs.getString("layout", "gesturebar")!!); private set       // gesturebar | split | toggle
    var portraitLayout by mutableStateOf(prefs.getString("portraitLayout", "standard")!!); private set  // standard (resizable) | onehanded
    var oneHandSide by mutableStateOf(prefs.getString("oneHandSide", "right")!!); private set        // left | right
    var sensitivity by mutableStateOf(prefs.getInt("sensitivity", 5)); private set
    var accel by mutableStateOf(prefs.getInt("accel", 5)); private set
    var sideBias by mutableStateOf(prefs.getInt("sideBias", 6)); private set   // 0=even, 10=strongly horizontal
    var showPreview by mutableStateOf(prefs.getBoolean("showPreview", true)); private set
    var padStyle by mutableStateOf(prefs.getString("padStyle", "gradient")!!); private set   // gradient | dots | plain
    var dragStyle by mutableStateOf(prefs.getInt("dragStyle", 0)); private set                 // drag-feedback visual
    var scrollSpeed by mutableStateOf(prefs.getInt("scrollSpeed", 4)); private set             // independent of pointer speed
    var invertX by mutableStateOf(prefs.getBoolean("invertX", false)); private set
    var invertY by mutableStateOf(prefs.getBoolean("invertY", false)); private set
    var swipeNav by mutableStateOf(prefs.getBoolean("swipeNav", true)); private set            // 2-finger horizontal → back/forward
    var keyHeight by mutableStateOf(prefs.getInt("keyHeight", 56)); private set                 // thumb-keyboard row height (dp)
    var keyGap by mutableStateOf(prefs.getInt("keyGap", 6)); private set                        // gap between keys (dp)
    var showArrowKeys by mutableStateOf(prefs.getBoolean("showArrowKeys", false)); private set  // landscape: dedicated arrow cluster vs. space-joystick
    var slideAnim by mutableStateOf(prefs.getBoolean("slideAnim", true)); private set            // animate the corner-slide highlight
    var padTimeout by mutableStateOf(prefs.getInt("padTimeout", 3)); private set                  // landscape full-trackpad auto-return (s); 0 = explicit only
    var momentum by mutableStateOf(prefs.getBoolean("momentum", true)); private set
    var firmPress by mutableStateOf(prefs.getBoolean("firmPress", false)); private set         // firm tap → right-click
    var volumeKeys by mutableStateOf(prefs.getString("volumeKeys", "off")!!); private set      // off | scroll | page | click
    var swapCmd by mutableStateOf(prefs.getBoolean("swapCmd", false)); private set
    var capsEsc by mutableStateOf(prefs.getBoolean("capsEsc", false)); private set
    var naturalScroll by mutableStateOf(prefs.getBoolean("naturalScroll", true)); private set
    var tapClick by mutableStateOf(prefs.getBoolean("tapClick", true)); private set
    var haptics by mutableStateOf(prefs.getBoolean("haptics", true)); private set
    var showReadout by mutableStateOf(prefs.getBoolean("showReadout", true)); private set
    var autoReconnect by mutableStateOf(prefs.getBoolean("autoReconnect", true)); private set
    var btName by mutableStateOf(prefs.getString("btName", "Relay")!!); private set
    var renameBt by mutableStateOf(prefs.getBoolean("renameBt", false)); private set
    var landscape by mutableStateOf(prefs.getBoolean("landscape", true)); private set
    var profile by mutableStateOf(prefs.getString("profile", "ipad")!!); private set            // ipad|mac|windows|linux|androidtv|appletv|ps
    var snippets by mutableStateOf(prefs.getString("snippets", "")!!.split("").filter { it.isNotEmpty() }); private set

    // ---- live log ----
    val log = mutableStateListOf<ActivityEvent>()

    // ---- lifecycle ----
    fun start() { hid.autoReconnect = autoReconnect; if (renameBt) hid.setAdapterName(btName); hid.start() }
    fun updateBtName(v: String) { btName = v; prefs.edit().putString("btName", v).apply(); if (renameBt) hid.setAdapterName(v) }
    fun updateRenameBt(v: Boolean) {
        renameBt = v; prefs.edit().putBoolean("renameBt", v).apply()
        if (v) hid.setAdapterName(btName) else hid.restoreAdapterName()
    }
    fun stop() { /* HID registration is owned by the foreground service; keep it alive across activity restarts */ }
    fun reconnect() = hid.reconnect()

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

    val bluetoothOn get() = adapter?.isEnabled == true
    val pairedDevices: List<BluetoothDevice> get() = hid.pairedDevices

    fun discoverableIntent(): Intent =
        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)

    fun connectTo(device: BluetoothDevice) {
        deviceName = safeName(device)
        conn = ConnState.PAIRING
        hid.connectToHost(device)
    }

    private fun safeName(d: BluetoothDevice): String? =
        try { d.name } catch (e: SecurityException) { d.address }

    fun batteryPct(): Int = runCatching {
        (context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager)
            .getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrDefault(0)

    // ---- input ----
    fun tapKey(modifier: Int, keycode: Int) {
        if (useWifi) wifi.key(modifier, keycode) else hid.tapKey(modifier, keycode)
    }
    fun mouseMove(dx: Int, dy: Int, wheel: Int = 0, buttons: Int = 0) {
        if (useWifi) {
            if (dx != 0 || dy != 0) wifi.move(dx, dy)
            if (wheel != 0) wifi.scroll(wheel)
            if (buttons != wifiBtn) {
                val changed = buttons xor wifiBtn
                if (changed and HidConstants.MOUSE_LEFT != 0) wifi.button("left", buttons and HidConstants.MOUSE_LEFT != 0)
                if (changed and HidConstants.MOUSE_RIGHT != 0) wifi.button("right", buttons and HidConstants.MOUSE_RIGHT != 0)
                if (changed and HidConstants.MOUSE_MIDDLE != 0) wifi.button("middle", buttons and HidConstants.MOUSE_MIDDLE != 0)
                wifiBtn = buttons
            }
        } else hid.sendMouseMove(dx, dy, wheel, buttons)
    }

    /** "Find pointer": jiggle the cursor left↔right so the host enlarges it and it's easy to spot. */
    fun findPointer() {
        val steps = intArrayOf(54, -54, 54, -54, 54, -54, 54, -54)
        steps.forEachIndexed { i, dx -> main.postDelayed({ hid.sendMouseMove(dx, 0, 0, 0) }, i * 28L) }
        logEvent("move", "find pointer")
    }

    fun logEvent(type: String, text: String) {
        val e = ActivityEvent(seq++, nowClock(), type, text)
        log.add(0, e)
        if (log.size > 40) log.removeRange(40, log.size)
    }

    private fun nowClock(): String {
        // HH:mm:ss without Date.now() restrictions — derive from elapsed wall clock.
        val cal = java.util.Calendar.getInstance()
        return "%02d:%02d:%02d".format(
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND),
        )
    }

    // ---- settings setters ----
    fun finishOnboarding() { onboarded = true; prefs.edit().putBoolean("onboarded", true).apply() }
    fun updateDark(v: Boolean) { dark = v; prefs.edit().putBoolean("dark", v).apply() }
    fun updateHue(v: AccentHue) { hue = v; prefs.edit().putString("hue", v.id).apply() }
    fun updateBehavior(v: String) { behavior = v; prefs.edit().putString("behavior", v).apply() }
    fun updateKeycap(v: String) { keycap = v; prefs.edit().putString("keycap", v).apply() }
    fun updateLayout(v: String) { layout = v; prefs.edit().putString("layout", v).apply() }
    fun updatePortraitLayout(v: String) { portraitLayout = v; prefs.edit().putString("portraitLayout", v).apply() }
    fun updateOneHandSide(v: String) { oneHandSide = v; prefs.edit().putString("oneHandSide", v).apply() }

    /** Restore Relay's original look & feel (leaves pairing, profile and connection alone). */
    fun resetDefaults() {
        updateDark(true); updateHue(AccentHue.from("152")); updateKeycap("sculpted"); updateLayout("gesturebar")
        updatePadStyle("gradient"); updateBehavior("sticky"); updateSensitivity(5)
        updateKeyHeight(56); updateKeyGap(6); updateSlideAnim(true)
    }
    fun updateSensitivity(v: Int) { sensitivity = v; prefs.edit().putInt("sensitivity", v).apply() }
    fun updateAccel(v: Int) { accel = v; prefs.edit().putInt("accel", v).apply() }
    fun updateSideBias(v: Int) { sideBias = v; prefs.edit().putInt("sideBias", v).apply() }
    fun updateShowPreview(v: Boolean) { showPreview = v; prefs.edit().putBoolean("showPreview", v).apply() }
    fun updatePadStyle(v: String) { padStyle = v; prefs.edit().putString("padStyle", v).apply() }
    fun updateDragStyle(v: Int) { dragStyle = v; prefs.edit().putInt("dragStyle", v).apply() }
    fun updateScrollSpeed(v: Int) { scrollSpeed = v; prefs.edit().putInt("scrollSpeed", v).apply() }
    fun updateInvertX(v: Boolean) { invertX = v; prefs.edit().putBoolean("invertX", v).apply() }
    fun updateInvertY(v: Boolean) { invertY = v; prefs.edit().putBoolean("invertY", v).apply() }
    fun updateSwipeNav(v: Boolean) { swipeNav = v; prefs.edit().putBoolean("swipeNav", v).apply() }
    fun updateKeyHeight(v: Int) { keyHeight = v; prefs.edit().putInt("keyHeight", v).apply() }
    fun updateKeyGap(v: Int) { keyGap = v; prefs.edit().putInt("keyGap", v).apply() }
    fun updateShowArrowKeys(v: Boolean) { showArrowKeys = v; prefs.edit().putBoolean("showArrowKeys", v).apply() }
    fun updateSlideAnim(v: Boolean) { slideAnim = v; prefs.edit().putBoolean("slideAnim", v).apply() }
    fun updatePadTimeout(v: Int) { padTimeout = v; prefs.edit().putInt("padTimeout", v).apply() }
    fun updateProfile(v: String) {
        profile = v
        // Non-Apple hosts: the ⌘ key should act as Ctrl so copy/paste & shortcuts land.
        swapCmd = v in setOf("windows", "linux", "androidtv")
        prefs.edit().putString("profile", v).putBoolean("swapCmd", swapCmd).apply()
    }
    fun updateMomentum(v: Boolean) { momentum = v; prefs.edit().putBoolean("momentum", v).apply() }
    fun updateFirmPress(v: Boolean) { firmPress = v; prefs.edit().putBoolean("firmPress", v).apply() }
    fun updateVolumeKeys(v: String) { volumeKeys = v; prefs.edit().putString("volumeKeys", v).apply() }

    /** Used by the volume-key remap in MainActivity. */
    fun scroll(amount: Int) = mouseMove(0, 0, amount, 0)
    fun click(right: Boolean = false) {
        val b = if (right) HidConstants.MOUSE_RIGHT else HidConstants.MOUSE_LEFT
        mouseMove(0, 0, 0, b); mouseMove(0, 0, 0, 0)
    }
    fun tapKeycode(keycode: Int) = tapKey(0, keycode)
    fun consumer(usage: Int) { if (useWifi) wifi.consumer(usage) else hid.sendConsumer(usage) }

    /** Replay a whole string as keystrokes (Type & Send / snippets / paste). */
    fun typeText(s: String) {
        if (s.isEmpty()) return
        if (useWifi) wifi.text(s) else hid.typeText(s)
        logEvent("key", "typed ${s.length} chars")
    }

    /** Current phone clipboard text (for paste-to-host). */
    fun clipboardText(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return ""
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
    }

    private val SNIP_SEP = ""
    fun addSnippet(s: String) {
        val t = s.trim(); if (t.isEmpty()) return
        snippets = (listOf(t) + snippets.filter { it != t }).take(20)
        prefs.edit().putString("snippets", snippets.joinToString(SNIP_SEP)).apply()
    }
    fun removeSnippet(s: String) {
        snippets = snippets.filter { it != s }
        prefs.edit().putString("snippets", snippets.joinToString(SNIP_SEP)).apply()
    }

    // ---- stay-awake jiggler (loop owned by the HID singleton so it survives Activity death) ----
    var jiggler by mutableStateOf(hid.jiggling); private set
    fun updateJiggler(on: Boolean) {
        if (on == jiggler) return
        jiggler = on
        hid.setJiggle(on)
        logEvent("move", if (on) "stay-awake on" else "stay-awake off")
    }

    // ---- profile-derived modifier labels (what each mod key actually emits on the target) ----
    // Apple targets use the familiar glyphs; other targets use plain words (the font has no ⊞/Win glyph).
    val isApple get() = profile in setOf("ipad", "mac", "appletv")
    private val metaWord get() = if (profile == "linux" || profile == "androidtv") "Super" else "Win"
    val ctrlKey: Pair<String, String> get() = when { swapCmd -> metaWord to "meta"; isApple -> "⌃" to "control"; else -> "Ctrl" to "ctrl" }
    val altKey: Pair<String, String> get() = if (isApple) "⌥" to "option" else "Alt" to "alt"
    val guiKey: Pair<String, String> get() = when { swapCmd -> "Ctrl" to "ctrl"; isApple -> "⌘" to "command"; else -> metaWord to "meta" }
    fun updateSwapCmd(v: Boolean) { swapCmd = v; prefs.edit().putBoolean("swapCmd", v).apply() }
    fun updateCapsEsc(v: Boolean) { capsEsc = v; prefs.edit().putBoolean("capsEsc", v).apply() }
    fun updateNaturalScroll(v: Boolean) { naturalScroll = v; prefs.edit().putBoolean("naturalScroll", v).apply() }
    fun updateTapClick(v: Boolean) { tapClick = v; prefs.edit().putBoolean("tapClick", v).apply() }
    fun updateHaptics(v: Boolean) { haptics = v; prefs.edit().putBoolean("haptics", v).apply() }
    fun updateShowReadout(v: Boolean) { showReadout = v; prefs.edit().putBoolean("showReadout", v).apply() }
    fun updateAutoReconnect(v: Boolean) { autoReconnect = v; hid.autoReconnect = v; prefs.edit().putBoolean("autoReconnect", v).apply() }
    fun updateLandscape(v: Boolean) { landscape = v; prefs.edit().putBoolean("landscape", v).apply() }

    // ---- HidPeripheralManager.Listener (callbacks arrive off the main thread) ----
    override fun onRegistrationChanged(registered: Boolean) {
        main.post { this.registered = registered }
    }

    override fun onConnectionStateChanged(deviceName: String?, connected: Boolean) {
        main.post {
            if (connected) {
                this.deviceName = deviceName
                conn = ConnState.CONNECTED
                latency = 8
            } else {
                conn = ConnState.IDLE
                latency = 0
            }
        }
    }
}
