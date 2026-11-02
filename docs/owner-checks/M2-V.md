# Owner vision reset: local continuation, 2 November 2026

The owner forbids remote writes for this continuation. No push, tag or publication is part of the handoff. The work is supplied as `kawkab-m2v.bundle`, based on `origin/main` at b5797b49aec119fc9dc8fd19008bcdb1ba0fe7b5. Earlier release-write failures are historical and are not a reason to stop development.

## What is implemented

| Rule | Local implementation | Remaining verification or limitation |
| --- | --- | --- |
| 1 | Voice and typed actions; spoken announcement followed by the full three-second cancel window; stop/cancel/Arabic cancellation and taps; direct Telecom calling and default-SIM SMS | Real call/SMS delivery and microphone timing need the phone. SMS submission is not presented as delivery. |
| 2 | Spoken contact/app names and ordinals; persistent owner choices reused; no choice buttons in the Pocket screens | Real address book and installed-app checks need the phone. |
| 3 | Banking/payment/password denylist; current spoken deletion evidence; actual delete controls checked in accessibility; no silent memory pruning; production processing restricted to the phone, including HTTP and tool-server hops | Legacy engine editors live behind Advanced; this is not a claim that arbitrary third-party software on the phone is controlled. |
| 4 | Shared Pocket LCD, F1–F8, fonts, physical keys, state LED, fixed LCD footer placement, corrected F4, eight screenshot tests, shared power-button surface | **Incomplete: physical Nothing Glyph mirror.** The public GDK AAR has no redistribution licence established here and is not bundled. Actual assistant-window lifecycle needs phone testing. |
| 5 and 5b | Six-item Settings, engine behind Advanced, English app resources for every shipped locale, LTR interface; spoken Arabic remains Arabic in the LCD | No translated interface is claimed. System-owned Android settings follow Android's own language. |
| 6 | Full local multilingual synthesis/train/calibrate/evaluate/export pipeline; frozen audio, labels and corpus provenance; personal owner templates plus sherpa second opinion; export requires the fixed gates for every frozen voice | Generic retraining was **not run**. A licensed Sudanese synthesis voice, speech tools and real negative corpus are missing. No 90% recall or false-wake pass is claimed. See training/wake/README.md. |
| 7 | Screen-lock-authenticated enrollment: ten wake words and two sentences; private no-backup recordings; sherpa speaker verification on the same PCM used for recognition; unknown voices produce no command; delete/redo through Voice | Real owner/other-speaker acceptance rates and Android audio-source support need the phone. The 0.65 speaker threshold is not a measured owner accuracy claim. |
| 8 | Pinned public Jev GGUF download; true verdict logits on the existing Llamatik runtime; contact/app and command/chat/act-or-ask paths; shared Android fixture reader plus disjoint calibration and threshold scripts; no automatic model choice without a production precision certificate | Android native compilation and real model parity/precision/latency are **unverified**. No NDK, installed GGUF or phone is available here. The supplied smoke set cannot certify production decisions; automatic model choices remain disabled. See training/decision/README.md. |
| 9 | Separate structured actions; sentence speech, owner-verified barge-in and follow-ups; verified power-panel utterances continue without a Send tap; offline Android voices only | **Incomplete: smart-turn integration and a listening/speed-selected Arabic/English synthesis winner.** The documented silence fallback remains. See the technical evaluation below. |
| 10 | Current/past/unsure memory, explicit single-valued changes, visible conflict review, dates only as stated, idle extraction/deduplication, durable successful-plan replay, contact/app/screenshot learning on Growth, deletion of only dependent summaries | Room transaction coverage is written for CI; the local VM does not run an emulator. Screenshot categories are owner-supplied, not fabricated automatic classifications. |

## Exact F4 correction

The heard command is VT323 22sp. CALLING/name/number are 46/32/22sp with 6dp between their text boxes. The digit is 150sp with an actual 127.5sp line box, aligned right above the 26sp block bar. The green LCD is 400dp high. The hint sits outside it in IBM Plex Mono 13sp, #5E5A52. STOP is 124dp, #2A2925, with an 8dp #0E0D0B shadow and 15sp #F0EDE6 text. Every screen's last LCD block is pinned to the panel bottom. Tests assert measured geometry, not just a style value.

## Voice component evaluation

- **VoiceStudio** (`debpalash/VoiceStudio`): the public repository declares AGPL-3.0, permitted for this owner's personal use. Evaluated as a reference; no code or weights are bundled. No tested Android build or handset listening result is available here.
- **Kokoro 82M** (`hexgrad/Kokoro-82M`): publisher metadata declares Apache-2.0 and lists English. It does not establish Arabic coverage. It cannot be selected as the Arabic/English winner from these facts.
- **Piper ar_JO-kareem-medium**: the model card says Jordanian Arabic, one speaker, 22,050Hz. Its dataset licence points to `AliMokhammad/arabicttstrain`, whose public repository declares no licence. That does not establish permission to redistribute the voice. It is not relabelled Sudanese and is not bundled.
- **smart-turn-v3** (`pipecat-ai/smart-turn-v3`, f766f81d3cfdf7737ac64aad813d91bbfd56bf93): BSD-2-Clause; CPU ONNX exports exist, including v3.2. No Android preprocessing parity or latency result is available here, so this model is not enabled. Recognition retains the documented 700ms silence fallback, which the Android recognizer may round.

There is no listening winner. Selecting one without hearing Arabic/English output and measuring the phone would fabricate the requested evaluation. The current fallback is Android TextToSpeech with network-required voices excluded. The owner should compare the same short English and Sudanese/Arabic sentences, first-audio latency, complete-utterance time, intelligibility and interruptions on the actual phone before choosing a replacement.

## Handoff and verification

Final local verification passed in 9m12s: Android lint, app/catalog style and static analysis, instrumented-source compilation, file maps, documentation, source/dependency/domain compliance, targeted interface/error tests and all eight screenshot goldens. The detailed test areas and outcomes are recorded in PROGRESS.md. Targeted unit/screenshot tests and lint/static/compliance checks run locally. The full Check and emulator belong to CI and have not run for unpublished local commits. No new APK is claimed published.

To inspect the bundle without changing the current branch:

```sh
git bundle verify kawkab-m2v.bundle
git fetch ./kawkab-m2v.bundle HEAD:m2v-review
git log --oneline main..m2v-review
```

The native decision adapter is source-built against the pinned Llamatik libraries in APK CI. That workflow change has not itself run here. The speaker asset is bundled and pinned; the much larger language and decision models remain owner-started downloads.
