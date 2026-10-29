# Progress

Dates are calendar dates in Asia/Dubai. The owner asked for commits on `main` and no pull requests, so each task records its commit instead of a pull-request link.

## M0

| Task | Status | Date | Commit |
|---|---|---|---|
| 1. Import Knotwork v0.12.1, build, create this file and `DECISIONS.md` | done | 28 Oct 2026 | `10a9f54c` (import), `7cffff68` (notes and doc-link exemption) |
| 2. Rename to `app.kawkab.android`, app name Kawkab (كوكب) | done | 28 Oct 2026 | `a7b775ea` |
| 3. Remove the extra hosted provider | done | 28 Oct 2026 | `aec4bb24` |
| 4. Three compliance checks | done | 28 Oct 2026 | `c9ff8b78` |
| 5. Licence audit, `THIRD_PARTY.md`, `NOTICE`, About credits | done | 28 Oct 2026 | `44c481a2` |
| 6. GitHub Actions: build, unit tests, compliance, APK size, `foss` arm64 artifact | done | 28 Oct 2026 | `eda85647` |
| 7. Arabic and RTL tests, emulator setup, Tier 1 harness with a stub engine | done | 28 Oct 2026 | `8213ab74` (`2a6a97ce` catalog strings, `c59fdac8` seven app files) |
| 8. Kawkab restyle, then tag `m0` | done | 29 Oct 2026 | `8f4c9c1b` |

## M1

| Task | Status | Date | Commit |
|---|---|---|---|
| 9. `OUTBOUND` risk tier, tool classification table and test | done | 29 Oct 2026 | `d8d162c3` |
| 10. OpenDroid accessibility as `LocalToolExecutor`s, plus the tap guard | done | 29 Oct 2026 | `3d3c03e2` |
| 11. Denylist (three layers) and the activity log | done | 29 Oct 2026 | `3d3c03e2` |
| 12. Freeze the command eval set | not started | | |
| 13. Deterministic Arabic/English fast path, stub messenger, M1 gates, tag `m1` | not started | | |

Next task: 12.

Tasks 10 and 11 share `3d3c03e2` because the accessibility service checks the tap guard and the denylist on the same path. Checks on 29 Oct 2026: `./gradlew :app:ktlintCheck :app:detekt :app:testFossDebugUnitTest --tests app.kawkab.android.domain.safety.HandsSafetyTest --tests app.kawkab.android.domain.models.ToolClassificationTest` passed, and `:app:verifyFileMap` and `:app:verifyDocLinks` passed. A full `check` is still to run before `m1`. No gate is BLOCKED. GitHub Actions was not run from this machine. The emulator was not run.

Task 9 checks, 29 Oct 2026: `./gradlew check :buildSrc:test` passed in 13m 15s: foss 6059 tests, full 6063, catalog 1037, buildSrc 501, 0 failures. Detekt reported 0 findings. The Roborazzi threshold was not changed. GitHub Actions was not run from this machine. No gate is BLOCKED.

Task 8 checks, 29 Oct 2026: `./gradlew :catalog:recordRoborazziDebug` passed in 1m 28s (743 goldens). `./gradlew :catalog:verifyRoborazziDebug` passed in 1m 17s. `./gradlew check :buildSrc:test` passed in 6m 45s: foss 6056 tests, full 6060, catalog 1036, buildSrc 501, 0 failures. The comparison threshold was not changed. GitHub Actions was not run from this machine. No gate is BLOCKED. Tag `m0` points at this commit.

Task 7 checks, 28 Oct 2026, all passed: `ArabicStringKeysTest`, `StubLlmInferenceEngineTest`, `ArabicLayoutDirectionTest`, `:app:compileFossDebugAndroidTestKotlin`, `:app:ktlintCheck`, `:catalog:ktlintCheck`, `:app:detekt`, `:catalog:detekt`, `:app:verifyFileMap`, `:app:verifyComplianceSource`. The emulator script was not run: this machine has no emulator. No gate is BLOCKED.

Task 5 checks, 28 Oct 2026: `./gradlew :app:testFossDebugUnitTest --tests app.kawkab.android.presentation.ui.about.AboutAcknowledgmentsTest :app:ktlintKotlinScriptCheck :app:verifyComplianceSource` passed (BUILD SUCCESSFUL in 35s). The compliance source scan passed (3432 files). No gate is BLOCKED.

Task 6 checks, 28 Oct 2026: `./gradlew :app:assembleFossRelease` passed in 5m 28s. `scripts/check-apk-size.sh` passed: 64380860 bytes, arm64-v8a only, limit 150000000. GitHub Actions was not run here, so the new job is not claimed as a CI pass. No gate is BLOCKED.
