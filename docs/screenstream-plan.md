# Screen stream to the web panel (MJPEG first)

The fastest honest path to "see the controlled screen live on your phone," so we can flip the
softened site copy back. Human-facing live view in the existing web panel and any browser, zero
client-side decode logic.

## Why MJPEG first
Motion-JPEG over HTTP (`multipart/x-mixed-replace`) renders natively in an `<img>` tag: no WebRTC
negotiation, no codec/WASM, no client JS. Smallest viable thing that works in every browser, and it
reuses capture code the agent path already has.

## Reuse what exists
The computer-use agent already captures the desktop in `agent/screen.py` (`mss` grabs, plus an
`ffmpeg` route). That grab loop is the producer; we only add encode-and-serve. No new capture stack.

## Components (smallest viable version)
1. Capture loop: one `mss.mss()` instance grabbing the primary monitor in a loop (proven in the agent path).
2. Encoder per frame: BGRA from mss, downscale (cap long edge ~1280px), JPEG via `cv2.imencode('.jpg', frame, [IMWRITE_JPEG_QUALITY, 60])` (or Pillow). Quality + resolution are the knobs.
3. HTTP MJPEG endpoint on the receiver's existing web server: `GET /stream.mjpg`, header
   `Content-Type: multipart/x-mixed-replace; boundary=frame`, then loop
   `--frame\r\nContent-Type: image/jpeg\r\nContent-Length: N\r\n\r\n<bytes>\r\n`. Frame-rate cap (10 to 15 fps) via a sleep. A single shared "latest frame" buffer written by the capture loop and read per connection, so we do not grab once per viewer.
4. Web panel viewer: add `<img src="/stream.mjpg">`, fit-to-viewport, behind the panel's existing auth/token. No JS decode.
5. Lifecycle/auth: start the capture thread lazily on the first viewer, stop on the last disconnect (ref-count) so idle CPU is zero. Gate the route behind the receiver's existing LAN auth.

## Latency / bandwidth (state honestly in copy)
- Latency: capture + JPEG encode + HTTP flush is ~80 to 200 ms glass-to-glass on LAN. Fine for couch/media-PC control and watching an agent; worse than WebRTC for fast cursor work.
- Bandwidth: full JPEG every frame (no inter-frame compression). 1280px, q60, 12 fps is ~8 to 25 Mbit/s on busy content, far less on static UIs. Fine on LAN, not for WAN/mobile data.
- CPU: JPEG encode every frame costs more than H.264; `cv2.imencode` is cheapest, hardware JPEG cheaper still.
- Levers: resolution cap, JPEG quality, frame-rate cap, and skip-frame-if-unchanged (hash/diff the grab) to drop bandwidth to near zero on static screens.

## Build order
- Phase 1 (ships the claim): shared-frame capture thread + `/stream.mjpg` + `<img>` in the panel, auth-gated, lazy start/stop. This alone makes "live screen on your phone" true.
- Phase 2 (polish): click-to-position by mapping `<img>` tap coords to the existing mouse channel; quality/fps slider; change-detection frame skipping.
- Phase 3 (WAN/low latency, if needed): swap transport to WebRTC/H.264 behind the same panel UI; MJPEG stays as the universal fallback.

## Flip the site copy back once Phase 1 lands
Restore these (softened during the pre-launch honesty pass):
- index.html hero subline: include "live screen" again.
- index.html app-mode blurb/bullet: "sees its screen" / "live screen over LAN".
- index.html couch-control card and use-cases couch scenario: drop the "coming soon" qualifier.
- pricing.html Free tier: the two "live screen" bullets.
- meta description + og:description: "live screen".
