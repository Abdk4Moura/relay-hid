"""Runtime configuration, read from environment / .env (see .env.example)."""

from __future__ import annotations

import os
from dataclasses import dataclass

try:
    from dotenv import load_dotenv

    load_dotenv()
except ImportError:  # dotenv is optional; env vars still work
    pass


@dataclass
class Config:
    relay_host: str = os.getenv("RELAY_HOST", "127.0.0.1")
    relay_port: int = int(os.getenv("RELAY_PORT", "47600"))
    relay_pin: str = os.getenv("RELAY_PIN", "")

    # pointer mapping: "abs" (relay >= 0.8.0 moveto) or "warp" (relative-only fallback)
    mouse_mode: str = os.getenv("MOUSE_MODE", "abs")
    action_pause: float = float(os.getenv("ACTION_PAUSE", "0.4"))

    # screen capture: "local" (mss) or "capture" (HDMI capture card via ffmpeg)
    screen_source: str = os.getenv("SCREEN_SOURCE", "local")
    capture_device: str = os.getenv("CAPTURE_DEVICE", "/dev/video0")
    capture_width: int = int(os.getenv("CAPTURE_WIDTH", "1920"))
    capture_height: int = int(os.getenv("CAPTURE_HEIGHT", "1080"))
    monitor: int = int(os.getenv("MONITOR", "1"))

    # model / safety
    max_steps: int = int(os.getenv("MAX_STEPS", "40"))
    require_confirm: bool = os.getenv("REQUIRE_CONFIRM", "1") != "0"

    gemini_api_key: str = os.getenv("GEMINI_API_KEY", "")
    gemini_model: str = os.getenv("GEMINI_MODEL", "gemini-2.5-computer-use-preview-10-2025")
    anthropic_api_key: str = os.getenv("ANTHROPIC_API_KEY", "")
    anthropic_model: str = os.getenv("ANTHROPIC_MODEL", "claude-sonnet-4-6")
