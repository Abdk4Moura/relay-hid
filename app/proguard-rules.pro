# Relay release rules.
# Keep the app's own classes intact — we use reflection in a few places (BluetoothAdapter.setName,
# HID descriptor wiring) and the surface is small, so shrinking our own code buys little but risks
# subtle breakage. R8 still shrinks/optimises the (much larger) library code.
-keep class com.cadayn.hidinput.** { *; }

# org.json is part of the platform; nothing to keep. Compose ships its own consumer rules via AGP.
-dontwarn org.jetbrains.annotations.**
