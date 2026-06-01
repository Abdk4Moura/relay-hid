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
use std::time::{SystemTime, UNIX_EPOCH};

use evdev::{
    uinput::VirtualDeviceBuilder, AttributeSet, EventType, InputEvent, Key, RelativeAxisType,
};
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

fn handle(stream: TcpStream, token: &str, inj: &mut Injector) {
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
                    continue;
                }
                _ => {
                    eprintln!("✗ {peer} bad/missing token — dropping");
                    break;
                }
            }
        }
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
    }
    println!("— {peer} disconnected");
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
    let token = token.unwrap_or_else(|| {
        // derive a 4-digit PIN from the clock if none supplied
        let n = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().subsec_nanos();
        format!("{:04}", n % 10000)
    });

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
    println!("┌─────────────────────────────────────────");
    println!("│ Relay desktop receiver");
    println!("│ Listening on 0.0.0.0:{port}");
    println!("│ PIN: {token}");
    println!("│ Enter this host's LAN IP + PIN in the Relay app.");
    println!("└─────────────────────────────────────────");

    for stream in listener.incoming() {
        match stream {
            Ok(s) => handle(s, &token, &mut inj),
            Err(e) => eprintln!("accept error: {e}"),
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
