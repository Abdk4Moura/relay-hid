package com.cadayn.hidinput.ui

/**
 * What a connected target can actually do. The UI renders FROM these flags, so it auto-simplifies
 * to the current target (no Bluetooth-only options on a WiFi link, no side-scroll where the BT mouse
 * descriptor cannot send it) and new device types plug in by declaring a capability set rather than
 * adding screens. This is the "simple yet extensible" backbone.
 *
 * Capabilities depend on BOTH the target profile (its kind) and the live transport: the rich path
 * (hi-res scroll, side-scroll, file transfer, screen stream, clipboard) needs the desktop receiver
 * over WiFi; Bluetooth-HID is the universal-but-basic path.
 */
enum class TargetKind { COMPUTER, TABLET, TV, CONSOLE }

data class Caps(
    val kind: TargetKind,
    val keyboard: Boolean,
    val trackpad: Boolean,
    val hiResScroll: Boolean,
    val sideScroll: Boolean,
    val mediaKeys: Boolean,
    val dpad: Boolean,
    val presenter: Boolean,
    val fileTransfer: Boolean,
    val screenStream: Boolean,
    val clipboard: Boolean,
)

object Capabilities {
    fun kindOf(profile: String): TargetKind = when (profile) {
        "mac", "windows", "linux" -> TargetKind.COMPUTER
        "ipad" -> TargetKind.TABLET
        "androidtv", "appletv" -> TargetKind.TV
        "ps" -> TargetKind.CONSOLE
        else -> TargetKind.COMPUTER
    }

    /**
     * @param profile the selected target profile
     * @param wifi true when connected to the desktop receiver over WiFi (the rich transport)
     */
    fun of(profile: String, wifi: Boolean): Caps {
        val kind = kindOf(profile)
        return Caps(
            kind = kind,
            keyboard = true,                          // every target accepts a HID keyboard
            trackpad = true,                          // HID mouse works on BT and WiFi
            hiResScroll = wifi,                       // REL_WHEEL_HI_RES is a receiver feature
            sideScroll = wifi,                        // BT mouse descriptor has no pan axis
            mediaKeys = true,                         // HID consumer page works on both
            dpad = true,
            presenter = kind == TargetKind.COMPUTER || kind == TargetKind.TABLET,
            fileTransfer = wifi,                      // chunked transfer is a receiver feature
            screenStream = wifi,                      // a WiFi link means a desktop receiver that can serve its screen
            clipboard = wifi,
        )
    }
}
