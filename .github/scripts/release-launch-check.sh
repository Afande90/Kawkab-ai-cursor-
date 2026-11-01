#!/usr/bin/env bash
#
# Task 31: launch the minified foss release APK the way the owner runs it.
#
# Installs app-foss-release.apk (R8-minified, arm64; the API 36 google_apis
# x86_64 image runs arm64 code through its native bridge), grants every runtime
# permission, enables the accessibility service, turns the wake word on, marks
# setup finished so the chat opens, launches MainActivity and checks after 30 s
# that the process is alive and MainActivity is the resumed activity.
#
# The wake word and setup flags are written into the app's private storage with
# `adb root`, which the google_apis emulator image allows. That reaches the same
# launch path the owner reached: MainActivity.onResume → WakeWordService →
# sherpa-onnx.
#
# Usage: bash .github/scripts/release-launch-check.sh <apk> <output dir>

set -uo pipefail

APK="$1"
OUT="$2"
PKG="app.kawkab.android"
SERVICE="$PKG/$PKG.accessibility.KawkabAccessibilityService"
mkdir -p "$OUT"

fail() {
  echo "::error::$1"
  adb logcat -d -b crash > "$OUT/logcat-crash.txt" 2>&1 || true
  adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
  exit 1
}

adb root >/dev/null 2>&1 || true
adb wait-for-device

echo "Installing $APK"
if ! adb install -r -g "$APK" > "$OUT/install.txt" 2>&1; then
  cat "$OUT/install.txt"
  fail "the release APK did not install"
fi

adb shell settings put secure enabled_accessibility_services "$SERVICE"
adb shell settings put secure accessibility_enabled 1
adb shell appops set "$PKG" RECORD_AUDIO allow || true

# First start creates the app's data directories; then stop it and write the flags.
adb shell am start -W -n "$PKG/.presentation.ui.MainActivity" > /dev/null 2>&1 || true
sleep 5
adb shell am force-stop "$PKG"

DATA="/data/data/$PKG"
OWNER=$(adb shell stat -c '%u:%g' "$DATA" | tr -d '\r')
adb shell mkdir -p "$DATA/shared_prefs" "$DATA/files/datastore"
adb shell "cat > $DATA/shared_prefs/wake_word.xml" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="enabled" value="true" />
    <long name="started_at" value="0" />
    <int name="start_battery" value="100" />
    <int name="wakes" value="0" />
    <boolean name="charged" value="false" />
</map>
XML
# DataStore PreferenceMap { "has_completed_onboarding": true }, protobuf wire format.
python3 - "$OUT/agent_preferences.preferences_pb" <<'PY'
import sys
key = b"has_completed_onboarding"
value = b"\x08\x01"                      # Value { boolean = true }
entry = b"\x0a" + bytes([len(key)]) + key + b"\x12" + bytes([len(value)]) + value
open(sys.argv[1], "wb").write(b"\x0a" + bytes([len(entry)]) + entry)
PY
adb push "$OUT/agent_preferences.preferences_pb" "$DATA/files/datastore/agent_preferences.preferences_pb" > /dev/null
adb shell chown -R "$OWNER" "$DATA/shared_prefs" "$DATA/files/datastore"
adb shell restorecon -R "$DATA" > /dev/null 2>&1 || true

adb logcat -c
echo "Launching"
adb shell am start -W -n "$PKG/.presentation.ui.MainActivity" | tee "$OUT/start.txt"
sleep 30

adb shell dumpsys activity services "$PKG" > "$OUT/services.txt" 2>&1 || true
adb exec-out screencap -p > "$OUT/screen.png" 2>/dev/null || true
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null 2>&1 || true

PID=$(adb shell pidof "$PKG" | tr -d '\r')
[[ -n "$PID" ]] || fail "the app process is gone 30 s after launch (crash on launch)"
RESUMED=$(adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -1)
echo "$RESUMED"
[[ "$RESUMED" == *"$PKG/.presentation.ui.MainActivity"* ]] || fail "MainActivity is not on screen 30 s after launch"
if adb logcat -d -b crash | grep -q "$PKG"; then
  fail "the app crashed after launch (crash buffer names it)"
fi

grep -q "WakeWordService" "$OUT/services.txt" && echo "Wake-word service is running." \
  || echo "::warning::the wake-word service is not running (it may have been disabled after a failed start)"
adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
echo "Release launch check passed: process $PID alive, MainActivity resumed."
