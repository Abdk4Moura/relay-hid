# Relay Trackpad / Pointer / Scroll Ergonomics Research

Citation-backed product research for making the phone-as-peripheral trackpad feel effortless and best-in-class. Focus pain points: scrolling feels hard, pointer/scroll control does not feel effortless. Idea on the table: dedicate the whole pad to a scroll mode.

Style note: this document deliberately uses no em dashes.

---

## How this maps to the current Relay code (read this first)

The recommendations below are written against the code as it exists today. The constraints that shape everything:

- BT HID path: `HidPeripheralManager.sendMouseMove(dx, dy, wheel, buttons)` builds a 4-byte relative report `[buttons, dX, dY, wheel]` and coerces each axis to a signed byte (`coerceIn(-127, 127)`). Wheel is therefore an integer line-tick count per report, range -127..127. There is no pan (HWHEEL) axis on the BT descriptor.
- WiFi path: `WifiLink.scroll(dy)` sends `{"t":"scroll","dy":N}` and `hscroll(dx)` sends `{"t":"hscroll","dx":N}`. The Rust receiver (`desktop/src/main.rs`) creates a uinput device that registers `REL_X, REL_Y, REL_WHEEL, REL_HWHEEL` and forwards via `enigo` 0.2 line scroll.
- The trackpad UI is `PointerPad` in `ui/screens/RemoteScreen.kt`. It uses Compose `detectDragGestures` with a linear `sensitivity/5f` multiplier, `detectTapGestures` for click, and a single `KEYBOARD_TAP` haptic. No scroll gesture, no inertia, no acceleration curve, no scroll mode.
- Existing prefs already in `RelayController`: `sensitivity` (default 5), `scrollSpeed` (default 4), `naturalScroll` (default true), `haptics` (default true).

THE CENTRAL FINDING: the wire protocol carries integer wheel ticks and the Linux receiver registers only `REL_WHEEL` (one logical line per unit), so the receiver caps scroll resolution at one line per tick. Every momentum / inertia / fine-scroll model produces fractional pixel deltas per frame. If you want effortless smooth scroll you must either (a) simulate momentum on the phone and emit ticks only when an accumulator crosses a threshold (works today, coarse), or (b) upgrade the protocol plus receiver to high-resolution scroll (the 120-units-per-detent standard on Linux, pixel units on macOS). Recommendation (b) is the single highest-leverage change and is detailed below.

---

## (a) Executive summary: top 5 recommendations ranked by impact-to-effort

1. Add a high-resolution smooth-scroll protocol and receiver (highest impact). Switch the scroll wire format from integer line ticks to the 120-units-per-detent convention used by Linux `REL_WHEEL_HI_RES` (kernel 5.0+, libinput 1.19+), pixel units on macOS, and fractional `WHEEL_DELTA` on Windows. Register `REL_WHEEL_HI_RES` and `REL_HWHEEL_HI_RES` on the uinput device and emit values in /120 fractions. Without this, no amount of phone-side physics will feel smooth because the receiver quantizes to whole lines. Source: [Who-T: libinput and high-resolution wheel scrolling](https://who-t.blogspot.com/2021/08/libinput-and-high-resolution-wheel.html), [libinput high-resolution scroll doc](https://wayland.freedesktop.org/libinput/doc/latest/incorrectly-enabled-hires.html).

2. Implement momentum (fling) scrolling on the phone with an exponential decay model, accumulate fractional deltas, and stream them at the display frame rate. Use the iOS deceleration constant (0.998 per ms normal, 0.99 fast) which is simpler and more predictable than Android's spline model for a streamed-delta use case. Source: [decelerationRate, Apple](https://developer.apple.com/documentation/uikit/uiscrollview/decelerationrate), [Deceleration mechanics of UIScrollView](https://medium.com/@esskeetit/scrolling-mechanics-of-uiscrollview-142adee1142c).

3. Replace the linear pointer sensitivity with an adaptive acceleration curve (libinput-style two-segment curve: deceleration below a slow threshold, 1:1 in the middle, linear gain to a max factor for fast flicks). This is what makes a small surface give both pixel-precision and full-screen traversal. Source: [libinput pointer acceleration](https://wayland.freedesktop.org/libinput/doc/latest/pointer-acceleration.html).

4. Make scroll mode the two-finger gesture by default (zero mode switch, the industry standard on Precision Touchpad, macOS, and libinput) and offer the founder's whole-pad scroll mode as an explicit one-handed-thumb complement via a held edge zone or a toggle. Do not make whole-pad scroll the only path. Phones reliably report 5 to 10 touch points, so two-finger detection is free. Source: [Touch gestures for Windows, Microsoft](https://support.microsoft.com/en-us/windows/touch-gestures-for-windows-a9d28305-4818-a5df-4e2b-e5590f850741).

5. Add subtle haptics: a `SEGMENT_TICK` or scaled `PRIMITIVE_TICK` per scroll line crossed (detent feel), `PRIMITIVE_CLICK` for taps/clicks, and `TOGGLE_ON`/`TOGGLE_OFF` for scroll-mode entry/exit. Keep continuous-scroll haptics very low amplitude. Source: [Android haptics API](https://developer.android.com/develop/ui/views/haptics/haptics-apis), [Haptics design principles, Android](https://developer.android.com/develop/ui/views/haptics/haptics-principles).

Impact-to-effort ranking rationale: #1 and #2 are the direct fix for "scrolling feels hard" and are medium effort (protocol + receiver + a Choreographer loop). #3 is the fix for "pointer not effortless," low-to-medium effort (pure phone-side math). #4 is a design decision, near-zero code. #5 is polish, low effort, high perceived-quality payoff.

---

## (b) Recommended scroll model with exact parameters

### Two regimes: tracking scroll (finger down) and momentum scroll (after release)

Tracking scroll (finger on pad, dragging in scroll mode or with two fingers):
- Map touch displacement to scroll distance with a direct gain. A natural baseline on a phone is roughly 1.0 to 1.5 lines of scroll per finger-cm, tuned by `scrollSpeed`. Concretely: `scrollPixels = dragDp * SCROLL_GAIN`, with `SCROLL_GAIN` ranging 0.6 (precise) to 2.2 (fast) across the existing `scrollSpeed` 0..9 slider.
- Apply a mild acceleration to scroll too (same shape as pointer, gentler), so a slow finger nudges one line and a fast swipe covers a page. Without this you get the KDE Connect failure mode: a single fixed multiplier that is "uncomfortably fast and difficult to use with any precision," with "no way to adjust the response curve." Source: [KDE Connect Bug 407293](https://www.mail-archive.com/kde-bugs-dist@kde.org/msg355310.html).

Momentum scroll (fling after the finger lifts):
- Capture release velocity `v0` in pixels/second using the last 2 to 4 touch samples (use `MotionEvent.getHistorical*` to get sub-frame samples; see latency section).
- Decay model (exponential, per-millisecond), matching iOS:

```
v(t) = v0 * d^(t_ms)         d = 0.998 (normal),  0.99 (fast/flick)
```

  At a 60 fps tick (16.67 ms), per-frame retention is `0.998^16.67 = 0.967` (normal) and `0.99^16.67 = 0.846` (fast). Source: [Deceleration mechanics of UIScrollView](https://medium.com/@esskeetit/scrolling-mechanics-of-uiscrollview-142adee1142c).
- Total projected distance from a fling (useful to decide whether a fling is worth animating). Watch the units: v0 is in px/second but the decay rate d is per millisecond, so the canonical WWDC projection divides v0 by 1000:

```
distance = (v0 / 1000) * d / (1 - d)   (Apple projection, WWDC18 Designing Fluid Interfaces)
```

  Sanity check: with d = 0.998, `d/(1-d) = 499`, and `-1/ln(0.998) = 499.5`, so resting distance is about 0.5 * v0(px/s). At the v0 cap of 8000 px/s that is about 3992 px (a few screens), which is correct. Omitting the `/1000` overshoots by 1000x (millions of px, a runaway fling), so keep the divide-by-1000 in any implementation. Source: [WWDC18 803 Designing Fluid Interfaces, via UIScrollView mechanics article](https://medium.com/@esskeetit/scrolling-mechanics-of-uiscrollview-142adee1142c).
- Per-frame displacement in the decay loop must also respect units: `framePixels = v * (frameMs / 1000)` with v in px/s (do not use `v * 1`). Update v each frame by the retention factor (`v *= 0.967` at 60 Hz normal), then emit `framePixels`.
- Stop threshold: end the fling when per-frame displacement `< 0.5 px` (about 0.1 pt resting threshold per Apple's article).
- Velocity cap: clamp `v0` to a maximum to avoid runaway flings, e.g. 8000 px/s. Minimum fling velocity to trigger momentum at all: about 50 px/s (below this, treat as a slow drag with no coast).

Alternative decay model (Android OverScroller spline), for reference and parity testing:
- Constants from AOSP `SplineOverScroller`: `INFLEXION = 0.35`, `DECELERATION_RATE = ln(0.78)/ln(0.9) ≈ 2.358`, `GRAVITY = 2000.0`, default fling friction `ViewConfiguration.getScrollFriction() ≈ 0.015`, `mPhysicalCoeff = GRAVITY_EARTH * 39.37 * ppi * 0.84`.
- Fling distance: `flingDistance = friction * physicalCoeff * exp( DECEL/(DECEL-1) * ln( INFLEXION*|v| / (friction*physicalCoeff) ) )`.
- Source: [AOSP OverScroller.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/widget/OverScroller.java).
- Recommendation: use the simpler iOS exponential model for Relay. The spline model is tuned to scroll a finite on-screen list to a rest position; Relay is streaming an open-ended scroll delta to a remote app, so a clean exponential decay with a fixed friction is easier to reason about, tune, and stream frame-by-frame.

### Translating physics deltas into the remote-scroll protocol

The physics above produces fractional pixel deltas per frame. The wire protocol must carry them without losing them:

Option A (works today, no receiver change): fractional-tick accumulator.
- Maintain `accum += frameScrollPixels / PIXELS_PER_LINE` where `PIXELS_PER_LINE ≈ 40` (a common wheel line on desktop; Windows `WHEEL_DELTA = 120` per notch, default 3 lines per notch, so roughly 40 px per line).
- Each frame, emit `ticks = trunc(accum)`, then `accum -= ticks`. Send `{"t":"scroll","dy":ticks}` only when `ticks != 0`.
- This preserves smoothness of intent but the receiver still moves in whole lines. Acceptable as a first prototype, but it will visibly step.

Option B (recommended target): high-resolution scroll protocol.
- New message: `{"t":"scroll2","dy120":N}` where N is in 120ths of a logical line (the Linux/Windows convention: 120 units = one detent). Source: [Who-T high-res wheel scrolling](https://who-t.blogspot.com/2021/08/libinput-and-high-resolution-wheel.html).
- Receiver: register `REL_WHEEL_HI_RES` (code 0x0b) and `REL_HWHEEL_HI_RES` (0x0c) on the uinput device in `desktop/src/main.rs`, and emit `REL_WHEEL_HI_RES` events with the /120 value, plus a coarse `REL_WHEEL` event each time a full 120 accumulates (libinput expects both; the low-res event is the discrete fallback). Source: [libinput incorrectly-enabled-hires doc](https://wayland.freedesktop.org/libinput/doc/latest/incorrectly-enabled-hires.html).
- On macOS, the equivalent is `CGEventCreateScrollWheelEvent` with `kCGScrollEventUnitPixel` (smooth pixel scroll) rather than `kCGScrollEventUnitLine`. enigo 0.2 uses line units; you will likely need to call the platform API directly or bump enigo for pixel scroll.
- On Windows, send fractional multiples of `WHEEL_DELTA` (120) via `SendInput` `MOUSEEVENTF_WHEEL`.

### Frame rate and streaming

- Drive the momentum animation off `Choreographer` (or Compose `withFrameNanos`) at the panel's native refresh (60, 90, or 120 Hz). Emit one scroll message per frame while coasting.
- Network budget: at 120 Hz that is 120 small JSON messages/second during a fling, which is trivial over LAN. Coalesce: never send a zero delta; if two frames round to the same tick, still send (smoothness) but skip exact zeros.

### Rubber-band / overscroll

This only matters if Relay ever renders its own scroll surface (it does not today; it streams to the remote app, which owns its own overscroll). Documented here for completeness and for any in-app scroll preview:
- Apple rubber-band function: `f(x, d, c) = (x * d * c) / (d + c * x)`, where x is overscroll distance, d is the dimension (width/height), c = 0.55. Source: [UIScrollView inertia, bouncing and rubber-banding](https://holko.pl/2014/07/06/inertia-bouncing-rubber-banding-uikit-dynamics/).

### Concrete default parameter table

| Parameter | Value | Notes |
| --- | --- | --- |
| Decay (normal) | d = 0.998 / ms | iOS normal |
| Decay (flick) | d = 0.99 / ms | iOS fast; use when release velocity is high |
| Per-frame retention @60Hz | 0.967 / 0.846 | derived |
| Min fling velocity | 50 px/s | below: no coast |
| Max fling velocity (cap) | 8000 px/s | anti-runaway |
| Stop threshold | 0.5 px/frame | end coast |
| Pixels per line | 40 | tick conversion (Win WHEEL_DELTA basis) |
| Hi-res unit | 120 / detent | wire format target |
| Tick frame rate | panel native (60/90/120) | Choreographer |
| Scroll gain range | 0.6 to 2.2 | mapped from `scrollSpeed` 0..9 |

---

## (c) Recommended pointer-acceleration curve with formula and parameters

Goal: from a small touch surface, get both fine precision (a slow finger moves the pointer 1:1 or slower) and fast traversal (a quick flick crosses the whole screen). The proven shape is libinput's adaptive (two-segment) curve.

### The curve (libinput adaptive model, adapted)

Define input speed `s` = finger speed in dp/ms (computed from per-sample deltas, smoothed over the last few `getHistorical*` samples). Output is a gain factor `g(s)` applied to the raw delta:

- Below a low threshold `s_min`: gain decelerates to a floor (sub-1:1) for pixel precision. libinput uses a max deceleration of 0.3 (pointer moves at 30% of device speed at the slowest). Source: [libinput pointer acceleration](https://wayland.freedesktop.org/libinput/doc/latest/pointer-acceleration.html).
- Between `s_min` and `s_mid`: gain rises through 1:1 (a 1:1 mapping for "regular movements").
- Above `s_mid`: gain increases linearly to a maximum acceleration factor. libinput caps adaptive acceleration at 3.5. Source: same doc.

A clean closed form that reproduces this shape (smootherstep ramp between a floor and ceiling):

```
let t   = clamp((s - s_min) / (s_max - s_min), 0, 1)
let ramp = t*t*(3 - 2*t)                      // smoothstep
g(s) = G_MIN + (G_MAX - G_MIN) * ramp
move = rawDelta * g(s) * userSensitivity
```

Recommended parameters for a phone trackpad:

| Parameter | Value | Meaning |
| --- | --- | --- |
| G_MIN | 0.35 | precision floor (libinput uses 0.3 max decel) |
| G_MAX | 3.0 to 3.5 | fast-flick ceiling (libinput caps at 3.5) |
| s_min | 0.2 dp/ms | below this, full precision |
| s_max | 2.5 dp/ms | at/above this, full acceleration |
| userSensitivity | 0.5 to 2.0 | the existing `sensitivity` slider, multiplied on top |

This separates the curve shape (fixed, tuned for feel) from the user's `sensitivity` (a flat multiplier on top), which is the right architecture. The current code does only the flat multiplier (`sensitivity/5f`), which is why it feels either twitchy or sluggish but never both-precise-and-fast.

### Reference points from real systems

- libinput flat profile: constant factor, 1:1, no velocity dependence (good as an optional "gamer/flat" mode for users who hate acceleration). Source: [libinput pointer acceleration](https://wayland.freedesktop.org/libinput/doc/latest/pointer-acceleration.html).
- Windows Enhanced Pointer Precision: a `SmoothMouseXCurve` (input speed) to `SmoothMouseYCurve` (output speed) lookup table in 16.16 fixed point at `HKCU\Control Panel\Mouse`, plus a pointer-speed slider whose multipliers are 0.1, 0.2, 0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0 for slider positions 1 to 11 (default 6 = 1.0). The 5-point curve is the OS-level analogue of the parametric curve above. Source: [How to customize Windows accel, ESReality](https://www.esreality.com/index.php?a=post&id=1945096), [SmoothMouseXCurve/YCurve guide](https://sugarsweetapps.com/blog/how-to-customize-mouse-acceleration-in-windows-11-smoothmousexcurve-and-smoothmouseycurve/).
- macOS pointer ballistics: an S-shaped curve defined by a table of physical-speed thresholds each with a multiplier (roughly 10+ thresholds), widely considered steep. Implementation is closed; the takeaway is "table-driven S-curve." Source: [Mouse Ballistics, Coding Horror](https://blog.codinghorror.com/mouse-ballistics/).

Recommendation: ship the smoothstep adaptive curve as default, expose a "Flat (no acceleration)" toggle for power users, keep the `sensitivity` slider as a flat top-multiplier.

---

## (d) Mode-switch design recommendation

The question the founder posed: should the whole pad become a dedicated scroll surface, and how do users switch modes without friction? Honest evaluation below; do not rubber-stamp the whole-pad-only idea.

### Verdict: two-finger scroll is the primary, whole-pad scroll mode is a complement

- Two-finger scroll requires no mode switch at all. The user puts two fingers down and drags; intent is unambiguous and instantaneous. This is the default on Windows Precision Touchpad, macOS, and libinput. Phones reliably detect 5 to 10 simultaneous touch points, so two-finger detection is free and reliable. Make this the primary scroll path. Source: [Touch gestures for Windows, Microsoft](https://support.microsoft.com/en-us/windows/touch-gestures-for-windows-a9d28305-4818-a5df-4e2b-e5590f850741).
- The whole-pad scroll mode genuinely helps in one specific, important case: one-handed thumb operation, where a second finger is awkward or impossible. So keep it, but as an opt-in complement, not the only way to scroll.

### Lowest-friction mode switch for the whole-pad scroll mode

Ranked by friction (lowest first):

1. Held edge zone (recommended): reserve a thin strip on the right edge (and optionally bottom for horizontal) as a scroll gutter, like classic Synaptics edge-scroll. Touching there scrolls; the rest stays pointer. Zero explicit toggle, discoverable, thumb-reachable. This is the libinput/Synaptics edge-scroll model.
2. Sticky toggle button: a small "Scroll" pill that flips the whole pad into scroll mode and back, with `TOGGLE_ON`/`TOGGLE_OFF` haptics. This matches the project's existing "SOLO mode" sticky-toggle decision for lone modifiers (see memory: Relay lone-modifier UX), so it is consistent with house style. Good for sustained reading.
3. Long-press to enter scroll-drag (drag-lock analogue): press and hold ~250 ms, then the pad becomes a scroll surface until release.

Avoid as primary switches: pressure (Android touch pressure is unreliable across devices and not true force on most), and double-tap-and-hold (slow, error-prone). Pressure can be an optional power-user accelerator only.

Recommended combination: two-finger scroll always on (no switch) + a right-edge scroll gutter for one-handed use + an optional sticky Scroll toggle for long reading sessions. This gives effortless scroll for two-handed users and a real one-handed path without forcing a mode on anyone.

What power tools do: Windows PTP and macOS give two-finger scroll with no mode and three/four-finger gestures for window/desktop actions (PTP three-finger swipe = task view / app switch, four-finger = virtual desktops). Synaptics historically offered edge-scroll plus two-finger. The pattern across all of them: scroll is a finger-count gesture, not a mode. Source: [Touch gestures for Windows, Microsoft](https://support.microsoft.com/en-us/windows/touch-gestures-for-windows-a9d28305-4818-a5df-4e2b-e5590f850741).

---

## (e) Haptics spec

Use Android haptics to make the trackpad feel physical. Two API tiers:

- HapticFeedbackConstants via `view.performHapticFeedback(...)`: system-consistent, has fallbacks, no permission needed. Use for discrete events. Note: minimum API levels vary per constant (the newer scroll/segment constants such as `SEGMENT_TICK`, `SEGMENT_FREQUENT_TICK`, `GESTURE_START/END` are recent additions; verify each against the official `HapticFeedbackConstants` reference before relying on it as a fallback, and `performHapticFeedback` is a no-op on devices that lack the constant).
- VibrationEffect.Composition (API 31+, needs `VIBRATE` permission): expressive scaled primitives, no system fallback, must check `vibrator.arePrimitivesSupported(...)`. Use for tuned detents and clicks where supported.

Source: [Android haptics API](https://developer.android.com/develop/ui/views/haptics/haptics-apis), [AOSP haptics constants and primitives](https://source.android.com/docs/core/interaction/haptics/haptics-constants-primitives).

### Event-to-haptic mapping

| Event | Primary (Composition, API 31+) | Fallback (HapticFeedbackConstants) | Intensity |
| --- | --- | --- | --- |
| Tap / left click | `PRIMITIVE_CLICK` scale 0.6 | `KEYBOARD_TAP` or `CONFIRM` | medium-crisp |
| Right click (two-finger tap) | `PRIMITIVE_CLICK` scale 0.8 | `CONTEXT_CLICK` | stronger |
| Scroll line crossed (detent) | `PRIMITIVE_LOW_TICK` scale 0.15 to 0.25 | `SEGMENT_TICK` | very subtle |
| Scroll-mode entered | `PRIMITIVE_TICK` scale 0.5 | `TOGGLE_ON` | light |
| Scroll-mode exited | `PRIMITIVE_TICK` scale 0.4 | `TOGGLE_OFF` | light |
| Fling launched | `PRIMITIVE_TICK` scale 0.6 + `PRIMITIVE_SPIN` scale 0.3 | `GESTURE_START` | light flourish |
| Fling settles / hits end | `PRIMITIVE_THUD` scale 0.4 | `GESTURE_END` | soft thud |
| Drag-lock engaged | `PRIMITIVE_CLICK` scale 0.7 | `LONG_PRESS` | firm |

Design rules (from Android guidance):
- Scroll/detent haptics must be very subtle and low amplitude because they fire frequently; reserve stronger effects for important, infrequent events. Source: [Android haptics API](https://developer.android.com/develop/ui/views/haptics/haptics-apis), [Haptics design principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles).
- Detent rate-limit: do not fire a detent tick more than ~every 1 to 2 scroll lines during fast flings, or it becomes a buzz. Cap detent haptics to roughly 30 to 60 Hz max and fade them out as fling velocity rises.
- Always gate on the existing `c.haptics` pref and `View.isHapticFeedbackEnabled`. Probe `arePrimitivesSupported` once and cache; fall back to the constants column on unsupported devices.

The premium feel comes from: a crisp `PRIMITIVE_CLICK` on tap, faint `LOW_TICK` detents while scrolling (so scrolling feels like a notched wheel), and a soft `THUD` when momentum settles. That trio is what makes a glass surface read as a physical trackpad.

---

## (f) Per-topic findings with citations

### 1. Prior art teardown

- Windows Precision Touchpad (PTP): the gold standard gesture vocabulary. Two-finger drag = scroll (vertical and horizontal), two-finger tap = right click, pinch = zoom, three-finger swipe = task view / app switch / show desktop, three-finger tap = search, four-finger swipe = virtual desktops, four-finger tap = notifications. Scroll is a finger-count gesture, never a mode. Source: [Touch gestures for Windows, Microsoft](https://support.microsoft.com/en-us/windows/touch-gestures-for-windows-a9d28305-4818-a5df-4e2b-e5590f850741).
- macOS / Magic Trackpad: two-finger scroll with inertia (decelerationRate 0.998 normal), rubber-band overscroll (c = 0.55), natural-scroll default. Gesture model is multi-finger, momentum everywhere. Source: [decelerationRate, Apple](https://developer.apple.com/documentation/uikit/uiscrollview/decelerationrate), [rubber-banding](https://holko.pl/2014/07/06/inertia-bouncing-rubber-banding-uikit-dynamics/).
- KDE Connect (closest direct competitor, phone-as-trackpad): does two-finger scroll and tap-to-click, works across desktops. But its documented weaknesses are exactly Relay's opportunity: scroll speed is "uncomfortably fast and difficult to use with any precision," with "no way to adjust the response curve," and the pointer sensitivity slider does not affect scroll. Relay should ship an adjustable scroll response curve from day one. Sources: [KDE Connect trackpad how-to, gHacks](https://www.ghacks.net/2025/07/31/how-to-use-your-phone-as-a-mouse-with-kde-connect/), [KDE Connect Bug 407293](https://www.mail-archive.com/kde-bugs-dist@kde.org/msg355310.html), [KDE Connect natural-scroll review request](https://mail.kde.org/pipermail/kdeconnect/2015-November/001298.html).
- Remote Mouse: offers two-finger scroll like a laptop trackpad, but full gesture support is platform-dependent (richer on Mac). Source: [Remote Mouse gestures blog](https://www.remotemouse.net/blog/gestures/).
- Unified Remote / Monect / Mobile Mouse Pro: all provide a trackpad surface with two-finger or edge scroll; none are known for a tuned acceleration curve or momentum, which is the differentiation gap.
- What the best get right: two-finger scroll as a no-mode gesture, momentum, multi-finger taps. What they get wrong (and Relay can win on): adjustable response curves, fine-vs-fast acceleration, premium haptics, one-handed scroll path.

### 2. Pointer ballistics / transfer functions

- libinput: flat profile (constant factor, 1:1) vs adaptive profile (default, velocity-dependent). Adaptive uses "trackers" to measure speed across recent events, decelerates at very slow speed (max deceleration 0.3), is 1:1 for regular movement, and ramps linearly to a max acceleration of 3.5 for fast movement. A custom profile lets you define `(n*step, f[n])` points and interpolate. Source: [libinput pointer acceleration](https://wayland.freedesktop.org/libinput/doc/latest/pointer-acceleration.html).
- Windows EPP: input-to-output speed lookup curve (`SmoothMouseXCurve`/`YCurve`, 5 points, 16.16 fixed point) plus pointer-speed multiplier slider (0.1 to 2.0, default 1.0). Source: [ESReality accel tutorial](https://www.esreality.com/index.php?a=post&id=1945096).
- macOS: closed table-driven S-curve, multiple physical-speed thresholds with per-threshold multipliers; considered steep. Source: [Mouse Ballistics, Coding Horror](https://blog.codinghorror.com/mouse-ballistics/).
- Best curve for a small surface: adaptive two-segment (precision floor at low speed, 1:1 mid, linear gain to ~3x at high speed), per the formula in section (c). Offer flat as an option.

### 3. Scroll physics

- iOS deceleration: `v(t) = v0 * d^t_ms`, d = 0.998 (normal), 0.99 (fast), applied per millisecond. Projected stop distance `= v0*d/(1-d)` (WWDC18 projection). Rubber band `f = (x*d*c)/(d + c*x)`, c = 0.55. Sources: [decelerationRate, Apple](https://developer.apple.com/documentation/uikit/uiscrollview/decelerationrate), [UIScrollView mechanics](https://medium.com/@esskeetit/scrolling-mechanics-of-uiscrollview-142adee1142c), [rubber-banding](https://holko.pl/2014/07/06/inertia-bouncing-rubber-banding-uikit-dynamics/).
- Android OverScroller spline: `INFLEXION 0.35`, `DECELERATION_RATE = ln(0.78)/ln(0.9) ≈ 2.358`, `GRAVITY 2000`, friction ≈ 0.015, `physicalCoeff = GRAVITY_EARTH*39.37*ppi*0.84`; fling distance and duration formulas as quoted in section (b). Source: [AOSP OverScroller.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/widget/OverScroller.java).
- Lines vs pixels vs smooth pixels: Linux high-res scroll uses 120 units per logical line (`REL_WHEEL_HI_RES`/`REL_HWHEEL_HI_RES`, kernel 5.0+, libinput 1.19+, `libinput_pointer_scroll_get_value_v120()`); the 120 comes from the Windows Vista API (`WHEEL_DELTA = 120`). macOS smooth pixel scroll = `kCGScrollEventUnitPixel`. Sources: [Who-T high-res wheel scrolling](https://who-t.blogspot.com/2021/08/libinput-and-high-resolution-wheel.html), [libinput high-res doc](https://wayland.freedesktop.org/libinput/doc/latest/incorrectly-enabled-hires.html).
- Mapping a drag to deltas that feel natural at both speeds: direct gain in tracking, plus a gentle acceleration so slow = one line, fast = a page; momentum on release. Avoid a single fixed multiplier (the KDE Connect mistake).

### 4. The whole-pad-as-scroll-surface idea

- Two-finger scroll already needs no mode and is the universal standard; phones detect plenty of touch points, so it is the right default. Whole-pad scroll mode is best as a one-handed complement. Lowest-friction switches: held edge gutter (best), sticky toggle (consistent with Relay's SOLO-mode pattern), long-press drag. Avoid pressure as a primary trigger. Sources: [Touch gestures for Windows, Microsoft](https://support.microsoft.com/en-us/windows/touch-gestures-for-windows-a9d28305-4818-a5df-4e2b-e5590f850741), Relay memory: lone-modifier UX (sticky SOLO toggle).

### 5. Gesture design

- Recommended vocabulary: one-finger drag = pointer move; tap = left click; two-finger drag = scroll (vertical + horizontal); two-finger tap = right click; three-finger tap = middle click; three-finger swipe = app/window switch (optional, maps to OS); long-press = drag-lock (hold to grab, move, release to drop); right-edge strip = one-handed scroll gutter.
- Touch points: Android reports many simultaneous pointers (commonly 5 to 10 reliably), so two- and three-finger detection is dependable.
- Disambiguating move vs scroll vs click: gate on pointer count at gesture start (1 = move, 2 = scroll, 2-tap = right click); use a small movement threshold (~8 to 12 dp) plus a short time window (~150 ms) to separate tap from drag; once a gesture's intent is locked at touch-down + threshold, do not switch mid-gesture.

### 6. Haptics and feedback

- See full spec in section (e). Key sources: [Android haptics API](https://developer.android.com/develop/ui/views/haptics/haptics-apis), [AOSP haptics constants and primitives](https://source.android.com/docs/core/interaction/haptics/haptics-constants-primitives), [Haptics design principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles), [VibrationEffect.Composition](https://developer.android.com/reference/android/os/VibrationEffect.Composition).

### 7. Latency and sampling

- Use `MotionEvent.getHistoricalX/Y(pointerIndex, historyPos)`: a single MotionEvent can batch several samples taken faster than the UI frame rate. Consume historical samples first (in time order), then the current sample, so you integrate every sub-frame motion instead of only the latest point. This both smooths pointer motion and gives an accurate release velocity for flings. Source: [MotionEvent, Android](https://stuff.mit.edu/afs/sipb/project/android/docs/reference/android/view/MotionEvent.html), [AOSP MotionEvent.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/view/MotionEvent.java).
- High-refresh panels (90/120/240 Hz) report touch faster; integrate all historical samples and stream deltas at panel refresh via Choreographer. Send relative deltas (not absolute) over the link, which the protocol already does.
- Batching/coalescing for the network: accumulate per-frame motion into one delta and send one message per frame; never send zero deltas; this caps message rate at the refresh rate.
- Target end-to-end latency for "effortless": aim for under ~50 ms motion-to-screen; under ~20 ms feels native. On a LAN the dominant cost is per-frame batching and receiver injection, not the wire, so frame-aligned sending is the main lever.

### 8. Ergonomics and reachability

- One-handed thumb: the thumb naturally reaches the lower and right portions of the screen, which is why a right-edge scroll gutter (and bottom edge for horizontal) is the ergonomic place for the one-handed scroll zone.
- Fitts's law: on a small surface, time to acquire a target grows with distance/size; an adaptive acceleration curve effectively shrinks "distance" for far targets (fast traversal) while keeping precision for small ones, which is the direct ergonomic justification for section (c). Source (background): [Mouse Ballistics, Coding Horror](https://blog.codinghorror.com/mouse-ballistics/).
- Gyro/IMU air-mouse complement: for fine pointing, a gyro air-mouse (the app's planned feature) pairs well with the trackpad: use the trackpad for travel and the gyro for fine aim, or trigger gyro-fine-mode on a held button. Treat it as a precision overlay, not a replacement, and apply the same low-speed precision floor.

---

## (g) What to prototype first (build list mapped to the app)

Ordered to deliver the felt improvement fastest, against `PointerPad` in `ui/screens/RemoteScreen.kt`, `RelayController`, `WifiLink`, and `desktop/src/main.rs`.

1. Two-finger scroll gesture in `PointerPad` (no new protocol). Add a `pointerInput` that counts active pointers; with 2 pointers, route drag deltas to `c.scroll(...)`/`c.scrollH(...)` instead of `c.mouseMove(...)`. Use a fractional-tick accumulator (Option A in section b) so slow scroll still emits occasional ticks. Immediate, visible win. Files: `RemoteScreen.kt`.

2. Adaptive pointer acceleration curve. Replace the flat `sensitivity/5f` with the smoothstep adaptive curve from section (c): compute finger speed from drag deltas (and `getHistorical*`), apply `g(s)`, then the `sensitivity` multiplier. Add a "Flat" toggle. Files: `RemoteScreen.kt`, `RelayController` (curve params + flat pref).

3. Momentum scrolling. On scroll-gesture release, capture velocity from the last few samples, run a `withFrameNanos` decay loop (d = 0.998/0.99), accumulate fractional ticks, stream per frame, stop at threshold. Files: `RemoteScreen.kt`.

4. High-resolution scroll protocol + receiver (the big one). Add `{"t":"scroll2","dy120":N}` (and hscroll equivalent) to `WifiLink`; in `desktop/src/main.rs` register `REL_WHEEL_HI_RES`/`REL_HWHEEL_HI_RES` and emit /120 values plus the coarse `REL_WHEEL` fallback. Add macOS pixel-scroll and Windows fractional `WHEEL_DELTA` paths (may need to bypass enigo 0.2 line scroll). Files: `WifiLink.kt`, `desktop/src/main.rs`, `desktop/src/cross.rs`. This converts the stepped scroll from steps 1 to 3 into genuinely smooth scroll.

5. Haptics pass. Wire the section (e) table: `PRIMITIVE_CLICK` on tap, faint `PRIMITIVE_LOW_TICK`/`SEGMENT_TICK` per scroll line (rate-limited), `TOGGLE_ON/OFF` on scroll-mode switch, soft `THUD` on momentum settle. Probe `arePrimitivesSupported`, gate on `c.haptics`. Files: `RemoteScreen.kt`, a small haptics helper.

6. One-handed scroll affordance. Add a right-edge scroll gutter and/or a sticky "Scroll" toggle (consistent with the SOLO-mode pattern). Files: `RemoteScreen.kt`.

7. Two/three-finger taps. Two-finger tap = right click (`c.click(true)`), three-finger tap = middle click. Files: `RemoteScreen.kt`.

Prototype order summary: ship 1 to 3 first (phone-only, instantly better feel), then 4 (the smoothness unlock), then 5 to 7 (polish and one-handed/power-user paths).

---

## Sources

- libinput pointer acceleration: https://wayland.freedesktop.org/libinput/doc/latest/pointer-acceleration.html
- libinput high-resolution scroll: https://wayland.freedesktop.org/libinput/doc/latest/incorrectly-enabled-hires.html
- Who-T, libinput and high-resolution wheel scrolling: https://who-t.blogspot.com/2021/08/libinput-and-high-resolution-wheel.html
- Apple decelerationRate: https://developer.apple.com/documentation/uikit/uiscrollview/decelerationrate
- UIScrollView deceleration mechanics: https://medium.com/@esskeetit/scrolling-mechanics-of-uiscrollview-142adee1142c
- UIScrollView rubber-banding: https://holko.pl/2014/07/06/inertia-bouncing-rubber-banding-uikit-dynamics/
- AOSP OverScroller.java: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/widget/OverScroller.java
- Microsoft, Touch gestures for Windows: https://support.microsoft.com/en-us/windows/touch-gestures-for-windows-a9d28305-4818-a5df-4e2b-e5590f850741
- ESReality, customize Windows accel (SmoothMouseXCurve/YCurve): https://www.esreality.com/index.php?a=post&id=1945096
- SmoothMouseXCurve/YCurve guide: https://sugarsweetapps.com/blog/how-to-customize-mouse-acceleration-in-windows-11-smoothmousexcurve-and-smoothmouseycurve/
- Mouse Ballistics (Coding Horror): https://blog.codinghorror.com/mouse-ballistics/
- Android haptics API: https://developer.android.com/develop/ui/views/haptics/haptics-apis
- AOSP haptics constants and primitives: https://source.android.com/docs/core/interaction/haptics/haptics-constants-primitives
- Android haptics design principles: https://developer.android.com/develop/ui/views/haptics/haptics-principles
- VibrationEffect.Composition: https://developer.android.com/reference/android/os/VibrationEffect.Composition
- Android MotionEvent: https://stuff.mit.edu/afs/sipb/project/android/docs/reference/android/view/MotionEvent.html
- AOSP MotionEvent.java: https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/view/MotionEvent.java
- KDE Connect trackpad how-to (gHacks): https://www.ghacks.net/2025/07/31/how-to-use-your-phone-as-a-mouse-with-kde-connect/
- KDE Connect Bug 407293 (scroll too fast, no curve): https://www.mail-archive.com/kde-bugs-dist@kde.org/msg355310.html
- KDE Connect natural-scroll review request: https://mail.kde.org/pipermail/kdeconnect/2015-November/001298.html
- Remote Mouse gestures: https://www.remotemouse.net/blog/gestures/
