"""Live screen stream → your phone's browser (MJPEG over HTTP).

The honest, zero-client-code way to SEE the screen you are driving with Relay, on the phone.
It serves Motion-JPEG (`multipart/x-mixed-replace`), which every browser renders natively in an
`<img>` tag: no WebRTC, no codec, no app build. Open the viewer page on the phone and watch.

Two frame sources, reusing the agent's capture code (`screen.py`):
  * local   = this machine's display (mss). Couch / media-PC: the receiver host streams itself.
  * capture = an HDMI->USB capture card via ffmpeg. The real out-of-band path: the target runs
              nothing, its HDMI goes to the card, and you watch the BIOS / login / dead OS live.
  * test    = a synthetic moving pattern (no display, no card). For verifying the server itself.

One capture loop fills a single shared "latest frame" buffer; every viewer reads that buffer, so
N phones cost one capture, not N. The loop starts on the first viewer and stops on the last
disconnect, so idle CPU is zero. The stream is gated by the relay PIN and bound to the LAN only:
it shows whatever is on the target, so never expose it to the open internet without the hosted relay.

Run:
    python3 stream.py --source test               # verify on any box
    python3 stream.py --source local              # stream this display
    python3 stream.py --source capture --device /dev/video0
Then open  http://<this-host-LAN-ip>:47700/?token=<PIN>  on the phone.
"""

from __future__ import annotations

import argparse
import hmac
import io
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

from config import Config

# --- tunables (env-overridable via Config-style getenv in main) -------------------------------
DEFAULT_PORT = 47700
DEFAULT_FPS = 12          # capture/serve rate cap
DEFAULT_MAX_EDGE = 1280   # downscale so the long edge is at most this many px
DEFAULT_QUALITY = 60      # JPEG quality 1..95
IDLE_FPS = 2              # when the screen is static, fall back to this serve rate
BOUNDARY = "relayframe"


class FrameProducer:
    """Owns the capture thread and the single latest-JPEG buffer. Ref-counts viewers so the
    capture loop runs only while someone is watching."""

    def __init__(self, source, fps=DEFAULT_FPS, max_edge=DEFAULT_MAX_EDGE, quality=DEFAULT_QUALITY):
        self.source = source
        self.fps = max(1, int(fps))
        self.max_edge = int(max_edge)
        self.quality = int(quality)

        self._lock = threading.Lock()
        self._cond = threading.Condition(self._lock)
        self._jpeg = b""          # latest encoded frame
        self._seq = 0             # increments each new frame (viewers wait on this)
        self._viewers = 0
        self._thread = None
        self._stop = threading.Event()
        self._last_hash = None
        self.last_error = None

    # ---- viewer lifecycle -----------------------------------------------------------------
    def add_viewer(self):
        with self._lock:
            self._viewers += 1
            if self._thread is None or not self._thread.is_alive():
                self._stop.clear()
                self._thread = threading.Thread(target=self._run, name="relay-capture", daemon=True)
                self._thread.start()

    def remove_viewer(self):
        with self._lock:
            self._viewers = max(0, self._viewers - 1)
            if self._viewers == 0:
                self._stop.set()

    def wait_for_frame(self, last_seq, timeout=5.0):
        """Block until a frame newer than last_seq exists; return (jpeg_bytes, seq)."""
        with self._cond:
            if self._seq == last_seq:
                self._cond.wait(timeout)
            return self._jpeg, self._seq

    # ---- capture loop ---------------------------------------------------------------------
    def _encode(self, png_bytes):
        from PIL import Image
        img = Image.open(io.BytesIO(png_bytes))
        if img.mode != "RGB":
            img = img.convert("RGB")
        w, h = img.size
        long_edge = max(w, h)
        if self.max_edge and long_edge > self.max_edge:
            scale = self.max_edge / long_edge
            img = img.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.BILINEAR)
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=self.quality)
        return buf.getvalue()

    def _run(self):
        period = 1.0 / self.fps
        idle_period = 1.0 / IDLE_FPS
        while not self._stop.is_set():
            t0 = time.monotonic()
            try:
                png, _w, _h = self.source.grab()
                jpeg = self._encode(png)
                self.last_error = None
            except Exception as e:  # capture can fail (no display, card unplugged); keep serving
                self.last_error = repr(e)
                time.sleep(0.5)
                continue

            # change detection: if the frame is identical to the last, throttle to IDLE_FPS so
            # a static BIOS/login screen costs almost no bandwidth, but still refreshes viewers.
            h = hash(jpeg)
            unchanged = h == self._last_hash
            self._last_hash = h
            with self._cond:
                self._jpeg = jpeg
                self._seq += 1
                self._cond.notify_all()

            target = idle_period if unchanged else period
            dt = time.monotonic() - t0
            if dt < target:
                self._stop.wait(target - dt)


class Handler(BaseHTTPRequestHandler):
    producer: FrameProducer = None
    token: str = ""
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # quieter than the default per-request stderr spam
        pass

    def _authed(self, qs):
        if not self.token:
            return True  # no PIN set: dev mode, LAN only (a warning was printed at startup)
        supplied = (qs.get("token", [""])[0]) or self.headers.get("X-Token", "")
        return hmac.compare_digest(supplied, self.token)

    def do_GET(self):
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)
        route = parsed.path

        if route == "/healthz":
            self._send_text(200, "ok")
            return

        if route == "/" or route == "/index.html":
            self._send_html(VIEWER_HTML)
            return

        if route == "/stream.mjpg":
            if not self._authed(qs):
                self._send_text(403, "forbidden: bad or missing token")
                return
            self._stream()
            return

        self._send_text(404, "not found")

    # ---- responses ------------------------------------------------------------------------
    def _send_text(self, code, body):
        data = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Connection", "close")
        self.end_headers()
        try:
            self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def _send_html(self, body):
        data = body.encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Connection", "close")
        self.end_headers()
        try:
            self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def _stream(self):
        self.send_response(200)
        self.send_header("Age", "0")
        self.send_header("Cache-Control", "no-cache, private")
        self.send_header("Pragma", "no-cache")
        self.send_header("Content-Type", f"multipart/x-mixed-replace; boundary={BOUNDARY}")
        self.end_headers()

        p = self.producer
        p.add_viewer()
        seq = 0
        try:
            while True:
                jpeg, seq = p.wait_for_frame(seq, timeout=5.0)
                if not jpeg:
                    continue
                head = (
                    f"--{BOUNDARY}\r\n"
                    f"Content-Type: image/jpeg\r\n"
                    f"Content-Length: {len(jpeg)}\r\n\r\n"
                ).encode()
                self.wfile.write(head)
                self.wfile.write(jpeg)
                self.wfile.write(b"\r\n")
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass
        finally:
            p.remove_viewer()


# minimal viewer: an <img> fit to the viewport. token comes from the page URL (?token=PIN).
VIEWER_HTML = """<!doctype html><html lang="en"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>Relay - live screen</title>
<style>
  html,body{margin:0;height:100%;background:#07120d;overflow:hidden}
  #wrap{position:fixed;inset:0;display:flex;align-items:center;justify-content:center}
  img{max-width:100%;max-height:100%;object-fit:contain;image-rendering:auto}
  #msg{position:fixed;inset:0;display:flex;align-items:center;justify-content:center;
    color:#5fe3a1;font:15px/1.5 system-ui,sans-serif;text-align:center;padding:24px}
</style></head><body>
<div id="msg">Connecting to the screen...</div>
<div id="wrap"><img id="v" alt=""></div>
<script>
  var token = new URLSearchParams(location.search).get('token') || '';
  var img = document.getElementById('v');
  var msg = document.getElementById('msg');
  img.onload = function(){ msg.style.display='none'; };
  img.onerror = function(){ msg.textContent = 'No stream. Check the PIN and that the screen stream is running.'; };
  img.src = '/stream.mjpg?token=' + encodeURIComponent(token);
</script>
</body></html>"""


class TestPattern:
    """A synthetic moving frame so the server can be verified with no display and no card.
    Returns (png_bytes, w, h) like the real sources."""

    def __init__(self, width=960, height=540):
        self.width = width
        self.height = height
        self.n = 0

    def grab(self):
        from PIL import Image, ImageDraw
        self.n += 1
        img = Image.new("RGB", (self.width, self.height), (8, 20, 14))
        d = ImageDraw.Draw(img)
        # a moving bar + frame counter so successive frames differ (exercises change-detection)
        x = (self.n * 13) % self.width
        d.rectangle([x, 0, x + 60, self.height], fill=(40, 200, 130))
        d.text((20, 20), f"RELAY TEST  frame {self.n}", fill=(220, 255, 235))
        d.text((20, 50), time.strftime("%H:%M:%S"), fill=(160, 230, 200))
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        return buf.getvalue(), self.width, self.height


def build_source(kind, args):
    if kind == "test":
        return TestPattern()
    from screen import make_source
    if kind == "local":
        return make_source("local", monitor=args.monitor)
    if kind == "capture":
        return make_source("capture", device=args.device, width=args.cap_w, height=args.cap_h)
    raise ValueError(f"unknown source {kind!r} (test|local|capture)")


def main():
    cfg = Config()
    ap = argparse.ArgumentParser(description="Relay live screen stream (MJPEG to the phone browser)")
    ap.add_argument("--source", default=os.getenv("STREAM_SOURCE", cfg.screen_source),
                    choices=["test", "local", "capture"])
    ap.add_argument("--port", type=int, default=int(os.getenv("STREAM_PORT", DEFAULT_PORT)))
    ap.add_argument("--fps", type=int, default=int(os.getenv("STREAM_FPS", DEFAULT_FPS)))
    ap.add_argument("--max-edge", type=int, default=int(os.getenv("STREAM_MAX_EDGE", DEFAULT_MAX_EDGE)))
    ap.add_argument("--quality", type=int, default=int(os.getenv("STREAM_QUALITY", DEFAULT_QUALITY)))
    ap.add_argument("--token", default=os.getenv("STREAM_PIN", cfg.relay_pin),
                    help="gate the stream with this token (defaults to the relay PIN)")
    ap.add_argument("--monitor", type=int, default=cfg.monitor)
    ap.add_argument("--device", default=cfg.capture_device)
    ap.add_argument("--cap-w", type=int, default=cfg.capture_width)
    ap.add_argument("--cap-h", type=int, default=cfg.capture_height)
    ap.add_argument("--bind", default=os.getenv("STREAM_BIND", "0.0.0.0"))
    args = ap.parse_args()

    source = build_source(args.source, args)
    Handler.producer = FrameProducer(source, fps=args.fps, max_edge=args.max_edge, quality=args.quality)
    Handler.token = args.token or ""

    httpd = ThreadingHTTPServer((args.bind, args.port), Handler)
    httpd.daemon_threads = True

    ip = lan_ip()
    tok = f"?token={args.token}" if args.token else ""
    print(f"┌ Relay screen stream  ({args.source} source, {args.fps} fps, q{args.quality}, <= {args.max_edge}px)")
    print(f"│ Viewer (open on your phone):  http://{ip}:{args.port}/{tok}")
    print(f"│ Bound to {args.bind}:{args.port}  (LAN only - do not expose to the internet)")
    if not args.token:
        print("│ WARNING: no token/PIN set. Anyone on this LAN can watch the screen.")
    print("└ Ctrl-C to stop.")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nstopping.")
        httpd.shutdown()


def lan_ip():
    import socket
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


if __name__ == "__main__":
    main()
