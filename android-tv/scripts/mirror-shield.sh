#!/usr/bin/env bash
# Peilaa Shieldin (tai muun adb-laitteen) ruutu Mac-ikkunaan. Vaatii: adb connect … ja hyväksyntä Shieldillä.
set -euo pipefail
ADB="${ADB:-adb}"
SER="${1:-192.168.1.116:5555}"

if ! command -v scrcpy >/dev/null 2>&1; then
  echo "Asenna: brew install scrcpy" >&2
  exit 1
fi
if ! command -v "$ADB" >/dev/null 2>&1; then
  echo "adb ei löydy. Asenna: brew install android-platform-tools" >&2
  exit 1
fi

"$ADB" connect "$SER" 2>/dev/null || true
# Shield / Android TV: oletusääni (opus) kaataa usein scrcpy:n — ääni pois oletuksena.
exec scrcpy -s "$SER" \
  --no-audio \
  --window-title "Shield (Kodinäyttö)" \
  --max-size 1920 \
  --video-buffer=50 \
  "${@:2}"
