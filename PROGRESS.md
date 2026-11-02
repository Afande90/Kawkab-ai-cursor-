# Progress

Dates are calendar dates in Asia/Dubai. The owner asked for commits on `main` and no pull requests, so each task records its commit instead of a pull-request link.

## M2-V2 continuation — 2 Nov 2026 (current)

Synced exactly as instructed: fetched origin and reset to `origin/main` at `8757a5ee`. No push, tag, publication or project-settings change was attempted. The owner will tag the release. Delivery is the incremental `kawkab-m2v2.bundle` from that base.

All three requested runtime changes are implemented. Foreground Phone (3a) Glyph output uses the official SDK fetched and checksum-verified by Gradle, not a committed AAR. Jev automatic choices use the owner-selected 0.85 boundary without a certificate gate, with private decision/outcome evidence and a full spoken cancel window for model-selected phone actions. CPU smart-turn is packaged by a pinned build-time model download; failures or more than 150 ms automatically use 700 ms silence. See [M2-V2.md](docs/owner-checks/M2-V2.md) for the exact behavior, pins, tests and handset checks.

Per-item local checks passed: Glyph policy tests plus Android lint/static/compliance (5m55s); Jev policy/audit/router/gate/storage tests plus Android lint/static/compliance (6m27s), followed by the model-selected-local-action window and all eight screenshot goldens with lint/static/compliance (7m38s); smart-turn's 24 targeted tests, Android lint/static/compliance, native-library merging and supply-chain pin guard (11m3s). No baseline or fixed test tolerance was expanded. Three independently frozen 80x800 feature references also reproduce to their recorded hashes. Host-only CPU inference confirms the tensor contract; it is not a phone timing or accuracy claim.

Final combined handoff verification passed (BUILD SUCCESSFUL, 2m16s, 90 tasks): selected Glyph/Jev/gate/smart-turn regressions, all eight screenshot goldens, instrumented-test Kotlin compilation, file maps, KDoc, documentation links and size-suppression inventory.

The handset itself and CI for these unpublished local commits are unmeasured. Phone (3a) has white Glyph hardware, so physical breathing/attention patterns mirror the LCD's green/amber states; it cannot emit RGB colours. Earlier write-block, Glyph, certificate and smart-turn status below is historical and superseded by this owner instruction and implementation.

## M2-V local continuation — 2 Nov 2026 (historical)

This entry supersedes the older release-write gate below. The owner pushed the earlier work and explicitly forbids further pushes, tags or publication. Development continued locally from `origin/main` at `b5797b49`. No remote write was attempted in this continuation. The delivery is a Git bundle, not a newly published APK or a claimed green CI run.

The rule-by-rule implementation and remaining gaps are in [M2-V.md](docs/owner-checks/M2-V.md). F4 now uses the requested 400dp LCD, 22/46/32/22sp text, 150sp right-aligned digit with a measured 127.5sp line box, an adjacent 26sp bottom progress bar, external hint and black-shadow STOP key. Every F screen has a bottom-pinned final block and a screenshot test. A measured regression caught Compose ignoring the single-line height style; an explicit height corrected it without loosening the test tolerance.

Completed targeted local runs cover the announcement-complete three-second gate, spoken deletion evidence, banking guards, voice-only ambiguity and learned choices, durable memory/skill history, owner speaker/wake policies, direct SMS, assistant utterance handoff, strict on-phone HTTP/MCP/model processing, English/LTR UI, owner-friendly errors and all eight screenshot screens. The integrated consent, owner-policy, SMS and egress batches passed; the final F4 geometry/screenshot/static batch passed in 1m27s. Python wake-pipeline tests: 7 passed. Decision calibration/mutation tests: 4 passed. The native adapter and standalone phone fixture reader pass host C++17 syntax checking against matching Llamatik headers; that does not verify Android linking or model inference.

Final local invocation: **BUILD SUCCESSFUL, 9m12s, 150 tasks**. Passed: `:app:lintFossDebug`, app/catalog `ktlintCheck` and `detekt`, `:app:compileFossDebugAndroidTestKotlin`, `verifyFileMap`, `verifyNoOrphanedKdoc`, `verifyDocLinks`, all three source/dependency/domain compliance checks, targeted `ArabicStringKeysTest` and `PocketErrorsTest`, and all eight `PocketDeviceSnapshotTest` cases in golden-verification mode. Android lint has 27 hints and its existing baseline; no baseline was expanded. Fixed the new Compose parameter-order issues, obsolete assistant strings and URI API warning. New source-folder descriptions were added without raising the file-map ratchet. The full Check and emulator are reserved for CI, as required by AGENTS.md.

Remaining gates are explicit: physical Glyph integration has no established SDK redistribution licence here; generic wake training lacks the licensed Sudanese voice and real negative corpus; speaker accuracy, Jev precision/parity/latency and real assistant/call/SMS behavior need the phone; native cross-compilation needs the pinned NDK; smart-turn and a listening-selected natural Arabic/English voice are not completed. The complete wake training pipeline and actual Android verdict-readout/calibration commands are supplied. No fabricated metric or production decision certificate is included.

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
| 21. Phone-access setup | done | 30 Oct 2026 | `3e1dd845` (`3d27b8ee` tests, `5b2ed210` extras guard and Continue, `ff0e9901` lint) |
| 22. Close out M1.5 and tag `m1.5` | done | 30 Oct 2026 | this commit, tag `m1.5` |

## M1.6

| Task | Status | Date | Commit |
|---|---|---|---|
| 23f. Engine window back to 4,096, allocation recovery, runtime labels | done | 31 Oct 2026 | `488dcfd8` (tests `de8ce71d`, `9c2c5cd0`) |
| 23d. Accessibility work per event; tab-switch benchmark (gate BLOCKED) | done | 31 Oct 2026 | `26b4eeac` (tests `dea53681`, benchmark `d59c0096`, `7a8e1caf`) |
| 23j. Call flow: chips, word matching, App info, dialer fallback, 60 s limit | done | 31 Oct 2026 | `ab579a21` (tests `8cd55511`) |
| 23h. Stray quotes, tool names as text, re-ask | done | 31 Oct 2026 | `154fa83b` (tests `9e62489b`) |
| 23i. LOCAL tier with Undo, readable cards, time format | done | 31 Oct 2026 | `bc69ddec` (tests `e14c5918`) |

| 23g. One reply in the user's language; shorter prompt (model scoring BLOCKED) | done | 1 Nov 2026 | `ed9a4a38`, `075f6e50`, `f4f95034` (set `e9b0c2c4`, tests `219397ec`) |
| 23e. GGUF active only after a load; Gemma fallback | done | 1 Nov 2026 | `6dfdbc81` (tests `2c0041fc`) |
| 23k. Real assistant role, tile, shortcut, power-button step | done | 1 Nov 2026 | `229d2f73`, `a7884058` (tests `31398016`) |
| 23l. Conversation mode, offline speech in and out | done | 1 Nov 2026 | `5636a43b` (tests `05e4e2b3`) |
| 23. Phone access easy to find and read | done | 1 Nov 2026 | `22cddc4e`, `2dcde7a4` (tests `2d2d870c`, `1b9b0e27`) |
| 23b. Floating accessibility button opens the panel | done | 1 Nov 2026 | `0a5c104e` (tests `6437e382`) |
| 23c. Mic dictates offline | done | 1 Nov 2026 | `04c9bbd7`, `2356b8c3`, `e1f0bea5` (tests `5b773dd3`) |
| 24. Planet mark replaces the old icon | done | 1 Nov 2026 | `790a4a3e` (tests `50993d31`) |
| 25. Onboarding shows this phone's model size | done | 1 Nov 2026 | `fa5247ff` (tests `2cc14113`) |
| 25b. One fixed signing key (secrets BLOCKED: owner steps in docs/owner-checks/M1.6.md) | done, BLOCKED part | 1 Nov 2026 | `c79851dc` |
| 25c. Pre-release APK per green push; Check for updates on About | done | 1 Nov 2026 | `e1e6475c` (tests `4bf4b478`) |
| 26. Close out: green Check, owner check, tag `m1.6` | done | 1 Nov 2026 | this commit |

Tag `m1.6` is on `9b2bc801` locally; pushing tags is refused from the build machine (HTTP 403), see docs/owner-checks/M1.7.md.

## M1.7

| Task | Status | Date | Commit |
|---|---|---|---|
| 27. Wake word "Kawkab" (recall/false-wake gate BLOCKED) | done | 1 Nov 2026 | `418c402c`, `65739f34` (set `85479370`, tests `6c296e6c`, `5d66f84c`, `24efb149`, script `05b2a1c7`) |
| 28. Continuous conversation (Whisper and sherpa voice BLOCKED, Android fallbacks) | done | 1 Nov 2026 | `a594ed83` (tests `3b862af2`) |
| 29. Hands-free actions within the safety rules | done | 1 Nov 2026 | `c5f01168`, `4c9abc51` (tests `c3540ca2`) |
| 30. Close out: green Check, owner check, tag `m1.7` | done | 1 Nov 2026 | this commit |

Next task: finish M1.8 close-out, then M2-V under the owner's vision reset. Do not start later milestones.

Tasks 23f, 23d, 23j, 23h and 23i, 31 Oct 2026: the GitHub Check passed on `532d80c6` (run 41252985203), which contains all five. Instrumented on `532d80c6` (run 41252985223): Full API 36, Foss API 36 and Full API 34 passed; the tab-switch benchmark job failed (see DECISIONS.md, 23d BLOCKED). Local checks for each task: `:app:ktlintCheck`, `:app:detekt`, `:app:verifyFileMap`, `:app:verifyNoOrphanedKdoc` and the targeted `:app:testFossDebugUnitTest` classes named in each test commit. BLOCKED gates: 23f emulator reply with E2B (hardware), 23d median frame time on the emulator.



Task 20c, 29 Oct 2026: a planner line must be one typed decision. The history keeps the goal and the last 9 steps. Four error tiers each have a recovery. The loop stops after 5 failures in a row, or the same unknown tool 3 times. Successful commands are stored on the Growth screen and can be deleted. The same sentence replays with no model when the grammar does not match. The owner line is first on every local and cloud prompt. Stated facts are injected when any are stored. A biometric check is asked only after the tap, and only when the tool tier and the words both say the step is risky. The hands path still completes on the tap, because the card has no fingerprint prompt yet. Older images in the prompt become a text note capped at 100 tokens. Gecko was not measured. EmbeddingGemma stays. PocketClaw's 1–3 second and 5 second figures are theirs. Checks: `:app:ktlintFormat`, `:app:ktlintCheck`, `:app:detekt`, and `:app:testFossDebugUnitTest` for `HarnessRulesTest` (10 tests, 0 failures), `ChatTierRouterTest`, `HandsSafetyTest`, `LiteRtNodeExecutorTest`, and `CloudLlmNodeExecutorTest` passed (`/tmp/gradle-t20c-tests2.log`, BUILD SUCCESSFUL in 10s). `:app:verifyFileMap` and `:app:verifyDocLinks` passed (`/tmp/gradle-t20c-verify.log`, BUILD SUCCESSFUL in 1s). The emulator was not run. No gate is BLOCKED. GitHub Actions was not run.

Task 20, 29 Oct 2026: FunctionGemma 270M, Parakeet TDT 0.6B v3, Kokoro 82M and Qwen3-ASR 0.6B are recorded with the licence on each model card. None is downloaded or in the APK. Parakeet's card lists 25 European languages and does not list Arabic. FunctionGemma was not fine-tuned. Llamatik stays as recorded in task 20b. Checks: `:app:verifyDocLinks` passed (`/tmp/gradle-t20-docs.log`, BUILD SUCCESSFUL in 1s). No application source changed, so the unit suite was not re-run for this notes-only task. No gate is BLOCKED. GitHub Actions was not run. The emulator was not run.

Task 19, 29 Oct 2026: Phone, Messages, Clock, Calendar, WhatsApp and Gmail each have a stored map and macros. Replay does not call a model. A screen that misses the stored marker stops the replay so the caller can use tier 3. An outbound step is not run until the owner has tapped. Chat still routes a typed line through the three tiers. Checks: `:app:ktlintFormat`, `:app:ktlintCheck`, `:app:detekt`, `:app:testFossDebugUnitTest` for `AppMapReplayTest` (4 tests, 0 failures), `:app:generateFileMap`, `:app:verifyFileMap`, and `:app:verifyDocLinks` passed. The test run is `/tmp/gradle-t19-tests2.log` (BUILD SUCCESSFUL in 47s). The file-map check is `/tmp/gradle-t19-verify.log` (BUILD SUCCESSFUL in 1s). The emulator was not run. No gate is BLOCKED. GitHub Actions was not run.

Task 18, 29 Oct 2026: `evals/commands/v2/commands.jsonl` is 150 commands. SHA-256 `7fc21d3c680d11e2576bb4021028167d131d66c63c6a2673664ea28e56a5a26a`, recorded in `DECISIONS.md`. No v1 line was reused. The parser was not changed. It matches 25 of 150. On the test split that is 10 of 78: call 3/13, sms 1/13, alarm 0/13, timer 2/13, open 2/13, calendar 2/13. The calibration split is 15 of 72. The v1 gate stays 90 percent on the v1 test split, and that test still matches every line. v2 was not given that gate. Checks: `:app:ktlintCheck` and `:app:testFossDebugUnitTest` for `CommandFastPathV2Test` and `CommandFastPathTest` passed (`/tmp/gradle-t18-score2.log`, BUILD SUCCESSFUL in 16s). No gate is BLOCKED. GitHub Actions was not run. The emulator was not run.

Task 21, 30 Oct 2026: after setup finishes, Phone access lists contacts, phone, calendar, notifications, microphone, the accessibility service, battery, and the default-assistant steps, in English and Arabic. The same list is on the privacy screen. A missing answer is off. A call, a text to a name, or a calendar event that lacks its permission returns a sentence, and the chat and the assistant panel show a card whose Grant button asks for it. Skip and Start from scratch still enter the chat immediately. Checks on the feature commit: `:app:ktlintCheck`, `:app:detekt`, `:app:verifyFileMap`, `:app:verifyNoOrphanedKdoc`, both merged-manifest checks, `:app:testFossDebugUnitTest` for `PhoneAccessStatusTest` (7 tests, 0 failures) and `ArabicStringKeysTest`, and `:app:compileFossDebugAndroidTestKotlin` passed (`/tmp/gradle-t21c.log`, BUILD SUCCESSFUL in 51s). Check on `5fe68a9f` failed `ExportedEntryExtrasGuardTest`. Check on `5b2ed210` failed lint (`BatteryLife` and `ModifierParameter`), and Instrumented that run failed while downloading the emulator package, before any test. Those two red runs are not passes. Check on `ff0e9901` passed (run 40950389769), including the foss arm64 APK of 134122900 bytes. Instrumented on `ff0e9901` passed (run 40950389718). No gate is BLOCKED.

Task 22, 30 Oct 2026: `docs/owner-checks/M1.5.md` says what to try on the phone. The full check is the green GitHub Check on `ff0e9901` (`./gradlew check` and the foss arm64 size job). The artifact `kawkab-foss-arm64-release` is uploaded from that run. Tag `m1.5` points at this commit. The full check was not re-run on this machine after that green Check. No gate is BLOCKED.

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


## M1.8 continuation — 2 Nov 2026

The workspace arrived without a checkout. Main was cloned, pulled with `--ff-only`, and inspected with `git log -30`, `git status`, `git tag`, remote branches and open PRs. The working tree was clean at `9b64c54f`. The two old translation branches have no commits outside main. The only open PR is dependency update #2; it is unrelated to this hotfix.

| Task | Status | Evidence |
|---|---|---|
| 31. Minified release launch in Check | existing implementation, green baseline | `7fd312cb`; Check run 41366560458 on `9b64c54f` passes API 36 release launch |
| 32. JNI keep rules, isolated wake process, safe mode | existing implementation plus local countdown follow-up | `b62bfa70`; original tests `9b64c54f` |
| 33. Local crash report | implemented; personal abort-message follow-up committed locally | native messages and exit descriptions omitted |
| 34. Power button | existing role check and guide; settings deep-link follow-up committed locally | exported power/gesture settings where present |
| 34b. Fast calling under reset rules 1 and 2 | local follow-ups; CI pending | spoken/typed cancel window, no chooser fallback, removable aliases in Growth |
| 35. Close-out, tags, published updated APK | BLOCKED | Git and REST writes HTTP 403; no completion tag |

Baseline Check, Instrumented and pre-release publishing passed on `9b64c54f`; this does not verify the local continuation. Detailed release status and APK provenance are in docs/owner-checks/M1.8.md. Local tags `m1.6` (`9b2bc801`) and `m1.7` (`ed0af57d`) were restored, but GitHub rejected their push. M2-V has not been started because the unfinished M1.8 release gate comes first.


Continuation local checks: **79 targeted tests passed, 0 failed, 0 errors, 0 skipped**; foss debug app and test sources compiled. app ktlintCheck, detekt, verifyFileMap, verifyNoOrphanedKdoc, verifyComplianceSource and verifyDocLinks passed in the final invocation (BUILD SUCCESSFUL, 2m 7s). No full local Check or emulator run. Follow-up CI and publication remain BLOCKED by HTTP 403. The exact test classes and recovery bundle are recorded in docs/owner-checks/M1.8.md.
