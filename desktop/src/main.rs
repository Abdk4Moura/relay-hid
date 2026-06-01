//! Relay desktop receiver (Linux).
//!
//! Listens on a TCP port for newline-delimited JSON events from the Relay phone
//! app and replays them as real system input via the kernel's uinput interface
//! (works under both X11 and Wayland — unlike X11-only injectors).
//!
//! Protocol (one JSON object per line, UTF-8):
//!   {"t":"hello","token":"<shared-pin>"}        // first message; must match
//!   {"t":"text","s":"hello world"}              // type a UTF-8 string
//!   {"t":"key","code":40,"mods":8}              // HID usage code + HID modifier bitmask
//!   {"t":"move","dx":12,"dy":-4}                // relative pointer move
//!   {"t":"scroll","dy":2}                       // wheel
//!   {"t":"button","b":"left","down":true}       // hold/release a mouse button
//!   {"t":"click","b":"right"}                   // press+release
//!   {"t":"consumer","usage":205}               // media/brightness (Consumer page)
//!
//! Build:  cargo build --release
//! Run:    ./target/release/relay-desktop --token 1234 --port 47600
//! uinput needs access to /dev/uinput — see README (udev rule, or run with sudo).

use std::io::{BufRead, BufReader};
use std::net::{TcpListener, TcpStream};
use std::process::Command;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{SystemTime, UNIX_EPOCH};

use evdev::{
    uinput::VirtualDeviceBuilder, AttributeSet, EventType, InputEvent, Key, RelativeAxisType,
};
use mdns_sd::{ServiceDaemon, ServiceInfo};
use qrcode::{render::svg, QrCode};
use serde::Deserialize;

#[derive(Deserialize)]
#[serde(tag = "t", rename_all = "lowercase")]
enum Msg {
    Hello { token: String },
    Text { s: String },
    Key { code: u16, #[serde(default)] mods: u8 },
    Move { dx: i32, dy: i32 },
    Scroll { dy: i32 },
    Button { b: String, down: bool },
    Click { b: String },
    Consumer { usage: u16 },
}

const SYN: i32 = 0; // SYN_REPORT

/// Shared state surfaced to the web control panel.
struct Status {
    port: u16,
    pin: String,
    ip_hint: String,
    connected: Option<String>,
    events: u64,
    last: String,
}
type Shared = Arc<Mutex<Status>>;

const VERSION: &str = env!("CARGO_PKG_VERSION");

struct Injector {
    dev: evdev::uinput::VirtualDevice,
}

impl Injector {
    fn new() -> std::io::Result<Self> {
        let mut keys = AttributeSet::<Key>::new();
        for k in all_keys() {
            keys.insert(k);
        }
        let mut axes = AttributeSet::<RelativeAxisType>::new();
        axes.insert(RelativeAxisType::REL_X);
        axes.insert(RelativeAxisType::REL_Y);
        axes.insert(RelativeAxisType::REL_WHEEL);
        let dev = VirtualDeviceBuilder::new()?
            .name("Relay Virtual Input")
            .with_keys(&keys)?
            .with_relative_axes(&axes)?
            .build()?;
        Ok(Self { dev })
    }

    fn emit(&mut self, events: &[InputEvent]) {
        let _ = self.dev.emit(events);
    }

    fn flush(&mut self) {
        self.emit(&[InputEvent::new(EventType::SYNCHRONIZATION, SYN as u16, 0)]);
    }

    fn key_ev(&mut self, key: Key, val: i32) {
        self.emit(&[InputEvent::new(EventType::KEY, key.code(), val)]);
    }

    /// Tap a HID-usage keycode while holding the HID modifier bitmask.
    fn tap_hid(&mut self, code: u16, mods: u8) {
        let key = match hid_to_key(code) {
            Some(k) => k,
            None => return,
        };
        let held = mods_to_keys(mods);
        for m in &held {
            self.key_ev(*m, 1);
        }
        self.key_ev(key, 1);
        self.key_ev(key, 0);
        for m in held.iter().rev() {
            self.key_ev(*m, 0);
        }
        self.flush();
    }

    fn text(&mut self, s: &str) {
        for ch in s.chars() {
            if let Some((key, shift)) = char_to_key(ch) {
                if shift {
                    self.key_ev(Key::KEY_LEFTSHIFT, 1);
                }
                self.key_ev(key, 1);
                self.key_ev(key, 0);
                if shift {
                    self.key_ev(Key::KEY_LEFTSHIFT, 0);
                }
                self.flush();
            }
        }
    }

    fn move_rel(&mut self, dx: i32, dy: i32) {
        self.emit(&[
            InputEvent::new(EventType::RELATIVE, RelativeAxisType::REL_X.0, dx),
            InputEvent::new(EventType::RELATIVE, RelativeAxisType::REL_Y.0, dy),
        ]);
        self.flush();
    }

    fn scroll(&mut self, dy: i32) {
        self.emit(&[InputEvent::new(
            EventType::RELATIVE,
            RelativeAxisType::REL_WHEEL.0,
            dy,
        )]);
        self.flush();
    }

    fn button(&mut self, b: &str, down: bool) {
        let key = match b {
            "right" => Key::BTN_RIGHT,
            "middle" => Key::BTN_MIDDLE,
            _ => Key::BTN_LEFT,
        };
        self.key_ev(key, if down { 1 } else { 0 });
        self.flush();
    }

    fn click(&mut self, b: &str) {
        self.button(b, true);
        self.button(b, false);
    }

    fn consumer(&mut self, usage: u16) {
        if let Some(key) = consumer_to_key(usage) {
            self.key_ev(key, 1);
            self.key_ev(key, 0);
            self.flush();
        }
    }
}

fn note(st: &Shared, f: impl FnOnce(&mut Status)) {
    if let Ok(mut s) = st.lock() {
        f(&mut s);
    }
}

fn handle(stream: TcpStream, token: &str, inj: &mut Injector, st: &Shared) {
    let peer = stream.peer_addr().map(|a| a.to_string()).unwrap_or_default();
    let mut authed = false;
    let reader = BufReader::new(stream);
    for line in reader.lines() {
        let line = match line {
            Ok(l) => l,
            Err(_) => break,
        };
        if line.trim().is_empty() {
            continue;
        }
        let msg: Msg = match serde_json::from_str(&line) {
            Ok(m) => m,
            Err(_) => continue,
        };
        if !authed {
            match msg {
                Msg::Hello { token: t } if t == token => {
                    authed = true;
                    println!("✓ {peer} authenticated");
                    note(st, |s| { s.connected = Some(peer.clone()); s.last = "connected".into(); });
                    continue;
                }
                _ => {
                    eprintln!("✗ {peer} bad/missing token — dropping");
                    break;
                }
            }
        }
        let desc = match &msg {
            Msg::Text { s } => format!("type \"{}\"", s.chars().take(24).collect::<String>()),
            Msg::Key { code, mods } => format!("key 0x{code:02x} mods 0x{mods:02x}"),
            Msg::Move { .. } => "move".into(),
            Msg::Scroll { .. } => "scroll".into(),
            Msg::Button { b, down } => format!("{b} {}", if *down { "down" } else { "up" }),
            Msg::Click { b } => format!("{b} click"),
            Msg::Consumer { usage } => format!("media 0x{usage:02x}"),
            Msg::Hello { .. } => String::new(),
        };
        match msg {
            Msg::Text { s } => inj.text(&s),
            Msg::Key { code, mods } => inj.tap_hid(code, mods),
            Msg::Move { dx, dy } => inj.move_rel(dx, dy),
            Msg::Scroll { dy } => inj.scroll(dy),
            Msg::Button { b, down } => inj.button(&b, down),
            Msg::Click { b } => inj.click(&b),
            Msg::Consumer { usage } => inj.consumer(usage),
            Msg::Hello { .. } => {}
        }
        if !desc.is_empty() {
            note(st, |s| { s.events += 1; s.last = desc; });
        }
    }
    println!("— {peer} disconnected");
    note(st, |s| { s.connected = None; s.last = "idle".into(); });
}

fn main() {
    let mut port: u16 = 47600;
    let mut token: Option<String> = None;
    let mut args = std::env::args().skip(1);
    while let Some(a) = args.next() {
        match a.as_str() {
            "--port" => port = args.next().and_then(|v| v.parse().ok()).unwrap_or(port),
            "--token" => token = args.next(),
            _ => {}
        }
    }
    let token = token
        .or_else(|| std::env::var("RELAY_TOKEN").ok())
        .unwrap_or_else(load_or_create_token);

    let mut inj = match Injector::new() {
        Ok(i) => i,
        Err(e) => {
            eprintln!("Failed to open /dev/uinput: {e}\nGrant access (see README) or run with sudo.");
            std::process::exit(1);
        }
    };

    let listener = match TcpListener::bind(("0.0.0.0", port)) {
        Ok(l) => l,
        Err(e) => {
            eprintln!("Failed to bind port {port}: {e}");
            std::process::exit(1);
        }
    };

    let status: Shared = Arc::new(Mutex::new(Status {
        port,
        pin: token.clone(),
        ip_hint: ip_hint(),
        connected: None,
        events: 0,
        last: "idle".into(),
    }));

    // local web control panel
    let ui_port = port.wrapping_add(1);
    {
        let st = status.clone();
        thread::spawn(move || serve_ui(ui_port, st));
    }
    let ui_url = format!("http://127.0.0.1:{ui_port}");
    let _ = Command::new("xdg-open").arg(&ui_url).spawn();

    // advertise on the LAN so the phone can auto-discover us (no IP typing). Keep alive.
    let _mdns = start_mdns(port);

    println!("┌─────────────────────────────────────────");
    println!("│ Relay desktop receiver");
    println!("│ Listening on 0.0.0.0:{port}");
    println!("│ PIN: {token}");
    println!("│ Control panel: {ui_url}");
    println!("│ In the Relay app enter this host's IP + the PIN.");
    println!("└─────────────────────────────────────────");

    for stream in listener.incoming() {
        match stream {
            Ok(s) => handle(s, &token, &mut inj, &status),
            Err(e) => eprintln!("accept error: {e}"),
        }
    }
}

fn hostname() -> String {
    Command::new("hostname")
        .output()
        .ok()
        .and_then(|o| String::from_utf8(o.stdout).ok())
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| "relay-host".to_string())
}

/// Advertise `_relay._tcp` on the LAN via mDNS so the phone can find us automatically.
fn start_mdns(port: u16) -> Option<ServiceDaemon> {
    let daemon = ServiceDaemon::new().ok()?;
    let host = format!("{}.local.", hostname());
    let props: [(&str, &str); 2] = [("v", VERSION), ("name", "Relay Desktop")];
    let info = ServiceInfo::new("_relay._tcp.local.", "Relay Desktop", &host, "", port, &props[..])
        .ok()?
        .enable_addr_auto();
    daemon.register(info).ok()?;
    println!("│ mDNS: advertising _relay._tcp on the LAN");
    Some(daemon)
}

fn config_dir() -> std::path::PathBuf {
    let base = std::env::var("XDG_CONFIG_HOME")
        .ok()
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| format!("{}/.config", std::env::var("HOME").unwrap_or_default()));
    std::path::PathBuf::from(base).join("relay-desktop")
}

/// Stable PIN persisted in the config dir so it survives restarts (generated once).
fn load_or_create_token() -> String {
    let path = config_dir().join("token");
    if let Ok(s) = std::fs::read_to_string(&path) {
        let t = s.trim().to_string();
        if !t.is_empty() {
            return t;
        }
    }
    let n = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().subsec_nanos();
    let token = format!("{:04}", n % 10000);
    let _ = std::fs::create_dir_all(config_dir());
    let _ = std::fs::write(&path, &token);
    token
}

/// Best-effort LAN/Tailscale IP to show in the panel (Tailscale first, then hostname -I).
fn ip_hint() -> String {
    if let Ok(o) = Command::new("tailscale").args(["ip", "-4"]).output() {
        if let Some(ip) = String::from_utf8_lossy(&o.stdout).split_whitespace().next() {
            if !ip.is_empty() {
                return ip.to_string();
            }
        }
    }
    if let Ok(o) = Command::new("hostname").arg("-I").output() {
        if let Some(ip) = String::from_utf8_lossy(&o.stdout).split_whitespace().next() {
            return ip.to_string();
        }
    }
    "your-ip".to_string()
}

fn json_escape(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            c if (c as u32) < 0x20 => out.push(' '),
            c => out.push(c),
        }
    }
    out
}

fn serve_ui(port: u16, status: Shared) {
    let server = match tiny_http::Server::http(("127.0.0.1", port)) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("control panel failed to start on :{port}: {e}");
            return;
        }
    };
    for req in server.incoming_requests() {
        if req.url().starts_with("/status") {
            let body = {
                let s = status.lock().unwrap();
                let conn = match &s.connected {
                    Some(c) => format!("\"{}\"", json_escape(c)),
                    None => "null".to_string(),
                };
                format!(
                    "{{\"port\":{},\"pin\":\"{}\",\"ip\":\"{}\",\"connected\":{},\"events\":{},\"last\":\"{}\",\"ver\":\"{}\"}}",
                    s.port, json_escape(&s.pin), json_escape(&s.ip_hint), conn, s.events, json_escape(&s.last), VERSION
                )
            };
            let header = tiny_http::Header::from_bytes(&b"Content-Type"[..], &b"application/json"[..]).unwrap();
            let _ = req.respond(tiny_http::Response::from_string(body).with_header(header));
        } else if req.url().starts_with("/qr") {
            let (ip, port, pin) = {
                let s = status.lock().unwrap();
                (s.ip_hint.clone(), s.port, s.pin.clone())
            };
            let data = format!("relay://{ip}:{port}?pin={pin}");
            let svg = QrCode::new(data.as_bytes())
                .map(|c| c.render::<svg::Color>().min_dimensions(220, 220).quiet_zone(true).build())
                .unwrap_or_default();
            let header = tiny_http::Header::from_bytes(&b"Content-Type"[..], &b"image/svg+xml"[..]).unwrap();
            let _ = req.respond(tiny_http::Response::from_string(svg).with_header(header));
        } else {
            let header = tiny_http::Header::from_bytes(&b"Content-Type"[..], &b"text/html; charset=utf-8"[..]).unwrap();
            let _ = req.respond(tiny_http::Response::from_string(INDEX_HTML).with_header(header));
        }
    }
}

// ----------------------------- mappings -----------------------------

fn mods_to_keys(mods: u8) -> Vec<Key> {
    let mut v = Vec::new();
    if mods & 0x01 != 0 {
        v.push(Key::KEY_LEFTCTRL);
    }
    if mods & 0x02 != 0 {
        v.push(Key::KEY_LEFTSHIFT);
    }
    if mods & 0x04 != 0 {
        v.push(Key::KEY_LEFTALT);
    }
    if mods & 0x08 != 0 {
        v.push(Key::KEY_LEFTMETA);
    }
    v
}

const LETTERS: [Key; 26] = [
    Key::KEY_A, Key::KEY_B, Key::KEY_C, Key::KEY_D, Key::KEY_E, Key::KEY_F, Key::KEY_G,
    Key::KEY_H, Key::KEY_I, Key::KEY_J, Key::KEY_K, Key::KEY_L, Key::KEY_M, Key::KEY_N,
    Key::KEY_O, Key::KEY_P, Key::KEY_Q, Key::KEY_R, Key::KEY_S, Key::KEY_T, Key::KEY_U,
    Key::KEY_V, Key::KEY_W, Key::KEY_X, Key::KEY_Y, Key::KEY_Z,
];
const DIGITS: [Key; 10] = [
    Key::KEY_1, Key::KEY_2, Key::KEY_3, Key::KEY_4, Key::KEY_5, Key::KEY_6, Key::KEY_7,
    Key::KEY_8, Key::KEY_9, Key::KEY_0,
];
const FKEYS: [Key; 12] = [
    Key::KEY_F1, Key::KEY_F2, Key::KEY_F3, Key::KEY_F4, Key::KEY_F5, Key::KEY_F6, Key::KEY_F7,
    Key::KEY_F8, Key::KEY_F9, Key::KEY_F10, Key::KEY_F11, Key::KEY_F12,
];

/// USB HID Usage (Keyboard/Keypad page 0x07) → Linux evdev key.
fn hid_to_key(code: u16) -> Option<Key> {
    Some(match code {
        0x04..=0x1d => LETTERS[(code - 0x04) as usize],
        0x1e..=0x27 => DIGITS[(code - 0x1e) as usize],
        0x28 => Key::KEY_ENTER,
        0x29 => Key::KEY_ESC,
        0x2a => Key::KEY_BACKSPACE,
        0x2b => Key::KEY_TAB,
        0x2c => Key::KEY_SPACE,
        0x2d => Key::KEY_MINUS,
        0x2e => Key::KEY_EQUAL,
        0x2f => Key::KEY_LEFTBRACE,
        0x30 => Key::KEY_RIGHTBRACE,
        0x31 => Key::KEY_BACKSLASH,
        0x33 => Key::KEY_SEMICOLON,
        0x34 => Key::KEY_APOSTROPHE,
        0x35 => Key::KEY_GRAVE,
        0x36 => Key::KEY_COMMA,
        0x37 => Key::KEY_DOT,
        0x38 => Key::KEY_SLASH,
        0x3a..=0x45 => FKEYS[(code - 0x3a) as usize],
        0x4a => Key::KEY_HOME,
        0x4b => Key::KEY_PAGEUP,
        0x4c => Key::KEY_DELETE,
        0x4d => Key::KEY_END,
        0x4e => Key::KEY_PAGEDOWN,
        0x4f => Key::KEY_RIGHT,
        0x50 => Key::KEY_LEFT,
        0x51 => Key::KEY_DOWN,
        0x52 => Key::KEY_UP,
        _ => return None,
    })
}

/// Consumer page (0x0C) usage → evdev media/brightness key.
fn consumer_to_key(usage: u16) -> Option<Key> {
    Some(match usage {
        0x00cd => Key::KEY_PLAYPAUSE,
        0x00b5 => Key::KEY_NEXTSONG,
        0x00b6 => Key::KEY_PREVIOUSSONG,
        0x00e2 => Key::KEY_MUTE,
        0x00e9 => Key::KEY_VOLUMEUP,
        0x00ea => Key::KEY_VOLUMEDOWN,
        0x006f => Key::KEY_BRIGHTNESSUP,
        0x0070 => Key::KEY_BRIGHTNESSDOWN,
        0x0223 => Key::KEY_HOMEPAGE,
        0x0221 => Key::KEY_SEARCH,
        _ => return None,
    })
}

/// US-layout char → (key, needs-shift) for typing arbitrary text.
fn char_to_key(c: char) -> Option<(Key, bool)> {
    if c.is_ascii_lowercase() {
        return Some((LETTERS[(c as u8 - b'a') as usize], false));
    }
    if c.is_ascii_uppercase() {
        return Some((LETTERS[(c as u8 - b'A') as usize], true));
    }
    if ('1'..='9').contains(&c) {
        return Some((DIGITS[(c as u8 - b'1') as usize], false));
    }
    Some(match c {
        '0' => (Key::KEY_0, false),
        ' ' => (Key::KEY_SPACE, false),
        '\n' => (Key::KEY_ENTER, false),
        '\t' => (Key::KEY_TAB, false),
        '-' => (Key::KEY_MINUS, false),
        '_' => (Key::KEY_MINUS, true),
        '=' => (Key::KEY_EQUAL, false),
        '+' => (Key::KEY_EQUAL, true),
        '[' => (Key::KEY_LEFTBRACE, false),
        '{' => (Key::KEY_LEFTBRACE, true),
        ']' => (Key::KEY_RIGHTBRACE, false),
        '}' => (Key::KEY_RIGHTBRACE, true),
        '\\' => (Key::KEY_BACKSLASH, false),
        '|' => (Key::KEY_BACKSLASH, true),
        ';' => (Key::KEY_SEMICOLON, false),
        ':' => (Key::KEY_SEMICOLON, true),
        '\'' => (Key::KEY_APOSTROPHE, false),
        '"' => (Key::KEY_APOSTROPHE, true),
        '`' => (Key::KEY_GRAVE, false),
        '~' => (Key::KEY_GRAVE, true),
        ',' => (Key::KEY_COMMA, false),
        '<' => (Key::KEY_COMMA, true),
        '.' => (Key::KEY_DOT, false),
        '>' => (Key::KEY_DOT, true),
        '/' => (Key::KEY_SLASH, false),
        '?' => (Key::KEY_SLASH, true),
        '!' => (Key::KEY_1, true),
        '@' => (Key::KEY_2, true),
        '#' => (Key::KEY_3, true),
        '$' => (Key::KEY_4, true),
        '%' => (Key::KEY_5, true),
        '^' => (Key::KEY_6, true),
        '&' => (Key::KEY_7, true),
        '*' => (Key::KEY_8, true),
        '(' => (Key::KEY_9, true),
        ')' => (Key::KEY_0, true),
        _ => return None,
    })
}

const INDEX_HTML: &str = r##"<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Relay · Desktop</title>
<style>
  :root{--bg:#0a0d0c;--surface:#121614;--border:#1f2724;--text:#e7ecea;--dim:#8a9691;--faint:#5a655f;--accent:#65ea92;--accent2:#4fb772}
  *{box-sizing:border-box;margin:0}
  body{background:var(--bg);color:var(--text);font-family:ui-sans-serif,system-ui,'Segoe UI',Roboto,sans-serif;display:flex;min-height:100vh;align-items:center;justify-content:center;padding:24px}
  .card{width:100%;max-width:440px;background:linear-gradient(180deg,var(--surface),var(--bg));border:1px solid var(--border);border-radius:18px;padding:26px}
  .top{display:flex;align-items:center;gap:11px;margin-bottom:22px}
  .logo{width:26px;height:26px;border-radius:7px;background:linear-gradient(160deg,var(--accent),var(--accent2))}
  .brand{font-weight:700;letter-spacing:.16em;font-size:14px}
  .ver{font-family:ui-monospace,monospace;font-size:10px;color:var(--faint);background:#0c100e;border:1px solid var(--border);border-radius:5px;padding:2px 6px}
  .pill{margin-left:auto;display:flex;align-items:center;gap:8px;background:#0c100e;border:1px solid var(--border);border-radius:9px;padding:6px 11px;font-size:12.5px}
  .dot{width:9px;height:9px;border-radius:50%;background:var(--faint);transition:.3s}
  .dot.on{background:var(--accent);box-shadow:0 0 10px var(--accent)}
  .lbl{font-family:ui-monospace,monospace;font-size:10px;letter-spacing:.14em;color:var(--faint);text-transform:uppercase;margin-bottom:8px}
  .pinbox{background:#0c100e;border:1px solid var(--border);border-radius:13px;padding:18px;display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}
  .pin{font-family:ui-monospace,monospace;font-size:38px;font-weight:700;letter-spacing:.14em;color:var(--accent)}
  .copy{background:var(--surface);border:1px solid var(--border);color:var(--dim);border-radius:9px;padding:8px 12px;font-size:12px;cursor:pointer}
  .copy:active{background:var(--border)}
  .row{display:flex;gap:12px;margin-bottom:16px}
  .stat{flex:1;background:#0c100e;border:1px solid var(--border);border-radius:12px;padding:13px}
  .stat .v{font-family:ui-monospace,monospace;font-size:17px;font-weight:600}
  .stat .k{font-size:11px;color:var(--faint);margin-top:4px}
  .hint{font-size:12.5px;color:var(--dim);line-height:1.55;background:#0c100e;border:1px solid var(--border);border-radius:12px;padding:14px}
  .hint b{color:var(--text);font-family:ui-monospace,monospace}
  .last{font-family:ui-monospace,monospace;font-size:11px;color:var(--faint);margin-top:14px;text-align:center;min-height:14px}
  .qrwrap{display:flex;flex-direction:column;align-items:center;gap:7px;margin-top:16px}
  .qr{width:138px;height:138px;background:#fff;border-radius:12px;padding:9px}
  .qrcap{font-family:ui-monospace,monospace;font-size:10px;letter-spacing:.1em;color:var(--faint);text-transform:uppercase}
</style></head>
<body><div class="card">
  <div class="top"><div class="logo"></div><div class="brand">RELAY</div><div class="ver" id="ver">desktop</div>
    <div class="pill"><span class="dot" id="dot"></span><span id="conn">waiting…</span></div></div>
  <div class="lbl">Pairing PIN</div>
  <div class="pinbox"><span class="pin" id="pin">····</span><button class="copy" id="copy">copy</button></div>
  <div class="row">
    <div class="stat"><div class="v" id="events">0</div><div class="k">events injected</div></div>
    <div class="stat"><div class="v" id="port">—</div><div class="k">port</div></div>
  </div>
  <div class="hint">Same WiFi? Relay finds this machine automatically (<b>Pairing → WiFi</b>). Or scan:</div>
  <div class="qrwrap"><img class="qr" src="/qr" alt="pairing QR"><div class="qrcap">scan to connect</div></div>
  <div class="hint" style="margin-top:14px">Manual: IP <b id="ip">…</b> · PIN above.</div>
  <div class="last" id="last"></div>
</div>
<script>
  async function tick(){
    try{
      const s = await (await fetch('/status')).json();
      pin.textContent = s.pin; port.textContent = s.port; ip.textContent = s.ip;
      events.textContent = s.events;
      const c = s.connected;
      dot.classList.toggle('on', !!c);
      conn.textContent = c ? ('connected · ' + c.split(':')[0]) : 'waiting for phone';
      last.textContent = s.last ? ('last: ' + s.last) : '';
      if(s.ver) ver.textContent = 'v'+s.ver;
    }catch(e){ conn.textContent='receiver offline'; dot.classList.remove('on'); }
  }
  copy.onclick=()=>{navigator.clipboard.writeText(pin.textContent);copy.textContent='copied';setTimeout(()=>copy.textContent='copy',1200)};
  tick(); setInterval(tick, 1000);
</script></body></html>"##;

/// Every key the virtual device must declare it can emit.
fn all_keys() -> Vec<Key> {
    let mut v = Vec::new();
    v.extend_from_slice(&LETTERS);
    v.extend_from_slice(&DIGITS);
    v.extend_from_slice(&FKEYS);
    v.extend_from_slice(&[
        Key::KEY_ENTER, Key::KEY_ESC, Key::KEY_BACKSPACE, Key::KEY_TAB, Key::KEY_SPACE,
        Key::KEY_MINUS, Key::KEY_EQUAL, Key::KEY_LEFTBRACE, Key::KEY_RIGHTBRACE,
        Key::KEY_BACKSLASH, Key::KEY_SEMICOLON, Key::KEY_APOSTROPHE, Key::KEY_GRAVE,
        Key::KEY_COMMA, Key::KEY_DOT, Key::KEY_SLASH,
        Key::KEY_HOME, Key::KEY_END, Key::KEY_DELETE, Key::KEY_PAGEUP, Key::KEY_PAGEDOWN,
        Key::KEY_LEFT, Key::KEY_RIGHT, Key::KEY_UP, Key::KEY_DOWN,
        Key::KEY_LEFTCTRL, Key::KEY_LEFTSHIFT, Key::KEY_LEFTALT, Key::KEY_LEFTMETA,
        Key::KEY_PLAYPAUSE, Key::KEY_NEXTSONG, Key::KEY_PREVIOUSSONG, Key::KEY_MUTE,
        Key::KEY_VOLUMEUP, Key::KEY_VOLUMEDOWN, Key::KEY_BRIGHTNESSUP, Key::KEY_BRIGHTNESSDOWN,
        Key::KEY_HOMEPAGE, Key::KEY_SEARCH,
        Key::BTN_LEFT, Key::BTN_RIGHT, Key::BTN_MIDDLE,
    ]);
    v
}
