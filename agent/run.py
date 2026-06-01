#!/usr/bin/env python3
"""Computer-use agent entry point.

    python run.py --provider gemini   --goal "Open a terminal and run: uname -a"
    python run.py --provider anthropic --goal "Take a screenshot and describe the screen"

Wires together: screen source (what the model sees) -> provider (the brain) -> executor ->
relay client (the hands, over your existing HID relay).
"""

from __future__ import annotations

import argparse
import sys

from config import Config
from executor import Executor
from relay_client import RelayClient
from screen import make_source


def build_screen(cfg: Config):
    if cfg.screen_source == "capture":
        return make_source("capture", device=cfg.capture_device,
                           width=cfg.capture_width, height=cfg.capture_height)
    return make_source("local", monitor=cfg.monitor)


def main() -> int:
    ap = argparse.ArgumentParser(description="HID computer-use agent")
    ap.add_argument("--provider", choices=["gemini", "anthropic"], default="gemini")
    ap.add_argument("--goal", required=True, help="natural-language task for the agent")
    ap.add_argument("--mouse-mode", choices=["abs", "warp"], help="override MOUSE_MODE")
    args = ap.parse_args()

    cfg = Config()
    if args.mouse_mode:
        cfg.mouse_mode = args.mouse_mode
    if not cfg.relay_pin:
        print("RELAY_PIN is empty — set it (see the relay control panel / .env).", file=sys.stderr)
        return 2

    screen = build_screen(cfg)

    with RelayClient(cfg.relay_host, cfg.relay_port, cfg.relay_pin) as relay:
        executor = Executor(relay, mouse_mode=cfg.mouse_mode, action_pause=cfg.action_pause)
        print(f"• connected to relay {cfg.relay_host}:{cfg.relay_port} "
              f"(mouse={cfg.mouse_mode}, screen={cfg.screen_source}, provider={args.provider})")

        if args.provider == "gemini":
            if not cfg.gemini_api_key:
                print("GEMINI_API_KEY is empty.", file=sys.stderr)
                return 2
            from providers.gemini import GeminiComputerUse
            agent = GeminiComputerUse(cfg, executor, screen)
        else:
            if not cfg.anthropic_api_key:
                print("ANTHROPIC_API_KEY is empty.", file=sys.stderr)
                return 2
            from providers.anthropic import AnthropicComputerUse
            agent = AnthropicComputerUse(cfg, executor, screen)

        agent.run(args.goal)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
