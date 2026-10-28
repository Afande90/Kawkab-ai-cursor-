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
| 8. Kawkab restyle, then tag `m0` | not started | | |

## M1

| Task | Status | Date | Commit |
|---|---|---|---|
| 9. `OUTBOUND` risk tier, tool classification table and test | not started | | |
| 10. OpenDroid accessibility as `LocalToolExecutor`s, plus the tap guard | not started | | |
| 11. Denylist (three layers) and the activity log | not started | | |
| 12. Freeze the command eval set | not started | | |
| 13. Deterministic Arabic/English fast path, stub messenger, M1 gates, tag `m1` | not started | | |

Next task: 8.

Task 7 checks, 28 Oct 2026, all passed: `ArabicStringKeysTest`, `StubLlmInferenceEngineTest`, `ArabicLayoutDirectionTest`, `:app:compileFossDebugAndroidTestKotlin`, `:app:ktlintCheck`, `:catalog:ktlintCheck`, `:app:detekt`, `:catalog:detekt`, `:app:verifyFileMap`, `:app:verifyComplianceSource`. The emulator script was not run: this machine has no emulator. No gate is BLOCKED.

Task 5 checks, 28 Oct 2026: `./gradlew :app:testFossDebugUnitTest --tests app.kawkab.android.presentation.ui.about.AboutAcknowledgmentsTest :app:ktlintKotlinScriptCheck :app:verifyComplianceSource` passed (BUILD SUCCESSFUL in 35s). The compliance source scan passed (3432 files). No gate is BLOCKED.

Task 6 checks, 28 Oct 2026: `./gradlew :app:assembleFossRelease` passed in 5m 28s. `scripts/check-apk-size.sh` passed: 64380860 bytes, arm64-v8a only, limit 150000000. GitHub Actions was not run here, so the new job is not claimed as a CI pass. No gate is BLOCKED.
