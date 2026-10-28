#!/usr/bin/env bash
# The same gates as the GitHub Actions check workflow, for a machine that has
# run out of CI minutes (spec section 0.7). Emulator tests are not included:
# this VM has no emulator. scripts/emulator-setup.sh is the Tier 1 setup.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

./gradlew check :buildSrc:test
./gradlew :app:assembleFossRelease
"$root/scripts/check-apk-size.sh" app/build/outputs/apk/foss/release/app-foss-release.apk
