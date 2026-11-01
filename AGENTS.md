# AGENTS.md: instructions for Jules (and any coding agent)

You are building **Kawkab (كوكب)**, a private on-device personal assistant for Android.
The complete plan is `KAWKAB_SPEC.md` in the root of this repo. It is the only source of truth.
Read the whole spec before your first change in every task.

## OWNER'S VISION RESET (2 Nov 2026). This overrides anything below that conflicts.

The owner: "The whole point is the voice command, and then by itself it should proceed to calling. No point of me clicking any button whatsoever. I'm coping with what's in the app I'm trying to reverse-engineer, instead of the app taking what's in it and changing it to what I want."

Kawkab is **voice-first and acts by itself**. Knotwork is the engine underneath, not the product the owner sees. These rules replace spec §0.4.1 to §0.4.3 and the `OUTBOUND` tap rule:

1. **Voice → action, with no button.** "Kawkab, call Abdalla Mubadala" places the call. "Kawkab, text Omar I'm late" sends the SMS. "Kawkab, set an alarm for 6" sets it.
   - Before any outbound action, Kawkab says what it is doing ("Calling Abdalla Mubadala") and acts after a **3-second cancel window**. Saying "stop", "cancel", "لا" or "وقف", or tapping, cancels it.
   - Nothing needs a tap, and nothing needs a "yes".
   - Typed commands behave the same way, with the same cancel window shown on screen.
2. **Ambiguity is resolved by voice**, never by buttons. "Which Abdalla: Mubadala or Wad Abdalla?" The owner answers by voice ("Mubadala", "the first"). Kawkab remembers the answer and never asks again for that name.
3. **Keep only these hard blocks:**
   - never act inside banking, payment, wallet or password screens (the denylist stays);
   - never delete the owner's data without an explicit spoken "yes";
   - everything stays on the phone.

   Update the tool-classification test, `safety/` and `DECISIONS.md` to match: the outbound tier becomes "announce + 3 s cancel", not "tap".
4. **The UI is "Pocket LCD" (owner chose direction F, 2 Nov).** The whole app looks like one physical device, not a chat app. This replaces the planet mockup and all Knotwork/Material screens the owner sees. Build it in Compose as one shared `KawkabDevice` scaffold used by every screen:
   - **Body:** vertical gradient `#D6D2C9 → #C7C3B9`. Header row: "KAWKAB K-1" left, state word + a round status LED right (IBM Plex Mono 11 sp, letter-spacing 0.16 em, `#5E5A52`).
   - **LED colours:** grey `#6E6A62` standby · green `#3FBF4A` listening/speaking · amber `#F2A51A` asking/needs you · orange `#E2611B` acting. Mirror the same state on the Nothing Glyph lights while the app is in the foreground (Glyph Developer Kit).
   - **LCD:** dark bezel `#2A2925` (radius 18, padding 14) around a green panel `#A9B48A` (radius 8, inner shadow), text `#1E2416` in the VT323 font, all caps for app text. Arabic/Sudanese speech is shown inside the LCD in Noto Kufi Arabic, right-to-left, as spoken; labels stay English ("YOU:", "KAWKAB:").
   - **Keys:** three under the LCD: HOME, SHOTS, GROWTH (`#E7E4DD`, shadow `0 4dp 0 #9B978E`; the current screen's key looks pressed: `#BDB8AE`, inset shadow). These replace the bottom bar.
   - **Big round orange talk key** (`#E2611B`, 124 dp, shadow `#9C3F0E`) with speaker-grille dot patterns either side. It is optional: voice always works without it. Its label changes with state (HOLD TO TALK / HEARING YOU / JUST SAY IT / CUT IN / MIC ON). During a countdown it becomes a black STOP key.
   - **Screens (match the owner-approved canvas boards F1–F8):** F1 Idle ("SAY KAWKAB_" blinking cursor, date, battery, "> ALARM 06:00 .... OK" log of what was just done) · F2 Listening (pixel waveform, live transcript, "I ACT WHEN YOU STOP TALKING") · F3 Which ("WHICH ABDALLA?" numbered 1/2, "SAY 1 OR 2, I'LL REMEMBER") · F4 Calling (name, number, "YOUR USUAL", huge 3-2-1 digit, block progress bar, STOP) · F5 Talk (YOU/KAWKAB lines, result line like "> REMINDER SET TUE 09:00") · F6 Shots (screenshots sorted into [JOB] [BILL] [PLACE] boxes, one inverted "? … SAY IT" box) · F7 Growth (what it learned this week, "RIGHT FIRST TIME 86%" with a pixel bar chart, "SAY 'FORGET …' TO REMOVE", MY VOICE key) · F8 Voice enrollment ("SAY 'KAWKAB'", [■■■■■■□□□□] 6/10, "ONLY YOU CAN WAKE ME").
   - Bundle VT323, IBM Plex Mono and Noto Kufi Arabic as font resources (all OFL, no network fetch). Respect reduced-motion (no blinking cursor). Minimum touch target 48 dp.
   - Add a Compose screenshot test per screen F1–F8.
5. **Hide the engine.**
   - Behind Settings → Advanced: Pipelines, Tools, the console strip, token counters, model file names, the "uses · set_alarm" hints, and the raw tool cards.
   - The bottom bar becomes Home, Screenshots, Growth (as in the mockup).
   - Settings keeps only: Voice, Phone access, Models, Privacy, About, Advanced.
5b. **The app's own text is English only** (owner, 2 Nov): titles, labels, menus, buttons, settings and notifications. No Arabic titles, and no right-to-left layout for the interface. Kawkab still understands and answers Arabic and Sudanese in conversation, and shows that text as spoken. Replace the Arabic `values-ar` interface strings with English for every locale, and turn the Arabic-translation test into a check that the interface text is English.
6. **The wake word is priority one.** 43 % recall, and 0/18 for the Arabic voice, is not acceptable.
   - Train a proper "Kawkab" detector, openWakeWord-style, on synthetic speech in many voices: Arabic, Sudanese-accented and English, with noise. Keep the sherpa-onnx keyword spotter as a second opinion.
   - Target recall ≥ 90 % and ≤ 1 false wake per 10 h on the frozen set.
   - Fix the launch crash (M1.8) first.

7. **Kawkab answers only the owner's voice.** This replaces the spec's "voice lock is convenience only" for wake-up.
   - Add a one-time enrollment in setup: the owner says "Kawkab" about 10 times and two short sentences, in the app.
   - Use these to:
     - (a) personalise the wake-word detector on his own "Kawkab" (few-shot fine-tune or template match on top of the generic detector);
     - (b) build a speaker profile with sherpa-onnx speaker verification.
   - A wake or command from any other voice is ignored. This is also the protection that makes rule 1 safe, alongside the 3 s cancel.
   - Recordings stay on the phone and can be deleted and redone in Settings → Voice.
8. **Jev decides act or ask** (spec §5; the owner insisted on "Gemma WITH Jev" from day one, and it was never built).
   - Use a Jev-style decision model (Jev-Style 0.8B, GGUF on the Llamatik runtime) for typed decisions with calibrated confidence: which contact, which app, act now or ask, and whether this is a command or a chat.
   - High confidence: act, with the 3 s cancel. Low confidence: one short spoken question.
   - Gemma stays for language and images.
9. **Natural voice loop (Parlor).** Use Parlor's design: end-of-turn detection (smart-turn), sentence-by-sentence speech, barge-in, and "actions never ride the speech" (the action decision is a separate structured call).
   - The owner's personal-use rule now allows AGPL: evaluate VoiceStudio and Kokoro or Piper for a natural Arabic and English voice that runs on the phone. Pick by listening quality and speed, and record the choice.
10. **Memory and learning that the owner can see.** Pull forward from M5:
    - Talorys memory-history rules and DeerFlow idle memory extraction;
    - every choice the owner makes (which Abdalla, which app, how he phrases things) is remembered and reused;
    - the Genie skill cache replays successful tasks with no model call;
    - all of it shows on the Growth screen with a delete button.

Do M1.8 (31–35, including 34b, with 34b rewritten by rules 1 and 2) and then **M2-V (this section, rules 1 to 10)** before anything else in this file.

## Where the work lives

This repository is dedicated to Kawkab. Work on `main`.

## How to work

1. **One small piece per task.** Each Jules task does one numbered task from the task list below, then opens a pull request. Never try to do a whole milestone in one task.
2. **Before finishing a task:**
   - run the build and the unit tests in your VM;
   - fix what fails;
   - say in the pull request which checks you ran and their results.
3. **Never stop to ask questions.** When the spec is silent, decide, add the decision with the date to `DECISIONS.md`, and continue.
4. **At most 3 serious attempts per gate.** If it still fails, mark it `BLOCKED` in `DECISIONS.md` with:
   - the measured number;
   - the cause;
   - the best fallback.

   Then finish the task. Never report a BLOCKED gate as passed. Never fake a result.
5. **Thresholds are fixed.**
   - Never loosen a threshold.
   - Never edit a test or eval file in the same commit as the code it tests.
   - Freeze eval data before any tuning (spec §12.3).
6. **Emulator tests run in GitHub Actions, not in your VM.** Your VM has no emulator, so write the emulator tests and the workflow, and let CI run them. If CI results aren't visible to you, say so in the pull request rather than claiming a pass.

## When the builder runs on the owner's laptop

This is a ThinkPad X280: 8 GB RAM, 4 cores, no GPU. Probably Windows.

- **Gradle memory.** Do not commit memory settings. Set Gradle's memory in the user-level `~/.gradle/gradle.properties`: `org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m`, `org.gradle.workers.max=2`, `kotlin.daemon.jvmargs=-Xmx1536m`.
- **Local checks.**
  - Run only the targeted unit tests for the task (`:app:testFossDebugUnitTest --tests ...`), plus `ktlintCheck` and `detekt` on the changed module.
  - Never run the emulator or the full `check` locally.
  - Push, and let GitHub Actions run the full Check and the instrumented emulator tests. Read the result with `gh run list` and `gh run view --log-failed`.
  - A task is done only when the GitHub Check is green.
- **Android SDK.** If it is missing, install the command-line tools into `%LOCALAPPDATA%\Android\Sdk` and accept the licences. Write `local.properties` with `sdk.dir`, and never commit that file.
- **Never create an assistant-instructions file named after the coding tool (for example via an init command), and never write the coding tool's or its vendor's name into any file.** The compliance scan (spec §0.1.3) would fail the build. These instructions live only in AGENTS.md.

## Hard rules (spec §0)

- **No removed hosted vendor, ever.** Kawkab must never contain or call that vendor in any form: no key, no SDK, no provider entry, no login. Remove Knotwork's copy of that provider in M0.
- **No subscription logins.** Kawkab never uses any subscription login (ChatGPT, Gemini, or any other consumer account).
- **Zero cost.** No paid API, service or hosting. Everything runs on the phone. No telemetry.
- **Images, audio and screenshots never leave the phone.**
- **Outbound actions always need a tap.** Anything that sends, calls, posts, shares, pays, submits or deletes needs a tap on a confirmation card that no setting can switch off (spec §0.4).
- **Licences.** Kawkab is the owner's personal app and is not sold. Owner decision, 29 Oct 2026; this overrides spec §0.3's ban on AGPL and non-commercial code.
  - Any open-source code or model may be reused, including AGPL and non-commercial licences, as long as its licence allows personal use.
  - Keep upstream licence headers.
  - Credit every donor in `NOTICE` and `THIRD_PARTY.md`.
  - Mark AGPL and non-commercial items `personal-use only` in `THIRD_PARTY.md`, so they can be swapped out if the app is ever sold.
  - Still never copy anything that calls the provider banned in spec §0.1, or any cloud login.

## Environment setup (put this in the Jules environment setup script)

```bash
# JDK 17+ and Android command-line tools; use the versions Knotwork pins if they differ
sudo apt-get update && sudo apt-get install -y openjdk-17-jdk unzip
export ANDROID_HOME=$HOME/android-sdk
mkdir -p $ANDROID_HOME/cmdline-tools
cd /tmp && curl -sSLo tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q tools.zip -d $ANDROID_HOME/cmdline-tools && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

If a download link is out of date, find the current one, fix the script and record the change in `DECISIONS.md`.

## Task list

Do these in order. Each line is one Jules task. When you finish one, the owner sends: **"Do the next task in AGENTS.md."**

To know which task is next, read `PROGRESS.md`. Create it in task 1. After each task, mark that task done in it with the date and the pull-request link.

### M0: Fork, rename, remove, audit, CI
1. Import Knotwork (github.com/alexeyw/knotwork, v0.12.1) at a pinned commit into this repo. Keep its history if possible. Make sure it builds. Create `PROGRESS.md` and `DECISIONS.md`.
2. Rename to `app.kawkab.android`, app name Kawkab (كوكب), as ONE commit with the build green (spec §2.2).
3. Remove the extra hosted provider everywhere (spec §0.1).
4. Add the three compliance checks (spec §0.1.3).
5. Write `LICENSES-AUDIT.md`, `THIRD_PARTY.md`, `NOTICE` and the About-screen credits (spec §0.3).
6. Set up the GitHub Actions workflow: build, unit tests, compliance, APK size check ≤ 150 MB, and the arm64 `foss` APK uploaded as a downloadable artifact (spec §0.7).
7. Add the Arabic-translation and RTL tests (spec §0.6), then the emulator setup script and the Tier 1 harness with a stub engine (spec §12.1).
8. Restyle with the Kawkab look: deep-space ground #070A14, indigo #5457E8, amber #F2B45A for "needs you", and the planet motif. Then tag `m0`.

### M1: Hands and the five core commands
9. Add the `OUTBOUND` risk tier, `safety/tool_classification.csv` and the tool-classification test (spec §0.4.1, §0.4.9).
10. Port the OpenDroid accessibility service as `LocalToolExecutor`s (spec §2.3), with the generic tap guard (spec §0.4.2).
11. Add the denylist with all three layers, and the activity log (spec §0.4.4, §0.4.6).
12. Freeze the command eval set in its own commit (spec §12.3).
13. Build the deterministic Arabic/English fast path for the five core commands and the stub messenger app. Run the M1 gates, then tag `m1`.

### M1.5: Make it usable (owner-phone findings and proven donors, 29 Oct 2026)

**Order change by the owner, 29 Oct 2026:** after task 17, do task 20b next (the system assistant and the Qwen3 1.7B tool-calling score), including the llama.cpp runtime it needs (Llamatik, task 20). Then continue 18, 19, 20c, 21, 22.


The owner installed the `m1` APK on his Nothing Phone (3a). He found these problems:
- The phone freezes while the model answers.
- Typed commands do nothing.
- The default chat is Knotwork's "Virtual Companion" demo.
- "Knotwork" still shows on screen.

These tasks fix that by reusing projects that already solved it. Each donor is reused with credit in `NOTICE` and `THIRD_PARTY.md`. Never copy a hosted model provider, and never copy donor names or trademarks.

Donors:
- **PokeClaw** (github.com/agents-io/PokeClaw, Apache-2.0): Gemma 4 E2B on LiteRT-LM, phone control, 3-tier routing, skills, stuck detection.
- **PhoneCLI** (github.com/HKUDS/PhoneCLI, MIT): app maps and macros replayed with no model call.
- **OnDevice LLM** (github.com/sebytzr16/on-device-llm, MIT): action cards and an offline voice loop.
- **Google FunctionGemma 270M "Mobile Actions"** (ai.google.dev/gemma/docs/mobile-actions): a tiny model trained only for phone actions.

14. **Branding.** Replace every user-visible "Knotwork", its logo and the "Virtual Companion" persona with Kawkab (كوكب) and the planet mark, in English and Arabic. Keep the upstream credit in `NOTICE` and on the About screen. Add a test that fails if "Knotwork" appears in any user-visible string resource or preset pipeline name.
15. **Performance defaults.** The phone must stay smooth while the model works.
    - Default the inference backend to CPU, and log which backend actually started (LiteRT-LM can fall back silently).
    - Default the model to Gemma 4 E2B on phones below flagship class; E4B stays an option.
    - Keep the model loaded between messages, and unload it after 2 minutes idle.
    - Cap injected context at about 1,500 tokens.
    - Cap output at 300 tokens for tool or JSON turns.
    - Turn thinking mode off for simple turns.
    - Enable Multi-Token Prediction only on GPU.
    - Stream every reply.
    - Write the measured time to first token and tokens per second to `MODELS.md` (Tier 2), and add them to the on-phone self-test (Tier 3).
    - Also try LiteRT-LM's NPU backend, which the owner's Snapdragon 7s Gen 3 has. Pocket LLM (github.com/dineshsoudagar/local-llms-on-android, MIT) runs Gemma 4 on GPU and NPU through LiteRT. Treat NPU as experimental: enable it only if the self-test shows it starts without crashing and is faster than CPU, otherwise keep CPU.
16. **Kawkab assistant pipeline.** Make it the default for the first chat and for new chats, replacing "Virtual Companion". Its system prompt says:
    - it is Kawkab;
    - it runs fully offline on the owner's phone (no server);
    - it answers briefly in the user's language (Sudanese Arabic, Modern Standard Arabic or English);
    - it uses its phone tools.

    Remove the Virtual Companion demo pipelines from the first-run library, and keep them as importable presets.
17. **3-tier routing (port from PokeClaw):**
    - **Tier 1:** wire `CommandFastPath` into the chat input. Matched commands go straight to the hands tools with no LLM call, and still show the `OUTBOUND` tap card.
    - **Tier 2:** fixed skill recipes, with the LLM writing only the text. Start with: reply to a message, read my last messages, set a reminder from a screenshot.
    - **Tier 3:** the agent loop, using LiteRT-LM native tool calling, with PokeClaw-style stuck detection (repeated action, unchanged screen) and step and time budgets.

    Add an instrumented test that types "set an alarm for 6" into the chat screen and asserts the alarm intent fires, with no LLM call.
18. **Harder command test.** In its own commit before any parser change, freeze `evals/commands/v2`:
    - 150 commands, none of whose phrasings appear in v1;
    - include word-order changes, typos, mixed Arabic and English, and real Sudanese expressions;
    - include indirect requests ("I'm late, tell Ahmad").

    Record its SHA-256 in `DECISIONS.md`, then score the parser honestly. Improve the parser only in a later commit, and report both scores.
19. **App maps (PhoneCLI idea).** For Phone, Messages, Clock, Calendar, WhatsApp and Gmail, record each app's navigation as a stored map plus macros.
    - Common tasks replay these with no model call.
    - Each macro verifies the screen after every step and falls back to tier 3 on any mismatch.
    - Outbound steps still need the tap card.
20. **Model candidates.** Record these in `MODELS.md` and the licence audit:
    - FunctionGemma 270M Mobile Actions, as a candidate tier-2 intent model (fine-tune only if free compute is available);
    - Parakeet TDT 0.6B v3 and Kokoro, as M4 speech candidates (from OnDevice LLM).
    - **Llamatik** (github.com/ferranpons/Llamatik, MIT), a Kotlin library over llama.cpp with schema-constrained JSON and embeddings, as the candidate runtime for the §5 judge GGUF instead of hand-written JNI;
    - Qwen3-ASR-0.6B, as an extra M4 speech candidate.

    Do not ship any of them without a passing licence check.
20b. **System assistant and a better tool-calling brain.** Donor: **Local Assistant** (github.com/sergarsilla/local-assistant, Apache-2.0).
    - **Default digital assistant.**
      - Register Kawkab as Android's default digital assistant (`VoiceInteractionService` and the assistant role), so a long-press of the power button or the assistant gesture opens a Kawkab panel over the current app.
      - Read the current screen through the assistant API, only when the owner allows "use text from screen" and only on request.
      - Release the model a few seconds after the panel closes.
      - Add the steps for making Kawkab the default assistant to the phone-access setup.
    - **Tool-calling brain.** Its author measured Qwen3 1.7B choosing the right tool 10 of 10 times, against 2 of 10 for Gemma 4 E2B.
      - Add Qwen3 1.7B (Apache-2.0) as a tool-calling brain candidate.
      - Score Gemma 4 E2B, Gemma 4 E4B and Qwen3 1.7B on the frozen command sets (v1 and v2) through the tier-3 path, and record the scores.
      - Make the best measured scorer the default for tool calls.
      - Keep Gemma for images.
20c. **Small-model harness rules** (tier 3 and skills).

    **From Genie** (github.com/Akeem1955/Genie: Gemma 4 on LiteRT-LM, offline phone agent):
    - Turn off automatic tool calling. Every tool call is checked by app code before it runs.
    - One tool call per turn. The planner returns a typed decision (`Act`, `Reply` or `Finish`), and plain text during planning is invalid.
    - Keep a sliding history: the goal plus the last 9 steps, with transient errors pruned after a success.
    - Classify errors into 4 tiers, each with its own recovery.
    - Add two circuit breakers: 5 failures in a row, or the same unknown tool requested 3 times.
    - Keep a **skill cache**: store each successful plan and replay it next time with no LLM call, ranked by how often it succeeded. This feeds the M5 growth loop and is shown on the Growth screen, where it can be deleted.
    - Inject stated user facts into every prompt.
    - Assess risk from at least two independent signals before asking for biometric confirmation. This is on top of the `OUTBOUND` tap rule, never instead of it.

    **From PocketClaw** (github.com/ManojRakshu/pocketclaw, MIT). It ran Gemma 4 E2B on the owner's exact chip (Snapdragon 7s Gen 3) with a 1–3 s first token for chat and about 5 s for images.
    - Put the owner's name and identity at the top of the system prompt.
    - Store old images in chat memory as their text description (about 100 tokens), not image bytes (about 30K tokens).
    - Consider Gecko 110M as a smaller embedding model than EmbeddingGemma, and measure it.
    - Exclude unused native libraries from the APK.

    Never port a donor's cloud logins or subscription sign-ins.
21. **Phone-access setup.** The `m1` APK never asked for any permission. Add an onboarding step and a "Phone access" screen in Settings, in English and Arabic, with a status line for each item:
    - **Runtime permissions:** ask for contacts, phone, calendar, notifications and microphone, each with a one-line reason.
    - **Accessibility service:**
      - explain it in plain words;
      - deep-link to `Settings.ACTION_ACCESSIBILITY_SETTINGS`;
      - show the Android 13+ "Allow restricted settings" step (App info → ⋮), with a picture;
      - detect when the service is on, and show it as on.
    - **Battery:** request exemption from battery optimisation, and explain the Nothing OS background setting.
    - **Missing permissions:** when a command needs a permission that is missing, show a card that leads straight to granting it. The app must never fail silently.

    Add tests for the status detection and for the missing-permission card.
22. **Close out M1.5.**
    - Run the full check.
    - Update `docs/owner-checks/M1.5.md` with exactly what the owner should try on the phone.
    - Make sure the CI APK artifact exists.
    - Tag `m1.5`.

### M1.6: Owner-phone polish (30 Oct 2026)

These come from the owner's first install of `m1.5`.

23. **Phone access must be easy to find and read.**
    - Put "Phone access" as the first row in Settings (BASIC), not as a small link at the bottom of Privacy.
    - While anything is off, show a banner at the top of the chat, "Kawkab needs N permissions", that opens the page.
    - Redesign the page with design-system cards. Each card shows:
      - a title (Contacts, Phone, Calendar, Notifications, Microphone, Screen control, Run in background, Default assistant);
      - an icon;
      - the one-line reason;
      - a clear On/Off status chip, green or amber;
      - one full-width button.
    - Put a progress line at the top ("5 of 8 on").
    - Refresh the status when the owner returns from Android settings.
    - Write all of it in Arabic and English.
    - Add screenshot goldens and a test that the Settings row and the banner exist.
23b. **Floating Kawkab button.** The owner turned on Android's accessibility shortcut. The floating planet button appears, but tapping it does nothing, because the service never handles it.
    - Add `flagRequestAccessibilityButton` to `accessibility_service_config.xml`.
    - Register an `AccessibilityButtonController` callback. A tap opens the Kawkab assistant panel over the current app, the same panel as a long-press of power, with the current screen available as context once the owner allows it.
    - A long-press, if Android exposes one, may open the full app.
    - Add a unit test that the callback opens the panel.
23c. **The microphone works now, before M4.** The owner tapped the mic and got "This model can't transcribe audio", because the active brain is a text-only GGUF.
    - Until the M4 voice stack lands, the mic button uses Android's `SpeechRecognizer` with `EXTRA_PREFER_OFFLINE = true`, in Arabic (`ar-SD`, then `ar`) and English, the way PokeClaw v0.7.0 does.
    - The transcript goes into the input box, so the owner can correct it before sending.
    - Never send audio to a cloud recogniser. If offline recognition is unavailable for a language, say so and offer the language download in Android settings.
    - If no recogniser exists, hide the error card and show a one-line hint instead.
23d. **Owner phone, 31 Oct: the whole phone and the app are sluggish. Fix the accessibility service's work per event.**

    The cause, from code review: `KawkabAccessibilityService.onAccessibilityEvent` listens to `typeAllMask` and, on every event from every app, walks the whole node tree (`traversal.describe(rootInActiveWindow)`) and runs the denylist regexes, on the main thread. Fix:
    - Listen only to `typeWindowStateChanged` and `typeWindowContentChanged`.
    - Set `android:notificationTimeout` to 300 ms or more.
    - Keep only package name, `FLAG_SECURE` and password state per event; these are cheap.
    - Build the full text snapshot lazily, only when an action is about to run or the owner asks to read the screen.
    - Throttle denylist text inspection to at most once every 500 ms, off the main thread.
    - The safety rule still holds: the full denylist check runs immediately before every action (§0.4.4).

    Also profile tab switching in the release build:
    - Add a Macrobenchmark for "switch Chat → Pipelines → Tools → More" with frame timing.
    - Add a Baseline Profile.
    - Make sure no model load or inference runs on the main thread.

    Gate: median frame time under 16 ms on the CI emulator. Report the before and after numbers.
    - **Owner measurement, 31 Oct 22:30.** With Kawkab's accessibility service off and the CPU backend, tab switching improved only a little. So the accessibility work is one cause, not the main one. Switching to GPU then froze the phone and killed the app.
    - Next suspect: memory pressure. Up to three models may be resident at once (E4B 3.4 GB, the Qwen GGUF 1.0 GB, embeddings), on 12 GB RAM with Nothing OS. Measure resident memory and GC/jank with Perfetto or Macrobenchmark, with no model loaded and with each model loaded.
    - Keep at most one LLM resident.
    - Default to E2B on 12 GB phones for chat and tools. A reference build on the same Snapdragon 7s Gen 3 (PocketClaw) gets a 1–3 s first token with E2B.
    - E4B is opt-in only, with a warning.
    - Tab switching must not depend on model state.
    - **Owner re-test, 31 Oct 23:41.** With CPU, E2B and the 4096 context, turning Kawkab's accessibility service back on made the app sluggish again. So the per-event accessibility work is a main cause after all, alongside the context bug. The fixes above (event types, timeout, lazy snapshot, background throttling) are priority one. Measure with the service on.
23e. **Owner phone, 31 Oct: "No active model" while the header shows `Qwen3-1.7B-Q4_K_M.gguf (active)`.**
    - Selecting a GGUF brain that is not downloaded, or not loaded by `LlamaGgufRuntime`, leaves chat with no model at all.
    - Fix the GGUF activation path end to end: download with SHA-256, load, generate.
    - Never mark a model active unless its file exists and it loaded once.
    - On any GGUF load failure, fall back to the last working LiteRT model (Gemma) and show why.
    - Add an instrumented test: activate a GGUF model, send "hello" and get a reply, using the smallest available GGUF.
23f. **Owner phone, 31 Oct: chat does not work with any model.**
    - **Root cause found in code review (regression from task 15).** Commit `a6fba56c` changed `SettingsDefaults.MAX_CONTEXT_LENGTH_DEFAULT` from 4096 to 1500. That value is passed to LiteRT-LM as `EngineConfig.maxNumTokens`, the whole KV budget. The Kawkab assistant prompt plus tool schemas exceeds 1500 tokens, so every model fails with "Failed to allocate tensors". It reproduced with E2B alone after a force-stop, so this is not memory pressure.
      - Restore an engine `maxNumTokens` of 4096.
      - Task 15's "about 1,500 tokens" was meant as the budget for *injected* context (documents, memory, screen text), never the engine window. Implement it there.
      - Add a unit test that the engine window is at least the system prompt, the tool schemas and the reply cap together.
      - Migrate stored settings already set to 1500 back to 4096.
    - **Gemma 4 E4B active on the CPU backend:** "hello" fails with `Status Code 13 … llm_litert_compiled_model_executor.cc:743 Failed to invoke the compiled model / Failed to allocate tensors`. The same E4B file answered on 29 Oct when the backend was GPU.
      - Find the cause. Suspects: a GGUF (Qwen) runtime still holding memory when the LiteRT model loads, or a CPU cache and context configuration that E4B cannot allocate.
      - Unload any other engine before loading.
      - On "allocate tensors" errors, retry once with a smaller context. Then fall back to E2B or GPU, and tell the owner exactly what happened in plain words, not a raw stack line.
    - **After the failure, the active model silently flipped back to Qwen3 1.7B.** A model the owner activated must stay active unless he changes it, or the app shows a clear notice saying why it switched.
    - **The Qwen3 1.7B preset is labelled "LiteRT"** but is a GGUF on llama.cpp. Label each model with its real runtime.
    - **Instrumented test on the CI emulator:** activate E2B on CPU, send "hello" and get a reply. Then switch to the GGUF model and back to E2B, and get a reply again.
23g. **Owner phone, 31 Oct: replies are slow to start, and the language handling is wrong.**
    - **Speed.** The benchmark gives a 1.1 s first token, but real Kawkab chats average 9.5 s over the last 3 runs (Gemma 4 E2B, CPU, 8.1 tok/s). The prefill of the system prompt and every tool schema dominates. Fix:
      - Send only the tool schemas the routed intent needs; tier 1 needs none.
      - Shorten the schemas.
      - Prefill the fixed system prompt once and reuse its KV state between turns, using LiteRT-LM session or KV save and restore if available.
      - Report the first-token time for "hello" and for a command. Target under 3 s on the owner's phone class.
    - **Language.** Asked in Sudanese Arabic, the reply came in Modern Standard Arabic and in English, each prefixed with a label ("Modern Standard Arabic:", "English:").
      - Rewrite the system prompt to "Reply once, in the same language and dialect the user wrote in. Never label the language. Never translate unless asked."
      - Add a few-shot Sudanese example.
      - Add a unit test on the prompt text and a v2-style eval of reply language on 20 frozen prompts (Sudanese, MSA, English).
23h. **Owner phone, 31 Oct: "Set an alarm for 6" set no alarm.** The owner's text had a trailing `"` (pasted).
    - **Tier 1.** `CommandFastPath.normalize` keeps quotes and punctuation, so the fast path missed it. Strip surrounding quotes (`" ' “ ” « »`), trailing punctuation (`. ! ? ، ؟`) and emoji before matching. Add v2-style test lines with these.
    - **Tier 3.** The model then replied with the plain text `set_alarm`, and nothing ran. A tool name or JSON printed as text must never reach the user.
      - Parse Gemma 4 tool calls from both the native tool-call channel and fenced or plain JSON.
      - If the output names a known tool without valid arguments, re-ask once with a grammar-constrained prompt. Otherwise answer "I couldn't do that, try: set an alarm for 6".
    - **Hang.** The same message sent again got no reply at all, and no error was shown. Every run must end in a reply, a card or a visible error within 60 s.
    - **Test.** An instrumented test types `Set an alarm for 6"` (with the stray quote) and asserts the alarm intent, with no LLM call.
23i. **Owner phone, 31 Oct: alarm works, but asks needlessly and reads like a developer tool.**
    - "Set an alarm for 6" worked after the owner tapped "Allow once" (06:00 tomorrow).
    - **Don't ask for local actions.** Alarm, timer, open app and calendar-add stay on the phone. Per spec §0.4, only `OUTBOUND` must stop for a tap, as the owner's mockup said: "Done. Stays on the phone, so I didn't ask."
      - Classify `set_alarm`, `set_timer`, `open_app` and `add_calendar_event` as a new `LOCAL` tier that never asks under the default policy.
      - Show an "Undo" chip for 10 s after each one, and record it in the activity log.
      - Keep `OUTBOUND` (call, SMS, WhatsApp, share, pay, delete) always asking.
      - Update `safety/tool_classification.csv` and its test.
    - **Human-readable cards.** The confirmation card shows `set_alarm` and raw JSON (`{"hour": "6",…}`). Show a plain sentence in the owner's language instead ("Call Ahmad?", "Send to Omar: I'm late"), with the raw arguments behind a "Details" toggle. Use the owner's mockup card layout: Send, Change, Cancel.
    - **Time formatting.** The reply said "Alarm set for 6:0." Format times properly, with the locale and AM/PM ("6:00 AM", "٦:٠٠ ص").
23j. **Owner phone, 31 Oct: the call flow failed.** Transcript, in order:
    1. "call abdalla" showed an Outbound card. After Allow, the reply listed 6 contacts as plain text, with duplicates (the same number written twice in different formats).
    2. The owner answered "the mubadala". The model echoed "the mubadala".
    3. "abdalla mubadala" got "What would you like to do with him?", so the call intent was lost.
    4. A follow-up containing "him" got "Which 'him'?" with 11 contacts whose names contain "him" (Ibrahim, Hashim), including the owner's own number.
    5. "call abdalla mubadala" showed an Outbound card. After Allow: "Missing permission: This needs a permission that is still off. Grant / Phone access". Tapping Grant repeatedly did nothing.
    6. Several turns sat on "Generating…".

    Fix all of these:
    - **Disambiguation.** Show contact matches as **tappable chips**: name, the last 4 digits and a label, at most 5, with duplicates merged by normalised E.164 number. Keep the pending intent (call or SMS) until the owner picks, cancels or 2 minutes pass. A follow-up like "the mubadala" or a chip tap completes the original command; it never goes to free chat.
    - **Matching.** Match whole words and the start of tokens, never substrings inside other words ("him" must not match "Ibrahim"). Pronouns (him, her, هو, هي) refer to the last resolved contact, or ask. Never list the owner's own number. Never dump phone numbers in plain text.
    - **Permission card.** If a runtime permission was denied permanently, `shouldShowRequestPermissionRationale` is false after a denial and the request returns instantly. Then Grant must open App info → Permissions for Kawkab (`ACTION_APPLICATION_DETAILS_SETTINGS`) with a one-line instruction. After returning, re-check and resume the pending action automatically.
    - **Calls without the permission.** If `CALL_PHONE` is not granted, fall back to `ACTION_DIAL` with the number filled in. That needs no permission and the owner presses call. Never fail with only an error.
    - **No silent hangs.** This reinforces 23h: every turn ends within 60 s in a reply, a card or an error with a Retry button.
    - **Instrumented tests:** duplicate contacts collapse; "him" does not match "Ibrahim"; a follow-up chip completes the call; denied `CALL_PHONE` falls back to the dialer.
23k. **Owner phone, 31 Oct: a long-press of the power button did not open Kawkab.**
    - Check on a Nothing OS 4 (Android 16) setup.
    - Phone access must show whether Kawkab really holds the assistant role (`RoleManager.isRoleHeld(ROLE_ASSISTANT)`) and, if not, open the right screen (`ACTION_VOICE_INPUT_SETTINGS` or the default-apps screen).
    - It must also tell the owner that the power button itself must be set to "Digital assistant" (Settings → System → Gestures or Buttons → Press and hold power button). Nothing OS may default it to the power menu.
    - Confirm `VoiceInteractionService` / `VoiceInteractionSessionService` are declared correctly, and that the session shows the panel.
    - Add a Quick Settings tile and a home-screen shortcut, "Ask Kawkab", as fallbacks that always work.
23l. **Talk to Kawkab now, before the full M4 voice stack.** The owner wants to speak to the app.
    - On top of 23c (mic to text), add a **conversation mode** button. Tap once: listen with offline `SpeechRecognizer`, end of speech, send automatically, speak the reply with Android `TextToSpeech` (offline voices, Arabic and English, matching the reply language), then listen again. Tap to stop.
    - Tapping the screen or speaking over the reply stops speech (barge-in).
    - Outbound actions still need a tap on the card. Voice never confirms them (spec §0.4.3).
    - When Kawkab is the assistant role, a long-press of power starts conversation mode directly.
    - Wake word "Kawkab" stays in M4 (sherpa-onnx keyword spotting). Schedule M4 right after M1.6.
24. **Leftover upstream logo.** Also replace the knot icon on the empty-chat header. The onboarding header still shows the old knot icon next to "Kawkab". Replace every user-visible upstream logo and icon with the planet mark. Extend the branding test to the onboarding header drawable.
25. **Onboarding model sizes.** The scenario cards say "3.66 GB model" even on phones that will get E2B. Show the size of the model this phone will actually download.
25b. **Install updates over the old version.** Today every CI build is signed with a new temporary debug key. So the owner must uninstall the app, which deletes the 3.6 GB model and his data, before each update.
    - Make one fixed signing key: generate a keystore once and store it as a GitHub Actions secret (base64, plus its passwords).
    - Sign every CI APK with that key, so a new build installs over the old one and keeps the models and data.
    - Never commit the keystore to the repository. The repo is public.
    - Record the decision in `DECISIONS.md`.
    - **Fallback** if the agent cannot create repository secrets: write the exact steps the owner needs into `docs/owner-checks/M1.6.md`, and mark the task BLOCKED.
25c. **One-tap APK download.**
    - On every green push to `main`, publish the signed APK as a GitHub Release asset (a pre-release, tagged with the build date and short commit).
    - The repo is public, so the owner can download the `.apk` directly without logging in or unzipping.
    - Keep only the 5 newest pre-releases.
    - Add an owner-started "Check for updates" button on the About screen. It reads the latest release, offers the download, and never checks automatically (spec §0.2).
26. **Close out:** green GitHub Check, `docs/owner-checks/M1.6.md`, tag `m1.6`.

### M1.7: Hands-free voice (owner's priority, 31 Oct 2026)

The owner wants to talk to Kawkab smoothly, without pressing anything. This pulls the core of spec §7 and M4 forward. Do it right after `m1.6` is tagged.

27. **Wake word "Kawkab".**
    - sherpa-onnx keyword spotting with "Kawkab" / "كوكب" as the keyword, plus Silero VAD, inside a low-power foreground service with a visible notification. On/off lives in Settings, and it is on after the owner enables it once in setup.
    - Audio never leaves the phone and is never stored.
    - Hard negatives from spec §12.3: "Kawab", "Karkab", "Kawkaba", "كوكبة", "كباب".
    - Report recall, false wakes per hour and battery use per hour on the self-test.
28. **Continuous conversation.**
    - After the wake word: an earcon, then listen. End of turn is detected automatically (smart-turn-v3 if licence-verified, otherwise a VAD silence timeout of about 700 ms). Offline speech-to-text, then Kawkab replies by voice, sentence by sentence, then listens again for a follow-up for about 8 s without the wake word. Silence ends the conversation.
    - Barge-in: speaking over Kawkab stops its speech.
    - Speech-to-text: sherpa-onnx Whisper (multilingual, size chosen by measurement), falling back to Android offline `SpeechRecognizer`.
    - Text-to-speech: an offline Arabic and English voice through sherpa-onnx (licence-checked Piper or Kokoro), falling back to Android `TextToSpeech`.
29. **Hands-free actions, within the safety rules.**
    - Local actions (alarm, timer, open app, calendar) just happen by voice, with a spoken confirmation and an "undo" voice command for 10 s.
    - Outbound actions (call, SMS, WhatsApp): Kawkab reads the action aloud ("Call Abdalla Mubadala?").
      - The default is a tap on the card.
      - The owner may enable "confirm by voice" in Settings, behind the warning (spec §0.4.3). It is accepted only with the screen on and unlocked; a "yes" with the phone locked never sends.
    - Contact choices are read aloud as a short numbered list, and the owner can answer by voice ("the second one", "Mubadala").
30. **Close out:** green Check, `docs/owner-checks/M1.7.md` (say "Kawkab, set an alarm for 6"; "Kawkab, call Abdalla Mubadala"), tag `m1.7`.

### M1.8: Hotfix, the release APK crashes on launch (owner phone, 2 Nov 2026)

The owner installed `build-20261101-ed0af57` (the foss release APK, R8-minified) on the Nothing Phone (3a), Android 16. He granted every permission. **Every launch shows the splash and then the app closes.** A long-press of power still shows only the power menu.

The CI emulator tests run the debug build, so nothing ever launched the minified release APK.

**Owner confirmed, 2 Nov 01:57:** with the microphone permission revoked, the same APK opens and works. So the crash is in the microphone path at launch (`WakeWordService` → sherpa-onnx). Start the investigation there.

Code-review suspects, to verify:
- `MainActivity.onCreate` → `WakeWordService.startIfEnabled` starts sherpa-onnx at launch. `proguard-rules.pro` has no keep rules for `com.k2fsa.sherpa.onnx.**`, and none for Llamatik (`com.llamatik.**`). JNI code that reads Kotlin fields or classes by name crashes once R8 renames them.
- Also check: the microphone foreground-service type and its permission on Android 14 and later; and model or keyword files missing on first launch.

31. **Reproduce first.**
    - Add a CI job that builds `assembleFossRelease`, installs that exact minified APK on the API 36 emulator, grants every runtime permission, enables the accessibility service, turns the wake word on, launches `MainActivity`, and asserts the process is alive with the chat visible after 30 s.
    - It must fail on `ed0af57`. Keep it in the Check permanently.
32. **Fix.**
    - Add precise keep rules for sherpa-onnx and Llamatik (JNI-facing classes and fields), and any others the job reveals.
    - Wrap every native or service start at launch so that a failure disables that feature, shows a card ("Wake word couldn't start: …") and never kills the app.
    - Add a crash breadcrumb: if the previous launch died within 10 s, start in safe mode (wake word off, accessibility work off) and say so.
33. **Crash reports the owner can send without adb.** Keep an on-device crash log of the last 5 crashes, with stack traces and no personal content. Add Settings → About → "Copy crash report", which copies the text so the owner can paste it to his reviewer. Nothing is uploaded.
34. **Power button.**
    - Phone access must show "Default assistant: on or off" from `RoleManager`.
    - It must say plainly that on Nothing OS the power button must be set to the assistant (Settings → System → Gestures or Buttons → Press and hold power button → Digital assistant), and deep-link there if an intent exists.
34b. **Calling must be faster than doing it by hand (owner verdict, 2 Nov).** The call works now, but it took: chips → approval card → Android's "Complete action using: Phone calls / Zoom" chooser → the call. The owner: "no faster than doing it myself." Fix:
    - **One tap at most.** Tapping a contact chip *is* the confirmation for that call, with no second card. With a single exact match ("call Abdalla Mubadala"), show one card, "Call Abdalla Mubadala (·6117)?", and one tap calls.
    - **No app chooser.** With `CALL_PHONE` granted, place the call through `TelecomManager.placeCall(Uri.fromParts("tel", number, null), …)`, or an `ACTION_CALL` intent restricted to the default dialer package (`TelecomManager.defaultDialerPackage`). Never let Zoom or another app intercept a phone call.
    - **Remember the choice.** After the owner picks "Abdalla Mubadala" for "abdalla", the next "call abdalla" goes straight to the one-tap card. Store the alias in memory and show it on the Growth screen, where it can be deleted.
    - **Voice.** With confirm-by-voice on and the phone unlocked, "Kawkab, call Abdalla Mubadala" → "Calling Abdalla Mubadala" → "yes" calls, with no tap.
    - **Instrumented tests:** chip tap places the call with no extra card; no chooser appears when two apps handle `tel:`; the second "call abdalla" skips the chips.
35. **Close out:** green Check including the release-launch job, `docs/owner-checks/M1.8.md`, a pre-release APK, tag `m1.8`. Push the `m1.6` and `m1.7` tags too; they are missing on GitHub.

### Later milestones
- **M2 to M8:** split each milestone in spec §13 into tasks of the same size as above.
- Write the new tasks into this list in a pull request before starting them.
- Always freeze eval data in its own task before any tuning.

## What to report in every pull request

- What changed, in plain English.
- Which checks you ran and their results, including any BLOCKED gates.
- What the next task is.
