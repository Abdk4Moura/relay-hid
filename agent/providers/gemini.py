"""Gemini 2.5 Computer Use agent loop.

Implements the documented Computer Use loop:
    goal + screenshot -> model returns a UI function_call -> we execute it via the relay ->
    capture a new screenshot -> send it back as the function response -> repeat.

The model emits coordinates on a normalized 0..999 grid; we denormalize against the actual
screenshot size before handing PIXELS to the Executor.

Browser-chrome-only predefined functions (open_web_browser, navigate, go_back, go_forward,
search) are EXCLUDED: we drive a whole machine over HID, not a controlled browser, so we force
the model onto the primitives that map cleanly to keyboard/mouse (click, type, scroll, keys,
drag). Everything the model needs to "navigate" it does by clicking the address bar and typing.

NOTE: the executor/relay path below is fully exercised by tools/demo.py. This file is the glue
to the live google-genai SDK; if your installed SDK version names a field differently, this is
the one place to adjust — the action translation in _dispatch() stays the same.
"""

from __future__ import annotations

from executor import Executor
from screen import ScreenSource

# Predefined functions that only make sense inside a managed browser — exclude so the model
# controls the OS the way a person would instead.
EXCLUDED = ["open_web_browser", "navigate", "go_back", "go_forward", "search"]


def _denorm(v: float, span: int) -> int:
    return int(round(v / 1000.0 * span))


class GeminiComputerUse:
    def __init__(self, cfg, executor: Executor, screen: ScreenSource):
        from google import genai  # lazy import: only needed for this provider

        self.cfg = cfg
        self.executor = executor
        self.screen = screen
        self.genai = genai
        self.client = genai.Client(api_key=cfg.gemini_api_key)

    def _tool_config(self):
        from google.genai import types

        return types.GenerateContentConfig(
            tools=[
                types.Tool(
                    computer_use=types.ComputerUse(
                        environment=types.Environment.ENVIRONMENT_BROWSER,
                        excluded_predefined_functions=EXCLUDED,
                    )
                )
            ]
        )

    def _grab_part(self):
        """Capture the target screen, update executor geometry, return an image Part."""
        from google.genai import types

        png, w, h = self.screen.grab()
        self.executor.set_screen(w, h)
        return types.Part.from_bytes(data=png, mime_type="image/png")

    def _confirm(self, fc) -> bool:
        """Honor the model's safety_decision: prompt the user before risky actions."""
        decision = None
        args = dict(getattr(fc, "args", {}) or {})
        decision = args.get("safety_decision") or getattr(fc, "safety_decision", None)
        needs = bool(decision) and str(decision).lower() not in ("allow", "regular", "safe")
        if not (needs or self.cfg.require_confirm and self._is_destructive(fc.name)):
            return True
        ans = input(f"  ⚠ confirm action '{fc.name}' {args}? [y/N] ").strip().lower()
        return ans == "y"

    @staticmethod
    def _is_destructive(name: str) -> bool:
        return name in ("drag_and_drop",)  # extend as you see fit

    def _dispatch(self, name: str, args: dict) -> None:
        """Translate one Gemini Computer Use function call into Executor calls."""
        ex = self.executor
        x = _denorm(args["x"], ex.screen_w) if "x" in args else 0
        y = _denorm(args["y"], ex.screen_h) if "y" in args else 0

        if name == "click_at":
            ex.click(x, y)
        elif name == "hover_at":
            ex.move(x, y)
        elif name == "type_text_at":
            if args.get("clear_before_typing", True):
                ex.click(x, y)
                ex.key_combo("ctrl+a")
                ex.key_combo("delete")
            else:
                ex.click(x, y)
            ex.type_text(args.get("text", ""))
            if args.get("press_enter"):
                ex.key_combo("enter")
        elif name == "key_combination":
            keys = args.get("keys", "")
            combo = keys if isinstance(keys, str) else "+".join(keys)
            if not ex.key_combo(combo):
                print(f"  (unmapped key combo: {combo!r})")
        elif name == "scroll_document":
            ex.scroll(0, 0, direction=args.get("direction", "down"))
        elif name == "scroll_at":
            mag = int(args.get("magnitude", 3) or 3)
            ex.scroll(x, y, direction=args.get("direction", "down"), clicks=max(1, mag // 100 or 3))
        elif name == "drag_and_drop":
            ex.drag(x, y, _denorm(args["destination_x"], ex.screen_w),
                    _denorm(args["destination_y"], ex.screen_h))
        elif name in ("wait_5_seconds", "wait"):
            ex.wait(5)
        else:
            print(f"  (no relay mapping for predefined function {name!r} — skipped)")

    def run(self, goal: str) -> None:
        from google.genai import types

        cfg = self._tool_config()
        contents = [
            types.Content(role="user", parts=[types.Part(text=goal), self._grab_part()])
        ]

        for step in range(1, self.cfg.max_steps + 1):
            resp = self.client.models.generate_content(
                model=self.cfg.gemini_model, contents=contents, config=cfg
            )
            cand = resp.candidates[0]
            contents.append(cand.content)

            calls = []
            for part in cand.content.parts or []:
                if getattr(part, "text", None):
                    print(f"[{step}] 💭 {part.text.strip()}")
                if getattr(part, "function_call", None):
                    calls.append(part.function_call)

            if not calls:
                print(f"[{step}] ✓ done — model returned no further actions.")
                return

            fc = calls[0]  # Computer Use issues one action per turn
            args = dict(fc.args or {})
            print(f"[{step}] ▶ {fc.name}({args})")
            if not self._confirm(fc):
                print("  ✗ user declined — ending run.")
                return
            self._dispatch(fc.name, args)

            # Return the post-action screenshot as this call's function response.
            fr = types.Part.from_function_response(name=fc.name, response={"status": "ok"})
            contents.append(types.Content(role="user", parts=[fr, self._grab_part()]))

        print(f"[!] reached MAX_STEPS={self.cfg.max_steps} without completion.")
