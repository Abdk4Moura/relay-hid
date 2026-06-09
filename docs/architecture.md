# Relay computer-use agent — architecture

A vision model is the **brain**; Relay is the **hands** (and a capture card is the **eyes** in
out-of-band mode). The loop: see a frame → decide an action → inject real input → see the result → repeat.

## The full loop (driving pop-os over the tailnet)

```
╔══════════════════ OPERATOR BOX · "the brain" (laptop / sandbox / Pi) ══════════════════════════╗
║                                                                                                 ║
║   ① SEE                      ② DECIDE                        ③ ACT                              ║
║   screen.py                  providers/{anthropic,gemini}    executor.py                        ║
║  ┌────────────────┐  frame  ┌────────────────────────┐ act  ┌──────────────────────────┐       ║
║  │ capture a frame│ (PNG)   │ vision computer-use     │ obj  │ pixels → relay JSON       │       ║
║  │ • mss (local)  │────────▶│ model decides:          │─────▶│ {"t":"moveto","x","y"}    │       ║
║  │ • capture card │         │ "click (840,512),       │      │ {"t":"click","b":"left"}  │       ║
║  └────────────────┘         │  type 'uname -a', Enter"│      │ {"t":"text","s":"uname"}  │       ║
║          ▲                  └────────────────────────┘      └─────────────┬────────────┘       ║
║          │  ④ fresh screenshot closes the loop                            │                    ║
║          │                                                     relay_client.py                  ║
║          │                                                     (newline-JSON/TCP, PIN, ❤)       ║
╚══════════╪══════════════════════════════════════════════════════════════╪══════════════════════╝
           │ screen pixels (HDMI / mss)                                     │ {"t":...}\n  TCP :47600
           │                                                                │ over WiFi / Tailscale
 ══════════╪════════════════════════════════════════════════════════════════╪═══════════════════════
                              REMOTE  pop-os  (the target)                  │
╔══════════╪════════════════════════════════════════════════════════════════▼═══════════════════════╗
║          │                                                  relay-desktop  (the receiver)          ║
║          │                                                  parse JSON → /dev/uinput               ║
║   ┌──────┴───────┐                                          virtual keyboard + mouse               ║
║   │ GPU / display│ HDMI out ─▶ (capture card, OOB mode)                 │ kernel input             ║
║   └──────┬───────┘                                                      ▼                          ║
║         ▲│ renders                                          Linux input subsystem                  ║
║   ┌─────┴▼──────────────────────────────────────────────────────────────────────────────────────┐ ║
║   │  COSMIC desktop + apps (terminal, Files, browser …)   ◀── events look 100% human ─────────────┘ ║
║   └─────────────────────────────────────────────────────────────────────────────────────────────┘  ║
╚══════════════════════════════════════════════════════════════════════════════════════════════════╝

     LOOP:  ③ act ─▶ pop-os changes ─▶ ① see new frame ─▶ ② decide ─▶ ③ act ─▶ …
```

## Two ways the brain attaches to the target

```
 IN-BAND (used live today):    brain ──TCP──▶ relay-desktop ──▶ /dev/uinput ──▶ pop-os
     • tiny receiver runs on the target; injects at the kernel. Screen via mss (local).
     • needs the receiver + uinput access on the target.

 OUT-OF-BAND ("KVM brain"):    brain ──TCP──▶ phone ──Bluetooth HID──▶ target (sees a keyboard)
                               target HDMI ──▶ capture card ──▶ brain (sees the screen)
     • target runs NOTHING. Works at BIOS / locked / kiosk / regulated / non-PC.
```

## Out-of-band capture wiring (pop-os example)

```
  pop-os HDMI out ──────▶ HDMI→USB capture card ──USB──▶ OPERATOR box
                          (UVC, shows as /dev/video0)     screen.py CaptureCard (ffmpeg/v4l2)

  OPERATOR box ──TCP──▶ phone (Relay app) ──Bluetooth HID──▶ pop-os USB/BT keyboard+mouse
                   (or a USB HID dongle plugged into pop-os)
```

- The **capture card lives on the operator box**, not on pop-os. pop-os just outputs HDMI as if to a monitor.
- To keep your own monitor, set pop-os to **mirror** its display to the capture-card output.
- If the brain *is* pop-os itself (agent runs on pop-os), skip the card — use `SCREEN_SOURCE=local` (`mss`).
- `.env`: `SCREEN_SOURCE=capture` + the v4l2 device; `MOUSE_MODE=warp` for the Bluetooth path (relative), `abs` for the uinput path.

### The all-in-one box ("AI KVM")
A **PiKVM-style** device (Raspberry Pi + HDMI-in capture + USB-OTG gadget that *emulates* a keyboard/mouse)
does **both eyes and hands out-of-band in one unit**: pop-os HDMI → box HDMI-in; box USB → pop-os USB.
That single box + the agent = a productizable "AI KVM" — the hardware angle worth considering.

See also: `agent/README.md`, `desktop/` (the receiver), and `docs/computer-use-strategy.md`.
```
