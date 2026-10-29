# AGENTS.md: instructions for Jules (and any coding agent)

You are building **Kawkab (كوكب)**, a private on-device personal assistant for Android.
The complete plan is `KAWKAB_SPEC.md` in the root of this repo. It is the only source of truth.
Read the whole spec before your first change in every task.

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

### Later milestones
- **M2 to M8:** split each milestone in spec §13 into tasks of the same size as above.
- Write the new tasks into this list in a pull request before starting them.
- Always freeze eval data in its own task before any tuning.

## What to report in every pull request

- What changed, in plain English.
- Which checks you ran and their results, including any BLOCKED gates.
- What the next task is.
