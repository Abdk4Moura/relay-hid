//! Windows + macOS backend.
//!
//! Mirrors the public surface the shared code (main/handle/serve_ui) calls — `Injector`,
//! `set_clip`/`get_clip`, `notify_desktop`, `start_tray` — but implemented with portable
//! crates instead of Linux's uinput/Wayland/D-Bus:
//!   - input    → enigo (SendInput on Windows, CGEvent on macOS)
//!   - clipboard→ arboard
//!   - notify   → notify-rust
//! There is no native tray here in v1; the web control panel (http://127.0.0.1:<ui>) is the UI.

use enigo::{
    Axis, Button, Coordinate, Direction, Enigo, Key, Keyboard, Mouse, Settings,
};

/// macOS's `Enigo` holds a `CGEventSource` (a raw pointer) that Rust marks `!Send`, which would
/// stop us sharing the injector across the per-connection threads via `Arc<Mutex<…>>`. All access
/// is serialized behind that mutex and `CGEventPost` is documented as usable from any thread, so a
/// `Send` wrapper is sound here. (On Windows `Enigo` is already `Send`; the wrapper is a no-op.)
struct SendEnigo(Enigo);
unsafe impl Send for SendEnigo {}

/// Replays phone input on this machine via the OS's synthetic-input API.
pub struct Injector {
    e: SendEnigo,
    /// Carry for high-res scroll: enigo 0.2 only does whole-line scroll, so we accumulate the
    /// phone's 1/120-detent units and emit one line each time a full 120 builds up.
    hires_v: i32,
    hires_h: i32,
}

impl Injector {
    pub fn new() -> std::io::Result<Self> {
        Enigo::new(&Settings::default())
            .map(|e| Injector { e: SendEnigo(e), hires_v: 0, hires_h: 0 })
            .map_err(|err| std::io::Error::new(std::io::ErrorKind::Other, format!("{err:?}")))
    }

    /// Tap a HID-usage keycode while holding the HID modifier bitmask. code 0 = lone modifier.
    pub fn tap_hid(&mut self, code: u16, mods: u8) {
        let held = hid_mods(mods);
        for m in &held {
            let _ = self.e.0.key(*m, Direction::Press);
        }
        if code != 0 {
            if let Some(k) = hid_to_key(code) {
                let _ = self.e.0.key(k, Direction::Click);
            }
        }
        for m in held.iter().rev() {
            let _ = self.e.0.key(*m, Direction::Release);
        }
    }

    pub fn text(&mut self, s: &str) {
        let _ = self.e.0.text(s);
    }

    pub fn move_rel(&mut self, dx: i32, dy: i32) {
        let _ = self.e.0.move_mouse(dx, dy, Coordinate::Rel);
    }

    /// `x`/`y` are normalized 0..32767; scale into the primary display's pixels.
    pub fn move_abs(&mut self, x: i32, y: i32) {
        if let Ok((w, h)) = self.e.0.main_display() {
            let px = (x as i64 * (w as i64 - 1) / 32767) as i32;
            let py = (y as i64 * (h as i64 - 1) / 32767) as i32;
            let _ = self.e.0.move_mouse(px, py, Coordinate::Abs);
        }
    }

    pub fn scroll(&mut self, dy: i32) {
        // HID wheel is +up; enigo's vertical scroll is +down → negate so directions agree.
        let _ = self.e.0.scroll(-dy, Axis::Vertical);
    }

    pub fn hscroll(&mut self, dx: i32) {
        let _ = self.e.0.scroll(dx, Axis::Horizontal);
    }

    /// High-res vertical scroll (1/120 detent units). enigo 0.2 has no pixel/hi-res scroll, so
    /// accumulate to whole lines. (A future enigo bump or direct SendInput/CGEvent call can carry
    /// the sub-line smoothness through to Windows/macOS; Linux already does via REL_WHEEL_HI_RES.)
    pub fn scroll_hr(&mut self, v120: i32) {
        self.hires_v += v120;
        let lines = self.hires_v / 120;
        if lines != 0 {
            self.hires_v -= lines * 120;
            // HID wheel is +up; enigo vertical scroll is +down → negate so directions agree.
            let _ = self.e.0.scroll(-lines, Axis::Vertical);
        }
    }

    pub fn hscroll_hr(&mut self, v120: i32) {
        self.hires_h += v120;
        let lines = self.hires_h / 120;
        if lines != 0 {
            self.hires_h -= lines * 120;
            let _ = self.e.0.scroll(lines, Axis::Horizontal);
        }
    }

    pub fn button(&mut self, b: &str, down: bool) {
        let btn = btn(b);
        let _ = self.e.0.button(btn, if down { Direction::Press } else { Direction::Release });
    }

    pub fn click(&mut self, b: &str) {
        let _ = self.e.0.button(btn(b), Direction::Click);
    }

    pub fn consumer(&mut self, usage: u16) {
        if let Some(k) = consumer_to_key(usage) {
            let _ = self.e.0.key(k, Direction::Click);
        }
    }
}

fn btn(b: &str) -> Button {
    match b {
        "right" => Button::Right,
        "middle" => Button::Middle,
        _ => Button::Left,
    }
}

fn hid_mods(mods: u8) -> Vec<Key> {
    let mut v = Vec::new();
    if mods & 0x01 != 0 {
        v.push(Key::Control);
    }
    if mods & 0x02 != 0 {
        v.push(Key::Shift);
    }
    if mods & 0x04 != 0 {
        v.push(Key::Alt);
    }
    if mods & 0x08 != 0 {
        v.push(Key::Meta); // Win/Super on Windows, ⌘ on macOS
    }
    v
}

fn f_key(i: u16) -> Option<Key> {
    Some(match i {
        0 => Key::F1,
        1 => Key::F2,
        2 => Key::F3,
        3 => Key::F4,
        4 => Key::F5,
        5 => Key::F6,
        6 => Key::F7,
        7 => Key::F8,
        8 => Key::F9,
        9 => Key::F10,
        10 => Key::F11,
        11 => Key::F12,
        _ => return None,
    })
}

/// USB HID Usage (Keyboard/Keypad page 0x07) → enigo key.
fn hid_to_key(code: u16) -> Option<Key> {
    Some(match code {
        0x04..=0x1d => Key::Unicode((b'a' + (code - 0x04) as u8) as char),
        0x1e..=0x26 => Key::Unicode((b'1' + (code - 0x1e) as u8) as char), // 1..9
        0x27 => Key::Unicode('0'),
        0x28 => Key::Return,
        0x29 => Key::Escape,
        0x2a => Key::Backspace,
        0x2b => Key::Tab,
        0x2c => Key::Space,
        0x2d => Key::Unicode('-'),
        0x2e => Key::Unicode('='),
        0x2f => Key::Unicode('['),
        0x30 => Key::Unicode(']'),
        0x31 => Key::Unicode('\\'),
        0x33 => Key::Unicode(';'),
        0x34 => Key::Unicode('\''),
        0x35 => Key::Unicode('`'),
        0x36 => Key::Unicode(','),
        0x37 => Key::Unicode('.'),
        0x38 => Key::Unicode('/'),
        0x3a..=0x45 => return f_key(code - 0x3a),
        0x4a => Key::Home,
        0x4b => Key::PageUp,
        0x4c => Key::Delete,
        0x4d => Key::End,
        0x4e => Key::PageDown,
        0x4f => Key::RightArrow,
        0x50 => Key::LeftArrow,
        0x51 => Key::DownArrow,
        0x52 => Key::UpArrow,
        _ => return None,
    })
}

/// Consumer page (0x0C) usage → enigo media key (brightness/etc. unsupported cross-platform).
fn consumer_to_key(usage: u16) -> Option<Key> {
    Some(match usage {
        0x00cd => Key::MediaPlayPause,
        0x00b5 => Key::MediaNextTrack,
        0x00b6 => Key::MediaPrevTrack,
        0x00e2 => Key::VolumeMute,
        0x00e9 => Key::VolumeUp,
        0x00ea => Key::VolumeDown,
        _ => return None,
    })
}

pub fn set_clip(s: &str) {
    if let Ok(mut cb) = arboard::Clipboard::new() {
        let _ = cb.set_text(s.to_owned());
    }
}

pub fn get_clip() -> String {
    arboard::Clipboard::new()
        .ok()
        .and_then(|mut cb| cb.get_text().ok())
        .unwrap_or_default()
}

pub fn notify_desktop(summary: &str, body: &str) {
    let _ = notify_rust::Notification::new()
        .summary(summary)
        .body(body)
        .appname("Relay")
        .show();
}

/// No native tray on Windows/macOS in v1 — the web control panel is the UI everywhere.
pub fn start_tray(_ui_port: u16, _st: crate::Shared) {}
