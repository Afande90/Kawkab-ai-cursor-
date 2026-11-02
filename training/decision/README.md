# Jev verdict reader on Llamatik

The pinned public Apache-2.0 model is `chaoliangUNSW/Jev-Style-0.8B-Decision-v3-GGUF`, revision `30bfb1168258226bdc658c9d3a9e2fa98bd2e9f5`, file `Jev-Style-0.8B-Decision-v3-Q4_K_M.gguf` (529296864 bytes), SHA-256 `0a19bc29bacc33e0d871146c8612b24dd14c2ed2e61cedeb7a928b0852628bac`.

Llamatik 2.0.0 exposes generation but no verdict logits in Kotlin. Generating JSON is not the model's decision protocol. The small JNI adapter links the exact `libllama.so`, `libggml.so`, `libggml-cpu.so`, `libggml-base.so` and `libomp.so` from its pinned AAR, with matching headers from llama.cpp `b29c606e28a01b1bc8c1351026a0fa6e616bf6c4`. It does not compile or ship another llama runtime. Preparation verifies the AAR hash. It renders chunks separately, verifies the three reserved token IDs, requests logits at the arrow slots, and returns logit(9542) minus logit(874). The phone budget is 4096 tokens with a 2048-token question head; over-budget input fails without truncating options. No generated speech is accepted as a decision.

Build with the NDK version pinned in the version catalog:

```sh
python3 training/decision/build_native.py --ndk /local/android-sdk/ndk/28.2.13676358
```

The script builds arm64 and x86_64 adapters into ignored jniLibs paths. APK CI invokes it after installing that NDK. Source-level C++ syntax was checked against matching headers locally; Android linking, actual GGUF inference, parity, latency and memory remain unmeasured here because the NDK and model are not installed. CI has not run for these local commits.

The owner's 2 November 2026 instruction supersedes the earlier production-certificate gate. Runtime automatic decisions are ON when the pinned GGUF and native adapter are installed: stable softmax confidence >= 0.85 selects an allowlisted option, and anything lower asks one spoken question. Outbound actions still use the announcement plus full three-second cancellation window. No calibration file or final-test precision certificate is required or loaded. Missing/invalid model or runtime asks. This confidence is not a measured accuracy claim.

Every attempt records options, raw scores, confidence, threshold, selected/asked result, reason and elapsed time in private no-backup `decision/decisions.jsonl`. Engine-owned session identity joins later tool observations (including refusal, cancellation and failure) to decision IDs; the next owner response is recorded as a follow-up. Logging never uploads content. An unwritable decision log falls back to asking. Old certificate files are ignored and are not automatically deleted. The frozen calibration tools below remain available for report-only evaluation and later tuning from real use.


The build also puts a standalone Android fixture reader in `build/decision/{abi}`.
It shares the exact C++ renderer and logit readout with the app; it is not included as a user-facing tool.
With an attached phone and the exact public model downloaded on the build computer:

```sh
python3 training/decision/run_phone.py --model-file /local/Jev-Style-0.8B-Decision-v3-Q4_K_M.gguf --output /local/phone-scores.jsonl
python3 training/decision/calibrate.py /local/phone-scores.jsonl /local/decision-report.json
```

The USB script verifies the model on both sides and the committed frozen fixture hash, pushes only the temporary runtime/model, reads actual verdicts, and removes its own temporary directory. It never reads app data or owner recordings. The script has not run against a phone here.

The checked-in v1 fixtures are a smoke set. A passing smoke report deliberately has `production_ready: false`. For production, prepare and commit a new independent manifest and its `SHA256SUMS` before reading scores; keep temperature, threshold and final partitions disjoint. Supply that manifest to both commands with `--manifest`. Add `--production` to calibration only for a final partition with at least 100 cases, at least 25 per decision kind and at least 25 each in English and Arabic. The report requires at least 95% final precision and a positive auto-action count in every kind and language. These sample counts are a minimum evaluation gate, not a statistical guarantee for all future speech. Never tune on the final set or replace failed labels. No production certificate is supplied or needed to enable the owner-selected runtime policy.
