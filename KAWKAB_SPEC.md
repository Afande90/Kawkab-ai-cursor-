# KAWKAB (كوكب): Build Spec for Codex (v1.1)

**Owner:** Ibrahim Osman · **Spec date:** 26 Oct 2026 · **Builder:** Codex · **Reviewer:** an independent code reviewer chosen by the owner, who reviews each milestone diff against this spec.

**Version note:** v1.0 was reviewed blind (22 findings: 4 blockers, 12 majors, 6 minors). v1.1 addresses every finding. `review-v1.md` sits next to this file.

Kawkab is a private personal assistant on one Android phone. You wake it by saying "Kawkab". It:
- does things on the phone (calls, messages, alarms, apps);
- files and finds the owner's screenshots;
- remembers what matters;
- asks before anything leaves the phone;
- gets better from the owner's corrections.

Everything runs on the phone by default, and nothing needs a paid service.

---

## How to use this spec

1. Read the whole file before writing code. Build milestone by milestone (§13).
2. Every milestone has three parts:
   - **A: automated acceptance.** Codex must pass it. It gates the next milestone.
   - **R: report-only measurements.** Recorded honestly. They never gate and are never tuned to look good.
   - **B: owner checks.** Written to `docs/owner-checks/M<n>.md`. **B never gates Codex.** The owner has said he will not run manual tests.
3. Where this spec is silent: decide, record the decision with its date in `DECISIONS.md`, and keep going. Never stop to ask.
4. **Retry cap.** Each A gate gets at most 3 serious attempts.
   - At the cap, mark the gate `BLOCKED` in `DECISIONS.md` with the measured number, the cause and the best fallback, then continue to the next milestone.
   - A `BLOCKED` gate is never reported as passed.
5. **Thresholds in this spec are fixed.** Only the reviewer may change them. Codex never loosens a threshold to pass.
6. **No self-graded passes.**
   - A gate result is invalid if its test or eval file was changed in the same commit as the code under test.
   - Eval data is frozen before tuning (§12.3).
   - Every eval script must fail when the model is replaced by a constant answer (mutation check).

---

## 0. Hard rules (non-negotiable)

### 0.1 Anthropic and subscription boundary

1. **Kawkab never calls Claude.**
   - No Anthropic API key, claude.ai login, Claude token or Anthropic SDK.
   - No Anthropic/Claude entry in any provider list.
   - Knotwork's bring-your-own-key provider list includes Anthropic. In M0, Codex finds and deletes it. Grep first: the `CloudProvider` enum, provider client construction, the model resolver, settings screens, strings, tests and docs (`docs/extending.md` §3 lists the touch points).
2. **No subscription logins of any kind power the app.** That covers Claude, ChatGPT and Gemini consumer accounts. Donor paths that offer them are never ported: Pi `/login`, OpenHuman.
3. **Compliance checks, all in CI:**
   - **(a) Source grep.** Case-insensitive `anthropic|claude` across the repo, including Gradle files, lockfiles and `.github/**`. Excluded paths, exactly:
     - `KAWKAB_SPEC.md`, `review-v1.md`, `DECISIONS.md`, `docs/**`
     - `THIRD_PARTY.md`, `NOTICE`, `LICENSE`, `MODELS.md`
     - `third_party/**/LICENSE*`, `third_party/**/METADATA`
     - `tests/compliance/**`

     Any other hit fails the build.
   - **(b) Dependency check.** `./gradlew :app:dependencies` output must contain no `anthropic` artifact.
   - **(c) Domain check.** The merged manifest, network security config and string resources must not contain `api.anthropic.com` or `claude.ai`.

### 0.2 Cost, privacy and the network

**Zero ongoing cost.** No paid API, hosting or SaaS is required.

**On-device by default.** The only network traffic allowed is:
1. model and voice downloads that the owner starts (Hugging Face or GitHub releases), with SHA-256 verification;
2. the optional cloud text fallback (rule 3 below).

Nothing else, ever. The ARTEMIS scorer in §12.4 runs on the build machine and is never part of the app.

**Cloud fallback is off by default and text-only:**
- Images, audio and screenshot content are never sent to any cloud model, whatever the sensitivity label.
- Text goes only with per-request owner consent.
- Enforced by a unit test: the cloud client rejects any image or audio content part.
- Also enforced by a network test with the fallback enabled.

**No telemetry, analytics or off-device crash reporting.** Keep Knotwork's `foss` flavour free of proprietary dependencies.

### 0.3 Licences and attribution

**M0 licence audit.** Codex builds `LICENSES-AUDIT.md`: one table of every library, ported file set, model, voice and dataset, with these columns:
- licence
- terms: commercial, non-commercial, attribution, gated, prohibited-use policy
- verified from: file and URL
- shipped in APK (yes/no)

Rules:
- Only verified, compatible items ship or are downloaded by the app.
- Unknown-licence items, such as the Sudanese Whisper fine-tunes and `arbml` datasets, may be used for local evaluation only, never shipped or downloaded by the app.

**Ported code:**
- Keeps upstream copyright headers.
- Gets a "Modified by Kawkab contributors" notice (Apache-2.0 §4b).
- Lives in a clearly named package or `third_party/<project>/` with a `METADATA` file, following ARTEMIS `third_party/mobile_use/METADATA`.
- Is recorded in `THIRD_PARTY.md` and `NOTICE`.

**Specific checks:**
- **ARTEMIS's embedded Minitap mobile-use:** check `third_party/mobile_use` licence files before porting any of its code. If unclear, take ideas only.
- **Gemma models** carry Google's Gemma terms and Prohibited Use Policy, not Apache-2.0 (check each repo's model card; the Gemma 4 LiteRT mirrors may differ). The About screen and `NOTICE` must carry the required Gemma notice and a pointer to the Prohibited Use Policy. Fine-tunes of Gemma models inherit those terms.
- **Piper voices** have per-voice licences. Check before shipping a download link.

**Reference only, never copied:**
- AGPL code (VoiceStudio).
- Non-commercial material (30u30, CC BY-NC-ND).

### 0.4 Safety rules

1. **The `OUTBOUND` risk tier.**
   - Add a new tool risk tier above Knotwork's existing ones.
   - It always shows a confirmation card that needs a **tap**.
   - No policy setting can quiet it. That includes Knotwork's "Approve tool calls: Never", per-tool risk overrides and MCP overrides.
   - Outbound covers:
     - sending a message or email
     - placing a call
     - posting
     - sharing or uploading a file
     - submitting a form
     - opening any payment or checkout screen
     - deleting the owner's data
2. **Generic tap guard.**
   - The ported accessibility service is generic and can tap anything.
   - So any tap, from any entry point (tool, skill, macro, routine), is gated as `OUTBOUND` when its target node's text, content description or view id matches the outbound pattern list. The list lives in `safety/outbound_patterns.txt`, includes Arabic, and is unit-tested. It covers at least: send, post, pay, buy, share, submit, transfer, confirm order, ارسال, إرسال, ابعت, دفع, شراء, تحويل, مشاركة, تأكيد.
3. **Voice confirmation is off by default.**
   - The owner may turn on "confirm by voice" in settings, behind a warning that a TV or recording could say it.
   - Even then, voice confirmation is accepted only with the screen on and unlocked.
   - Voice lock (§7) never authorises an outbound action on its own.
4. **No acting in banking, payment, wallet, authenticator or password-manager contexts.** Checked at every accessibility event and immediately before every action, not only at planning time. Three layers:
   - **(a) Package denylist.** Ships with UAE banks and payment apps, Google Wallet, authenticators, password managers and the Settings security pages.
   - **(b) Heuristics:**
     - `FLAG_SECURE` windows
     - nodes with `isPassword`
     - text matching IBAN, card number or OTP patterns
     - checkout or payment screens inside any app
   - **(c) Intent guard.** Intents and deep links that target a denylisted package or a payment URI are refused.

   When blocked, Kawkab hands control to the owner and says why. Editing the denylist needs biometric or device-PIN confirmation.
5. **Kawkab never widens its own autonomy.** Learning may change accuracy and decision thresholds within owner-set limits, never which actions skip confirmation. Skills, macros and accepted routines inherit the strictest risk of any step inside them, and their replay never skips confirmation.
6. **Every action is logged** in an on-device activity log, exportable and never uploaded. Each entry records:
   - what was asked
   - what ran
   - the entry point
   - the result
   - which confirmation was given
7. **Everything learned is visible and deletable** (§9).
8. **Logins, passwords and CAPTCHAs are handed back to the owner** (Open-AutoGLM's take-over pattern). Kawkab never types a password.
9. **A CI test enumerates every registered tool executor**, including ported ones, against a committed classification table `safety/tool_classification.csv`. It fails if any outbound-capable tool is not `OUTBOUND`, or if any tool is unclassified.

### 0.5 Target hardware and memory

**The phone:** Nothing Phone (3a): Snapdragon 7s Gen 3, 12 GB RAM, 256 GB storage, Android 16 (API 36), Nothing OS 4.0. It is a mid-range processor, so design for seconds per step.

**Background killing.** Nothing OS kills background apps aggressively. Kawkab:
- runs a foreground service;
- asks once for battery-optimisation exemption;
- explains the Nothing OS setting in onboarding.

**Sideloaded accessibility on Android 13+.** It requires the owner to tap "Allow restricted settings" in App info. Onboarding shows this step with a screenshot.

**Memory plan.** Measure every row in M0 to M4 and record it in `MODELS.md`.

| Model | File size (approx.) | When resident |
|---|---|---|
| Brain: Gemma 4 E4B, or E2B if memory is short | about 3.7 GB / 2.6 GB | While a request is being handled; unloaded after 2 minutes idle |
| Judge: 0.8B GGUF Q4_K_M | about 0.53 GB | With the brain, or alone for inbox work |
| Embeddings: EmbeddingGemma 2 | measure | Loaded for inbox and search batches |
| OCR | small | On demand |
| Wake-word spotting + VAD | small | Always, in the foreground service |
| Speech-to-text | measure | From wake until the end of the turn |

- **Budget:** total Kawkab memory ≤ 5 GB, measured in the on-phone self-test.
- **Scheduler policy:** inbox processing batches items, so the brain loads once per batch, not once per screenshot.
- If the budget cannot hold E4B together with the judge and embeddings, E2B becomes the default brain and the decision goes into `DECISIONS.md`.

**Distribution:** a sideloaded APK and an F-Droid-style `foss` build. There is no Play Store submission. Release signing uses the debug key, as Knotwork does when no key is configured. This is recorded.

**APK size gate:** arm64 `foss` release ≤ 150 MB. Models are downloaded, never bundled.

### 0.6 Languages

- Sudanese Arabic, Modern Standard Arabic and English, mixed freely.
- The UI is fully bilingual with correct right-to-left layout.
- A unit test fails if any `strings.xml` key lacks an `ar` translation.
- A Compose test asserts `LayoutDirection.Rtl` under the Arabic locale.
- Screenshot goldens are informative only.

### 0.7 Build environment

**CI:** GitHub Actions Linux runners with KVM, used for builds and emulator tests. Never use macOS runners.

**Tiers of CI work:**
- **Per pull request:** the build, unit tests, compliance, the safety tests and the Tier 1 emulator smoke subset.
- **Nightly or manual:** the full Tier 1 suite and all Tier 2 evaluations (§12).

**Pinned toolchain:** keep Knotwork's pinned JDK, Gradle, Android SDK and NDK versions. Record the exact versions in `DECISIONS.md`.

**Native builds:**
- llama.cpp and sherpa-onnx are built once per pinned commit and cached as prebuilt `.so` artifacts, keyed by commit and NDK version.
- They are never rebuilt on every PR.

**Static analysis:** Knotwork's detekt, lint, file-map and coverage checks stay hard.
- Ported code gets one documented baseline update per port, recorded in `DECISIONS.md`.
- After that, new code must meet the gates.

**CI minutes:** if free CI minutes run out, the build machine runs the same scripts locally, using `scripts/ci-local.sh`.

---

## 1. What v1 is, and is not

**v1 does:**
- Wakes on "Kawkab". There is also a tap-to-talk button and a Quick Settings tile.
- Handles five core command types reliably:
  1. call someone
  2. send a text message (SMS)
  3. set an alarm or timer
  4. open an app
  5. add a calendar event
- Messaging apps such as WhatsApp are best-effort through accessibility, with an outbound confirmation.
- Reads the screen on request ("read my screen") and answers "what was that screenshot about".
- Keeps a screenshot inbox: new screenshots and anything shared to Kawkab are read, categorised, filed or sent to review, and are searchable by meaning.
- Keeps memory with change history.
- Learns from corrections, visible on a Growth screen.
- Gives a morning brief and deadline reminders.

**v1 does not:**
- listen all the time;
- run on iOS;
- act in banking or payment contexts;
- talk on phone calls;
- write or run its own code;
- use any cloud service by default;
- train models on the phone (M7 is plumbing only, off by default).

---

## 2. Architecture decision

### 2.1 Base: fork Knotwork

Knotwork is at github.com/alexeyw/knotwork, v0.12.1. Pin the exact commit. It is Apache-2.0, built with Kotlin, Jetpack Compose and Hilt, minSdk 34, and already has:

- **On-device inference** through LiteRT-LM, with Gemma 4 E2B/E4B presets and multimodal image input to the on-device model (`docs/architecture.md` §3.7).
- **Typed-node pipelines** (14 node types) with an editor.
- **`LocalToolExecutor` tools** with risk levels and a confirmation gate (`docs/extending.md` §2).
- **Integrations:**
  - an MCP client (Streamable HTTP and SSE, custom headers, loopback cleartext with consent);
  - AppFunctions calling, on API 36+;
  - share target, shortcuts, Quick Settings tile and triggers.
- **Memory and security:** long-term memory with semantic search, a SQLCipher database, Keystore-sealed keys and encrypted backup.
- **Run records:** reproducible run records and resume from the last completed node.

### 2.2 Order of work in M0

1. **Rename** to `app.kawkab.android`, app name Kawkab (كوكب), as one mechanical commit with the full build green.
   - Covers the package, applicationId, Room schemas, SQLCipher and Keystore aliases, `buildSrc` checks and fastlane metadata.
   - Knotwork is credited in `NOTICE` and on the About screen.
2. **Remove** the Anthropic provider (§0.1).
3. **Run the licence audit** (§0.3).
4. **Restyle** with the owner's Kawkab mockup:
   - deep-space ground;
   - electric indigo used sparingly;
   - amber for "needs you";
   - the planet motif;
   - via the `catalog` module tokens.

### 2.3 Hands: port from OpenDroid

**Source and licence:**
- Use upstream yashab-cyber/opendroid at a pinned commit.
- Compare it with the owner's snapshot in his toolbox backup (`kawkab-tools/opendroid-main.zip`, the JMAN730 fork state, v1.0.7), and record which you used.
- Both are Apache-2.0.

**What to take:**
- the AccessibilityService;
- the app automators and action executors (`accessibility/`, `actions/`);
- the alias resolver;
- macro recording;
- habit detection.

Wrap every action as a Knotwork `LocalToolExecutor` with a risk tier from `safety/tool_classification.csv`.

**Do not port:**
- OpenDroid's LLM providers;
- its social-media manager;
- its finance, food-shopping and transport actions;
- its MCP server.

**Permissions:**
- Prefer intents:
  - `ACTION_SENDTO` with an `smsto:` URI, plus an accessibility tap on the send button, gated `OUTBOUND`;
  - `ACTION_DIAL`, or `ACTION_CALL` after confirmation;
  - `AlarmClock` intents;
  - `CalendarContract` insert.
- Keep `CALL_PHONE` only for `ACTION_CALL` behind the `OUTBOUND` gate.
- Do not request `SEND_SMS` or `SYSTEM_ALERT_WINDOW` unless a measured need is recorded.
- Update `PRIVACY.md` and `SECURITY.md` to describe the accessibility service honestly.

### 2.4 Optional MCP spike

The zero-code spike (Knotwork talking to OpenDroid's MCP server) is optional and time-boxed to one hour. It is not part of any gate.

---

## 3. Modules

| Module | Job | Built from |
|---|---|---|
| `brain` | Plan, answer, write; read images | Knotwork LiteRT-LM engine, Gemma 4 E4B (E2B fallback) |
| `fastpath` | Map short commands to one tool call | Deterministic Arabic/English grammar for the 5 core types first; a prompted model only for leftovers |
| `judge` | Typed decisions with confidence: category, act-or-ask, urgency | §5 |
| `hands` | Operate the phone | Ported OpenDroid + ARTEMIS mechanisms (§4) |
| `eyes` | Screenshots and screen reading | Accessibility screenshot, gallery watcher, OCR, Gemma vision |
| `ears` | Wake word, VAD, end of turn, speech-to-text, voice lock | sherpa-onnx (Apache-2.0) |
| `mouth` | Speak replies | sherpa-onnx TTS (§6) |
| `memory` | Facts with history, inbox items, search | Knotwork memory + Talorys rules + embeddings |
| `growth` | Rulebook, corrections, skills, Growth screen | ACE-style playbook, OpenDroid macro recording |
| `proactive` | Morning brief, reminders | Knotwork triggers + Talorys scheduling rules |
| `selftest` | On-phone measurements | §12 |
| `training` | Flag off; plumbing only in v1 | §10 |

**Pipeline rule (from DeerFlow and Weft):**
- Every step gets a small, isolated context containing only its own job.
- Completed steps are summarised.
- Results go to files.
- Old screenshots become short text summaries (ARTEMIS history compression).

**Speech rule (from Parlor):**
- Actions never ride the speech. The spoken reply and the action decision are separate calls.
- The action decision is grammar-constrained JSON.

---

## 4. Hands: how Kawkab operates the phone

1. **Order of preference:**
   - AppFunctions, when the target app exposes one. That needs API 36+, and few third-party apps expose them today.
   - Then a direct Android intent.
   - Then accessibility-tree actions.
   - Then OCR and vision coordinates, as a last resort.
2. **Look before tapping.** Before each tap, confirm the target still exists in the live accessibility tree and on screen. If not, re-observe. This is ARTEMIS's pre-execution check.
3. **Act, then verify in the background.** A verifier checks the previous step against system state. On deviation, roll back to the last good state and re-plan. This is ARTEMIS's optimistic pipeline.
4. **Tap bursts** handle transient menus.
5. **App finder:** a plain lookup table plus fuzzy match, in Arabic and English, from app names to package names.
6. **Contacts:** fuzzy, relationship-aware resolution using the ported alias resolver. If the match is ambiguous, ask with tappable choices.
7. **Shizuku** is optional and never required.
8. **Locked phone:** intent-only actions that Android allows while locked (alarm, timer) run. Anything else asks the owner to unlock.

---

## 5. Judge: typed decisions with confidence

**Default:** the Jev-style decision model `chaoliangUNSW/Jev-Style-0.8B-Decision-v3-GGUF`.
- Q4_K_M quantisation.
- Verify its licence in M0. Arabic is in its listed languages, but quality is unverified.
- It runs on llama.cpp built for Android arm64, through JNI, as a cached prebuilt `.so`.
- Decisions are read from next-token probabilities over the option labels in one forward pass.
- References: the `jev_score.cpp` shipped in that repo, khimaros/verdict, and Pi `packages/ai` (MIT).

**Defined fallbacks, simplest first.** Used automatically if the default cannot be downloaded, built or calibrated:
1. Logistic regression or a small MLP over EmbeddingGemma vectors (text plus image), trained on the training split. This is easy to calibrate.
2. A prompted Gemma 4 E2B with grammar-constrained output.

**Splits:** three disjoint splits from frozen data (§12.3):
- calibration (temperature fit);
- threshold selection;
- final test.

All reported numbers come from the final test split.

**Policy:**
- Act automatically only where final-test auto-acted precision is ≥ 95%.
- Below that, ask with tappable options.
- If no threshold reaches 95%, the judge never auto-acts. It only suggests.

---

## 6. Models and access

**Nobody will supply a Hugging Face token.** For each model:
- If it is gated or unreachable, use the next fallback automatically.
- Record the substitution in `MODELS.md`.
- Any gate that depended on the missing model becomes report-only.

`docs/owner-checks/models.md` lists what the owner could do later to unlock a gated model: accept the licence on Hugging Face and paste a token on the Models screen. It never gates anything.

| Role | Default | Fallbacks, in order | Notes |
|---|---|---|---|
| Brain and vision | `litert-community/gemma-4-E4B-it-litert-lm` | `litert-community/gemma-4-E2B-it-litert-lm` | Knotwork presets; Gemma terms apply |
| Leftover commands | Prompted brain with grammar-constrained JSON | — | No fine-tuning in v1 unless a free GPU path is available; the gate does not depend on it |
| Judge | Jev-Style 0.8B v3 GGUF | Embedding classifier, prompted Gemma E2B | §5 |
| Speech-to-text | Whisper multilingual (MIT) via sherpa-onnx, size chosen by M4 measurement | Sudanese fine-tunes `AymanMansour/Whisper-Sudanese-Dialect-*` for local evaluation only until licensed | Report Sudanese error rate |
| Wake word | sherpa-onnx keyword spotting with "Kawkab" as keyword | openWakeWord-trained model, only if training can run on the build machine | KWS needs no GPU |
| VAD | Silero VAD | — | |
| End of turn | `pipecat-ai/smart-turn-v3` | VAD silence timeout | Verify licence |
| Voice lock | sherpa-onnx speaker verification | — | Convenience filter, never security (§7) |
| Speech out | A licence-verified Arabic Piper voice via sherpa-onnx, plus an English voice | MOSS-TTS-Nano-100M if exportable to an on-device runtime and licence-cleared | |
| OCR | PaddleOCR Arabic mobile recogniser + a Latin recogniser, via an on-device runtime | — | Exact text first, then the brain interprets |
| Search embeddings | `litert-community/embeddinggemma-2-text-vision-440m-litert-lm` | `litert-community/embeddinggemma-2-text-270m-litert-lm` | |
| Screen specialist (M8 only) | `mPLUG/GUI-Owl-1.5-2B-Instruct` | `ByteDance-Seed/UI-TARS-2B-SFT` | |

Before using any repo id above, Codex confirms it exists and records its licence. Models download on first use with their size shown and SHA-256 verified.

---

## 7. Voice loop

1. Keyword spotting and VAD run in the foreground service. Audio never leaves the phone and is never stored outside a session.
2. On "Kawkab", in order:
   - an earcon;
   - VAD and end-of-turn detection;
   - speech-to-text;
   - `fastpath`, then `brain` if needed.
3. The reply shows the transcript first, then speaks sentence by sentence (Parlor).
4. **Barge-in:** speaking over Kawkab stops it.
5. **Voice lock:**
   - It is an optional convenience filter that ignores commands from voices that don't match the enrolled owner.
   - It is not a security control. It never authorises an outbound action, because those always need a tap (§0.4.3).
   - Report its false-accept and false-reject rates and a replay-attack test result.
6. Room mode (continuous listening) is not in v1.

---

## 8. Eyes and the screenshot inbox

1. **Inputs:**
   - a gallery watcher on the Screenshots folder (MediaStore observer, `READ_MEDIA_IMAGES`);
   - the share target;
   - "read my screen", which takes an accessibility screenshot.
2. **Per item, in a batch:**
   - OCR;
   - Gemma vision description;
   - judge decides the category (Job, Event, Money/Bill, Place, Idea, Reference, Contact, Other);
   - judge extracts fields (dates, amounts, names);
   - embed;
   - store.
3. **Routing:**
   - **Confident:** filed, and a reminder is offered when a deadline was found.
   - **Unsure:** a "Needs your eye" card with tappable choices.
   - Every correction is logged.
4. **Privacy:** screenshot content never leaves the phone (§0.2). A "sensitive" flag only hides an item from the brief and from search previews.
5. **Search:** embeddings, date filters and the image shown with the result.

---

## 9. Memory and growth

**Memory rules (from Talorys memory history, MIT):**
- Each fact has a status: current, past or unsure.
- Nothing is overwritten silently.
- Explicit change words on single-valued topics make the old fact past and link it to the new one. The words include sold, moved, no longer, بعت, اتنقلت, ما عاد. Single-valued topics include home, job, car and phone.
- Unclear conflicts keep both facts and go to a review queue.
- A date is recorded only when it is stated.

**When memory is written (from DeerFlow):** extraction runs after a quiet period or while charging, and drops near-duplicates.

**Growth loops, all on-device:**
1. **Rulebook (ACE playbook):**
   - After each task, record what worked or failed as short structured rules.
   - Rules are curated incrementally.
   - Only the relevant rules are injected into later prompts.
2. **Corrections:** every correction becomes a labelled example for the judge's training split.
3. **Skills by demonstration:**
   - The ported macro recorder turns a demonstrated task into a skill stored as a `SKILL.md`-format file.
   - Skills inherit the strictest risk of any step inside them (§0.4.5).
4. **Habits:** the ported habit detection proposes routines. An accepted routine still confirms its outbound steps.

**Growth screen:**
- What was learned.
- The accuracy trend.
- A Remove button on each item. Removing an item also removes everything derived only from it.

---

## 10. On-phone training (M7: plumbing only in v1, flag off)

Goal for later: the judge improves overnight from the owner's corrections. In v1 Codex builds only the safe machinery around training, demonstrated with a toy model. The real training result is recorded as unverified.

**Weights:** training needs original weights (safetensors), not GGUF.
- Record which repo provides them, for example `chaoliangUNSW/Jev-Style-0.8B-Decision-v3`, and its licence.

**Guards. Training runs only when all of these hold:**
- the phone is charging;
- the screen is off;
- battery is ≥ 50%;
- battery temperature is below a configurable limit, default 38 °C.

Also:
- At the temperature limit it pauses, and resumes once the battery has cooled.
- It runs on a duty cycle, default 30 s work then 30 s rest.
- It checkpoints after every step and resumes after any kill.

**Logging.** Every step writes a local log line with:
- time
- step duration
- battery temperature and level
- memory use
- loss

**Exam gate:**
- A new adapter replaces the old one only if it beats it on the frozen held-out split. Otherwise it is rolled back and the reason logged.
- Replay old examples so new learning does not erase old.

**Method references (ideas only, verify licences before using code):**
- MeBP: arxiv.org/abs/2510.03425
- MobileFineTuner: github.com/Edge-Intelligence-Lab/MobileFineTuner
- Online-SDFT: github.com/lin826/SLM-Online-SDFT
- Soup's eval gate and replay: github.com/MakazhanAlpamys/Soup

**M7 never blocks shipping.**

---

## 11. Known risks (Codex records how each was handled)

1. **Small-model phone control may be unreliable.** M1 measures the deterministic path; on-phone self-test numbers show reality. Fallbacks: prefer intents, narrow the command set, add the M8 screen specialist.
2. **Two native runtimes** (LiteRT-LM and llama.cpp) cost memory and APK size. Handled by the §0.5 budget and the APK size gate.
3. **Sudanese dialect** speech-to-text quality is unknown. It is reported honestly. Fallback: show the transcript for correction.
4. **Nothing OS background killing.** The self-test logs whether the wake-word service survives 8 hours, as a report-only item.
5. **Heat during training.** M7 is off and guarded.
6. **Donor drift.** Pin commits. Merge upstream changes deliberately, never automatically.
7. **Big-company overlap** (Gemini screen automation, Meta Muse, Nothing Essential Space). Kawkab's reasons to exist are on-device privacy, the owner's dialect and learning from him. Never trade those away.

---

## 12. Verification: proving it without the owner

### 12.1 Three tiers (each gate names its tier)

- **Tier 1, emulator** (`google_apis` x86_64 image, API 36, debug variant, which keeps all ABIs).
  - Real inference is **stubbed**. LiteRT-LM ships no x86_64 library, so any gate that needs Gemma on x86_64 is invalid.
  - Tier 1 proves the plumbing:
    - tool dispatch and the deterministic fast path;
    - the `OUTBOUND` gate and the denylist;
    - the activity log;
    - memory rules and the scheduler;
    - the RTL layout.
  - **Emulator setup by script** (`scripts/emulator-setup.sh`):
    - `adb shell pm grant` for runtime permissions;
    - `adb shell settings put secure enabled_accessibility_services …` and `accessibility_enabled 1`;
    - `appops` for restricted settings;
    - test contacts seeded.
  - **Messaging:** WhatsApp is not on emulator images, so Codex builds `testapps/stubmessenger`. It has a known compose screen and a send button, and stands in for messaging apps. WhatsApp itself is an owner check only.
- **Tier 2, build machine:** model-level accuracy with reference runtimes, labelled "reference runtime, not device":
  - judge: llama.cpp Python bindings or the CLI;
  - speech: sherpa-onnx Python;
  - OCR: an ONNX or Paddle runtime;
  - Gemma: GGUF builds of the same model family, with the differences from the device artifact stated in the report.
- **Tier 3, the real phone.** Report-only.
  - On first install, while charging and idle, Kawkab runs the non-destructive suite. No messages are sent and no calls are placed.
  - The suite includes a **parity check**: the same 50 judge items and 50 inbox items as Tier 2, with the accuracy difference reported.
  - It writes `selftest-<date>.json`. Owner check: share that one file back once (share sheet, or `adb pull`). If nobody does, these results stay on the phone, and the docs say so plainly.

### 12.2 Independent checking

Where possible, the evidence comes from outside the component being tested:
- system state confirms the action (the alarm exists in AlarmManager or the clock provider; the stub messenger received the message; the calendar row exists), never the model's own claim;
- a reviewer, not Codex, may change thresholds.

ARTEMIS (Apache-2.0) may be used on the build machine as an extra external scorer if a free Gemini key exists. It is never required and never part of the app.

### 12.3 Evaluation data rules

1. **Frozen first.** All eval sets are created and committed in their own commit before any model selection, threshold choice, prompt tuning or template work. Their SHA-256 hashes are recorded in `DECISIONS.md`. Later changes need a new version and the reviewer's approval.
2. **Disjoint splits.** Calibration, threshold selection and final test are kept apart. For synthetic data, the **test** split uses different TTS voices, different HTML templates and different phrasings from anything used to build or tune the system.
3. **Commands:**
   - at least 150 commands across the 5 core types;
   - English, Modern Standard Arabic and Sudanese phrasing, for example "رسل لأحمد إني متأخر", "صحيني الساعة ستة", "اتصل على أخوي";
   - each with its expected tool call.
4. **Wake-word audio:**
   - Positives: at least 500 "Kawkab" utterances from several TTS voices and speeds, mixed with noise.
   - Hard negatives: "Kawab", "Karkab", "Kawkaba", "كوكبة", "كباب", and at least 10 hours of real recorded negative audio from licensed corpora, for example Common Voice Arabic and English, and MUSAN noise and music. Verify licences in M0.
5. **Screenshots:**
   - at least 150 synthetic screenshots in Arabic and English, rendered from HTML templates (job posts, bills, maps, chats, event posters, receipts), labelled with category and fields;
   - plus a report-only set of public or Creative Commons real screenshots, if licensed ones can be found.
6. **Mutation check:** every eval script is run once with a constant-answer model and must fail. The run is recorded.

---

## 13. Milestones

Each milestone ends with:
- a tagged commit;
- updated `DECISIONS.md`, `THIRD_PARTY.md`, `MODELS.md`, `LICENSES-AUDIT.md`;
- `docs/owner-checks/M<n>.md`.

### M0: Fork, rename, remove, audit, CI

**A:**
- Rename commit green.
- Anthropic provider removed.
- All three compliance checks pass.
- `LICENSES-AUDIT.md` complete.
- `foss` arm64 release APK builds and is ≤ 150 MB.
- Every string key has an Arabic translation.
- RTL Compose test passes.
- Emulator setup script works.
- Tier 1 harness runs, with the stub engine.

**R:** build times and CI minutes used.

**B:** install the APK and open it in Arabic and English.

### M1: Hands and the five core commands (typed)

**A (Tier 1):**
- ≥ 90% success per core type on the frozen command test split, through the deterministic fast path, verified against system state.
- **100%** of outbound actions, across tools, skills, macros and the generic tap path, stop for a tap confirmation that no setting can disable. The tool-classification test passes.
- The denylist blocks all three layers (§0.4.4), including intents.
- An activity log entry exists for every action.

**R (Tier 3):**
- median command-to-action time;
- share of commands needing the brain.

**B:**
- type "call [contact]" and confirm;
- try a WhatsApp message.

### M2: Judge

**A:**
- The llama.cpp JNI module loads the judge on arm64 and x86_64, Tier 1, as a load test with a tiny model.
- Fallback selection works when the default model is missing.
- The policy never auto-acts below the 95% precision threshold (unit test).

**R (Tier 2, final test split):**
- accuracy
- expected calibration error
- auto-acted precision and coverage
- the same numbers for each fallback

**R (Tier 3):** parity difference and latency.

**B:** none.

### M3: Screenshot inbox and search

**A:**
- The gallery watcher, share target and batch pipeline work end to end in Tier 1 with stubbed models.
- The network test proves no image ever leaves the device.

**R (Tier 2, final test split):**
- correct category among auto-filed items
- share routed to review
- date and amount exact-match
- search top-3 accuracy

**R (Tier 3):** parity.

**B:** screenshot a job post and see it filed.

### M4: Voice

**A:**
- The wake-word service, VAD, end of turn, barge-in and the voice confirmation setting (off by default) work in Tier 1 with injected audio.
- Voice confirmation is refused when the screen is locked.

**R (Tier 2, test voices disjoint from tuning):**
- wake-word recall on positives
- false wakes per hour on real negative audio
- spoken-command intent accuracy
- Sudanese word error rate per speech-to-text candidate
- voice-lock false-accept and false-reject rates and the replay result

**R (Tier 3):**
- wake-word service battery use per hour
- 8-hour survival under Nothing OS

**Targets the reviewer will judge against:**
- recall ≥ 90%
- false wakes ≤ 1 per 10 hours
- intent accuracy ≥ 85%

**B:** say "Kawkab, set an alarm for six".

### M5: Memory and growth

**A (Tier 1):**
- Every Talorys memory-history case passes.
- A recorded correction changes the next identical decision in ≥ 90% of replayed cases, using stub models and the rulebook path.
- Every learned item is deletable, cascading to everything derived from it.
- Skill recording and replay work for 3 scripted multi-step tasks, and replay still confirms outbound steps.

**B:** open the Growth screen.

### M6: Proactive

**A (Tier 1):**
- A reminder missed by under 24 hours runs once.
- Duplicates never run twice.
- Reminders survive a reboot.
- Exact alarms use `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` where granted; otherwise inexact alarms plus WorkManager, as a tested fallback.
- The morning brief is generated on-device from the calendar, inbox deadlines and reminders.

**B:** read tomorrow's brief.

### M7: Training plumbing (flag off)

**A:** with a toy model on the build machine and the emulator:
- the guards are unit-tested with simulated sensor values;
- training resumes after a forced kill;
- the exam gate rolls back a worse adapter;
- per-step logs are written.

**R:** whether real judge training ran anywhere, and its numbers, or "not run".

M7 never blocks shipping.

### M8: Optional extras (only after M0 to M7)

- the GUI-Owl or UI-TARS screen specialist, if Tier 3 shows the brain path failing often;
- room mode;
- an optional desk link to a home-server agent (DeerFlow 2.0) over Telegram.

All are off by default, and each gets its own gate.

---

## 14. Donor index

Licences marked "verify" are confirmed in the M0 audit before use.

| Donor | Licence | What is taken |
|---|---|---|
| Knotwork (alexeyw/knotwork) | Apache-2.0 | Base app |
| OpenDroid (yashab-cyber/opendroid; snapshot of fork JMAN730/opendroid) | Apache-2.0 | Accessibility hands, actions, alias resolver, macro recording, habits |
| ARTEMIS (google/artemis) | Apache-2.0; embedded mobile-use: verify | Ideas: pre-tap check, verify and rollback, tap bursts, history compression, app finder |
| Parlor | Apache-2.0 | Voice-loop design, end-of-turn, actions separate from speech |
| Talorys | MIT | Memory history rules, scheduling robustness |
| DeerFlow 2.0 | MIT | Isolated contexts, idle memory extraction, SKILL.md skills |
| Pi (packages/ai) | MIT | Classifier interface reference |
| sherpa-onnx | Apache-2.0 | Speech stack |
| llama.cpp | MIT | Judge runtime |
| Soup | Apache-2.0 | Eval gate and replay ideas |
| Open-AutoGLM | Apache-2.0 | Take-over pattern |
| MeBP, MobileFineTuner, Online-SDFT | verify | M7 ideas only |
| VoiceStudio | AGPL-3.0 | Reference only, never linked or copied |

## 15. Files Codex maintains

- `DECISIONS.md`
- `THIRD_PARTY.md`
- `MODELS.md`
- `LICENSES-AUDIT.md`
- `NOTICE`
- `safety/` (outbound patterns, tool classification)
- `eval/` (frozen datasets with hashes, and scripts)
- `scripts/emulator-setup.sh`, `scripts/ci-local.sh`
- `testapps/stubmessenger`
- `docs/owner-checks/`
- `docs/architecture-kawkab.md`
- `docs/selftest.md`
- a short `README.md` in Arabic and English
