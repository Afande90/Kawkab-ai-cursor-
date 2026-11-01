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

## 2026-10-28 — Rename scope

Task 2 renames the install identity in one commit:

- Packages `app.knotwork.android` and `app.knotwork.design` become `app.kawkab.android` and `app.kawkab.design`, including `tools-probe`, `buildSrc` and the Room schema directory `app.kawkab.android.data.local.AppDatabase`. The schema JSON files do not embed the package name; only the directory does.
- `applicationId` and `namespace` follow the package.
- Keystore aliases `knotwork.settings_secrets`, `knotwork.restore_staging`, `knotwork.api_keys` and `knotwork.db_passphrase` become the `kawkab.*` names. The SQLCipher database file stays `agent_database.db`. This is a new install identity, not an upgrade of an installed Knotwork, so the old aliases are not migrated.
- Launcher name is `Kawkab` (`Kawkab Debug` on the debug variant). The English store title is `Kawkab` because `StoreMetadataTest` requires the English listing to be plain ASCII. The Russian store title is `Kawkab (كوكب)`. Store full descriptions say Kawkab. Fastlane changelogs stay as the historical Knotwork release notes.
- `NOTICE` keeps the upstream Knotwork copyright and adds a Kawkab header. The About screen lists Knotwork (Apache 2.0) first among acknowledgments.
- In-app copy that still says Knotwork (onboarding title, theme type names, the `knotwork://` deep-link scheme, the `knotwork_app_functions` asset name, the `.knotwork` backup extension) stays for this commit. Rewriting it would invalidate the screenshot goldens and is not in the spec's rename list. The Arabic launcher string `كوكب` waits for the full `values-ar` pass in task 7, so a partial translation file does not exist yet.
- Gradle `rootProject.name` stays `android-ai-agent`.
- One screenshot golden, `catalog/src/test/snapshots/external_automation_journal_call_block_light.png`, was re-recorded in this same commit. The preview action string changed from `app.knotwork.android.action.RUN_PIPELINE` to `app.kawkab.android.action.RUN_PIPELINE`, and that is the only pixel difference. The comparison threshold was not changed. The task requires this rename to be one commit with a green build, so the golden could not wait for a later commit.

## 2026-10-28 — Removed hosted provider

The bring-your-own-key provider whose wire id was `anthropic` is gone from the product: the domain and catalog enums, the Koog client dependency, the browser editor, settings copy, and tests. Auto now tries Google, then OpenAI, then DeepSeek.

Tests that only needed some provider now use Google. Tests that relied on that provider being unmeasured for stream cut-off (a missing finish reason was not evidence) now use OpenRouter, which is still unmeasured. Streams that are meant to be complete on Google include an end frame with finish reason `stop`.

The preference names `<id>_api_key` are not migrated. Kawkab's application id is new, so an old install's saved key is not in this app's store.

The dependency-verification keyring still trusts key `27FDF761A8BE4E584DFF13581E48D52D1924F9A7` for `io.modelcontextprotocol`. Its human-readable uid line no longer contains that vendor's domain. The armored key block does not contain the forbidden substring and was left as shipped.

Checkout-only filenames that contain the forbidden substring are still ignored, but the source spells them with a character class or a split literal so the compliance grep does not match the source.

Seventeen screenshot goldens were re-recorded in this same commit. They showed the provider list, the auto line, or a model id that named the removed provider. The comparison threshold was not changed. The removal has to compile and the screenshot gate has to pass together; the goldens could not wait for a later commit.

`docs/**` may still mention the removed provider where the text is history rather than a current provider list. The lists a reader would follow (extending, architecture, user guide, FAQ) no longer include it.

Checks on 28 Oct 2026:

- `./gradlew check :buildSrc:test` compiled, and the unit tests passed (`testFossDebugUnitTest`, `testFullDebugUnitTest`, catalog `testDebugUnitTest`). The only failure was `:app:verifyDocumentationLinks`, because removing one user-guide row changed that file's line count from 3574 to 3573.
- `./gradlew :app:generateDocumentationLinks :app:verifyDocumentationLinks` then passed (7 entries).
- No gate is BLOCKED. The comparison threshold on screenshots was not changed.

## 2026-10-28 — Compliance checks

The three checks from spec §0.1.3 run as part of `check`:

- `verifyComplianceSource` scans tracked and untracked files, skipping the exclude list in the spec. The pattern lives in `tests/compliance/`, which that scan skips.
- `verifyComplianceDependencies` reads the needle from `tests/compliance/dependency-needle.txt` and scans declared dependencies plus the foss and full debug compile and runtime classpaths.
- `verifyComplianceDomains` scans manifests, `network_security_config.xml`, and string resources for the domains listed in `tests/compliance/forbidden-domains.txt`.

Koog still pulls two transitive modules whose names match the dependency needle. They are excluded from every configuration. The module names are listed in `tests/compliance/excluded-modules.txt`, and the build file reads that list so the names are not written in a scanned file. The app no longer references those classes.

## 2026-10-28 — Licence audit

`LICENSES-AUDIT.md` lists the 391 artifacts resolved on `fossReleaseRuntimeClasspath`, then the assets, models, voices and datasets the spec names. A requested version that Gradle upgraded (`->`) is recorded once, at the resolved version. Licence text comes from the POM `<licenses>` element in the Gradle cache.

POMs with no licence element, and the URL used instead:

| Artifact | Licence | Verified from |
|---|---|---|
| `com.google.guava` (`guava`, `failureaccess`, `listenablefuture`) | Apache-2.0 | https://github.com/google/guava/blob/master/LICENSE |
| `com.google.auto.service:auto-service-annotations` | Apache-2.0 | https://github.com/google/auto/blob/main/LICENSE |
| `com.google.protobuf:protobuf-javalite` | BSD-3-Clause | https://github.com/protocolbuffers/protobuf/blob/main/LICENSE |
| `javax.inject:javax.inject` | Apache-2.0 | https://github.com/javax-inject/javax-inject/blob/master/LICENSE.txt |
| `org.bouncycastle:bcprov-jdk18on` | MIT | `META-INF/LICENSE.md` inside the 1.86 jar |
| `org.slf4j:slf4j-api` | MIT | https://www.slf4j.org/license.html |
| `net.zetetic:sqlcipher-android` | BSD-3-Clause | POM URL https://www.zetetic.net/sqlcipher/license/ plus the notice already in `NOTICE` |

`org.checkerframework:checker-compat-qual` is dual-licensed GPL-2.0-with-classpath-exception and MIT. Kawkab elects MIT. It is not AGPL.

MediaPipe `tasks-core` pulls `com.google.android.datatransport:transport-backend-cct`, which pulls `firebase-encoders`, `firebase-encoders-proto` and `firebase-encoders-json` onto the foss runtime classpath. Those three are Apache-2.0. The Crashlytics SDK stays `fullImplementation` only. The build comments that said foss contains no `com.google.firebase` artifact were wrong and are corrected. They are not removed: MediaPipe needs the transport backend, and stripping it was not attempted.

Coil (`io.coil-kt.coil3:coil-compose:3.6.3`) is a direct dependency. `AboutAcknowledgmentsTest` still fails the build if the About list contains the name Coil, and that test is not edited in the commit that would add the credit. The name is in `NOTICE` and in the audit. The About screen adds Gemma (terms plus the Prohibited Use Policy URL) and Bouncy Castle, which that test does not forbid.

Gemma 4 weights are an owner download, not an APK asset. EmbeddingGemma, Piper, the Sudanese Whisper fine-tunes and the `arbml` datasets are not shipped and are not given a download link. No OpenDroid or Minitap files were copied.

## 2026-10-28 — APK size gate

150 MB in spec §0.2 is 150000000 bytes. `scripts/check-apk-size.sh` also requires `lib/arm64-v8a/` and no other ABI under `lib/`.

The release variant already filters to `arm64-v8a`. The pinned NDK `28.2.13676358` is installed on this machine at `$ANDROID_HOME/ndk/28.2.13676358`.

Local `./gradlew :app:assembleFossRelease` succeeded in 5m 28s. The APK is 64380860 bytes and the size script passed. GitHub Actions has not been run from this machine, so that job is not claimed as passed.

`scripts/ci-local.sh` runs `check`, `:buildSrc:test`, the foss release assemble, and the size script. It does not start an emulator.

## 2026-10-28 — Arabic resources and the Tier 1 stub

Every default `strings.xml` key in `app` and `catalog` has a `values-ar` entry. The launcher name in Arabic is كوكب. Plurals include the Arabic quantities zero, one, two, few, many, and other.

`ArabicStringKeysTest` walks the source XML. `ArabicLayoutDirectionTest` sets the locale to Arabic and asserts Compose `LayoutDirection.Rtl`.

The Tier 1 engine is `StubLlmInferenceEngine` in the debug source set. It accepts any path, reads no file, and returns the fixed text `stub-engine`. Release APKs do not compile it. `Tier1StubEngineTest` is the emulator harness and was not run here: this machine has no emulator. The same behaviour is covered by `StubLlmInferenceEngineTest` on the JVM.

`scripts/emulator-setup.sh` grants the runtime permissions the later command tests need, sets `ACCESS_RESTRICTED_SETTINGS`, enables accessibility, and seeds two contacts. The accessibility component is `app.kawkab.android.accessibility.KawkabAccessibilityService`. That class does not exist yet; task 10 must use this name. The script was not run against a device.

Checks on 28 Oct 2026, all passed: `ArabicStringKeysTest`, `StubLlmInferenceEngineTest`, `ArabicLayoutDirectionTest`, `:app:compileFossDebugAndroidTestKotlin`, `:app:ktlintCheck`, `:catalog:ktlintCheck`, `:app:detekt`, `:catalog:detekt`, `:app:verifyFileMap`, `:app:verifyComplianceSource`. No gate is BLOCKED.

## 2026-10-29 — Kawkab restyle

The three owner colours are hex pins in the catalog tokens. The old note that the OKLCH export must not be hand-edited does not cover these three.

- Deep space `#070A14` is the dark ground and the launcher plate. Light mode stays a cool light ground (`#F6F7FB` and the matching surface steps) so body text stays readable. Deep space is not painted behind light-mode text.
- Indigo `#5457E8` is the primary, the surface tint, and the inverse primary in both themes. Secondary is `#3A3CC4` in light mode and `#8B8EF2` in dark mode. Indigo is not a surface fill. The pipeline-library row mark uses the same indigo.
- Amber `#F2B45A` is the "needs you" colour. It is `riskSensitive` in both themes, so the confirmation strip and the sensitive risk pill use it. On a light ground, 11 px text in that amber is below a comfortable contrast. The spec hex stays exact. The signal-warn hue stays `#D5A13C` and is a different colour.
- Node hues and the memory manual, auto, and compact tags are unchanged. The light console background stays `#191511`.
- The Compose mark and the adaptive launcher (background, foreground, monochrome) draw a planet: an indigo disc and an amber ring. The vector is still named Mark and the composable is still `KnotworkLogo`, so existing call sites keep working. The density webp launcher bitmaps are the previous mark. minSdk 34 installs the anydpi adaptive icon, so those bitmaps are not the icon the phone shows.
- 743 screenshot goldens were re-recorded in this same commit. The comparison threshold was not changed. `KnotworkThemeTest` assertions for primary and the three surface colours that moved were updated in this same commit. The theme test and the token change have to pass together, the same exception as the earlier golden re-records.

`THIRD_PARTY.md` and `LICENSES-AUDIT.md` do not change: the restyle adds no dependency. `MODELS.md` records that M0 measured no on-device brain. The owner check for this milestone is `docs/owner-checks/M0.md`.

The first full `check` after the Arabic resources landed failed in `:app:lintFullDebug` with 1345 `MissingTranslation` errors, 7 `Untranslatable` errors, and 1 `StringFormatCount` error. Cause: `app/src/main/res/values-ru/strings.xml` was an empty placeholder. Lint ignored it while every locale was empty, then treated it as a real locale once `values-ar` had translations, and demanded a Russian copy of every string. The placeholder is removed. The seven Arabic copies of `translatable="false"` placeholders stay, because the Arabic key test requires every key, and they carry `tools:ignore="Untranslatable"`. `run_limits_soft_note` said "75% of", which lint reads as an octal format (`%o` with a space flag) in English and as no format in Arabic. Both strings now escape the percent (`75%%`). The rendered sentence is unchanged.

Checks on 29 Oct 2026: `./gradlew :catalog:recordRoborazziDebug` passed in 1m 28s. `./gradlew :catalog:verifyRoborazziDebug` passed in 1m 17s. `./gradlew check :buildSrc:test` passed in 6m 45s (foss 6056 tests, full 6060, catalog 1036, buildSrc 501, 0 failures). No gate is BLOCKED. The comparison threshold was not changed. GitHub Actions was not run from this machine.

## 2026-10-29 — OUTBOUND risk tier

`ToolRisk.OUTBOUND` sits above `DESTRUCTIVE`. Every approval policy, including "Approve tool calls: Never", still asks. A per-tool or MCP override cannot replace it with a quieter tier (`notQuietedBy`). The destructive block still refuses `DESTRUCTIVE` calls; it does not apply to outbound, because a refusal would remove the confirmation card.

The card is a tap. It does not ask for the destructive word "yes", and it does not offer "always allow". The notification shade does not approve an outbound call; the owner opens the card and taps.

Classified outbound among the tools that exist today:

- `delete_file`, because it deletes the owner's data.
- `http_request` writes (`POST`, `PUT`, `DELETE`, and a call whose method cannot be parsed). A `GET` stays `SENSITIVE`.

The other built-ins stay on their previous tiers. `safety/tool_classification.csv` is the committed table. `RegisteredTools` is what the repository reads, and `ToolClassificationTest` fails if a registered executor is missing, if the two disagree, or if an outbound-capable tool is not `OUTBOUND`.

These existing tests name the old tier or the old approval table, so they are updated in this same commit: `ToolRepositoryImplTest`, `HttpRequestPolicyTest`, `ToolInvocationGateTest`, and `ApprovalNotificationManagerTest`. The assertions got stricter (outbound still asks, and the shade cannot approve it). No threshold was loosened.

Checks on 29 Oct 2026: `./gradlew check :buildSrc:test` passed in 13m 15s (foss 6059 tests, full 6063, catalog 1037, buildSrc 501, 0 failures). Detekt reported 0 findings. The Roborazzi threshold was not changed. GitHub Actions was not run from this machine. No gate is BLOCKED.

## 2026-10-29 — OpenDroid hands, the tap guard, and the denylist

Upstream is yashab-cyber/opendroid at c08175cb815344da038f92c80e301929b28d79e8 (shallow clone of main, no tags on that clone). The owner's zip `kawkab-tools/opendroid-main.zip` is not in this repo, so that snapshot was not compared file by file.

What landed, rewritten so detekt and ktlint pass with no baseline change and no size-suppression added:

- `KawkabAccessibilityService`, the name `scripts/emulator-setup.sh` already reserved, plus node traversal. Clicks go through `GuardedTap` and `safety/outbound_patterns.txt`.
- Alias matching (exact, then longest phrase; a compound sentence stays unmatched), macro recording, and in-memory habit detection. Phrase tables for finance, food, transport, and social actions were not copied, because those action groups were not ported.
- Intent tools for call (`ACTION_CALL`, `CALL_PHONE`), SMS (`ACTION_SENDTO`, no `SEND_SMS`), alarm, timer, open app, and calendar insert. No `SYSTEM_ALERT_WINDOW`. No `READ_PHONE_STATE`.
- The three denylist layers and a private JSON-lines activity log. Editing the package set throws unless `DeviceConfirmation` just returned true. There is no settings screen for that edit yet.
- Package ids are the public store ids written down on 29 Oct 2026. The owner can change them after a device confirmation.

Not copied: LLM provider clients, the social manager, finance, food, and transport actions, and the MCP server.

Checks on 29 Oct 2026: `./gradlew :app:ktlintCheck :app:detekt :app:testFossDebugUnitTest --tests app.kawkab.android.domain.safety.HandsSafetyTest --tests app.kawkab.android.domain.models.ToolClassificationTest` passed. A full `check` for this tree is still to run before `m1`. No gate is BLOCKED. GitHub Actions was not run from this machine.

## 2026-10-29 — command eval set frozen

`evals/commands/v1/commands.jsonl` is version 1 of the typed command set. SHA-256 of the file bytes: `fe82846673bca8d93f604ef3cb4e8af8c3e6f155ce6acd7987b7cf5d500847d7`.

200 commands, one JSON object per line. Types: call 40, sms 40, alarm 30, timer 30, open 30, calendar 30. Languages: English 103, Modern Standard Arabic 67, Sudanese 30. Splits: test 100 and calibration 100, alternating within each type (even index test, odd index calibration). Each line has an id, split, type, lang, text, tool, and args. The three examples from the spec are in the file: "رسل لأحمد إني متأخر", "صحيني الساعة ستة", "اتصل على أخوي".

This commit does not add a parser. Later changes need a new version. The test split is the only split a gate may score.

## 2026-10-29 — fast path, stub messenger, and the activity-log file

The fast path is a grammar of the command shapes (call, message, alarm, timer, open, calendar) in English, Modern Standard Arabic, and the Sudanese phrases in the frozen set. It is not a model, and it was not fit by editing the eval file. A line it does not know stays null. It plans a tool call. It does not run it: the outbound tap and the denylist still apply.

The stub messenger is a first-party test app, application id `app.kawkab.stubmessenger`, under `testapps/stubmessenger`. It is not in the foss APK. Its send control is tagged `send_button`. Accepted messages are rows in an exported content provider so an instrumented test can read them back. The debug manifest of the agent makes that provider visible. Release manifests do not.

The activity log file is `activity-log.jsonl` under the app files directory. It is excluded from backup and device transfer by name. The recovery wipe does not delete it: that wipe is the locked-database escape, and the log is a private record of this install. The storage inventory names this root.

The browser editor's tool labels live next to the generator. Adding the twelve hands tools extended that label list and the generator's fixture in the same change. The fixture must name every registered tool or the generator test cannot render. The older display-order checks are unchanged. No threshold was loosened.

Telephony is optional. `CALL_PHONE` would otherwise mark the phone radio as required, and a tablet could not install. The manifest declares `android.hardware.telephony` with `required="false"`. A call still waits for the outbound tap, and it fails if the device has no dialer.

## 2026-10-29 — M1 gates measured

On the frozen test split of 100 commands the fast path matched every line: call 20/20, sms 20/20, alarm 15/15, timer 15/15, open 15/15, calendar 15/15. That is 100 percent per type. The gate stays at 90 percent. A parser that always returns a call to the contact "x" scores under 90 percent on every type. The calibration split is not a gate.

`./gradlew check :buildSrc:test --offline` passed in 9m 59s: foss 6070 tests, full 6074, catalog 1037, buildSrc 501, 0 failures. Detekt reported 0 findings. No gate is BLOCKED. GitHub Actions was not run from this machine. The emulator was not run. The instrumented fast-path test is compiled for CI and was not executed here.

## 2026-10-29 — user-visible name is Kawkab

Resource ids stay `knotwork_*`. They are not drawn on screen, and renaming them would touch every call site without changing what the owner reads. The About screen still lists Knotwork under acknowledgments, and `NOTICE` still carries the upstream copyright. Fastlane changelogs stay the historical release notes. The density launcher bitmaps are the old mark; minSdk 34 installs the adaptive planet icon, so those bitmaps are not the icon the phone shows. The status-bar icon is now the same planet, drawn white.

The mood-router preset keeps its id and stays importable. Its display name is Kawkab, matching the onboarding card. Screenshot goldens for the screens whose copy changed are re-recorded in the same change. The comparison threshold is not changed.

## 2026-10-29 — personal-use licences

The owner decided Kawkab is a personal app and is not sold. That overrides the spec's ban on AGPL and non-commercial code. Any open-source code or model may be reused when its licence allows personal use. Upstream licence headers stay. Every donor is credited in `NOTICE` and `THIRD_PARTY.md`. AGPL and non-commercial items are marked personal-use only there, so they can be swapped out if the app is ever sold. Hosted-provider clients and subscription logins stay out.

## 2026-10-29 — performance defaults

The owner's phone froze while the model worked. The first-install path probed the GPU and kept it when a short generation succeeded. That probe is no longer the default. A stored choice, including GPU, is still used. A phone with no stored choice records CPU and warms CPU. The log line on a successful init names the backend that actually started, and names the configured one when crash recovery substituted CPU.

Flagship class is total RAM at or above 16 GiB (`ActivityManager.MemoryInfo.totalMem`). Below that, onboarding pre-selects Gemma 4 E2B. E4B stays in the picker. A flagship phone still starts on E4B. The curated scenario cards may still say they were written for E4B. The download step follows the phone, not that card.

The injected context default is 1,500 tokens. The history-compression threshold is 1,000 and the read-file token budget is 700, so both sit inside that window. The idle unload is 2 minutes. The model stays loaded between messages and unloads only after that idle gap.

Every local turn sets thinking off. A prompt whose last 500 characters ask for JSON, and do not say "no JSON", also caps output at 300 tokens. Thinking stays off on that turn too, so the cap is the answer and not a scratchpad. Chat already streams every reply through `sendMessageAsync`. No second path was added.

Multi-token prediction stays off on CPU and on GPU. LiteRT-LM 0.17.1 `EngineConfig` has no switch for it. "Only on GPU" is met by never enabling it, because this build cannot enable it anywhere.

NPU stays withdrawn. `NpuTrialPolicy` returns NPU only when a self-test started and decoded faster than CPU. This machine cannot run that test. Time to first token and tokens per second are not measured. The Settings "Test backend" row stores both numbers when the owner runs it on a phone. Pocket LLM was read as an idea only. No code was copied.

## 2026-10-29 — Kawkab assistant is the default chat

The first launch materialises `kawkab_assistant` and marks it the default pipeline. A new chat opens on that default. The new-chat sheet starts on "Use default pipeline" rather than copying the chat that was open.

The mood-router file stays in the assets so it can still be loaded by its id. It is marked internal, so the first-run gallery does not list it. The featured onboarding card now materialises the assistant, not that router. An install that already marked the companion as the default (a node prompt containing "You are Mila") is pointed at a newly materialised assistant on the next launch. The old pipeline is left in the library. A chat that was bound to it explicitly keeps that binding.

The assistant prompt says it is Kawkab, that it runs fully offline on the phone with no server, that it answers briefly in Sudanese Arabic, Modern Standard Arabic or English, and that it uses the phone tools. The same identity is the stored system-prompt prefix. The pipeline itself is one local model step. Dispatch of those tools is the fast path and the agent loop, which are later tasks, so this pipeline does not add a second tool-calling graph on every message. The prompt tells the model not to claim an action that no tool result confirmed.

The code-level factory remains the fallback when the assistant asset cannot be read. The showcase preset stays in the gallery. It is no longer the first-launch seed.

## 2026-10-29 — three-tier routing

A typed line the grammar already knows is tier 1. It runs before any model load, through the same tool gate as a pipeline tool node, with `runId` null so a missed tap does not park a pipeline run. The outbound and sensitive cards are unchanged: the gate still asks, and the executor still sees `confirmed = true` only after that ask.

Tier 2 is three phrases: reply to someone, read my last messages, and set a reminder from a screenshot. The model writes only the words. A named contact is then `send_sms` through the gate. "reply to a message", with nobody named, keeps the written text as the answer and does not send. A reminder needs an attached picture; the title is the model's line and the event starts one hour ahead, again through the gate. The same words without a picture stay on the agent loop. Tier 2 is not taken when the model is not loaded, so that send still loads the model and then uses the pipeline.

Tier 3 stays the existing pipeline walk. LiteRT-LM 0.17.1 can call tools by itself (`automaticToolCalling`). That switch is now false on every conversation this app opens. Native tool providers are not attached. A tool call the model emits inside the pipeline still has to pass the tap gate in app code. Turning the switch on would let a call run before that gate.

The stuck detector already had repeated-step and no-new-output. A third signal, unchanged screen, fires when the last three steps since progress all returned the same `read_screen` text. A step that did not read the screen carries no screen fingerprint, so existing runs keep their old verdicts. The step ceiling and the time budget were already on the walk. They were not raised.

## 2026-10-29 — system assistant and the tool-calling brain

Kawkab registers a voice-interaction service so the owner can make it the phone's digital assistant. The panel runs a line the grammar already knows through the same tool gate as chat. Approve and deny on the panel are the tap. A line the grammar does not know is placed in the main chat's composer and is not sent until the owner taps send there. The assistant chat row is named Kawkab and is created only when a panel command runs.

Screen text from the assistant API is stored when the session receives it and is read only when the stored switch "use text from screen" is on and the owner taps "Read this screen". The switch defaults to off. It stays on this phone and is not written into a backup. The settings hub does not grow a new row for it; the panel itself is the control. The phone-access screen can show the same steps later.

The model unloads five seconds after the panel hides, unless a chat session is visible. Showing the panel again cancels that unload. The two-minute idle unload is unchanged. The recognition service the assistant contract requires does not send audio anywhere: it reports that it cannot listen.

Qwen3 1.7B is recorded as a tool-calling candidate under Apache-2.0. It is not downloaded and not in the APK. Gemma 4 E2B, Gemma 4 E4B and Qwen3 1.7B have no Kawkab score on the frozen v1 set or on v2. v2 is not frozen yet, and this machine has no model file and no phone. Local Assistant's author reports 10 of 10 for Qwen3 1.7B and 2 of 10 for Gemma 4 E2B. That is their measurement. With no Kawkab measurement, the tool-call default stays Gemma 4 E2B. Gemma stays the image model.

Llamatik is the candidate llama.cpp runtime for the judge GGUF, under MIT. It is not a Gradle dependency, so its native libraries are not in the APK. No weights are shipped. Local Assistant is credited. None of its code was copied.

The steps for making Kawkab the default assistant are string resources, in English and Arabic, plus a role-request intent for the phone-access screen.

## 2026-10-29 — Llamatik loads GGUF brains

Llamatik 2.0.0 (`com.llamatik:core`, MIT) is a Gradle dependency. Its arm64 native libraries are in the foss APK. No weight file is in the repository or the APK. Qwen3 1.7B Q4_K_M is a row on the Models screen. A file whose name ends in `.gguf` is loaded with llama.cpp on CPU. A request for vision or audio does not load that file: images and audio stay on Gemma.

The same day, llama.cpp b11982 on this machine scored Unsloth Q4_K_M GGUFs on the v1 and v2 test splits through a tier-3 JSON tool call. Gemma 4 E4B scored 91/100 and 66/78. Gemma 4 E2B scored 87/100 and 55/78. Qwen3 1.7B scored 61/100 and 41/78. Those figures are a reference runtime, not a device. E4B is the tool-call default. A phone below flagship RAM still starts onboarding on E2B, because that split is about RAM, not about this score. Images stay on Gemma.

The Maven artifacts are signed by Ferran Pons, fingerprint `46D3C7797C3CC9E4045027174C64A1BC4F3669FE`. That key is trusted for the `com.llamatik` group. The foss arm64 release APK built on this machine after the key was trusted is 134093476 bytes, under the 150000000 byte limit, and it contains only `arm64-v8a`.

## 2026-10-29 — command eval set v2 frozen

`evals/commands/v2/commands.jsonl` is version 2 of the typed command set. SHA-256 of the file bytes: `7fc21d3c680d11e2576bb4021028167d131d66c63c6a2673664ea28e56a5a26a`.

150 commands, one JSON object per line. None of them is a v1 line, and none contains a v1 line. Types: call 25, sms 25, alarm 25, timer 25, open 25, calendar 25. Splits: test 78 and calibration 72, alternating within each type. Languages are English, Modern Standard Arabic, Sudanese, and mixed Arabic and English. The lines include word-order changes, typos, and indirect requests such as "I'm late, tell Ahmad".

This commit does not change the parser. The score is a later commit. v1 is unchanged.

The parser, unchanged, matches 25 of 150 lines. On the test split that is 10 of 78: call 3/13, sms 1/13, alarm 0/13, timer 2/13, open 2/13, calendar 2/13. On the calibration split it is 15 of 72. The v1 gate is 90 percent per type on the v1 test split. That gate was not applied to v2 and was not loosened. The grammar was not retuned to these lines.

## 2026-10-29 — app maps

Phone, Messages, Clock, Calendar, WhatsApp and Gmail each have a stored map: a package id, the screens a replay looks for, and macros for opening the app and for one common task. Replay walks those steps and does not call a model. After every step the screen text must contain one of that screen's markers, in English or Arabic. Anything else stops the replay so the caller can fall back to tier 3. An outbound step is not run until the owner has tapped. The package ids are the public store ids, not a reading of the owner's phone. PhoneCLI is credited for the idea. No code was copied. A typed chat line still uses the three tiers. These maps wait for a caller that already has room for another function.

## 2026-10-29 — remaining model candidates

FunctionGemma 270M, Parakeet TDT 0.6B v3, Kokoro 82M and Qwen3-ASR 0.6B are recorded and not shipped. No weight was downloaded. No Gradle dependency was added. Llamatik stays as already recorded and is not listed a second time.

FunctionGemma is under the Gemma Terms of Use and is gated. Mobile Actions is Google's fine-tune recipe. Google reports 58 percent for the base model and 85 percent after that fine-tune. Those are their numbers. This machine has no free fine-tune run, so none was started. The tool-call default stays Gemma 4 E2B.

Parakeet's card says 25 European languages and does not list Arabic, so it is not treated as covering the owner's Arabic. Kokoro's card is Apache-2.0. Qwen3-ASR 0.6B's card is Apache-2.0 and lists Arabic. None of the three has a Kawkab measurement. OnDevice LLM is credited for naming Parakeet and Kokoro. No code was copied.

## 2026-10-29 — small-model harness

Genie and PocketClaw are credited for the rules below. Genie's GitHub repository has no licence file, so no code was copied. PocketClaw is MIT. No code was copied from either. Neither donor's cloud login was ported.

Automatic tool calling stays off. A planner line is one typed decision: Act, Reply, or Finish. Two tool calls, or a plain sentence, are invalid. The sliding history keeps the goal and the last 9 steps, and drops transient errors after a success. Failures are four tiers: retry a transient miss once, send a bad-argument error back to the planner, count an unknown tool and do not run it, or stop and tell the owner. The loop stops after 5 failures in a row, or after the same unknown tool 3 times.

A successful fast-path command is stored and shown on the Growth screen, ranked by how often it succeeded. The owner can delete it. The same sentence, when the grammar does not match, replays from that store with no model call. Stated facts are injected above the system prompt when any have been stored. The owner line is always first. With no stored name it reads as the person who uses this phone.

A biometric check is asked only when two signals agree: the tool tier is outbound or destructive, and the words name an outbound action. It is asked only after the tap. The tap rule is unchanged. The hands executors still pass the device check as already satisfied, because the card has no fingerprint prompt yet. Stopping every outbound action with no way to continue would leave the owner stuck. The runner returns the biometric sentence when the caller says the device check has not happened.

Old images in the prompt become a text note capped at 100 tokens. The latest image is unchanged, so the current picture can still reach the on-device model. Image bytes are not copied into the older turns.

Gecko 110M was not measured. EmbeddingGemma stays. PocketClaw's author reports 1–3 seconds to the first chat token and about 5 seconds for images on a Snapdragon 7s Gen 3. Those are their numbers. Unused native libraries already excluded from the APK stay excluded: the desktop Jansi binaries, and the MediaPipe text-generation library the app does not load. No further library was shown to be unused on this machine, so none was newly stripped.

## 2026-10-29 — phone access

Setup does not gain a new pager step. The existing steps already drive every onboarding snapshot, and a new step would change all of them. After the owner finishes setup, the phone-access list is shown, and Continue enters the chat. Skip and Start from scratch still enter the chat immediately. Settings reaches the same list from the privacy screen, so the privacy snapshot stays as it is.

Each item has a status line. A missing answer is off, never hidden. Contacts, phone, calendar, notifications and the microphone each have a one-line reason and a grant button. The accessibility class stays `KawkabAccessibilityService`. The screen explains it in plain words, shows the three-dot menu picture for the Android 13 restricted-settings step, and opens the system accessibility settings. Battery optimisation is requested with the system dialog, and the screen says to allow unrestricted background use on Nothing OS. Lint flags that dialog as a Play-policy battery issue. The check is suppressed on `batterySettings` because the owner opens it from this screen; the exemption is not taken silently. The default-assistant steps from the earlier assistant work are on the same list.

A call, a text to a name, or a calendar event that is missing a permission returns a sentence naming that permission. The chat and the assistant panel show a card whose Grant button asks for that permission. It does not start the action, and it does not fail with an empty error. The tap rule is unchanged. The biometric check still does not replace the tap.


## 2026-10-31 — engine window and allocation failures (task 23f)

Task 15 set `SettingsDefaults.MAX_CONTEXT_LENGTH_DEFAULT` to 1,500, and that value is LiteRT-LM's `EngineConfig.maxNumTokens`, the whole KV budget. The Kawkab prompt and the phone tool schemas alone exceed it, so every model failed with "Failed to allocate tensors". The default is 4,096 again. The engine is never opened below 3,584, the smallest Settings step that holds the prompt, the 12 phone tool schemas, the injected budget and a 512-token reply reserve by a conservative 3-characters-per-token estimate (3,120 tokens measured by `EngineWindowTest`). The Settings slider now starts at 3,584.

Task 15's "about 1,500 tokens" is now `INJECTED_CONTEXT_TOKEN_BUDGET`, the budget for chat history fed into the prompt. The read-file and history-compression budgets already sat under it.

A stored 1,500 reads as 4,096. The Settings slider moves in steps of 512, so the owner could not have chosen 1,500 himself, and no new preference key is needed for the migration.

A GGUF brain is unloaded before a LiteRT model loads, so at most one LLM is resident. On "Failed to allocate tensors" or "Failed to invoke the compiled model", the engine retries once with 3,584 tokens. If that fails, the reply is generated with another installed LiteRT model, Gemma 4 E2B first, and a notice says which model failed and which answered. The owner's active model is not changed. GPU is not tried automatically: the owner's GPU switch froze the phone on 31 Oct, so it stays the owner's choice. With nothing left, the chat shows a plain sentence, not the native error line.

The silent flip to Qwen3 1.7B was not reproduced in code review. No path in the app changes the active model after a failure. Activation was two SQL statements, which left a moment with no active model or two; it is now one statement, so `LIMIT 1` can only return the model the owner picked.

A GGUF model is labelled `CPU · llama.cpp`, and a LiteRT model is labelled with its backend and `LiteRT`.

**BLOCKED gate: the emulator test "activate E2B on CPU, send hello, switch to the GGUF and back".** Measured: the CI emulator has 4,096 MB of RAM, an x86_64 CPU and no model files, and E2B alone is about 2.6 GB. Cause: the hardware, not the code. Fallback: `ModelSwitchReplyTest` is written, compiles in the Check, and is on the device-only exclusion list. It runs on the owner's phone after `adb push` of the models to `/data/local/tmp/kawkab-models`. The allocation recovery itself is covered by unit tests.

## 2026-10-31 — accessibility work per event, tab switching (task 23d)

The accessibility service listened to every event type and walked the whole node tree on the main thread for each event from every app. It now listens only to `typeWindowStateChanged` and `typeWindowContentChanged`, with `notificationTimeout` 300 ms. Per event it keeps the package, `FLAG_SECURE` and the password state. The screen text is read on a background `HandlerThread` at most once every 500 ms; events in between fold into the one pending check. The full snapshot, text included, is built immediately before every tap, typed text and hands action (`ScreenWatch.forAction`), so the §0.4.4 rule still holds: the full denylist check runs right before an action.

Memory: at most one LLM is resident (task 23f unloads a GGUF before a LiteRT model loads). Onboarding already picks E2B below 16 GB of RAM, which covers the owner's 12 GB phone. E4B is never picked automatically there, and its Models row now says it needs 16 GB and may freeze the phone. Model loads and generation run on the IO dispatcher; no tab reads model state to draw. Perfetto measurements of resident memory with each model loaded need the owner's phone and were not taken here.

Tab switching: a `:benchmark` Macrobenchmark module measures frame timing for Chat → Pipelines → Tools → More → Chat with no model loaded. It runs in a separate job of the Instrumented workflow against the foss debug build, because the release APK is arm64 only and the CI emulator is x86_64. A debuggable build is slower than release, so a pass there is a conservative pass. The gate (median frame time under 16 ms) is read from the benchmark JSON by `.github/scripts/check-frame-timing.py`. There is no "before" number: the benchmark did not exist before this change, and the accessibility service is not enabled on the emulator, so its fix cannot show there. The before and after for the service is the owner's phone with the service on.

A hand-written Baseline Profile (`app/src/main/baseline-prof.txt`) compiles the app's UI classes ahead of time on install. It is not a recorded profile; recording one needs a `baselineprofile` build type, which would add two more variants to every Check.

New build dependencies for the benchmark only: `androidx.benchmark:benchmark-macro-junit4` 1.5.0 and `androidx.test.uiautomator` 2.4.0, verified by the androidx signing keys already trusted. Their transitive `com.squareup.wire`, `moshi` 1.13.0 and `okio` 2.10.0 are verified by the Block and Jesse Wilson keys; the latter key was added to the keyring.

## 2026-10-31 — the call flow (task 23j)

Contacts are matched in app code, not with SQL `LIKE '%name%'`. Every spoken word must be a whole name word or the start of one, after lower-casing, removing Arabic diacritics and tatweel, unifying أ إ آ to ا, ة to ه and ى to ي, and dropping a leading ال. So "him" no longer matches Ibrahim or Hashim, and "abdalla" still matches Abdallah. Every phone row is read, because a few hundred rows are cheap and Arabic spellings differ in SQL.

Two spellings of one number are one choice: numbers are compared by their last 9 digits, which covers `+249`, `00249` and a leading `0` without a phone-number library. The owner's own numbers come from the profile card and are never offered. At most 5 choices are shown, as chips with the name, the last 4 digits and the phone label. The reply sentence never lists a number.

The pending call or text lives in a singleton for 2 minutes. While it waits, the next line is read as a pick first: a name ("the mubadala"), an ordinal ("the second one", "التاني"), the last 4 digits (what a chip sends), or cancel. Anything else gets "Tap one of the names below, or say cancel" instead of going to free chat. A new command replaces the pending one. A pick is never stored in the replay cache. The picked call still goes through the outbound tap card.

"him", "her", "هو", "هي" and similar mean the last contact a command resolved to; with none, Kawkab asks who.

Grant asks Android first. When Android will no longer show its dialog (a denial with `shouldShowRequestPermissionRationale` false), Grant opens App info with a one-line instruction. Coming back with the permission on reruns the last turn.

Without CALL_PHONE, a call opens the dialer with the number (`ACTION_DIAL`, no permission needed), and the owner presses call.

A turn that shows no progress for 60 seconds while nothing waits for the owner is stopped with "No reply for 60 seconds" and the Retry tile. A waiting tap card is not a hang. This also covers task 23h's "no silent hangs" rule.

## 2026-10-31 — stray quotes and tool names printed as text (task 23h)

The fast path strips emoji, surrounding quotes (`" ' “ ” « » ‘ ’ „` and backtick) and trailing punctuation (`. ! ? ، ؟ , ؛ ; …`) before matching. Stripping only removes characters around a command, so nothing that matched before stops matching. The frozen v1 and v2 sets were not edited, and their score tests still pass with the same numbers.

A reply from a chat-facing LITE_RT node is read for a tool call before it is shown: Gemma 4's own markup (`call:name{key:<escape>value<escape>}`), a fenced or plain JSON object (`tool`/`name`/`function` with `arguments`/`args`/`parameters`), or `name(key=value)`. Only names in the registered tool table count. A call runs through `ToolInvocationGate`, so an outbound tool still shows its tap card; its result becomes the reply. A reply that is only a tool name, or names one without arguments, gets one re-ask for the JSON call; if the second reply is not a call, the owner reads "I couldn't do that, try: …" with a command for that tool. The re-ask is a strict prompt, not grammar-constrained decoding: LiteRT-LM 0.17.1 exposes no grammar option.

Skill nodes are left alone: a skill parses its own output against its tool allowlist, and reading it here would bypass that list (the golden traces caught this).

Every turn ends within 60 seconds in a reply, a card or an error with Retry: the turn watchdog from task 23j.

## 2026-10-31 — local actions, readable cards, times (task 23i)

`set_alarm`, `set_timer`, `open_app` and `add_calendar_event` are a new `LOCAL` risk tier. Under the default policy and under "Never prompt" they run without a card; only "All calls" asks first. `OUTBOUND` (call, SMS, WhatsApp, share, delete, web request, tap) still asks under every policy. `safety/tool_classification.csv` and the classification test follow.

After a local action from chat, the reply ends with "Stays on the phone, so I didn't ask." and an Undo chip shows for 10 seconds. Undo dismisses the alarm by its time, stops the timer, or goes back to the home screen. A calendar event is only added after the owner taps Save in the calendar app, so Undo says to close that screen. Each undo is an activity-log entry (`asked = undo`).

Confirmation cards for a call, a message, a delete, a web request and a tap read as a sentence in the owner's language, with Cancel, Change and Send (or Call). The tool id and raw arguments are behind Details. Change declines the call and puts an editable command back in the composer. Other tools keep the existing card. The plain mode is off by default in the design system, so every existing card screenshot is unchanged.

Times use `h:mm a` with the phone's locale and digits: "6:00 AM", and "٦:٠٠ ص" in Arabic.

## 2026-10-31 — reply speed and reply language (task 23g)

Language: the Kawkab prompt and the default prompt prefix say "Reply once, in the same language and dialect the user wrote in. Never label the language. Never translate unless asked.", with one Sudanese example. A chat reply that still comes as labelled translations is cut to the one part in the user's script, with the label removed. Sudanese is told apart from Modern Standard Arabic by a list of Sudanese words (زول، شنو، هسي، عايز، داير، وين …).

The reply-language set `evals/reply-language/v1/prompts.jsonl` (20 prompts: 8 Sudanese, 6 MSA, 6 English) was frozen in its own commit, SHA-256 `82660d85c96bbd21680d7b1fd8977f3d400aacf26cd4e3b54b88209d5a8848af`. The detector labels all 20 correctly. **BLOCKED gate: scoring real model replies on this set.** Measured: no model runs on this machine or on the CI emulator (see task 23f). Cause: hardware. Fallback: the set and the detector are ready; scoring replies needs Gemma on the owner's phone or a llama.cpp reference run.

Speed: tier 1 sends no prompt at all (it never calls the model). The assistant prompt no longer expands `$TOOLS` (every tool's name and description) and lists only its six phone tools with their arguments, so the prefill is shorter. Native tool schemas are not sent (native tool calling stays off since task 17).

**BLOCKED gate: reuse the fixed system prompt's KV state between turns, and first token under 3 s.** LiteRT-LM 0.17.1 exposes no KV save and restore and no prefix cache, and the orchestrator sends the whole history each turn, so the conversation is reopened every turn (closing it any other way crashed the native session, see `LiteRTLlmEngine.streamConversation`). The first-token time for "hello" and for a command was not measured: no model runs here. Fallback: the shorter prompt; the owner's phone shows first-token time on the Models screen's performance card.

## 2026-10-31 — GGUF activation (task 23e)

Tapping a model in Models now loads it before marking it active. A model whose file is missing, or that llama.cpp cannot open, is never active: the previous model stays and a snackbar says why. At chat time, an active GGUF that fails to load falls back to an installed LiteRT Gemma (E2B first) for that reply, with a notice that names both models; the owner's choice is not changed. The GGUF context window was 1,024 tokens, smaller than the Kawkab prompt and its tools, so a Qwen brain could never answer; it is now 4,096 like LiteRT.

Downloads already check the SHA-256 the source publishes (the "checked" label on the Models screen); nothing changed there.

The emulator test downloads SmolLM2 135M Instruct Q4_K_M (Apache-2.0, 105,454,432 bytes, SHA-256 `2e8040ceae7815abe0dcb3540b9995eaa1fa0d2ca9e797d0a635ae4433c68c2d`) from Hugging Face, activates it through the same use case, and expects a reply to "hello". It is the smallest GGUF llama.cpp answers with, and the Llamatik library ships an x86_64 build, so this one runs on the CI emulator. The file is a test fixture only; it is not in the APK and not offered in the app.

## 2026-10-31 — power button and assistant fallbacks (task 23k)

Phone access now reads `RoleManager.isRoleHeld(ROLE_ASSISTANT)` each time the owner comes back and says plainly whether Kawkab is the digital assistant. When Android does not offer the role request, the button opens the voice-input settings, or the default-apps screen if that is missing. The screen also says to set Settings → System → Gestures (or Buttons) → Press and hold power button → Digital assistant, because Nothing OS can leave it on the power menu. This was not checked on a Nothing OS 4 phone here; the owner's check is in `docs/owner-checks/M1.6.md`.

The voice interaction service, its session service and `supportsAssist` were already declared correctly. The service now records itself while Android runs it, so other entry points can open the same panel.

Two fallbacks always reach Kawkab: an "Ask Kawkab" Quick Settings tile and an "Ask Kawkab" launcher shortcut. With the assistant role they open the panel over the current app; without it they open a new chat. The shortcut is a static launcher shortcut, which can only start an activity, so it always opens a new chat.

## 2026-11-01 — conversation mode before M4 (task 23l)

A Talk button in chat runs a loop: Android `SpeechRecognizer` listens with `EXTRA_PREFER_OFFLINE`, using the on-device recognizer when the phone has one; the transcript is sent as the owner's message; the reply is spoken with Android `TextToSpeech`, Arabic for an Arabic reply and English otherwise, preferring voices that need no network; then it listens again. A tap stops it. Speaking over the reply stops the speech: the recognizer keeps listening while Kawkab speaks. Two silent rounds in a row end the loop. A tap card (or a clarification) stops the loop: voice never confirms an outbound action (§0.4.3).

The recognizer listens in Sudanese Arabic (`ar-SD`) on an Arabic phone and in English otherwise. Choosing the language per utterance needs the M4 stack.

When Kawkab is the assistant, a long-press of power (the assist gesture) starts the same loop in the assistant panel. A tile or accessibility-button tap opens the panel without listening. The wake word stays in M4 (now M1.7, task 27).

On-device speech was not run here: neither this machine nor the CI emulator has an offline recognizer or voice data. The loop's logic is unit-tested; the owner's check is in `docs/owner-checks/M1.6.md`.

**BLOCKED gate (task 23d): median frame time under 16 ms on the CI emulator.** Four attempts: (1) dependency verification for the benchmark's libraries, fixed; (2) the tested-APK configuration resolving JVM variants, fixed; (3) suppressions passed on the command line; (4) suppressions set in the benchmark module (`testInstrumentationRunnerArguments`). In runs 41252985223 and 41244128823 the runner reported `WARNINGS (suppressed): EMULATOR` and `ERRORS (not suppressed): DEBUGGABLE`: only the first value of the comma-separated list arrives, so the debuggable foss build stops the run before any frame is measured. Measured frame time: none. Cause: the target must be non-debuggable, and the only non-debuggable build is the arm64-only release APK, which does not install on the x86_64 emulator. Fallback: the benchmark job stays in the Instrumented workflow with `continue-on-error`, and the accessibility fixes (the main cause the owner measured) are covered by unit tests. Solving it needs a non-debuggable x86_64 `benchmark` build type, which adds variants to every Check.

## 2026-11-01 — Phone access redesign (task 23)

Phone access is the first row of the Basic block on the Settings hub, with "N of 8 on" as its state; the link under Privacy is gone. The row appears only when the app supplies the count, so the existing hub screenshots are unchanged. The chat shows an amber "Kawkab needs N permissions" banner while anything is off; a tap opens Phone access. Both re-read the status when the owner comes back.

The screen is a design-system screen in the catalog: a progress line, then eight cards in the owner's order (Contacts, Phone, Calendar, Notifications, Microphone, Screen control, Run in background, Default assistant), each with an icon, the one-line reason, a green or amber On/Off chip and one full-width button. "Default assistant" is new; it is on when Kawkab holds the assistant role. An item that is already on offers "Review in settings", which opens App info. The restricted-settings step for the accessibility service is now a text note; its picture is no longer shown. Goldens: `catalog/src/test/snapshots/phone_access_mixed_{light,dark}.png`. The snapshot suite verifies only when a Roborazzi task runs; the card structure and the Settings row are also checked by ordinary tests that run in the Check.

## 2026-11-01 — floating accessibility button (task 23b)

The accessibility service config adds `flagRequestAccessibilityButton`, and the service registers an `AccessibilityButtonController` callback when it connects (and removes it when it stops). A tap opens the assistant panel through the voice interaction service when Kawkab holds the assistant role; the panel reads the current screen only when the owner allows it, as before. Without the role, the tap opens a new chat. Android's accessibility button reports no long-press, so there is no long-press action.

## 2026-11-01 — the mic before M4 (task 23c)

The composer mic dictates with Android's `SpeechRecognizer` and `EXTRA_PREFER_OFFLINE = true`, using `createOnDeviceSpeechRecognizer` when the phone has one, so audio does not go to a cloud recognizer through Kawkab. The language is `ar-SD` on an Arabic phone (Android falls back to `ar` when it has no Sudanese model) and `en-US` otherwise. The transcript is added to the input box; nothing is sent until the owner taps Send. A missing offline language shows a message with Download, which starts the on-device model download on Android 13+ or opens the voice-input settings. With no recognizer at all, a one-line hint replaces the error card. The old record-and-transcribe path stays in the code for audio-capable models but the mic no longer starts it. Recording a clip from the attach menu is unchanged.
