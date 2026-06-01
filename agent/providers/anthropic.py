"""Anthropic Computer Use agent loop (alternative provider).

Claude's `computer` tool works in ACTUAL pixel coordinates (no 0..999 normalization), so the
executor receives pixels straight from the tool call. We feed Claude the display dimensions and
a screenshot, run the tool-use loop, and return a fresh screenshot as each tool_result.

Anthropic recommends a display no larger than ~1280x800 (XGA/WXGA) for best targeting accuracy;
if you drive a 1080p+ target, consider downscaling in the screen source.
"""

from __future__ import annotations

import base64

from executor import Executor
from screen import ScreenSource

BETA = "computer-use-2025-01-24"
TOOL_TYPE = "computer_20250124"


class AnthropicComputerUse:
    def __init__(self, cfg, executor: Executor, screen: ScreenSource):
        import anthropic  # lazy import

        self.cfg = cfg
        self.executor = executor
        self.screen = screen
        self.client = anthropic.Anthropic(api_key=cfg.anthropic_api_key)

    def _shot_block(self) -> dict:
        png, w, h = self.screen.grab()
        self.executor.set_screen(w, h)
        b64 = base64.b64encode(png).decode("ascii")
        return {"type": "image",
                "source": {"type": "base64", "media_type": "image/png", "data": b64}}

    def _coord(self, args: dict) -> tuple[int, int]:
        c = args.get("coordinate") or [0, 0]
        return int(c[0]), int(c[1])

    def _dispatch(self, args: dict) -> None:
        ex = self.executor
        action = args.get("action")
        if action == "mouse_move":
            ex.move(*self._coord(args))
        elif action in ("left_click", "click"):
            ex.click(*self._coord(args), "left")
        elif action == "right_click":
            ex.click(*self._coord(args), "right")
        elif action == "middle_click":
            ex.click(*self._coord(args), "middle")
        elif action == "double_click":
            ex.click(*self._coord(args), "left", count=2)
        elif action == "left_click_drag":
            x, y = self._coord(args)
            start = args.get("start_coordinate") or [ex._cx, ex._cy]
            ex.drag(int(start[0]), int(start[1]), x, y)
        elif action == "type":
            ex.type_text(args.get("text", ""))
        elif action == "key":
            if not ex.key_combo(args.get("text", "")):
                print(f"  (unmapped key: {args.get('text')!r})")
        elif action == "scroll":
            x, y = self._coord(args)
            amt = int(args.get("scroll_amount", 3) or 3)
            ex.scroll(x, y, direction=args.get("scroll_direction", "down"), clicks=amt)
        elif action == "wait":
            ex.wait(float(args.get("duration", 1)))
        # 'screenshot' / 'cursor_position' need no input action; just return a fresh frame.

    def run(self, goal: str) -> None:
        _, w, h = self.screen.grab()
        self.executor.set_screen(w, h)
        tools = [{"type": TOOL_TYPE, "name": "computer",
                  "display_width_px": w, "display_height_px": h, "display_number": 1}]
        messages = [{"role": "user",
                     "content": [{"type": "text", "text": goal}, self._shot_block()]}]

        for step in range(1, self.cfg.max_steps + 1):
            resp = self.client.beta.messages.create(
                model=self.cfg.anthropic_model, max_tokens=2048,
                tools=tools, messages=messages, betas=[BETA],
            )
            messages.append({"role": "assistant", "content": resp.content})

            tool_results = []
            saw_tool = False
            for block in resp.content:
                if block.type == "text":
                    print(f"[{step}] 💭 {block.text.strip()}")
                elif block.type == "tool_use":
                    saw_tool = True
                    args = dict(block.input)
                    print(f"[{step}] ▶ {args.get('action')}({args})")
                    if self.cfg.require_confirm and args.get("action") == "left_click_drag":
                        if input("  ⚠ confirm drag? [y/N] ").strip().lower() != "y":
                            print("  ✗ declined — ending run.")
                            return
                    self._dispatch(args)
                    tool_results.append({
                        "type": "tool_result", "tool_use_id": block.id,
                        "content": [self._shot_block()],
                    })

            if not saw_tool:
                print(f"[{step}] ✓ done — model issued no tool calls.")
                return
            messages.append({"role": "user", "content": tool_results})

        print(f"[!] reached MAX_STEPS={self.cfg.max_steps} without completion.")
