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
| 12. Freeze the command eval set | done | 29 Oct 2026 | `8d7feef9` |
| 13. Deterministic Arabic/English fast path, stub messenger, M1 gates, tag `m1` | done | 29 Oct 2026 | `3c2a6115` (`eeae2f0e` gates, `9ff6afd3` denylist pattern, `489ee2af` editor labels, `d57e10ab` telephony) |

## M1.5

| Task | Status | Date | Commit |
|---|---|---|---|
| 14. User-visible Kawkab name, planet mark, companion card | done | 29 Oct 2026 | `1881edde` |
| 15. Performance defaults | done | 29 Oct 2026 | `a6fba56c` |
| 16. Kawkab assistant pipeline as the default chat | done | 29 Oct 2026 | `095468cb` |
| 17. Three-tier routing and the fast path in chat | done | 29 Oct 2026 | `284d0402` |
| 18. Harder command set, frozen then scored | not started | | |
| 19. App maps and macros | not started | | |
| 20. Model candidates, not shipped | not started | | |
| 20b. System assistant, Qwen3 1.7B, and the Llamatik runtime | done | 29 Oct 2026 | `cbbcf491` |
| 20c. Small-model harness rules | not started | | |
| 21. Phone-access setup | not started | | |
| 22. Close out M1.5 and tag `m1.5` | not started | | |

Next task: 18. Freeze the harder command set, score the parser, and only then change it. Then 19, the remaining candidates from 20 (Llamatik is already recorded), 20c, 21, and 22.

Task 20b, 29 Oct 2026: Kawkab can register as the phone's digital assistant. A matched command on the panel goes through the tap gate. Screen text stays off until the owner allows it and asks. The model unloads five seconds after the panel hides, unless a chat is open. Qwen3 1.7B and Llamatik are recorded and not shipped. Scores for Gemma 4 E2B, Gemma 4 E4B and Qwen3 1.7B on v1 and v2 are not measured, so Gemma stays the tool-call default and the image model. Checks: `:app:ktlintCheck`, `:app:detekt`, `:app:verifyFileMap`, `:app:verifyDocLinks`, and `:app:testFossDebugUnitTest` for `AssistantPanelTest`, `PreferenceStorageSnapshotTest`, `PreferenceBackupPolicyTest`, and `SettingsDefaultsTest` passed (`/tmp/gradle-t20b-tests5.log`, BUILD SUCCESSFUL in 52s, 10 assistant tests, 0 failures). `:app:lintFossDebug` passed after the panel colors moved to `toColorInt` (`/tmp/gradle-t20b-lint2.log`, BUILD SUCCESSFUL in 2m 26s). The emulator was not run. No gate is BLOCKED. GitHub Actions was not run.

Task 17, 29 Oct 2026: a line the grammar knows runs before any model load and still goes through the tap gate. Reply, read-last-messages, and screenshot-reminder are fixed skills: the model writes only the words, and a send still asks. An unchanged `read_screen` is a third stuck signal. Native tool calling is off. Checks: `:app:ktlintFormat`, `:app:ktlintCheck`, `:app:detekt`, `:app:generateFileMap`, `:app:verifyFileMap`, and `:app:testFossDebugUnitTest` for `ChatTierRouterTest`, `GraphStuckDetectorTest`, and `ChatHomeViewModelTest` passed (`/tmp/gradle-t17-tests3.log`, BUILD SUCCESSFUL in 22s). `:app:compileFossDebugAndroidTestKotlin` passed, so `TierOneAlarmChatTest` compiles. The emulator was not run. No gate is BLOCKED. GitHub Actions was not run.

Task 16, 29 Oct 2026: the first launch seeds the Kawkab assistant and marks it the default. A new chat starts on that default. The featured onboarding card materialises the same preset. The mood-router companion stays in the assets, marked internal, so the first-run gallery does not list it and it can still be loaded by id. An existing default whose prompts say "You are Mila" is pointed at a new assistant on the next launch. The old pipeline stays in the library. Checks: `:app:ktlintFormat`, `:app:ktlintCheck`, `:catalog:ktlintCheck`, `:app:detekt`, `:app:generateBrowserEditorConstants`, `:app:verifyBrowserEditorConstants`, `:app:verifyFileMap`, `:app:verifyDocLinks`, and `:app:testFossDebugUnitTest` for `InitializeAppUseCaseTest`, `PipelinePresetCatalogValidationTest`, `OnboardingScenarioCatalogTest`, `GoldenTraceCatalogueTest`, and `GoldenTraceTest` passed (`/tmp/gradle-t16-check.log`, BUILD SUCCESSFUL in 1m 11s). The assistant golden trace was recorded with `-PrecordGoldenTraces` and then matched without that flag. No gate is BLOCKED. GitHub Actions was not run. The emulator was not run.

Task 15, 29 Oct 2026: a first install stays on CPU and logs the backend that started. Phones under 16 GiB of RAM pre-select Gemma 4 E2B. The context default is 1,500 tokens, history compression starts at 1,000, and a file read is budgeted at 700. The model unloads after 2 idle minutes and stays loaded between messages. Thinking is off. A tool or JSON tail caps output at 300 tokens. Replies were already streamed. Multi-token prediction stays off because LiteRT-LM 0.17.1 has no switch. NPU stays withdrawn: no phone self-test has shown it starting and beating CPU. Time to first token and tokens per second are not measured. The Settings test-backend row stores both when the owner runs it. Checks: `:app:ktlintFormat`, `:app:ktlintCheck`, `:app:detekt`, `:app:generateFileMap`, `:app:verifyFileMap`, `:app:verifyDocLinks`, and `:app:testFossDebugUnitTest` for `SettingsDefaultsTest`, `AgentIdleManagerTest`, `PrepareInferenceBackendUseCaseTest`, `ReplyBudgetTest`, `NpuTrialPolicyTest`, `DefaultBrainTest`, `OnboardingViewModelTest`, and `LiteRTLlmEngineTest` passed (`/tmp/gradle-t15-test.log`, BUILD SUCCESSFUL in 1m 17s). No gate is BLOCKED. GitHub Actions was not run. The emulator was not run.

Task 14, 29 Oct 2026: notifications, backup copy, onboarding and the status-bar icon say Kawkab. The About screen still credits Knotwork. `BrandNameTest` and the restore semantics line were checked with `:app:testFossDebugUnitTest --tests app.kawkab.android.i18n.BrandNameTest` and `:catalog:testDebugUnitTest --tests app.kawkab.design.screens.settings.backup.BackupContentSemanticsTest`, and ktlint passed. Snapshot goldens for the changed screens were re-recorded. The comparison threshold was not changed. No gate is BLOCKED. GitHub Actions was not run from this machine.

Task 13, 29 Oct 2026: the fast path matched the frozen test split in full: call 20/20, sms 20/20, alarm 15/15, timer 15/15, open 15/15, calendar 15/15. The gate is 90 percent per type. A constant call parser scores under that gate on every type. `./gradlew check :buildSrc:test --offline` passed in 9m 59s: foss 6070 tests, full 6074, catalog 1037, buildSrc 501, 0 failures. Detekt reported 0 findings. No gate is BLOCKED. GitHub Actions was not run from this machine. The emulator was not run. `Tier1FastPathStateTest` is written for CI and was not executed here. Tag `m1` points at this commit.

Task 12, 29 Oct 2026: `evals/commands/v1/commands.jsonl` is 200 commands. SHA-256 `fe82846673bca8d93f604ef3cb4e8af8c3e6f155ce6acd7987b7cf5d500847d7`, recorded in `DECISIONS.md`. No parser is in that commit. No gate is BLOCKED.

Tasks 10 and 11 share `3d3c03e2` because the accessibility service checks the tap guard and the denylist on the same path. Checks on 29 Oct 2026: `./gradlew :app:ktlintCheck :app:detekt :app:testFossDebugUnitTest --tests app.kawkab.android.domain.safety.HandsSafetyTest --tests app.kawkab.android.domain.models.ToolClassificationTest` passed, and `:app:verifyFileMap` and `:app:verifyDocLinks` passed. The full `check` for these tasks is recorded under task 13. No gate is BLOCKED. GitHub Actions was not run from this machine. The emulator was not run.

Task 9 checks, 29 Oct 2026: `./gradlew check :buildSrc:test` passed in 13m 15s: foss 6059 tests, full 6063, catalog 1037, buildSrc 501, 0 failures. Detekt reported 0 findings. The Roborazzi threshold was not changed. GitHub Actions was not run from this machine. No gate is BLOCKED.

Task 8 checks, 29 Oct 2026: `./gradlew :catalog:recordRoborazziDebug` passed in 1m 28s (743 goldens). `./gradlew :catalog:verifyRoborazziDebug` passed in 1m 17s. `./gradlew check :buildSrc:test` passed in 6m 45s: foss 6056 tests, full 6060, catalog 1036, buildSrc 501, 0 failures. The comparison threshold was not changed. GitHub Actions was not run from this machine. No gate is BLOCKED. Tag `m0` points at this commit.

Task 7 checks, 28 Oct 2026, all passed: `ArabicStringKeysTest`, `StubLlmInferenceEngineTest`, `ArabicLayoutDirectionTest`, `:app:compileFossDebugAndroidTestKotlin`, `:app:ktlintCheck`, `:catalog:ktlintCheck`, `:app:detekt`, `:catalog:detekt`, `:app:verifyFileMap`, `:app:verifyComplianceSource`. The emulator script was not run: this machine has no emulator. No gate is BLOCKED.

Task 5 checks, 28 Oct 2026: `./gradlew :app:testFossDebugUnitTest --tests app.kawkab.android.presentation.ui.about.AboutAcknowledgmentsTest :app:ktlintKotlinScriptCheck :app:verifyComplianceSource` passed (BUILD SUCCESSFUL in 35s). The compliance source scan passed (3432 files). No gate is BLOCKED.

Task 6 checks, 28 Oct 2026: `./gradlew :app:assembleFossRelease` passed in 5m 28s. `scripts/check-apk-size.sh` passed: 64380860 bytes, arm64-v8a only, limit 150000000. GitHub Actions was not run here, so the new job is not claimed as a CI pass. No gate is BLOCKED.
