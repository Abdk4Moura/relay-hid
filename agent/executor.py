"""Provider-agnostic action executor.

A computer-use model (Gemini, Claude, …) emits UI actions. Each provider adapter translates
its model-specific function calls into the small vocabulary of methods here, all expressed in
TARGET PIXEL coordinates. This class is the only place that knows how to turn a pixel target
into relay messages — in particular how to hit an ABSOLUTE coordinate with a relay that may
only do relative moves.

Mouse modes:
  abs   — uses the relay's `moveto` (relay >= 0.8.0). Deterministic; the preferred path.
  warp  — relative-only fallback: slam the pointer to (0,0) by over-moving past the top-left,
          then move right/down by exactly the target pixels. Works on the Bluetooth-HID path
          and older relays, but pointer acceleration on the target can skew it — keep target
          mouse acceleration off for best results.
"""

from __future__ import annotations

import time

from relay_client import ABS_MAX, RelayClient
from keymap import parse_combo


class Executor:
    def __init__(self, relay: RelayClient, *, mouse_mode: str = "abs", action_pause: float = 0.4):
        self.relay = relay
        self.mouse_mode = mouse_mode
        self.action_pause = action_pause
        self.screen_w = 1
        self.screen_h = 1
        self._cx = 0  # tracked pointer position (pixels) for warp mode
        self._cy = 0

    def set_screen(self, w: int, h: int) -> None:
        self.screen_w = max(1, w)
        self.screen_h = max(1, h)

    # ----- pointer positioning -----
    def move(self, x: int, y: int) -> None:
        x = max(0, min(self.screen_w - 1, int(x)))
        y = max(0, min(self.screen_h - 1, int(y)))
        if self.mouse_mode == "abs":
            nx = round(x / self.screen_w * ABS_MAX)
            ny = round(y / self.screen_h * ABS_MAX)
            self.relay.moveto(nx, ny)
        else:
            # Pin to the top-left corner (over-travel guarantees clamping), then offset in.
            self.relay.move(-(self.screen_w + 200), -(self.screen_h + 200))
            if x or y:
                self.relay.move(x, y)
        self._cx, self._cy = x, y
        time.sleep(0.05)

    # ----- clicks / buttons -----
    def click(self, x: int, y: int, button: str = "left", count: int = 1) -> None:
        self.move(x, y)
        for i in range(max(1, count)):
            if i:
                time.sleep(0.06)
            self.relay.click(button)
        self._pause()

    def mouse_down(self, x: int, y: int, button: str = "left") -> None:
        self.move(x, y)
        self.relay.button(button, True)

    def mouse_up(self, x: int, y: int, button: str = "left") -> None:
        self.move(x, y)
        self.relay.button(button, False)
        self._pause()

    def drag(self, x1: int, y1: int, x2: int, y2: int, button: str = "left") -> None:
        self.move(x1, y1)
        self.relay.button(button, True)
        time.sleep(0.08)
        self.move(x2, y2)
        time.sleep(0.08)
        self.relay.button(button, False)
        self._pause()

    # ----- keyboard -----
    def type_text(self, text: str) -> None:
        self.relay.text(text)
        self._pause()

    def key_combo(self, combo: str) -> bool:
        """Replay a chord like 'ctrl+c'. Returns False if the key can't be mapped."""
        parsed = parse_combo(combo)
        if parsed is None:
            return False
        usage, mods = parsed
        self.relay.key(usage, mods)
        self._pause()
        return True

    # ----- scroll -----
    def scroll(self, x: int, y: int, direction: str = "down", clicks: int = 3) -> None:
        if x or y:
            self.move(x, y)
        dy = -clicks if direction.lower() == "down" else clicks
        self.relay.scroll(dy)
        self._pause()

    def wait(self, seconds: float = 1.0) -> None:
        time.sleep(seconds)

    def _pause(self) -> None:
        time.sleep(self.action_pause)
