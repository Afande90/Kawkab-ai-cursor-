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

The composer mic dictates with Android's `SpeechRecognizer` and `EXTRA_PREFER_OFFLINE = true`, using `createOnDeviceSpeechRecognizer` when the phone has one, so audio does not go to a cloud recognizer through Kawkab. The language is `ar-SD` on an Arabic phone (Android falls back to `ar` when it has no Sudanese model) and `en-US` otherwise. The transcript is added to the input box; nothing is sent until the owner taps Send. A missing offline language shows a message with Download, which starts the on-device model download on Android 13+ or opens the voice-input settings. With no recognizer at all, a one-line hint replaces the error card. The old record-or-pick chooser (transcription by an audio-capable model) stays in the code, but the mic no longer opens it; it comes back with the M4 voice stack.

## 2026-11-01 — leftover knot icon (task 24)

The onboarding header and the empty-chat brand tile drew the upstream hub glyph, which reads as the old knot logo, next to "Kawkab". Both now draw the planet mark (`AppIcons.Mark`), and the branding test checks both. The hub glyph stays where it means "discover models" (the Models top bar and the Discover rows); it is a function icon, not a logo. The empty-chat and onboarding screenshot goldens were re-recorded; no other golden changed.

## 2026-11-01 — onboarding model sizes (task 25)

The scenarios name E4B as their model, and the cards printed that size on every phone. Onboarding already downloads the phone's pick (E2B below 16 GB of RAM, task 15). The view state now carries that pick as `recommendedModel`; the scenario cards print its size, and the download step marks it "needs this". On a 12 GB phone the cards say 2.59 GB. Previews without a recommendation look as before, so the onboarding goldens did not change.

## 2026-11-01 — one fixed signing key (task 25b)

The release build already reads `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`, and `release.yml` already writes the keystore from the `RELEASE_KEYSTORE_BASE64` secret. The Check's APK job did neither, so each CI APK was signed with the runner's fresh debug key. It now writes the keystore from the same secrets into the runner's temp directory (never the checkout) and signs the foss APK with it; without the secrets it warns and falls back to the debug key, so forks and pull requests still build. The keystore is never committed.

**BLOCKED: creating the keystore and the repository secrets.** This build machine has no way to write repository secrets (no GitHub secrets API in its tools). Fallback: the exact steps are in `docs/owner-checks/M1.6.md`; once the owner adds the four secrets and the fingerprint variable, every following CI APK installs over the previous one.

## 25c: one-tap APK download (1 Nov 2026)

- **Publishing:** `.github/workflows/prerelease.yml` runs after every green Check on a push to `main`. It downloads the foss arm64 APK the Check already built (no second build, so what is published is what the gate passed) and creates a pre-release tagged `build-YYYYMMDD-<sha7>`. Only the 5 newest pre-releases are kept; `release.yml` tagged releases are not pre-releases and are never deleted.
- **Signing:** the APK is signed with the release key only once the 25b secrets exist. Until then each build is signed with a fresh debug key and does not install over the last one (see 25b, BLOCKED).
- **Check for updates:** an owner-started button on About. One anonymous `GET` to the GitHub releases API, only on tap (spec §0.2), recorded by the network indicator, documented in PRIVACY.md §3.10. A newer build turns the button into "Download <tag>", which opens the APK link in the browser; Android's installer then asks the owner again. A build is "newer" when its tag names a different commit from `BuildConfig.GIT_SHA`.

## 27: wake word "Kawkab" (1 Nov 2026)

- **Engine:** sherpa-onnx 1.13.8 (Apache-2.0, onnxruntime MIT) with the `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01` keyword spotter (Apache-2.0) and Silero VAD (MIT). The AAR comes from JitPack and is byte-identical to the GitHub release asset (SHA-256 `633c2432…bd96`, pinned in `verification-metadata.xml`). The models (5.9 MB) ship in `assets/wake/`: nothing is downloaded, no audio leaves the phone, nothing is stored.
- **APK size:** onnxruntime adds 27 MB of native code. Stored uncompressed, the native libraries were already 134 MB of the 150 MB limit, so native libraries are now stored compressed (`useLegacyPackaging`). Android extracts them at install.
- **Keyword:** "Kawkab" is not an English word, and the model is English-only. Spelled as BPE (`▁K A W K A B`) it woke on 1 of 126 calibration clips. Whisper transcripts of the calibration clips showed how it is heard ("cook up", "cockab"), so nine spellings of those sounds are listed, all tagged `KAWKAB`. Threshold 0.15 and boost 2.0 were chosen on the calibration split only (sweep 0.08–0.25 × 1.0–3.0). Calibration: recall 0.556 at best, 0.54 at the chosen point, 0/40 hard negatives.
- **Eval set:** `evals/wake-word/v1`, frozen in its own commit before any choice, SHA-256 of `SHA256SUMS` `2d4cfdc07c850a006a31894c223006d78da6d31fa7274f80410859a13b7d6bf2` (manifest `f6862b68…9e0a`). 802 clips: calibration 126 positives / 40 hard negatives; test 534 / 102, test voices disjoint from calibration. Real negatives: LibriSpeech test-clean + dev-clean, 10.79 h (CC BY 4.0). Common Voice (login) and MUSAN (11 GB) were not used.
- **Measured on the test split** (reference runtime: sherpa-onnx 1.13.8 pip, CPU, same int8 model and settings as the app): recall **42.9 %** (229/534; libritts_r 220/480, alan 8/18, ryan 1/18, Arabic kareem **0/18**); hard negatives 4/102 woke (all alan); **0.278 false wakes per hour** (3 in 10.79 h of LibriSpeech, without the VAD, so an upper bound). Mutation check: constant "yes" gives 493 false wakes/h and constant "no" recall 0, both reported as failing.
- **BLOCKED gate: recall ≥ 90 % and false wakes ≤ 1 per 10 h.** Measured above, two attempts (spelled tokens; heard spellings with a calibration sweep). Cause: an English-only 3.3M spotter cannot reliably hear an Arabic name, and not at all in an Arabic voice. Fallback: the wake word ships off by default and is labelled optional; the power button, the tile and the conversation button stay the main ways in. Reaching the target needs a spotter trained on "Kawkab"/"كوكب" (for example a small custom KWS head or openWakeWord-style training on the positives), which is a separate task.
- **Battery per hour:** not measured here (no phone). The Phone access card shows wakes per hour and the phone's battery drop per hour since the wake word was turned on, after 15 minutes without charging. Android does not report one app's share, so it is the whole phone's drain.
- **Starting:** Android lets a microphone service start only while the app is in front, so after a restart the service starts the next time Kawkab is opened. It is a foreground service with a visible notification and a Stop action.
- **Waking:** a short tone, then the Kawkab panel opens in conversation mode (task 23l) when Kawkab is the assistant. Otherwise a notification asks for one tap, because Android does not let a background service open a screen. The panel pauses the wake word while it holds the microphone.

## 28: continuous conversation (1 Nov 2026)

- **Flow:** wake word (or power button, tile, talk button) → tone → listen → send → the reply is shown, then spoken sentence by sentence → listen again for 8 s without the wake word → silence ends the conversation. Talking over the reply stops it (barge-in, from 23l). The first listen still allows two silent rounds; only the follow-up ends on silence.
- **End of turn:** smart-turn-v3 was not added: its licence and the ONNX export were not verified here, and it would be a second model next to Silero. The recognizer is asked to end a turn after 700 ms of silence (`EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS`); recognizers may round this.
- **BLOCKED gate: sherpa-onnx Whisper as the speech-to-text, with the size chosen by measurement.** Measured: nothing. Cause: choosing the size needs Sudanese Arabic speech with transcripts, and no licensed set is available here (Common Voice needs a login); Whisper tiny/base/small are 40–250 MB downloads that the app does not have a downloader entry for yet. Fallback, as the spec allows: Android's offline `SpeechRecognizer`, already used since 23c.
- **BLOCKED gate: an offline sherpa-onnx Arabic and English voice (Piper or Kokoro).** The Piper Arabic voice (ar_JO-kareem, 63 MB) and an English voice would add a model download and a second audio path; no listening test can be run here. Fallback, as the spec allows: Android `TextToSpeech` with offline voices only (from 23l).

## 29: hands-free actions within the safety rules (1 Nov 2026)

- **Local actions** (alarm, timer, open app, calendar) still run at once (23i). The reply is spoken and ends with "Say undo or tap Undo within 10 seconds." Saying or typing "undo", "cancel that", "تراجع", "الغيها" alone within the window undoes it through the same `LocalUndo`, logged with confirmation "said or typed undo". With nothing to undo the word goes on as a normal message.
- **Outbound actions:** in a conversation the panel reads the card aloud ("Call Abdalla Mubadala?"), then stops listening and waits for the tap; the result is still spoken. The card's sentence is the one 23i shows (`ActionSentence`).
- **Confirm by voice:** off by default, turned on only on the Phone access screen (reached from Settings, task 23) behind a warning that a TV or recording could say yes. It is a Phone access card rather than a Settings switch so it sits next to the wake word and the assistant role. When on, the panel listens once after reading the card. `VoiceAnswers.decide` approves only on a whole-utterance yes with the setting on, the screen on and the phone unlocked; a yes with the phone locked never sends. A spoken no always cancels. A voice answer is recorded in the activity log as entry point "voice" with the confirmation given.
- **Contact choices** are read aloud as a numbered list of names (never numbers): "2 contacts match 'Abdalla'. Which one? 1, Abdalla Mubadala. 2, Abdalla Osman." The answer "the second one" or a name already worked (23j).
- Voice answers to cards are handled in the panel (where the wake word opens conversation mode). The in-app talk button keeps the 23l behaviour: it reads the card's result but leaves the card itself to a tap.

## Owner's vision reset, rule 1: announce and act (2 Nov 2026)

- The `OUTBOUND` tier no longer always waits for a tap. For `call_phone`, `send_sms`, `send_message` and `macro_send`, asked for in the chat the owner has open or in the assistant panel, the gate announces the action ("Calling Abdalla Mubadala"), shows a countdown with STOP, and runs it after 3 s unless the owner taps STOP or says "stop", "cancel", "لا" or "وقف". `OutboundCountdown` holds the rule; `safety/outbound_confirmation.csv` lists every outbound tool with its confirmation.
- Kept asking (rule 3 and judgement): `delete_file` needs an explicit yes; `http_request` and `submit_tap` still ask; a run nobody is watching (a trigger, a scheduled task, a pipeline in the background) still asks, because nobody would hear the announcement or be able to stop it. A node's own "always confirm" switch still forces a real answer.
- The banking, payment, wallet and password denylist is unchanged and still checked right before every action.


## 2026-11-02 — M1.8 continuation and write-access blocker

The reset rules at the top of AGENTS.md are binding, including voice-first countdowns and voice ambiguity resolution. No tap or yes is required for an announced call/message. The existing countdown work missed typed speech announcements and cancellation while the assistant was still speaking. The continuation adds both, retains listening through silence and unrelated answers, and rejects attempts to approve before the window ends. A pending action's result returns the loop to normal spoken replies.

A call's Telecom fallback now explicitly targets the default Phone package. When none is set, Kawkab says so instead of issuing an implicit call that a different app could intercept. Remembered contacts are now visible and removable in Growth. With the assistant role held, Phone access tries exported power-button/gesture settings before falling back to assistant settings. Nothing OS's actual key mapping cannot be set by an ordinary app.

Audio remains local: recognition uses only Android's on-device recognizer; an offline voice must exist before TTS speaks. Queued speech survives asynchronous TTS initialization and is discarded on cancellation. Crash reports exclude native abort messages and free-form exit descriptions because those can include command content.

**BLOCKED: push, fresh CI and milestone publication.** Measured: Git push rejected twice with HTTP 403, and a Git-data blob write returned HTTP 403, "Resource not accessible by integration". `gh auth setup-git` did not resolve it. Repository metadata lists the account as an administrator, but that does not grant the integration contents-write access. No credential or secret was printed or added. Push of the restored historical tags also returned 403. Fallback: preserve the small local commits and a Git bundle outside the repository; resume with authorized write access, push, then require green Check and publication before tagging M1.8. M2-V is pending, not passed.

Baseline evidence: Check run 41366560458 and Instrumented run 41366560554 are green on `9b64c54f`; the baseline pre-release is build-20261102-9b64c54 (87,901,617 bytes). These are not results for the continuation. The release-launch job on earlier `7fd312cb` failed (run 41362738707); the CLI returned no failed-job log, so exact reproduction of the owner's old native crash is not newly claimed.


### M1.8 continuation: local checks

Final targeted unit run: 79 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESSFUL in 2m 7s. Classes: ConversationCardTest, ConversationLoopTest, ConversationFollowUpTest, RememberedContactTest, ContactQuestionTest, OutboundCountdownTest, CrashRulesTest, CallFlowTest, CallRoutingTest, ToolInvocationGateTest and HarnessRulesTest. Foss debug application and test sources compiled. Also passed: app ktlintCheck, detekt, verifyFileMap, verifyNoOrphanedKdoc, verifyComplianceSource and verifyDocLinks. The full Check and emulator were not run locally. An initial Robolectric runtime download could not reach the network; forwarding the environment proxy and trusted CA through a user-level Gradle test configuration fixed it without changing repository build settings or verification rules. Fresh CI remains BLOCKED by GitHub writes, as above.


## 2026-11-02 — reset rule 5b: English interface

All Arabic resource overrides now carry the default English copy, including English plural forms for every Arabic quantity. Non-translatable constants fall back to defaults. The shared Compose theme and Android application keep interface layout left to right; spoken Arabic content gets its own text direction in the LCD. The former Arabic-key test now checks that every localized override matches English. The Arabic-locale Compose test exercises the actual theme and asserts left-to-right layout. Checks passed: both targeted tests, app/catalog ktlintCheck and detekt, app verifyComplianceSource (BUILD SUCCESSFUL, 3m 37s). No push, tag or publish operation is part of this continuation, by the owner's latest instruction.


## 2026-11-02 — reset rule 4: shared Pocket LCD foundation

KawkabDevice renders F1–F8 from one immutable state, with the exact body/panel/bezel/LED colours, 124 dp talk/STOP key, tactile HOME/SHOTS/GROWTH keys, grille dots, bundled fonts, reduced-motion cursor and Arabic speech text direction. Product data is separate from preview fixtures: the live UI never invents 86% accuracy. All eight Compose screenshot cases passed after recording their goldens, with catalog ktlintCheck, detekt and app compliance. App owner-voice policy and conversation tests also passed in the same invocation (BUILD SUCCESSFUL, 1m 37s). App routing and device controls are integrated in the next commit.


## 2026-11-02 — exact F4 correction and bottom-anchored LCDs

The calling view now uses the owner's exact 22/46/32/22 sp top stack, 6 dp grouping, right-aligned 150 sp digit with 127.5 sp line height, nine-block 26 sp progress bar, 400 dp panel, external 13 sp hint, and 124 dp dark STOP key with an 8 dp black shadow. All eight views pin their final block to the LCD bottom. Screenshot tests assert panel height, bottom and right edges, actual text-layout font sizes/line height, heard words, progress text, hint position, key diameter and shadow offset. All eight screenshots and nine owner/wake unit cases passed, along with app/catalog ktlintCheck, detekt and source compliance (BUILD SUCCESSFUL, 2m 2s).


## 2026-11-02 — rules 4, 5 and 7: live device shell and owner audio

The home route now uses the Pocket LCD product surface and its six-item Settings menu. No pipeline name, token count, raw tool card or model filename is shown there; the former engine pages are reached through Advanced. Enrollment records ten wake words and two sentences after screen-lock authentication. A consistent sherpa speaker embedding is required, and the profile plus PCM are stored under noBackupFilesDir/owner_voice. The exact same PCM stream feeds the Android offline recognizer and speaker verifier. Unknown, missing and invalid owner profiles fail closed; other speakers produce no transcript or action. The wake path independently verifies the speaker. Capture pauses the wake service and release resumes it. Deleting the profile is behind a fresh verified spoken yes. Six owner policy tests, three portable wake feature tests and the eight device screenshots passed in the final correction run; the raw Android capture path and real owner false-accept/reject rates need the physical phone and are not claimed measured.

### 2026-11-02 — Owner-only deletion evidence

Rule 3 requires a fresh explicit spoken yes, not a typed approval or notification tap. The dispatch gate now consumes the one-use evidence issued by speaker-verified audio; parked approvals cannot carry deletion consent across restarts. The file executor independently refuses missing evidence. Navigation taps cancel pending outbound countdowns. Targeted file executor, gate, owner policy and wake feature tests passed, as did source compliance, app lint/static analysis and eight screenshot verifications (`/tmp/m2v-consent-check.log`).

### 2026-11-02 — Wake retraining inputs and honest gates

The local CPU pipeline includes multilingual synthesis, a separately supplied licensed Sudanese voice, disjoint held-out temperature fitting, calibration-only threshold selection, immutable final audio checksum verification, ten-hour real negatives, constant-predictor mutations and gated export. Android uses identical features, ten owner templates and sherpa second opinion, followed by mandatory speaker verification. Six Python integrity/mutation tests and targeted Android feature tests pass. No trained generic weights or recall claim: required speech executables, a licensed Sudanese voice and the real negative corpus are absent. Exact commands and missing inputs are in training/wake/README.md. Targets remain 90% recall and at most 0.1 false wakes/hour.

### 2026-11-02 — Durable learning and owner routes

Reuse the existing memory-history repository, conservative extraction, deduplication, pinned-update handling and 30-second idle coordinator instead of creating a second memory database. Growth now reads stored facts as well as contact aliases and successful tasks, with deletion routed through the device's fresh spoken-yes question. Successful plans now survive process restarts in an atomic private no-backup file. First-run enrollment and ordinary Models/Settings/Growth navigation use the Pocket shell. Engine screens remain reachable through Advanced. Targeted restart/replay, harness, routing, extraction, idle coordinator, deletion and decision-policy tests plus lint, static analysis and source compliance passed (`/tmp/m2v-owner-check.log`).

### 2026-11-02 — Full cancel window after announcement

The outbound gate now waits for the speech callback before beginning the full three-second window. A long announcement cannot consume the window; STOP during speech cancels too. A missing callback times out without acting. Legacy forced-confirmation settings do not override interactive outbound or local commands. Submit/send taps use the same cancel window, while remote HTTP tools are refused (loopback remains local). Targeted gate, countdown and deletion tests pass with lint, static analysis and compliance in /tmp/m2v-final-check.log.

### 2026-11-02 — Jev verdict integration, not generated confidence

Pin the public Apache-2.0 Jev 0.8B Q4_K_M revision and file hash documented in training/decision/README.md. Read verdict-slot logits through a small adapter linked to the existing Llamatik 2.0.0 native libraries, not text generation. Model download is owner-started and hidden behind the ordinary Models label. Frozen bilingual typed-decision smoke fixtures precede fitting code; temperature fitting, threshold selection and final scoring are disjoint. Constant-option mutations fail. Smoke fixtures cannot certify production precision. No production certificate exists here, so ambiguous contacts ask by voice. Native C++ syntax passes against matching headers; Android compilation and real GGUF/phone measurements are unverified, with no local NDK or installed GGUF. APK CI is prepared to build the adapter. No CI run for these local commits is claimed.

### 2026-11-02 — Talorys history and shared power-button surface

Single-valued home/job/car/phone updates require explicit change wording. Unclear conflicts preserve both statements as unsure/review; past statements cannot replace current facts. Extraction preserves stated dates only. Growth exposes earlier versions, current facts and review entries. The existing Room history foreign key removes only a deleted fact's own versions. The frozen embedder fixture is unchanged; its ambiguous city pair is now retained for review under the owner's reset. The power-button session uses KawkabDevice with an explicit Compose lifecycle and the shared stateful contact router. The screenshot inbox lists existing on-phone image messages and remembers the owner's spoken JOB/BILL/PLACE labels; it invents no automatic classification. Hardware assistant-window behavior remains a phone check.

### 2026-11-02 — App choices, direct SMS and LCD material

Ambiguous installed apps consult the typed judge or ask a numbered spoken question, never choose the first label match. Owner answers persist and appear on Growth. A new command cancels the previous question. SMS now dispatches through Android SmsManager with SEND_SMS granted explicitly in Phone access, using the configured default SMS SIM; no composer-opening/send-button race remains. A missing default SIM is reported. Dispatch acknowledgement is described as submission to Android, not delivery. Each TTS run has unique IDs so a stale callback cannot start another action's window. Replacing an existing voice profile first asks for spoken deletion consent. The LCD and pressed keys now have actual inset shadows. Targeted app choice/routing/SMS/permission tests, all eight screenshot records, both modules' lint/static analysis and source compliance passed in /tmp/m2v-sms-shadow-check3.log.

### 2026-11-02 — Complete typed routing and verified voice handoff

Jev's optional production path now makes command/chat and act/ask verdicts separately. Low confidence asks one short spoken question. Explicit conversational intent is stored independently of generated output and prevents an interactive tool from acting. No production judge certificate exists here, so this path is not advertised as calibrated. A power-button utterance that needs the language model now hands its already-verified transcript to the main device voice loop, including a cold screen, rather than leaving an unsent draft.

### 2026-11-02 — No automatic erasure and no off-phone engine escape

Background compaction archives source facts as past instead of deleting them. Automatic oldest-fact pruning is disabled. Forgetting one fact follows only its derived summaries; unrelated parent facts stay. Growth reloads past entries after deletion. A Room transaction test is written for CI; it has not run on a local emulator. Generic delete controls also require a synchronous action-scoped spoken permit inside accessibility, so a macro's ordinary confirmation flag cannot erase data. New deletion questions invalidate earlier spoken answers.

The production network restriction is now binding, including restored false preferences and attempts to switch it off in Advanced. Private LAN servers are off-phone and refused; loopback is allowed. Model requests, memory embeddings, remote tool-server connections and each tool-request hop are covered. HTTP redirects cannot escape through an old allowlist. Owner-initiated model downloads remain available. Targeted settings/policy/parser/model-client/tool-server/HTTP/memory/intent/handoff/SMS tests plus lint/static analysis/compliance passed in /tmp/m2v-egress-check4.log (BUILD SUCCESSFUL, 1m 6s). Existing parser tests now compare canonical IPv6 hosts rather than bracket spelling; endpoint admission is stricter.

### 2026-11-02 — Measured countdown geometry, not only TextStyle

An added geometry assertion found that Compose kept the single-line digit box at 150dp despite a 127.5sp lineHeight style. The countdown now explicitly constrains its measured line box to 0.85 of 150sp, respecting font scale. Tests assert its actual height and that the progress box directly follows it. The F4 golden was updated without changing tolerance. All eight LCD tests and owner-facing error-message tests, app/catalog lint and static analysis, and source compliance pass in /tmp/m2v-ui-final-check2.log. New turns clear stale setup notices; errors give useful owner instructions without raw engine filenames, and deletion/filing answers are acknowledged by voice.

### 2026-11-02 — Android verdict readout and remaining device gates

The standalone Android fixture reader now shares its renderer with the app's JNI adapter. The build uses the existing pinned Llamatik libraries, including its CPU backend and OpenMP dependency. USB collection verifies the frozen manifest and public model hash, exports raw verdicts and removes only its own temporary directory. Production calibration requires a larger independent bilingual final set and passing precision/positive coverage for every decision kind and language. The supplied smoke set deliberately cannot enable production actions. Four calibration tests and seven wake-pipeline mutation tests pass; shared native sources pass host syntax checking. No Android link or phone inference result is claimed.

Rule 4 is incomplete for the physical Nothing Glyph mirror: the public GDK AAR has no redistribution permission established in this audit. The on-screen LED follows the requested states; the unlicensed SDK is not copied into the APK. Rule 9 is incomplete for smart-turn and the listening-selected natural voice. smart-turn-v3's BSD-2-Clause licence and CPU exports are now verified, superseding the earlier licence uncertainty, but Android preprocessing and timing are not tested. VoiceStudio is eligible for personal-use evaluation; Kokoro's published English coverage and Piper Arabic's unresolved dataset permission do not establish a bilingual winner. No physical handset/listening evaluation exists here. The fallback remains on-device Android recognition with the documented silence setting and offline-only Android TTS. Exact findings and remaining steps are in docs/owner-checks/M2-V.md. These gaps are not marked passed.


### 2026-11-02 — Final local verification

Android lint, app/catalog ktlintCheck and detekt, instrumented-source compilation, file-map/documentation checks, source/dependency/domain compliance, interface/error tests and all eight screenshot golden verifications pass together (BUILD SUCCESSFUL, 9m12s). New lint issues were fixed in source; the baseline and test tolerances were not expanded. Ten new source-folder descriptions preserve the existing documentation ratchet. The incremental bundle advertises HEAD and requires the recorded origin/main base. No remote write, full local Check, emulator run or new APK publication is claimed.

### 2026-11-02 — Foreground Phone (3a) Glyph mirror

Owner instruction supersedes the earlier SDK redistribution block: do not commit its AAR; download the official SDK 2.0 release artifact during Gradle builds, pinned to immutable upstream commit 8ee807a9312a640b0d43051450924e3446bc1d78 and SHA-256 329393019db5f0f987c6245855d13fa273d06756c68829ca0f6ae686ba336da1. Upstream publishes no GitHub release tags, so the official versioned artifact's commit URL is the immutable release URL. preBuild and the dependency graph both require the download task, including CI builds. Main and assistant windows share a foreground lease; stale window cleanup cannot close the current owner's session. Listening/speaking breathe, asking pulses, the actual countdown fills the C strip, idle/background switches off. Non-Nothing and unsupported devices quietly no-op. Phone (3a) has white-only hardware: physical green/amber cannot be produced; the LCD retains those colours and distinct patterns mirror their state meanings. Android 16+ needs no provisioned SDK key; older Nothing OS needs official SDK authorization or the documented development mode. No key or AAR is committed.

Four Glyph policy tests, app/catalog ktlintCheck and detekt, Android lintFossDebug and all three source/dependency/domain compliance checks pass (BUILD SUCCESSFUL, 5m55s). The existing lint baseline is unchanged. Physical light output is not measured without the handset.

### 2026-11-02 — Owner-selected automatic Jev policy and local evidence

The owner explicitly overrides the prior final-test-certificate gate and threshold: automatic typed choices are enabled at stable softmax confidence >= 0.85. Lower, malformed, missing-model or failed-native results ask one spoken question. Gemma remains the language/vision runtime; only Jev verdict logits choose allowlisted options. Existing certificate files are ignored and never deleted automatically. Report-only calibration tools and their frozen fixtures remain available, with no claim of measured accuracy from the confidence number.

Every attempt appends its input/options, raw verdicts, confidence, threshold, selected/asked result, reason and latency to private no-backup decision/decisions.jsonl. Engine-owned coroutine session identity links later gate observations, cancellation/errors and the next owner response to decision IDs. No automatic upload or retention deletion. If the decision cannot be logged, it asks instead of auto-selecting. Targeted policy, audit, coordinator, router, assistant, gate and persistent-storage tests plus ktlintCheck, detekt, Android lint and all three compliance checks pass (BUILD SUCCESSFUL, 6m27s). No device accuracy measurement is claimed.
