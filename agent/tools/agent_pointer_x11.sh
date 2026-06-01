#!/usr/bin/env bash
#
# Give the relay's virtual input its OWN independent cursor (and keyboard focus) on X11,
# using Multi-Pointer X (XInput2). After this runs, the agent drives a separate "Relay Agent"
# cursor that moves independently of your real mouse — you can keep working while it operates.
#
# Requires: an X11 session (XDG_SESSION_TYPE=x11) and `xinput`. The relay-desktop receiver must
# already be RUNNING so its virtual devices exist before we reattach them.
#
#   tools/agent_pointer_x11.sh           # create + attach
#   tools/agent_pointer_x11.sh --remove  # tear down (returns devices to your pointer)
#
set -euo pipefail

MASTER="Relay Agent"
DEVICES=("Relay Virtual Input" "Relay Virtual Pointer (abs)")

if [ "${XDG_SESSION_TYPE:-}" != "x11" ]; then
    echo "✗ This needs an X11 session, but XDG_SESSION_TYPE='${XDG_SESSION_TYPE:-unset}'."
    echo "  Wayland compositors have a single seat/cursor — a second independent OS cursor"
    echo "  isn't possible there. See the README 'Independent cursor' section for the Wayland"
    echo "  options (nested session / VM / out-of-band capture card)."
    exit 1
fi
command -v xinput >/dev/null || { echo "✗ 'xinput' not found (install x11-xserver-utils / xinput)."; exit 1; }

dev_id() { xinput list --id-only "$1" 2>/dev/null || true; }

if [ "${1:-}" = "--remove" ]; then
    if xinput list --name-only | grep -qx "${MASTER} pointer"; then
        xinput remove-master "${MASTER} pointer" >/dev/null
        echo "✓ removed master '${MASTER}' — devices returned to your core pointer."
    else
        echo "• no '${MASTER}' master pointer to remove."
    fi
    exit 0
fi

# 1. Create the dedicated master pointer (+ paired keyboard) if it doesn't exist yet.
if ! xinput list --name-only | grep -qx "${MASTER} pointer"; then
    xinput create-master "$MASTER" >/dev/null
    echo "✓ created master pointer '${MASTER}'."
else
    echo "• master '${MASTER}' already exists."
fi

# 2. Reattach each relay virtual device to it. Pointer events drive the new cursor; key events
#    route to the paired '${MASTER} keyboard', so the agent also gets its OWN keyboard focus.
attached=0
for d in "${DEVICES[@]}"; do
    id="$(dev_id "$d")"
    if [ -n "$id" ]; then
        xinput reattach "$id" "${MASTER} pointer"
        echo "  ↳ attached '$d' (id $id)"
        attached=$((attached + 1))
    fi
done

if [ "$attached" -eq 0 ]; then
    echo "✗ No relay virtual devices found. Start relay-desktop first, then re-run this."
    echo "  (Looked for: ${DEVICES[*]})"
    exit 1
fi

echo
echo "✓ The relay now drives an independent '${MASTER}' cursor."
echo "  Your real mouse is untouched. Tear down with: $0 --remove"
echo
echo "  Tip: to make the agent cursor visually distinct, set a different cursor theme for it,"
echo "  e.g.   xsetroot is global — instead use a large/colored XCURSOR theme via the helper"
echo "         described in the README ('distinct cursor sprite')."
