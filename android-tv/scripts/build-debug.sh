#!/usr/bin/env bash
# Käännä debug-APK JDK 21:llä (tai 17). Gradle 8.7 ei toimi Java 25 -runtimeilla.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ "$(uname -s)" == "Darwin" ]] && [[ -x /usr/libexec/java_home ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || true)"
    export JAVA_HOME
  fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "Aseta JAVA_HOME osoittamaan JDK 17 tai 21:een (ei Java 25)." >&2
  exit 1
fi

./gradlew :app:assembleDebug

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "APK: $APK"
