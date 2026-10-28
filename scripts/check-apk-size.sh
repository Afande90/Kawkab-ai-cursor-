#!/usr/bin/env bash
# Fail unless the foss release APK is at most 150 MB and contains only arm64-v8a.
# 150 MB is 150000000 bytes (decimal), recorded in DECISIONS.md.
set -euo pipefail

apk="${1:?path to the foss release APK}"
limit=$((150 * 1000 * 1000))

if [[ ! -f "$apk" ]]; then
  echo "APK not found: $apk" >&2
  exit 1
fi

bytes="$(stat -c%s "$apk")"
echo "APK $apk is $bytes bytes (limit $limit)."
if (( bytes > limit )); then
  echo "APK is over 150 MB." >&2
  exit 1
fi

entries="$(unzip -Z1 "$apk")"
if ! grep -q 'lib/arm64-v8a/' <<<"$entries"; then
  echo "APK has no lib/arm64-v8a/ entries." >&2
  exit 1
fi
other="$(grep -E '^lib/[^/]+/' <<<"$entries" | sed 's|^lib/\([^/]*\)/.*|\1|' | sort -u | grep -v '^arm64-v8a$' || true)"
if [[ -n "$other" ]]; then
  echo "APK contains ABIs other than arm64-v8a:" >&2
  echo "$other" >&2
  exit 1
fi

echo "APK size and ABI check passed."
