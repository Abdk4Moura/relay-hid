package com.cadayn.hidinput

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.cadayn.hidinput.ui.RelayApp
import com.cadayn.hidinput.ui.RelayController
import com.cadayn.hidinput.ui.theme.RelayTheme

class MainActivity : ComponentActivity() {

    private lateinit var controller: RelayController
    private var permsGranted by mutableStateOf(false)

    private val requestPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permsGranted = btGranted()
            if (permsGranted) startHid()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller = RelayController(applicationContext)

        // Adaptive: allow rotation so portrait gives the thumb keyboard and
        // landscape gives the full desktop layout. (Respects the user's rotation lock.)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER

        ensurePermissions()

        setContent {
            RelayTheme(dark = controller.dark, hue = controller.hue) {
                RelayApp(
                    c = controller,
                    permsGranted = permsGranted,
                    onMakeDiscoverable = {
                        runCatching { startActivity(controller.discoverableIntent()) }
                    },
                    onRequestPerms = { ensurePermissions() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // re-establish the HID link if it dropped while the screen was off
        if (::controller.isInitialized && permsGranted) controller.reconnect()
    }

    // Optional: repurpose the volume rocker (foreground only) — scroll / page / click.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (::controller.isInitialized && controller.volumeKeys != "off") {
            val up = keyCode == KeyEvent.KEYCODE_VOLUME_UP
            val down = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            if (up || down) {
                when (controller.volumeKeys) {
                    "scroll" -> controller.scroll(if (up) 10 else -10)
                    "page" -> controller.tapKeycode(if (up) 0x4B else 0x4E)   // PageUp / PageDown
                    "click" -> controller.click(right = down)
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (::controller.isInitialized && controller.volumeKeys != "off" &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        if (::controller.isInitialized) controller.stop()  // HID stays alive via the foreground service
        super.onDestroy()
    }

    private fun btGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun startHid() {
        HidService.start(this)   // foreground service keeps the registration alive across lock
        controller.start()
    }

    private fun ensurePermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            permsGranted = btGranted()
            if (permsGranted) startHid()
        } else {
            requestPerms.launch(missing.toTypedArray())
        }
    }
}
