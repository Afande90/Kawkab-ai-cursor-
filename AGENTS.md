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

- **No Claude, ever.** Kawkab must never contain or call Anthropic or Claude in any form: no key, no SDK, no provider entry, no login. Remove Knotwork's Anthropic provider in M0.
- **No subscription logins.** Kawkab never uses any subscription login (ChatGPT, Claude, Gemini consumer accounts).
- **Zero cost.** No paid API, service or hosting. Everything runs on the phone. No telemetry.
- **Images, audio and screenshots never leave the phone.**
- **Outbound actions always need a tap.** Anything that sends, calls, posts, shares, pays, submits or deletes needs a tap on a confirmation card that no setting can switch off (spec §0.4).
- **Licences.** Keep upstream licence headers. Credit every donor in `NOTICE` and `THIRD_PARTY.md`. Never copy AGPL or non-commercial code.

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
3. Remove the Anthropic provider everywhere (spec §0.1).
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

### Later milestones
- **M2 to M8:** split each milestone in spec §13 into tasks of the same size as above.
- Write the new tasks into this list in a pull request before starting them.
- Always freeze eval data in its own task before any tuning.

## What to report in every pull request

- What changed, in plain English.
- Which checks you ran and their results, including any BLOCKED gates.
- What the next task is.
