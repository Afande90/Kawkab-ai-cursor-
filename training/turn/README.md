# CPU semantic end of turn

smart-turn-v3.2-cpu.onnx is the 8,679,182-byte int8 CPU model in pipecat-ai/smart-turn-v3 at f766f81d3cfdf7737ac64aad813d91bbfd56bf93. SHA-256: 2bb026316b14a660486a75b1733cd3fbab8c2fd0314dc9af7be49f8cca967e4f. Gradle's downloadSmartTurnModel task verifies it before packaging it under voice/. No runtime network request or owner-data upload is needed.

ONNX Runtime Android 1.28.0 runs sequentially with one CPU thread. The Java bridge and the existing sherpa runtime both use stable ORT C API 28; packaging keeps one libonnxruntime.so per ABI. Model warm-up runs off the UI thread. Each listening session has its own native cancellation handle. Features plus inference have a 150 ms wall-clock budget. A load error, invalid score, timeout or result arriving after 150 ms degrades that session automatically to 700 ms of silence. The next session can use the cached model normally. No production certificate or manual toggle is required.

Silero observes the exact captured PCM. A separate monitor requests semantic completion after a short pause; AudioRecord continues during inference. A fresh speech onset invalidates an older completion. Short utterances retain at least the existing one-second PCM minimum for owner verification. The Android recognizer receives an external-audio segmented session: intermediate segments are buffered and do not dispatch commands. The endpoint closes input and stops recognition; only the final transcript passing the existing speaker check reaches the voice loop. A recognizer rejecting segmented sessions retries once with the documented platform silence fallback. Recognizers ignoring optional endpoint hints may still end earlier; handset behavior is an owner check. The existing 15-second capture cap remains.

The 80x800 log-mel extractor follows the published Whisper-tiny/8-second convention: left padding, full-window normalization, 400-point periodic-Hann centered-reflect STFT, 160-sample hop, Slaney mel filters and upstream log scaling. Three independent frozen NumPy reference tensors cover short input, long input and silence. Every bin is checked within the frozen 0.0005 absolute tolerance. These synthetic mathematical signals are not a semantic-accuracy dataset.

Reproduce the reference in a NEW empty folder:

```sh
python3 -m pip install -r training/turn/requirements.txt
python3 training/turn/reference_features.py /tmp/turn-reference-check
```

Compare SHA256SUMS with app/src/test/resources/turn/v1/SHA256SUMS. The script refuses to overwrite existing data and verifies the upstream Apache-2.0 reference source before executing its math helpers. Android extraction is first-party; Daily's model licence and Microsoft's runtime licence are retained in third_party/ and packaged in assets/licenses/.

A host-only ONNX CPU smoke run confirmed input float[batch,80,800] and a sigmoid completion output named logits. It returned finite probabilities for the synthetic tensors. This is not phone performance, Arabic quality, or a claim that every phone meets 150 ms. Budget/failure tests verify the automatic fallback.
