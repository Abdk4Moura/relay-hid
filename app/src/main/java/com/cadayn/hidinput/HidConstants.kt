package com.cadayn.hidinput

/**
 * HID report descriptor and usage constants.
 *
 * This describes a COMPOSITE device with two top-level collections, distinguished
 * by Report ID:
 *   - Report ID 1: Boot-compatible Keyboard  (8-byte report: modifier, reserved, 6 keys)
 *   - Report ID 2: Relative Mouse            (4-byte report: buttons, dX, dY, wheel)
 *   - Report ID 3: Consumer Control          (2-byte report: one 16-bit usage code)
 *
 * The iPad (HID host) reads this descriptor at pairing time and exposes the phone
 * as a standard external keyboard + mouse. Keyboard reports are wired up in v1;
 * the mouse collection is already present so adding a trackpad later needs NO
 * re-pairing.
 */
object HidConstants {

    const val REPORT_ID_KEYBOARD = 1
    const val REPORT_ID_MOUSE = 2
    const val REPORT_ID_CONSUMER = 3

    // --- Consumer Control usages (Consumer page 0x0C) — media / volume / brightness / nav ---
    const val CC_PLAY_PAUSE = 0x00CD
    const val CC_NEXT = 0x00B5
    const val CC_PREV = 0x00B6
    const val CC_MUTE = 0x00E2
    const val CC_VOL_UP = 0x00E9
    const val CC_VOL_DOWN = 0x00EA
    const val CC_BRI_UP = 0x006F
    const val CC_BRI_DOWN = 0x0070
    const val CC_HOME = 0x0223      // AC Home
    const val CC_SEARCH = 0x0221    // AC Search (Spotlight)

    // --- Keyboard modifier bitmask (first byte of the keyboard report) ---
    const val MOD_NONE = 0x00
    const val MOD_LCTRL = 0x01   // Control
    const val MOD_LSHIFT = 0x02  // Shift
    const val MOD_LALT = 0x04    // Option (Alt) on iPad
    const val MOD_LGUI = 0x08    // Command (⌘) on iPad
    const val MOD_RCTRL = 0x10
    const val MOD_RSHIFT = 0x20
    const val MOD_RALT = 0x40
    const val MOD_RGUI = 0x80

    // --- A few named HID usage keycodes (USB HID Usage Table, Keyboard page 0x07) ---
    const val KEY_NONE = 0x00
    const val KEY_ENTER = 0x28
    const val KEY_ESC = 0x29
    const val KEY_BACKSPACE = 0x2A
    const val KEY_TAB = 0x2B
    const val KEY_SPACE = 0x2C
    const val KEY_RIGHT = 0x4F
    const val KEY_LEFT = 0x50
    const val KEY_DOWN = 0x51
    const val KEY_UP = 0x52
    const val KEY_HOME = 0x4A
    const val KEY_END = 0x4D
    const val KEY_DELETE = 0x4C
    const val KEY_PAGE_UP = 0x4B
    const val KEY_PAGE_DOWN = 0x4E
    // Function keys F1–F12 are contiguous (F1=0x3A … F12=0x45).
    const val KEY_F1 = 0x3A
    const val KEY_F5 = 0x3E
    const val KEY_B = 0x05    // 'b' — blank slide in presentations

    // --- Mouse button bits (first byte of the mouse report) ---
    const val MOUSE_LEFT = 0x01
    const val MOUSE_RIGHT = 0x02
    const val MOUSE_MIDDLE = 0x04

    /**
     * The combined keyboard + mouse report descriptor.
     *
     * Built from ints (then narrowed to bytes) to avoid Kotlin's signed-Byte casting
     * noise for values > 0x7F. Each row corresponds to one HID item; see the keyboard
     * and mouse sections below.
     */
    val REPORT_DESCRIPTOR: ByteArray = intArrayOf(
        // ----------------------- Keyboard (Report ID 1) -----------------------
        0x05, 0x01,        // Usage Page (Generic Desktop)
        0x09, 0x06,        // Usage (Keyboard)
        0xA1, 0x01,        // Collection (Application)
        0x85, 0x01,        //   Report ID (1)
        0x05, 0x07,        //   Usage Page (Keyboard/Keypad)
        0x19, 0xE0,        //   Usage Minimum (Left Control)
        0x29, 0xE7,        //   Usage Maximum (Right GUI)
        0x15, 0x00,        //   Logical Minimum (0)
        0x25, 0x01,        //   Logical Maximum (1)
        0x75, 0x01,        //   Report Size (1)
        0x95, 0x08,        //   Report Count (8)
        0x81, 0x02,        //   Input (Data,Var,Abs) — 8 modifier bits
        0x95, 0x01,        //   Report Count (1)
        0x75, 0x08,        //   Report Size (8)
        0x81, 0x01,        //   Input (Const) — reserved byte
        0x95, 0x06,        //   Report Count (6)
        0x75, 0x08,        //   Report Size (8)
        0x15, 0x00,        //   Logical Minimum (0)
        0x25, 0x65,        //   Logical Maximum (101)
        0x05, 0x07,        //   Usage Page (Keyboard/Keypad)
        0x19, 0x00,        //   Usage Minimum (0)
        0x29, 0x65,        //   Usage Maximum (101)
        0x81, 0x00,        //   Input (Data,Array) — 6 key slots
        0xC0,              // End Collection

        // ------------------------- Mouse (Report ID 2) ------------------------
        0x05, 0x01,        // Usage Page (Generic Desktop)
        0x09, 0x02,        // Usage (Mouse)
        0xA1, 0x01,        // Collection (Application)
        0x85, 0x02,        //   Report ID (2)
        0x09, 0x01,        //   Usage (Pointer)
        0xA1, 0x00,        //   Collection (Physical)
        0x05, 0x09,        //     Usage Page (Buttons)
        0x19, 0x01,        //     Usage Minimum (Button 1)
        0x29, 0x03,        //     Usage Maximum (Button 3)
        0x15, 0x00,        //     Logical Minimum (0)
        0x25, 0x01,        //     Logical Maximum (1)
        0x95, 0x03,        //     Report Count (3)
        0x75, 0x01,        //     Report Size (1)
        0x81, 0x02,        //     Input (Data,Var,Abs) — 3 buttons
        0x95, 0x01,        //     Report Count (1)
        0x75, 0x05,        //     Report Size (5)
        0x81, 0x01,        //     Input (Const) — 5 bits padding
        0x05, 0x01,        //     Usage Page (Generic Desktop)
        0x09, 0x30,        //     Usage (X)
        0x09, 0x31,        //     Usage (Y)
        0x09, 0x38,        //     Usage (Wheel)
        0x15, 0x81,        //     Logical Minimum (-127)
        0x25, 0x7F,        //     Logical Maximum (127)
        0x75, 0x08,        //     Report Size (8)
        0x95, 0x03,        //     Report Count (3)
        0x81, 0x06,        //     Input (Data,Var,Rel) — X, Y, Wheel
        0xC0,              //   End Collection
        0xC0,              // End Collection

        // ------------------- Consumer Control (Report ID 3) -------------------
        // One 16-bit usage code per report (0 = release). Covers media transport,
        // volume, brightness, and a couple of AC nav usages (Home / Search).
        0x05, 0x0C,        // Usage Page (Consumer)
        0x09, 0x01,        // Usage (Consumer Control)
        0xA1, 0x01,        // Collection (Application)
        0x85, 0x03,        //   Report ID (3)
        0x15, 0x00,        //   Logical Minimum (0)
        0x26, 0xFF, 0x03,  //   Logical Maximum (0x03FF)
        0x19, 0x00,        //   Usage Minimum (0)
        0x2A, 0xFF, 0x03,  //   Usage Maximum (0x03FF)
        0x75, 0x10,        //   Report Size (16)
        0x95, 0x01,        //   Report Count (1)
        0x81, 0x00,        //   Input (Data,Array) — one consumer usage
        0xC0               // End Collection
    ).map { it.toByte() }.toByteArray()
}
