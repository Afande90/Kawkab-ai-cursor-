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

## M1

No new third-party component. The stub messenger is first-party test code and is not in the foss APK.

## Development tool, not in the APK

Drawflow 0.0.60 is inlined in `pipeline-editor.html` under the MIT licence. The notice is in `NOTICE`.
