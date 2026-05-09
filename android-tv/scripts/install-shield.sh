#!/usr/bin/env bash
# Asenna debug-APK Shieldille verkko-ADB:llä (langaton USB-debuggaus).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -f "$APK" ]]; then
  echo "Puuttuva APK. Aja ensin: $ROOT/scripts/build-debug.sh" >&2
  exit 1
fi

ADB="${ADB:-adb}"
if ! command -v "$ADB" >/dev/null 2>&1; then
  echo "adb ei löydy PATHissa. macOS: brew install android-platform-tools" >&2
  exit 1
fi

RAW="${1:-${SHIELD_ADB_HOST:-}}"
if [[ -z "$RAW" ]]; then
  echo "Käyttö: $0 <shield-ip>              # oletusportti 5555" >&2
  echo "   tai: $0 <osoite:portti>" >&2
  echo "   tai: SHIELD_ADB_HOST=<ip> $0" >&2
  exit 1
fi

if [[ "$RAW" == *:* ]]; then
  TARGET="$RAW"
else
  TARGET="${RAW}:5555"
fi

echo "→ adb connect $TARGET"
"$ADB" connect "$TARGET"
"$ADB" devices -l
echo "→ adb install -r $(basename "$APK")"
"$ADB" install -r "$APK"
echo "Valmis."
