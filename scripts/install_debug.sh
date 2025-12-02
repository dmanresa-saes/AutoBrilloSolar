#!/usr/bin/env bash
set -euo pipefail

DEVICE=${1:-}
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK not found. Run ./gradlew :app:assembleDebug first." >&2
  exit 1
fi

ADB="/home/usuario/Android/Sdk/platform-tools/adb"
if [[ -n "$DEVICE" ]]; then
  ADB="$ADB -s $DEVICE"
fi

$ADB install -r "$APK_PATH"

# Whitelist in Doze / AppOps to avoid aggressive killing
$ADB shell dumpsys deviceidle whitelist +com.autobrillo.solar.debug || true
$ADB shell appops set com.autobrillo.solar.debug RUN_ANY_IN_BACKGROUND allow || true
$ADB shell appops set com.autobrillo.solar.debug RUN_IN_BACKGROUND allow || true

echo "Installation + background exemptions applied."
