# Third-party components

Kawkab is derived from Knotwork (copyright 2026 Aleksei Volodin), Apache License 2.0. See `LICENSE` and `NOTICE`.

The licence of every library, asset, model, voice and dataset is in `LICENSES-AUDIT.md`. Required notice texts are in `NOTICE`. The About screen lists the same bundled components a person sees in the app, including Gemma and the Prohibited Use Policy link.

## What ships in the foss APK

Resolved from `fossReleaseRuntimeClasspath` on 28 Oct 2026: 391 artifacts. 379 are Apache-2.0. The rest are:

| Component | Licence |
|---|---|
| SQLCipher (`net.zetetic:sqlcipher-android`) | BSD-3-Clause |
| Protocol Buffers lite, AndroidX datastore external protobuf | BSD-3-Clause |
| Bouncy Castle `bcprov` | MIT |
| Model Context Protocol Kotlin SDK | MIT |
| SLF4J API | MIT |
| Checker Framework qualifiers (`checker-qual`, and `checker-compat-qual` under its MIT option) | MIT |
| Reactive Streams | CC0-1.0 |
| Inter, JetBrains Mono | SIL OFL 1.1 |
| Universal Sentence Encoder (bundled TFLite asset) | Apache-2.0 |

Coil (`io.coil-kt.coil3:coil-compose`) is a direct Apache-2.0 dependency. It is named in `NOTICE` and in the audit. The About list does not use that name; `DECISIONS.md` records why.

`com.google.firebase:firebase-encoders`, `firebase-encoders-proto` and `firebase-encoders-json` are on the foss classpath because MediaPipe `tasks-core` depends on `transport-backend-cct`. They are Apache-2.0. They are not the Crashlytics SDK.

## Full flavour only

Firebase Crashlytics (BoM 34.19.0) is a `fullImplementation` dependency. It is not in the foss APK.

## Downloaded by the app, not inside the APK

Gemma 4 E4B and E2B (LiteRT community mirrors) under the Gemma Terms of Use and the Prohibited Use Policy:

- https://ai.google.dev/gemma/terms
- https://ai.google.dev/gemma/prohibited_use_policy

Fine-tunes inherit those terms. EmbeddingGemma has no verified model card in this repository yet, so the app does not download it.

## Not shipped and not downloaded

- Sudanese Whisper fine-tunes and `arbml` datasets: licence unknown, local evaluation only.
- Piper voices: none selected. Each voice is checked before a download link exists.
- OpenDroid (Apache-2.0, yashab-cyber/opendroid @ c08175cb): accessibility traversal, alias matching, macro recording, and habit detection are adapted in the app. The owner's zip snapshot was not in the repo. LLM providers, the social manager, finance, food, transport, and the MCP server were not copied.
- ARTEMIS / Minitap mobile-use: not ported. No `third_party/mobile_use` tree yet.
- VoiceStudio (AGPL) and non-commercial material (30u30, CC BY-NC-ND): reference only, never copied.
- Pocket LLM (github.com/dineshsoudagar/local-llms-on-android, MIT): named as a Gemma 4 GPU and NPU idea. No code was copied.
- PhoneCLI (github.com/HKUDS/PhoneCLI, MIT): the idea of stored app maps and macros replayed with no model call. No code was copied.
- Local Assistant (github.com/sergarsilla/local-assistant, Apache-2.0): the idea of registering as the digital assistant and of trying Qwen3 1.7B for tool calls. No code was copied. Their published tool-choice counts are theirs, not a Kawkab measurement.
- Llamatik (github.com/ferranpons/Llamatik, MIT, com.llamatik:core 2.0.0): the llama.cpp runtime. The library is a Gradle dependency, so its native code is in the APK. No weights are bundled.
- sherpa-onnx (github.com/k2-fsa/sherpa-onnx, Apache-2.0, 1.13.8 AAR) with ONNX Runtime (MIT): the wake word (task 27). Bundled in `assets/wake/`: the sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01 keyword model (Apache-2.0) and Silero VAD (github.com/snakers4/silero-vad, MIT).
- Qwen3 1.7B (Apache-2.0): a selectable tool-call brain. The Models screen can download the Q4_K_M GGUF. The weights are not in the APK.
- OnDevice LLM (github.com/sebytzr16/on-device-llm, MIT): the idea of Parakeet and Kokoro for on-device speech. No code was copied.
- FunctionGemma 270M (google/functiongemma-270m-it, Gemma Terms of Use, gated): a tier-2 intent candidate. Not downloaded. Not fine-tuned here.
- Parakeet TDT 0.6B v3 (nvidia/parakeet-tdt-0.6b-v3, CC-BY-4.0): an M4 speech-to-text candidate. The card lists 25 European languages and does not list Arabic. Not downloaded.
- Kokoro 82M (hexgrad/Kokoro-82M, Apache-2.0): an M4 speech candidate. Not downloaded.
- Qwen3-ASR 0.6B (Qwen/Qwen3-ASR-0.6B, Apache-2.0): an extra M4 speech-to-text candidate. The card lists Arabic. Not downloaded.
- Genie (github.com/Akeem1955/Genie): the harness rules for one typed tool decision, a short history, error tiers, circuit breakers, a skill cache, stated facts, and a second risk signal before biometric confirmation. The repository lists no licence. No code was copied.
- PocketClaw (github.com/ManojRakshu/pocketclaw, MIT): the owner line at the top of the prompt, old images kept as text, and Gecko as an embedding candidate. No code was copied. Their published timings are theirs, not a Kawkab measurement. This is not Pocket LLM.

## M1

No new third-party component. The stub messenger is first-party test code and is not in the foss APK.

## Development tool, not in the APK

Drawflow 0.0.60 is inlined in `pipeline-editor.html` under the MIT licence. The notice is in `NOTICE`.

## Pocket LCD fonts

VT323, IBM Plex Mono and Noto Kufi Arabic are bundled from google/fonts under SIL OFL 1.1. Licence copies are in third_party/fonts. No fonts are fetched at runtime.

## Owner speaker verification

The 3D-Speaker CAMPPlus ONNX export is Apache-2.0, pinned at echo-hello/speech_campplus_sv_zh_en_16k-common_advanced-onnx commit 1a64735d2ab8c9b005101d5b0deff78c148248c0. The bundled speaker model SHA-256 is 945bcefe95672434d6ecf4c45bd0464d7e82be77d55985d0ec2f3253638110e1. Upstream license is retained in third_party/speaker/LICENSE. No speech recordings are bundled with it.

- Jev-Style 0.8B Decision v3 (Apache-2.0): optional, owner-started public model download. Verdict protocol reference; no upstream runtime source copied. Exact revision, hash and unmeasured gates: `training/decision/README.md`. No hosted inference.
- llama.cpp (MIT): matching public C API headers are prepared at build time against the exact submodule commit of the existing Llamatik 2.0.0 runtime. No second runtime is compiled.

- Owner voice-loop evaluation, 2 November 2026: VoiceStudio (`debpalash/VoiceStudio`, AGPL-3.0, personal-use only) was evaluated as a reference, not linked or copied. smart-turn-v3 (`pipecat-ai/smart-turn-v3`, BSD-2-Clause) has CPU ONNX exports but is not enabled without Android parity/timing checks. Kokoro remains an English candidate. Piper Arabic redistribution permission is unresolved, so no voice weights are shipped. Details: `docs/owner-checks/M2-V.md`.

## Foreground Glyph mirror (owner-authorized personal use)

Nothing Glyph Developer Kit 2.0: official SDK, personal-use only under the owner's explicit instruction. The AAR is not committed. Gradle downloads the official release artifact from immutable upstream commit 8ee807a9312a640b0d43051450924e3446bc1d78, SHA-256 329393019db5f0f987c6245855d13fa273d06756c68829ca0f6ae686ba336da1. Vendor bytecode is not modified. Phone (3a)'s lights are white; the app mirrors green/amber state meanings using breathing and attention patterns, and displays live countdown progress on its C strip. Android 16+ removes the SDK API-key restriction. Older Nothing OS versions need an issued key or Nothing's documented development mode; unsupported/rejected devices quietly do nothing.

## CPU semantic end of turn

smart-turn-v3.2-cpu.onnx: Daily/Pipecat, BSD-2-Clause. HF revision f766f81d3cfdf7737ac64aad813d91bbfd56bf93, SHA-256 2bb026316b14a660486a75b1733cd3fbab8c2fd0314dc9af7be49f8cca967e4f, 8,679,182 bytes. Gradle downloads the public model at build time and packages it for offline CPU inference. The licence is retained in third_party/smart-turn/LICENSE. No owner audio is downloaded or uploaded.

ONNX Runtime Android 1.28.0 (MIT) adds the Java CPU bridge. The existing sherpa library uses ORT C API 28, as does the new bridge. A single shared runtime is packaged per ABI. Public publisher key 369E771178C41DE26253F280E96373C25092C07A was verified against the Maven Central AAR/POM signatures and is trusted only for this exact module/version; artifact SHA-256 values are also pinned. No private signing key is stored.

The first-party Whisper-compatible feature extractor follows the published 8-second smart-turn input convention. Its complete 80x800 outputs are checked against independent frozen Transformers v4.57.1/NumPy references (Apache-2.0 reference utilities). These are mathematical synthetic signals, not owner audio or an accuracy claim.
