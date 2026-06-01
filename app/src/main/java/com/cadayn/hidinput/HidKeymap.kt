package com.cadayn.hidinput

/**
 * Maps printable ASCII characters to a (modifier, HID keycode) pair so that typed
 * text can be replayed as keyboard reports. Layout assumes a US keyboard, which is
 * what the iPad falls back to for a generic HID keyboard.
 */
object HidKeymap {

    /** Returns (modifierMask, keycode) for [c], or null if it can't be typed. */
    fun charToKey(c: Char): Pair<Int, Int>? {
        // Letters
        if (c in 'a'..'z') return HidConstants.MOD_NONE to (0x04 + (c - 'a'))
        if (c in 'A'..'Z') return HidConstants.MOD_LSHIFT to (0x04 + (c - 'A'))

        // Digits 1-9 then 0
        if (c in '1'..'9') return HidConstants.MOD_NONE to (0x1E + (c - '1'))
        if (c == '0') return HidConstants.MOD_NONE to 0x27

        // Whitespace / control
        when (c) {
            ' ' -> return HidConstants.MOD_NONE to HidConstants.KEY_SPACE
            '\n' -> return HidConstants.MOD_NONE to HidConstants.KEY_ENTER
            '\t' -> return HidConstants.MOD_NONE to HidConstants.KEY_TAB
        }

        // Punctuation (US layout). Shifted symbols carry MOD_LSHIFT.
        return PUNCTUATION[c]
    }

    private val S = HidConstants.MOD_LSHIFT
    private val N = HidConstants.MOD_NONE

    private val PUNCTUATION: Map<Char, Pair<Int, Int>> = mapOf(
        '-' to (N to 0x2D), '_' to (S to 0x2D),
        '=' to (N to 0x2E), '+' to (S to 0x2E),
        '[' to (N to 0x2F), '{' to (S to 0x2F),
        ']' to (N to 0x30), '}' to (S to 0x30),
        '\\' to (N to 0x31), '|' to (S to 0x31),
        ';' to (N to 0x33), ':' to (S to 0x33),
        '\'' to (N to 0x34), '"' to (S to 0x34),
        '`' to (N to 0x35), '~' to (S to 0x35),
        ',' to (N to 0x36), '<' to (S to 0x36),
        '.' to (N to 0x37), '>' to (S to 0x37),
        '/' to (N to 0x38), '?' to (S to 0x38),
        // Shifted number row
        '!' to (S to 0x1E), '@' to (S to 0x1F), '#' to (S to 0x20),
        '$' to (S to 0x21), '%' to (S to 0x22), '^' to (S to 0x23),
        '&' to (S to 0x24), '*' to (S to 0x25), '(' to (S to 0x26),
        ')' to (S to 0x27)
    )
}
