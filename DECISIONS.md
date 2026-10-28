# Decisions

When the spec is silent, the choice is written here with its date and the work continues.

Dates are calendar dates in Asia/Dubai.

## 2026-10-28 — Knotwork import pin

Imported `alexeyw/knotwork` tag `v0.12.1`.

- Annotated tag: `61a73368b4af519cd09d95296d6ee28762afb073`
- Commit: `93bca14d4774a42960a24cb881198389005831e7`
- History: kept, by merging unrelated histories (2108 upstream commits) into this repository.

## 2026-10-28 — README at import

`README.md` is the upstream Knotwork README with a short Kawkab banner at the top. The banner points at `KAWKAB_SPEC.md` and `AGENTS.md`. The upstream build notes stay in place until a later task replaces them with the bilingual Kawkab README (spec §15).

## 2026-10-28 — Pinned toolchain

Taken from the imported tree, not loosened.

| Piece | Version |
|---|---|
| JDK | 21 (daemon toolchain in `gradle/gradle-daemon-jvm.properties`; this machine has OpenJDK 21.0.12.1). Production bytecode target remains JVM 17. |
| Gradle | 9.8.0 (wrapper) |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.4.20 |
| compileSdk | `release(37)` |
| minSdk | 34 |
| targetSdk | 37 |
| NDK | 28.2.13676358 (release builds only; not required for `check`) |
| Robolectric test SDK | 36 |

`sdkmanager` has no package named `platforms;android-37`. The stable platforms installed for compileSdk 37 are `platforms;android-37.0` and `platforms;android-37.2`, plus `platforms;android-36` for Robolectric, `build-tools;36.0.0`, `build-tools;37.0.0` and `platform-tools`. The command-line tools zip named in `AGENTS.md` (`commandlinetools-linux-11076708_latest.zip`) downloaded and ran.

The NDK is not installed yet. Debug builds and `./gradlew check` do not need it. A release APK (task 6) does.

## 2026-10-28 — Planning docs and the doc-link gate

Knotwork's `:app:verifyDocLinks` treats an inline-code span that looks like a repository path as a claim that the file exists today. `KAWKAB_SPEC.md` names files later tasks create (`scripts/ci-local.sh`, `scripts/emulator-setup.sh`, `docs/owner-checks/models.md`, `docs/architecture-kawkab.md`, `docs/selftest.md`). Those spans failed the gate on the import.

`KAWKAB_SPEC.md`, `AGENTS.md`, `PROGRESS.md` and `DECISIONS.md` are added to `codePathExemptions` in `app/build.gradle.kts`, next to `CHANGELOG.md`. Their Markdown links are still checked. The spec is not rewritten to satisfy the gate.

## 2026-10-28 — Task 1 build results

- `:app:assembleFossDebug`: BUILD SUCCESSFUL in 5m 33s.
- `./gradlew check :buildSrc:test`: failed only on `:app:verifyDocLinks` (the seven paths above). After the exemption, `:app:verifyDocLinks` passed: 532 internal links and 243 inline-code paths across 38 documents.
- Unit tests, from the JUnit XML of that `check` run, all with 0 failures and 0 errors:
  - `testFossDebugUnitTest`: 6055 tests
  - `testFullDebugUnitTest`: 6059 tests
  - `catalog` `testDebugUnitTest`: 1035 tests
  - `buildSrc` `test`: 501 tests

No gate is BLOCKED.
