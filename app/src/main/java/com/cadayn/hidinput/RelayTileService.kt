package com.cadayn.hidinput

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cadayn.hidinput.ui.RelayController

/** Quick-Settings tile: connect to the last WiFi desktop / disconnect. */
class RelayTileService : TileService() {

    private val c get() = RelayController.getInstance(applicationContext)

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val ctrl = c
        if (ctrl.wifiConnected) ctrl.wifiDisconnect() else ctrl.reconnectLastWifi()
        refresh()
        // connection is async — refresh again once it settles
        Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 1400)
    }

    private fun refresh() {
        val t = qsTile ?: return
        val on = c.wifiConnected
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Relay WiFi"
        t.contentDescription = if (on) "Connected to ${c.wifiHost}" else "Tap to connect last desktop"
        t.updateTile()
    }
}
