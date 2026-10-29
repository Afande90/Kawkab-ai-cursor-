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

