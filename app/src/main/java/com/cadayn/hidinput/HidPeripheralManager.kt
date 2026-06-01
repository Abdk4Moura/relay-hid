package com.cadayn.hidinput

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import java.util.concurrent.Executors

/**
 * Registers this Android device as a Bluetooth HID peripheral (keyboard + mouse)
 * and sends input reports to the connected host (the iPad).
 *
 * Permission note: every Bluetooth call below requires BLUETOOTH_CONNECT (Android 12+).
 * MainActivity requests it before calling [start]; methods are annotated
 * @SuppressLint("MissingPermission") on that contract.
 */
@SuppressLint("MissingPermission")
class HidPeripheralManager private constructor(private val context: Context) {

    interface Listener {
        fun onRegistrationChanged(registered: Boolean)
        fun onConnectionStateChanged(deviceName: String?, connected: Boolean)
    }

    var listener: Listener? = null

    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null

    private val recon = context.getSharedPreferences("hid", Context.MODE_PRIVATE)
    /** When true, on (re)registration the phone tries to reconnect to the last host. */
    var autoReconnect: Boolean = true

    var isRegistered: Boolean = false; private set

    // Single worker so key press/release ordering is preserved.
    private val worker = Executors.newSingleThreadExecutor()

    val bluetoothEnabled: Boolean get() = adapter?.isEnabled == true
    val isConnected: Boolean get() = host != null
    val hostName: String? get() = host?.let { safeName(it) }
    val pairedDevices: List<BluetoothDevice> get() = adapter?.bondedDevices?.toList() ?: emptyList()

    /** Acquire the HID_DEVICE profile proxy; registration happens once it connects. Idempotent. */
    fun start() {
        if (hidDevice != null) { tryAutoReconnect(); return }
        adapter?.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
    }

    /** Re-establish the link to the last host (e.g. on screen unlock / app resume). */
    fun reconnect() = tryAutoReconnect()

    fun stop() {
        try {
            hidDevice?.unregisterApp()
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (e: Exception) {
            Log.w(TAG, "stop() failed", e)
        }
        hidDevice = null
        host = null
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                listener?.onRegistrationChanged(false)
            }
        }
    }

    private fun registerApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Android HID Keyboard",
            "Android phone acting as a Bluetooth keyboard & mouse",
            "Cadayn",
            BluetoothHidDevice.SUBCLASS1_COMBO,   // combo keyboard + mouse
            HidConstants.REPORT_DESCRIPTOR
        )
        val ok = hidDevice?.registerApp(sdp, null, null, worker, hidCallback) ?: false
        Log.i(TAG, "registerApp requested, accepted=$ok")
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.i(TAG, "onAppStatusChanged registered=$registered")
            isRegistered = registered
            listener?.onRegistrationChanged(registered)
            if (registered) tryAutoReconnect()
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.i(TAG, "onConnectionStateChanged ${device.address} state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    recon.edit().putString("lastHost", device.address).apply()
                }
                BluetoothProfile.STATE_DISCONNECTED -> if (device == host) host = null
            }
            listener?.onConnectionStateChanged(safeName(device), state == BluetoothProfile.STATE_CONNECTED)
        }
    }

    /** On (re)registration, ask the last-known host to reconnect (device-initiated). */
    private fun tryAutoReconnect() {
        if (!autoReconnect || host != null) return
        val mac = recon.getString("lastHost", null) ?: return
        val dev = adapter?.bondedDevices?.firstOrNull { it.address == mac } ?: return
        Log.i(TAG, "auto-reconnect → $mac")
        worker.execute { runCatching { hidDevice?.connect(dev) } }
    }

    private fun safeName(device: BluetoothDevice): String? =
        try { device.name } catch (e: SecurityException) { device.address }

    /** Connect to an already-paired host (e.g. reconnect to the iPad). */
    fun connectToHost(device: BluetoothDevice) {
        hidDevice?.connect(device)
    }

    /** Rename the phone's Bluetooth adapter (GLOBAL — affects all BT) so hosts show this name.
     *  Saves the original once so it can be restored. Hosts already bonded keep the cached name
     *  until they forget & re-pair. */
    fun setAdapterName(name: String): Boolean {
        val a = adapter ?: return false
        if (recon.getString("origBtName", null) == null) {
            runCatching { a.name }.getOrNull()?.let { recon.edit().putString("origBtName", it).apply() }
        }
        return runCatching { a.name = name; true }.getOrDefault(false)
    }

    fun restoreAdapterName() {
        val a = adapter ?: return
        val orig = recon.getString("origBtName", null) ?: return
        runCatching { a.name = orig }
        recon.edit().remove("origBtName").apply()
    }

    // ----------------------------- Keyboard -----------------------------

    /** Tap a key (with optional modifier), then release. Runs on the worker thread. */
    fun tapKey(modifier: Int, keycode: Int) {
        worker.execute {
            sendKeyboardReport(modifier, intArrayOf(keycode))
            Thread.sleep(12)
            sendKeyboardReport(HidConstants.MOD_NONE, IntArray(0))
        }
    }

    /** Type a string by replaying each character as a press/release. */
    fun typeText(text: String) {
        worker.execute {
            for (c in text) {
                val mapped = HidKeymap.charToKey(c) ?: continue
                sendKeyboardReport(mapped.first, intArrayOf(mapped.second))
                Thread.sleep(10)
                sendKeyboardReport(HidConstants.MOD_NONE, IntArray(0))
                Thread.sleep(10)
            }
        }
    }

    private fun sendKeyboardReport(modifier: Int, keys: IntArray) {
        val dev = host ?: return
        val report = ByteArray(8)               // [modifier, reserved, k1..k6]
        report[0] = modifier.toByte()
        for (i in 0 until minOf(keys.size, 6)) {
            report[2 + i] = keys[i].toByte()
        }
        hidDevice?.sendReport(dev, HidConstants.REPORT_ID_KEYBOARD, report)
    }

    // -------------------------- Consumer Control ------------------------
    // Media / volume / brightness. Press the usage, then release with 0.

    fun sendConsumer(usage: Int) {
        worker.execute {
            sendConsumerReport(usage)
            Thread.sleep(12)
            sendConsumerReport(0)
        }
    }

    private fun sendConsumerReport(usage: Int) {
        val dev = host ?: return
        val report = byteArrayOf((usage and 0xFF).toByte(), ((usage shr 8) and 0xFF).toByte())
        hidDevice?.sendReport(dev, HidConstants.REPORT_ID_CONSUMER, report)
    }

    // ---------------------------- Stay-awake ----------------------------
    // Lives on the singleton (process lifetime) so it survives the Activity being
    // destroyed — keeps firing as long as the foreground service holds the process.

    private val jiggleMain = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile var jiggling = false; private set
    private val jiggleTask = object : Runnable {
        override fun run() {
            sendMouseMove(1, 0); sendMouseMove(-1, 0)   // imperceptible nudge
            jiggleMain.postDelayed(this, 25_000L)
        }
    }
    fun setJiggle(on: Boolean) {
        jiggling = on
        jiggleMain.removeCallbacks(jiggleTask)
        if (on) jiggleMain.postDelayed(jiggleTask, 1_000L)
    }

    // ------------------------------- Mouse ------------------------------
    // Wired into the descriptor and ready for v2 (trackpad UI). dx/dy/wheel are
    // relative, clamped to the signed-byte range the descriptor declares.

    fun sendMouseMove(dx: Int, dy: Int, wheel: Int = 0, buttons: Int = 0) {
        val dev = host ?: return
        val report = byteArrayOf(
            buttons.toByte(),
            dx.coerceIn(-127, 127).toByte(),
            dy.coerceIn(-127, 127).toByte(),
            wheel.coerceIn(-127, 127).toByte()
        )
        hidDevice?.sendReport(dev, HidConstants.REPORT_ID_MOUSE, report)
    }

    companion object {
        private const val TAG = "HidPeripheral"

        @Volatile private var INSTANCE: HidPeripheralManager? = null

        /** Process-wide singleton so the foreground service and the UI share one registration. */
        fun getInstance(context: Context): HidPeripheralManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: HidPeripheralManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
