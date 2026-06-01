"""TCP client for the Relay desktop receiver / phone app.

Speaks the exact newline-delimited JSON protocol implemented in desktop/src/main.rs:

    {"t":"hello","token":"<pin>"}        # auth, must be first
    {"t":"text","s":"hello"}             # type a UTF-8 string
    {"t":"key","code":40,"mods":8}       # HID usage code + HID modifier bitmask
    {"t":"move","dx":12,"dy":-4}         # RELATIVE pointer move
    {"t":"moveto","x":16384,"y":8000}    # ABSOLUTE position, normalized 0..32767 (relay >= 0.8.0)
    {"t":"scroll","dy":2}                # wheel detents
    {"t":"button","b":"left","down":true}
    {"t":"click","b":"right"}

This is the single executor seam: a computer-use model decides *what* to do, and every
action ultimately becomes one of these messages — whether the relay injects it via Linux
uinput, or the phone re-emits it as Bluetooth HID into an unmodified target machine.
"""

from __future__ import annotations

import json
import socket
import threading
import time

# Absolute-axis full scale — must match ABS_MAX in desktop/src/main.rs.
ABS_MAX = 32767

# The relay drops a connection that sends nothing for CLIENT_TIMEOUT_SECS (12s in main.rs).
# A computer-use loop idles far longer than that while the model thinks, so we heartbeat well
# inside the window to keep the socket alive across turns.
HEARTBEAT_SECS = 5.0


class RelayClient:
    def __init__(self, host: str, port: int, pin: str, *, timeout: float = 5.0):
        self.host = host
        self.port = port
        self.pin = pin
        self.timeout = timeout
        self._sock: socket.socket | None = None
        self._lock = threading.Lock()
        self._hb_stop: threading.Event | None = None
        self._hb_thread: threading.Thread | None = None

    # ----- connection -----
    def connect(self) -> None:
        s = socket.create_connection((self.host, self.port), timeout=self.timeout)
        s.settimeout(None)
        self._sock = s
        self._send({"t": "hello", "token": self.pin})
        self._start_heartbeat()

    def close(self) -> None:
        self._stop_heartbeat()
        if self._sock is not None:
            try:
                self._sock.close()
            finally:
                self._sock = None

    def _start_heartbeat(self) -> None:
        self._hb_stop = threading.Event()

        def beat() -> None:
            while self._hb_stop is not None and not self._hb_stop.wait(HEARTBEAT_SECS):
                try:
                    self.ping()
                except OSError:
                    return

        self._hb_thread = threading.Thread(target=beat, daemon=True)
        self._hb_thread.start()

    def _stop_heartbeat(self) -> None:
        if self._hb_stop is not None:
            self._hb_stop.set()
            self._hb_stop = None

    def __enter__(self) -> "RelayClient":
        self.connect()
        return self

    def __exit__(self, *_exc) -> None:
        self.close()

    def _send(self, obj: dict) -> None:
        if self._sock is None:
            raise RuntimeError("not connected — call connect() first")
        line = json.dumps(obj, separators=(",", ":")) + "\n"
        # The heartbeat thread and the action loop both write; serialize so frames never interleave.
        with self._lock:
            self._sock.sendall(line.encode("utf-8"))

    def ping(self) -> None:
        self._send({"t": "ping"})

    # ----- input primitives -----
    def text(self, s: str) -> None:
        self._send({"t": "text", "s": s})

    def key(self, code: int, mods: int = 0) -> None:
        self._send({"t": "key", "code": code, "mods": mods})

    def move(self, dx: int, dy: int) -> None:
        self._send({"t": "move", "dx": dx, "dy": dy})

    def moveto(self, x_norm: int, y_norm: int) -> None:
        """Absolute position; x_norm/y_norm already scaled into 0..ABS_MAX."""
        self._send({"t": "moveto", "x": x_norm, "y": y_norm})

    def scroll(self, dy: int) -> None:
        self._send({"t": "scroll", "dy": dy})

    def button(self, b: str, down: bool) -> None:
        self._send({"t": "button", "b": b, "down": down})

    def click(self, b: str = "left") -> None:
        self._send({"t": "click", "b": b})

    def double_click(self, b: str = "left", gap: float = 0.06) -> None:
        self.click(b)
        time.sleep(gap)
        self.click(b)
