# Computer-use agent (HID relay as the hands)

A vision **computer-use model decides what to do**; its actions are translated into the relay's
existing newline-JSON protocol and injected as real keyboard/mouse input — over Linux `uinput`
(WiFi/desktop path) or Bluetooth HID into a completely **unmodified target machine**.

```
 screenshot of target ──▶  Gemini 2.5 Computer Use  ──▶  UI action (click x,y / type / key …)
 (mss or capture card)     (or Claude Computer Use)            │
                                                    executor.py  (pixels → relay JSON)
                                                               │
                              relay_client.py ──TCP──▶ relay-desktop (uinput) ──▶ target
                                                               └▶ or phone (Bluetooth HID) ──▶ target
```

This is a genuine computer-use loop, **not** "screenshot → paste into chat → read it back":
a purpose-built model emits *structured* actions, an executor performs them, a fresh screenshot
closes the loop, repeat — with safety confirmation on risky steps.

## Why two coordinate modes
Vision models emit **absolute** click targets. A USB/BT mouse is **relative**. We solve it two ways:

- **`abs`** (default, preferred) — the relay grew an absolute `moveto` (relay **≥ 0.8.0**); the
  model's coordinate maps 1:1 to the screen. Deterministic.
- **`warp`** — relative-only fallback for the Bluetooth-HID path or older relays: slam the
  pointer to a corner, then move in by the exact pixel offset. Works everywhere but pointer
  acceleration on the target can skew it — disable target mouse acceleration for best results.

## Capturing the target's screen
Set `SCREEN_SOURCE`:
- **`local`** — grab this machine's display (`mss`). For testing the whole pipeline on one box,
  or when the agent runs on the same Linux host as the relay.
- **`capture`** — grab an **HDMI→USB capture card** (`ffmpeg`, `/dev/video0`). The real
  out-of-band setup: the target runs *nothing* — its HDMI feeds the card, your HID relay drives
  its input. This is the "KVM brain" configuration.

## Setup
```bash
cd agent
python -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env        # then fill in RELAY_PIN and an API key
```
Make sure the relay is running (`desktop/`) and shows your PIN in its control panel.

## 1. Verify the pipe (no API spend)
```bash
python tools/demo.py            # cursor should jump to center & type on the target
python tools/demo.py --warp     # if abs didn't move the cursor on your compositor
```

## 2. Run with a model
```bash
python run.py --provider gemini    --goal "Open the file manager and create a folder called demo"
python run.py --provider anthropic --goal "Open a terminal and run: uname -a"
```

## Files
| file | role |
|------|------|
| `relay_client.py` | speaks the relay's newline-JSON protocol; auto-heartbeats (relay drops idle sockets after 12s) |
| `executor.py` | provider-agnostic actions in **pixels** → relay messages; abs/warp mouse mapping |
| `keymap.py` | symbolic keys ("ctrl+c") → relay HID usage code + modifier bitmask |
| `screen.py` | pluggable capture: `LocalScreen` (mss) / `CaptureCard` (ffmpeg/v4l2) |
| `providers/gemini.py` | Gemini 2.5 Computer Use loop (browser-only fns excluded) |
| `providers/anthropic.py` | Claude Computer Use loop (alternative) |
| `run.py` | CLI entry point |
| `tools/demo.py` | model-free pipeline smoke test |

## Notes / limits
- The provider files are the glue to the live SDKs; if an installed SDK names a field
  differently, adjust there — the executor/relay path is SDK-independent and exercised by
  `tools/demo.py`.
- Gemini Computer Use is **browser-tuned**; the browser-chrome-only predefined functions
  (`navigate`, `go_back`, …) are excluded so it drives the OS via click/type/scroll/keys.
- This drives **real input on a real machine**. Test against a throwaway target first and keep
  `REQUIRE_CONFIRM=1` until you trust a task.
