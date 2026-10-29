# Models

Measured sizes are bytes on disk in this repository. A row with no file here is not in the APK. Estimates from the spec are not measurements.

| Model | In the APK | Measured | When resident |
|---|---|---|---|
| Universal Sentence Encoder (`app/src/main/assets/universal_sentence_encoder.tflite`) | yes | 6120274 bytes | Knotwork semantic search, as upstream shipped it |
| Brain: Gemma 4 E4B or E2B | no | not measured in M0 | Owner download. Not loaded by the Tier 1 stub |
| Judge 0.8B GGUF | no | not measured | Not in M0 |
| EmbeddingGemma 2 | no | not measured | Not shipped |
| OCR, wake word, speech-to-text | no | not measured | Not in M0 |

M0 proves the plumbing with `StubLlmInferenceEngine` on the debug variant. That stand-in reads no model file. The 5 GB budget is not measured until a brain is on a device.
