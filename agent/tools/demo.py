#!/usr/bin/env python3
"""Model-free pipeline check. Proves the relay path end-to-end WITHOUT any API spend:
connect -> heartbeat -> absolute move to screen center -> click -> type -> a key combo.

    python tools/demo.py            # uses .env (RELAY_HOST/PORT/PIN, MOUSE_MODE, screen size)
    python tools/demo.py --warp     # force relative-warp pointer mode

Watch the target: the cursor should jump to the middle and you should see typed text. If the
cursor doesn't move in abs mode, your compositor rejected the absolute device — use --warp.
"""

from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from config import Config          # noqa: E402
from executor import Executor      # noqa: E402
from relay_client import RelayClient  # noqa: E402
from screen import make_source     # noqa: E402


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--warp", action="store_true", help="force relative-warp pointer mode")
    args = ap.parse_args()

    cfg = Config()
    mode = "warp" if args.warp else cfg.mouse_mode
    if not cfg.relay_pin:
        print("set RELAY_PIN first (see relay panel / .env)", file=sys.stderr)
        return 2

    # Determine target geometry from the screen source so coordinates land correctly.
    src = make_source("local", monitor=cfg.monitor) if cfg.screen_source == "local" else \
        make_source("capture", device=cfg.capture_device,
                    width=cfg.capture_width, height=cfg.capture_height)
    _, w, h = src.grab()

    with RelayClient(cfg.relay_host, cfg.relay_port, cfg.relay_pin) as relay:
        ex = Executor(relay, mouse_mode=mode)
        ex.set_screen(w, h)
        print(f"• {w}x{h}, mouse={mode} — moving to center & typing")
        ex.move(w // 2, h // 2)
        ex.click(w // 2, h // 2)
        ex.type_text("relay computer-use pipeline OK")
        ex.key_combo("ctrl+a")
        print("• done — did the cursor jump and text appear on the target?")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
