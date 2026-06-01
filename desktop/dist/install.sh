#!/bin/sh
# Relay desktop receiver installer (Linux, x86_64). No root except the one-time
# uinput rule. Installs the binary + an auto-updating launcher + app-menu entry +
# autostart. Re-run anytime to repair.
#
#   curl -fsSL https://raw.githubusercontent.com/Abdk4Moura/relay-desktop/main/dist/install.sh | sh

set -e
REPO="Abdk4Moura/relay-desktop"
REL="https://github.com/$REPO/releases/latest/download"
RAW="https://raw.githubusercontent.com/$REPO/main/dist"

BIN_DIR="$HOME/.local/bin"
DATA="${XDG_DATA_HOME:-$HOME/.local/share}/relay-desktop"
APPS="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
ICONS="${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor/256x256/apps"
AUTOSTART="$HOME/.config/autostart"
mkdir -p "$BIN_DIR" "$DATA" "$APPS" "$ICONS" "$AUTOSTART"

echo "→ downloading receiver…"
curl -fsSL "$REL/relay-desktop-x86_64-linux" -o "$DATA/relay-desktop"
chmod +x "$DATA/relay-desktop"
curl -fsSL "$REL/VERSION" -o "$DATA/VERSION" 2>/dev/null || true

echo "→ installing launcher, icon, app entry…"
curl -fsSL "$RAW/relay-desktop-launch" -o "$BIN_DIR/relay-desktop-launch"; chmod +x "$BIN_DIR/relay-desktop-launch"
curl -fsSL "$RAW/relay-desktop.png" -o "$ICONS/relay-desktop.png" 2>/dev/null || true
curl -fsSL "$RAW/relay-desktop.desktop" -o "$APPS/relay-desktop.desktop"
cp "$APPS/relay-desktop.desktop" "$AUTOSTART/relay-desktop.desktop"
update-desktop-database "$APPS" 2>/dev/null || true
gtk-update-icon-cache "${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor" 2>/dev/null || true

echo
echo "✓ Installed. Launch \"Relay Desktop\" from your app menu (auto-updates + opens the panel)."
echo
echo "One-time uinput permission (so it can inject input without sudo):"
echo "  echo 'KERNEL==\"uinput\", GROUP=\"input\", MODE=\"0660\", OPTIONS+=\"static_node=uinput\"' | sudo tee /etc/udev/rules.d/99-uinput.rules"
echo "  sudo udevadm control --reload-rules && sudo udevadm trigger && sudo usermod -aG input \"\$USER\""
echo "  (then log out / back in)"
