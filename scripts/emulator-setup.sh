#!/usr/bin/env bash
# Prepare a Tier 1 emulator (API 36, google_apis, x86_64, debug variant).
# Spec §12.1. Does not boot the emulator. Run it after the AVD is online.
#
# Usage: scripts/emulator-setup.sh [package]
# Default package is the debug application id.
set -euo pipefail

pkg="${1:-app.kawkab.android.debug}"
# The accessibility service is added in task 10. The component name is fixed here
# so the grant and the service stay the same class.
service="${pkg}/app.kawkab.android.accessibility.KawkabAccessibilityService"

if ! adb get-state >/dev/null 2>&1; then
  echo "No adb device. Boot the API 36 emulator first." >&2
  exit 1
fi

adb wait-for-device
adb shell input keyevent 82 || true

permissions=(
  android.permission.POST_NOTIFICATIONS
  android.permission.READ_CONTACTS
  android.permission.WRITE_CONTACTS
  android.permission.READ_CALENDAR
  android.permission.WRITE_CALENDAR
  android.permission.RECORD_AUDIO
  android.permission.CALL_PHONE
)
for permission in "${permissions[@]}"; do
  adb shell pm grant "$pkg" "$permission" || echo "skip grant $permission"
done

adb shell appops set "$pkg" ACCESS_RESTRICTED_SETTINGS allow || echo "skip appops ACCESS_RESTRICTED_SETTINGS"

adb shell settings put secure enabled_accessibility_services "$service"
adb shell settings put secure accessibility_enabled 1

seed_contact() {
  local display_name="$1"
  local phone="$2"
  local raw
  raw="$(adb shell content insert --uri content://com.android.contacts/raw_contacts --bind account_type:n:null --bind account_name:n:null)"
  raw="$(printf '%s' "$raw" | tr -d '\r')"
  local id="${raw##*/}"
  adb shell content insert --uri content://com.android.contacts/data \
    --bind raw_contact_id:i:"$id" \
    --bind mimetype:s:vnd.android.cursor.item/name \
    --bind data1:s:"$display_name" >/dev/null
  adb shell content insert --uri content://com.android.contacts/data \
    --bind raw_contact_id:i:"$id" \
    --bind mimetype:s:vnd.android.cursor.item/phone_v2 \
    --bind data1:s:"$phone" \
    --bind data2:i:2 >/dev/null
  echo "seeded contact $display_name ($id)"
}

seed_contact "Ahmad" "+971500000001"
seed_contact "Sam" "+971500000002"

echo "Emulator setup finished for $pkg"
