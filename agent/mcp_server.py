#!/usr/bin/env python3
"""MCP server exposing the HID relay as computer-use tools.

Instead of running its own model loop, this server hands the EYES and HANDS to whatever MCP
client connects (Antigravity CLI `agy`, Claude Code, etc.). The client's own model is the
computer-use brain: it calls `screenshot` to see the target, reasons, then calls `click` /
`type_text` / `key` / `scroll` / `drag` — each of which is injected into an unmodified target
machine through your existing relay (uinput or Bluetooth HID).

Coordinates are in the PIXEL space of the most recent `screenshot` (its width/height are
reported in the tool result). Call `screenshot` first, and again after any action that changes
the screen.

Run:  python mcp_server.py            # stdio transport (default for agy / Claude Code)
Config in .env (RELAY_*, MOUSE_MODE, SCREEN_SOURCE, …) — same as the standalone agent.
"""

from __future__ import annotations

import os
import sys

# MCP clients launch this with their own cwd; make sibling modules importable regardless.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from mcp.server.fastmcp import FastMCP, Image  # noqa: E402

from config import Config  # noqa: E402
from executor import Executor  # noqa: E402
from relay_client import RelayClient  # noqa: E402
from screen import make_source  # noqa: E402

mcp = FastMCP("hid-computer-use")

_cfg = Config()
_relay: RelayClient | None = None
_executor: Executor | None = None
_screen = None


def _ensure() -> tuple[Executor, object]:
    """Lazily open the relay connection and screen source on first tool use."""
    global _relay, _executor, _screen
    if _executor is None:
        if not _cfg.relay_pin:
            raise RuntimeError("RELAY_PIN is empty — set it in .env (see relay control panel).")
        _screen = (make_source("local", monitor=_cfg.monitor)
                   if _cfg.screen_source == "local"
                   else make_source("capture", device=_cfg.capture_device,
                                    width=_cfg.capture_width, height=_cfg.capture_height))
        _relay = RelayClient(_cfg.relay_host, _cfg.relay_port, _cfg.relay_pin)
        _relay.connect()
        _executor = Executor(_relay, mouse_mode=_cfg.mouse_mode, action_pause=_cfg.action_pause)
    return _executor, _screen


@mcp.tool()
def screenshot() -> list:
    """Capture the target screen. Returns the image plus its pixel dimensions; all click/move
    coordinates must be given in THIS image's pixel space. Call again after the screen changes."""
    ex, screen = _ensure()
    png, w, h = screen.grab()
    ex.set_screen(w, h)
    return [f"screen is {w}x{h} pixels; give coordinates in this space.",
            Image(data=png, format="png")]


@mcp.tool()
def click(x: int, y: int, button: str = "left", count: int = 1) -> str:
    """Move the pointer to (x, y) and click. button: left|right|middle. count: 1=single, 2=double."""
    ex, _ = _ensure()
    ex.click(x, y, button=button, count=count)
    return f"clicked {button} x{count} at ({x},{y})"


@mcp.tool()
def move(x: int, y: int) -> str:
    """Move the pointer to (x, y) without clicking (hover)."""
    ex, _ = _ensure()
    ex.move(x, y)
    return f"moved to ({x},{y})"


@mcp.tool()
def type_text(text: str) -> str:
    """Type a UTF-8 string at the current focus (no automatic Enter)."""
    ex, _ = _ensure()
    ex.type_text(text)
    return f"typed {len(text)} chars"


@mcp.tool()
def key(combo: str) -> str:
    """Press a key or chord, e.g. 'enter', 'ctrl+c', 'cmd+shift+t', 'ArrowDown'."""
    ex, _ = _ensure()
    ok = ex.key_combo(combo)
    return f"pressed {combo}" if ok else f"unmapped key combo: {combo!r}"


@mcp.tool()
def scroll(x: int, y: int, direction: str = "down", clicks: int = 3) -> str:
    """Scroll at (x, y). direction: up|down. clicks: number of wheel detents."""
    ex, _ = _ensure()
    ex.scroll(x, y, direction=direction, clicks=clicks)
    return f"scrolled {direction} {clicks} at ({x},{y})"


@mcp.tool()
def drag(x1: int, y1: int, x2: int, y2: int, button: str = "left") -> str:
    """Press at (x1,y1), drag to (x2,y2), release. Used for selections and drag-and-drop."""
    ex, _ = _ensure()
    ex.drag(x1, y1, x2, y2, button=button)
    return f"dragged {button} ({x1},{y1})->({x2},{y2})"


@mcp.tool()
def wait(seconds: float = 1.0) -> str:
    """Pause to let the target UI settle (e.g. after launching an app)."""
    ex, _ = _ensure()
    ex.wait(seconds)
    return f"waited {seconds}s"


if __name__ == "__main__":
    mcp.run()  # stdio transport
