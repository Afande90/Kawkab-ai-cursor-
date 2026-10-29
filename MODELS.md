# Models

Measured sizes are bytes on disk in this repository. A row with no file here is not in the APK. Estimates from the spec are not measurements.

| Model | In the APK | Measured | When resident |
|---|---|---|---|
| Universal Sentence Encoder (`app/src/main/assets/universal_sentence_encoder.tflite`) | yes | 6120274 bytes | Knotwork semantic search, as upstream shipped it |
| Brain: Gemma 4 E4B or E2B | no | not measured | Owner download. Below flagship RAM the onboarding pick is E2B. E4B stays in the picker |
| Judge 0.8B GGUF | no | not measured | Not in M0 |
| EmbeddingGemma 2 | no | not measured | Not shipped |
| OCR, wake word, speech-to-text | no | not measured | Not in M0 |

M0 proves the plumbing with `StubLlmInferenceEngine` on the debug variant. That stand-in reads no model file. The 5 GB budget is not measured until a brain is on a device.

M1 adds no model file. The typed fast path is a grammar. The Tier 1 stub still reads no model.

M1.5 performance, measured on 29 Oct 2026 from this machine:

| Number | Result |
|---|---|
| Time to first token | not measured |
| Tokens per second | not measured |
| NPU versus CPU | not tried on a phone |

Cause: this machine has no Gemma file and no phone, so LiteRT never ran a generation. The on-phone self-test is Settings → Test backend. A successful probe stores tokens per second and, from this change on, milliseconds to the first token. NPU stays withdrawn until that self-test records a start that does not crash and a tokens-per-second figure higher than CPU. No such pair of numbers exists. CPU remains the backend. Multi-token prediction is off on every backend because LiteRT-LM 0.17.1 has no switch for it.
