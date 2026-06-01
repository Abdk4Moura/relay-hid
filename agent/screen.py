"""Screen capture sources for the computer-use loop.

The model needs a picture of the TARGET's screen each turn. How you get it depends on how
much the target is allowed to run:

  * LocalScreen (mss)  — grabs the screen of the machine running this script. Use this to
                         test the whole pipeline on ONE box, or when the agent runs on the
                         same Linux host as the relay.
  * CaptureCard (v4l2) — grabs frames from an HDMI->USB capture device (e.g. /dev/video0)
                         via ffmpeg. This is the real out-of-band path: the target stays
                         completely unmodified — its HDMI goes to the capture card, your HID
                         relay drives its keyboard/mouse, and nothing runs on the target.

Every source returns (png_bytes, width_px, height_px). Width/height are what the executor
uses to denormalize the model's 0..999 coordinates into pixels.
"""

from __future__ import annotations

import io
import subprocess
from typing import Protocol


class ScreenSource(Protocol):
    def grab(self) -> tuple[bytes, int, int]: ...


class LocalScreen:
    """Capture a monitor of the local machine via `mss`. monitor=1 is the primary display."""

    def __init__(self, monitor: int = 1):
        self.monitor = monitor

    def grab(self) -> tuple[bytes, int, int]:
        import mss  # imported lazily so the capture-card path needs no GUI deps
        from PIL import Image

        with mss.mss() as sct:
            mon = sct.monitors[self.monitor]
            shot = sct.grab(mon)
            img = Image.frombytes("RGB", shot.size, shot.bgra, "raw", "BGRX")
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        return buf.getvalue(), img.width, img.height


class CaptureCard:
    """Grab a single frame from a V4L2 capture device with ffmpeg. Out-of-band: the target
    runs nothing. Requires `ffmpeg` on PATH and a capture card at `device` (e.g. /dev/video0)."""

    def __init__(self, device: str = "/dev/video0", width: int = 1920, height: int = 1080):
        self.device = device
        self.width = width
        self.height = height

    def grab(self) -> tuple[bytes, int, int]:
        from PIL import Image

        cmd = [
            "ffmpeg", "-loglevel", "error",
            "-f", "v4l2",
            "-video_size", f"{self.width}x{self.height}",
            "-i", self.device,
            "-frames:v", "1",
            "-f", "image2pipe", "-vcodec", "png", "pipe:1",
        ]
        out = subprocess.run(cmd, capture_output=True, check=True).stdout
        img = Image.open(io.BytesIO(out)).convert("RGB")
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        return buf.getvalue(), img.width, img.height


def make_source(kind: str, **kw) -> ScreenSource:
    if kind == "local":
        return LocalScreen(monitor=int(kw.get("monitor", 1)))
    if kind == "capture":
        return CaptureCard(
            device=kw.get("device", "/dev/video0"),
            width=int(kw.get("width", 1920)),
            height=int(kw.get("height", 1080)),
        )
    raise ValueError(f"unknown screen source: {kind!r} (expected 'local' or 'capture')")
