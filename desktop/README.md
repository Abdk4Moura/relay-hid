# Relay desktop receiver (Linux)

Turns your Linux machine into a target for the **Relay** phone app over **WiFi**.
Injects keyboard/mouse input via the kernel's **uinput** interface, so it works
under both **X11 and Wayland** (unlike X11-only injectors).

> WiFi mode is for **computers**. iPads can't be controlled this way — use Relay's
> Bluetooth HID mode for those. WiFi unlocks things BT can't: clipboard sync,
> file drop, longer range, no pairing (those land in later phases).

## Build

```sh
cd desktop
cargo build --release
```

Build deps: a Rust toolchain (`rustup`) and the kernel headers are not required —
`evdev` talks to `/dev/uinput` directly. (Built/tested against `evdev = 0.12`.)

## Grant uinput access (one-time, avoids running as root)

```sh
sudo cp 99-uinput.rules /etc/udev/rules.d/
sudo udevadm control --reload-rules && sudo udevadm trigger
sudo usermod -aG input $USER     # log out / back in for the group to take effect
```

(Or just run the binary with `sudo` to skip this.)

## Run

```sh
./target/release/relay-desktop --token 1234 --port 47600
```

It prints the **PIN** and port. In the Relay app, switch to **WiFi mode**, enter
this machine's **LAN IP** + the **PIN**, and connect.

## Protocol (for the app side)

Newline-delimited JSON over TCP, UTF-8. First line must authenticate:

| message | meaning |
|---|---|
| `{"t":"hello","token":"1234"}` | handshake (required first) |
| `{"t":"text","s":"hello"}` | type a UTF-8 string |
| `{"t":"key","code":40,"mods":8}` | HID usage code + HID modifier bitmask (bit0 Ctrl, 1 Shift, 2 Alt, 3 GUI) |
| `{"t":"move","dx":12,"dy":-4}` | relative pointer move |
| `{"t":"scroll","dy":2}` | wheel |
| `{"t":"button","b":"left","down":true}` | hold/release a button (`left`/`right`/`middle`) |
| `{"t":"click","b":"right"}` | press + release |
| `{"t":"consumer","usage":205}` | media/brightness (Consumer-page usage) |

The codes are exactly what Relay already produces for Bluetooth HID, so the app
just serializes the same events to the socket instead of the HID report.

## Roadmap

- **Phase 1 (this):** type / move / click / scroll / media over WiFi.
- **Phase 2:** mDNS auto-discovery (`_relay._tcp`), TLS, clipboard sync, file drop.
- **Phase 3:** absolute pointing via a streamed desktop thumbnail; multi-host.
