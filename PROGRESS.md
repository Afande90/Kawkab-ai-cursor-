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
| 18. Harder command set, frozen then scored | done | 29 Oct 2026 | `c38bf3d3` |
| 19. App maps and macros | done | 29 Oct 2026 | `67b3a9f4` |
| 20. Model candidates, not shipped | done | 29 Oct 2026 | `594bbeea` |
| 20b. System assistant, Qwen3 1.7B, and the Llamatik runtime | done | 29 Oct 2026 | `76e46ff6` (`7f74cadc` runtime) |
| 20c. Small-model harness rules | done | 29 Oct 2026 | `66779270` |
| 21. Phone-access setup | done | 29 Oct 2026 | `3e1dd845` (`3d27b8ee` tests) |
| 22. Close out M1.5 and tag `m1.5` | not started | | |

Next task: 22. Close out M1.5 and tag `m1.5`, after the latest Check on `main` is green.

Task 20c, 29 Oct 2026: a planner line must be one typed decision. The history keeps the goal and the last 9 steps. Four error tiers each have a recovery. The loop stops after 5 failures in a row, or the same unknown tool 3 times. Successful commands are stored on the Growth screen and can be deleted. The same sentence replays with no model when the grammar does not match. The owner line is first on every local and cloud prompt. Stated facts are injected when any are stored. A biometric check is asked only after the tap, and only when the tool tier and the words both say the step is risky. The hands path still completes on the tap, because the card has no fingerprint prompt yet. Older images in the prompt become a text note capped at 100 tokens. Gecko was not measured. EmbeddingGemma stays. PocketClaw's 1–3 second and 5 second figures are theirs. Checks: `:app:ktlintFormat`, `:app:ktlintCheck`, `:app:detekt`, and `:app:testFossDebugUnitTest` for `HarnessRulesTest` (10 tests, 0 failures), `ChatTierRouterTest`, `HandsSafetyTest`, `LiteRtNodeExecutorTest`, and `CloudLlmNodeExecutorTest` passed (`/tmp/gradle-t20c-tests2.log`, BUILD SUCCESSFUL in 10s). `:app:verifyFileMap` and `:app:verifyDocLinks` passed (`/tmp/gradle-t20c-verify.log`, BUILD SUCCESSFUL in 1s). The emulator was not run. No gate is BLOCKED. GitHub Actions was not run.

Task 20, 29 Oct 2026: FunctionGemma 270M, Parakeet TDT 0.6B v3, Kokoro 82M and Qwen3-ASR 0.6B are recorded with the licence on each model card. None is downloaded or in the APK. Parakeet's card lists 25 European languages and does not list Arabic. FunctionGemma was not fine-tuned. Llamatik stays as recorded in task 20b. Checks: `:app:verifyDocLinks` passed (`/tmp/gradle-t20-docs.log`, BUILD SUCCESSFUL in 1s). No application source changed, so the unit suite was not re-run for this notes-only task. No gate is BLOCKED. GitHub Actions was not run. The emulator was not run.

Task 19, 29 Oct 2026: Phone, Messages, Clock, Calendar, WhatsApp and Gmail each have a stored map and macros. Replay does not call a model. A screen that misses the stored marker stops the replay so the caller can use tier 3. An outbound step is not run until the owner has tapped. Chat still routes a typed line through the three tiers. Checks: `:app:ktlintFormat`, `:app:ktlintCheck`, `:app:detekt`, `:app:testFossDebugUnitTest` for `AppMapReplayTest` (4 tests, 0 failures), `:app:generateFileMap`, `:app:verifyFileMap`, and `:app:verifyDocLinks` passed. The test run is `/tmp/gradle-t19-tests2.log` (BUILD SUCCESSFUL in 47s). The file-map check is `/tmp/gradle-t19-verify.log` (BUILD SUCCESSFUL in 1s). The emulator was not run. No gate is BLOCKED. GitHub Actions was not run.

Task 18, 29 Oct 2026: `evals/commands/v2/commands.jsonl` is 150 commands. SHA-256 `7fc21d3c680d11e2576bb4021028167d131d66c63c6a2673664ea28e56a5a26a`, recorded in `DECISIONS.md`. No v1 line was reused. The parser was not changed. It matches 25 of 150. On the test split that is 10 of 78: call 3/13, sms 1/13, alarm 0/13, timer 2/13, open 2/13, calendar 2/13. The calibration split is 15 of 72. The v1 gate stays 90 percent on the v1 test split, and that test still matches every line. v2 was not given that gate. Checks: `:app:ktlintCheck` and `:app:testFossDebugUnitTest` for `CommandFastPathV2Test` and `CommandFastPathTest` passed (`/tmp/gradle-t18-score2.log`, BUILD SUCCESSFUL in 16s). No gate is BLOCKED. GitHub Actions was not run. The emulator was not run.

Task 21, 29 Oct 2026: after setup finishes, Phone access lists contacts, phone, calendar, notifications, microphone, the accessibility service, battery, and the default-assistant steps, in English and Arabic. The same list is on the privacy screen. A missing answer is off. A call, a text to a name, or a calendar event that lacks its permission returns a sentence, and the chat and the assistant panel show a card whose Grant button asks for it. Skip and Start from scratch still enter the chat immediately. Checks: `:app:ktlintCheck`, `:app:detekt`, `:app:verifyFileMap`, `:app:verifyNoOrphanedKdoc`, both merged-manifest checks, `:app:testFossDebugUnitTest` for `PhoneAccessStatusTest` (7 tests, 0 failures) and `ArabicStringKeysTest`, and `:app:compileFossDebugAndroidTestKotlin` passed (`/tmp/gradle-t21c.log`, BUILD SUCCESSFUL in 51s). The emulator was not run. No gate is BLOCKED. GitHub Actions was not run for this commit yet.

Task 20b, measured 29 Oct 2026: Llamatik 2.0.0 loads a GGUF on the phone. Qwen3 1.7B is a selectable brain. llama.cpp b11982 on this machine scored the v1 and v2 test splits through the tier-3 tool-calling path. Those numbers are a reference runtime, not device. Gemma 4 E4B scored 91/100 and 66/78 and is the tool-call default. Gemma 4 E2B scored 87/100 and 55/78 and stays the onboarding pick below flagship RAM. Qwen3 1.7B scored 61/100 and 41/78. Images stay on Gemma. `cbbcf491` did not measure these scores. The foss arm64 APK on this machine after the signing key was trusted is 134093476 bytes. Check on `b78aa7e2` passed, including the foss arm64 size job. The emulator was not run. No gate is BLOCKED.

The fast path, retuned on the v2 calibration split only (`c758bc5e`, score locked in `45516a7f`), matches the v1 test split 100/100. On the v2 test split it matches 16/78: call 5/13, sms 3/13, alarm 0/13, timer 3/13, open 3/13, calendar 2/13. The calibration split is 72/72. v2 was not edited. The v1 gate was not loosened.

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
