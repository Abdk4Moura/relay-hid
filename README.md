# Android → iPad Bluetooth HID Input

Turn an Android phone (Android 9 / API 28+) into a **Bluetooth keyboard & mouse**
that an iPad discovers and uses like any other external keyboard/trackpad — no app
on the iPad, works system-wide.

## How it works

The phone registers itself as a **Bluetooth HID peripheral** using Android's
`BluetoothHidDevice` API and advertises a composite **keyboard + mouse** report
descriptor. The iPad acts as the HID *host*: it pairs from Settings → Bluetooth and
reads input reports the phone sends.

Modifier mapping (standard HID → iPad):

| HID modifier | iPad key |
|--------------|----------|
| GUI          | Command ⌘ |
| Alt          | Option ⌥  |
| Ctrl         | Control ⌃ |
| Shift        | Shift ⇧   |

v1 implements the keyboard (modifiers, arrows, special keys, type-a-string). The
**mouse collection is already in the descriptor** (`sendMouseMove` exists) so adding
a trackpad UI later needs **no re-pairing**.

## Project layout

```
app/src/main/java/com/cadayn/hidinput/
  HidConstants.kt          report descriptor + keycode/modifier constants
  HidKeymap.kt             ASCII → (modifier, keycode) for typing strings
  HidPeripheralManager.kt  profile proxy, registerApp, send reports (kbd + mouse)
  MainActivity.kt          permissions, UI, modifier toggles, arrows, send-text
```

## Build & run

1. Open the project in **Android Studio** (it will set up the Gradle wrapper on first sync).
2. Connect the phone, press **Run**.
3. In the app: grant Bluetooth permissions → status shows **Registered**.
4. Tap **Make discoverable**.
5. On the **iPad**: Settings → Bluetooth → tap the phone → pair.
6. Status shows **Connected ✓**. Toggle modifiers, tap arrows/keys, or type text and **Send**.

Reconnect later with the **Connect paired** button.

## Pairing notes / troubleshooting

- If the phone never appears on the iPad, confirm Bluetooth is ON and you pressed
  **Make discoverable** (300s window).
- Some Androids only let the *host* initiate the first bond — pair from the iPad side.
- Watch live: `adb logcat -s HidPeripheral` shows registration + connect events.

---

## Live demo over Tailscale (wireless adb)

You don't need USB to deploy/observe — connect to the phone over its Tailscale IP.
The phone must be Android 11+ for wireless debugging pairing.

> Note: the **Bluetooth pairing to the iPad is physical** and can't be observed
> remotely. What Tailscale gives you is wireless **deploy + logcat**: install the
> app and stream HID registration / iPad-connect events live.

### On the phone (one-time)
1. Settings → Developer options → **Wireless debugging** → ON.
2. Tap **Pair device with pairing code** — note the `IP:PORT` and 6-digit code.
3. The phone is on Tailscale, so use its **Tailscale IP** (100.x.y.z) for the connect step.

### On your dev machine (Android Studio host)
```bash
# Pair once (use the IP:PORT + code from the pairing dialog)
adb pair 100.x.y.z:<pairPort>
# enter the 6-digit code when prompted

# Connect to the persistent debug port (shown on the Wireless debugging screen)
adb connect 100.x.y.z:<debugPort>

adb devices                       # confirm it's listed
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s HidPeripheral       # watch HID events live
```

In Android Studio, once `adb connect` succeeds the phone shows up as a normal
deployment target — just press **Run**.

### If you want this Linux box to drive adb over Tailscale
This machine currently has no JDK / Android SDK / adb. To drive deploys + logcat from
here, install platform-tools (adb) and join this box to your tailnet:
```bash
# adb only (enough to install APKs + read logcat; build still happens in Android Studio)
sudo apt-get update && sudo apt-get install -y android-tools-adb
adb connect 100.x.y.z:<debugPort>
```
Building the APK *here* additionally needs a JDK 17 + the Android SDK — ask and I'll
set that up.
