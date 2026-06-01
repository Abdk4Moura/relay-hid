"""Map symbolic key names (as emitted by computer-use models) to the relay's
HID-usage `key` message: a Keyboard/Keypad page (0x07) usage code plus a modifier
bitmask. Mirrors hid_to_key()/mods_to_keys() in desktop/src/main.rs.

Models express shortcuts as strings like "ctrl+c", "Return", "cmd+shift+t". We parse
those into (usage_code, mod_bitmask) so the relay can replay the chord.
"""

from __future__ import annotations

# HID modifier bitmask — matches mods_to_keys() in the relay.
MOD_CTRL = 0x01
MOD_SHIFT = 0x02
MOD_ALT = 0x04
MOD_META = 0x08

_MOD_ALIASES = {
    "ctrl": MOD_CTRL, "control": MOD_CTRL,
    "shift": MOD_SHIFT,
    "alt": MOD_ALT, "option": MOD_ALT,
    "cmd": MOD_META, "command": MOD_META, "meta": MOD_META,
    "super": MOD_META, "win": MOD_META, "windows": MOD_META,
}

# Symbolic key name -> HID Keyboard/Keypad usage (page 0x07). Only the codes the relay's
# hid_to_key() actually maps are useful; the rest are dropped with a warning by the caller.
_NAMED_KEYS = {
    "enter": 0x28, "return": 0x28,
    "esc": 0x29, "escape": 0x29,
    "backspace": 0x2A,
    "tab": 0x2B,
    "space": 0x2C, "spacebar": 0x2C,
    "minus": 0x2D, "-": 0x2D,
    "equal": 0x2E, "=": 0x2E,
    "[": 0x2F, "]": 0x30, "\\": 0x31,
    ";": 0x33, "'": 0x34, "`": 0x35,
    ",": 0x36, ".": 0x37, "/": 0x38,
    "home": 0x4A, "pageup": 0x4B, "page_up": 0x4B,
    "delete": 0x4C, "del": 0x4C,
    "end": 0x4D, "pagedown": 0x4E, "page_down": 0x4E,
    "right": 0x4F, "arrowright": 0x4F,
    "left": 0x50, "arrowleft": 0x50,
    "down": 0x51, "arrowdown": 0x51,
    "up": 0x52, "arrowup": 0x52,
}
# function keys F1..F12 -> 0x3A..0x45
for _i in range(1, 13):
    _NAMED_KEYS[f"f{_i}"] = 0x3A + (_i - 1)


def _single_key_usage(name: str) -> int | None:
    """Resolve one non-modifier key token to its HID usage code, or None if unmapped."""
    k = name.strip().lower()
    if not k:
        return None
    if len(k) == 1 and "a" <= k <= "z":
        return 0x04 + (ord(k) - ord("a"))
    if len(k) == 1 and "1" <= k <= "9":
        return 0x1E + (ord(k) - ord("1"))
    if k == "0":
        return 0x27
    return _NAMED_KEYS.get(k)


def parse_combo(combo: str) -> tuple[int, int] | None:
    """Parse "ctrl+shift+t" -> (usage_code, mod_bitmask). Returns None if the base key
    can't be mapped to a relay-supported usage. Accepts '+' or '-' separators."""
    raw = combo.replace("-", "+") if combo not in ("-", "+") else combo
    tokens = [t for t in raw.split("+") if t != ""] or [combo]
    mods = 0
    base: str | None = None
    for tok in tokens:
        low = tok.strip().lower()
        if low in _MOD_ALIASES:
            mods |= _MOD_ALIASES[low]
        else:
            base = tok  # last non-modifier token wins
    if base is None:
        return None
    usage = _single_key_usage(base)
    if usage is None:
        return None
    return usage, mods
