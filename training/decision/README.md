# Jev verdict reader on Llamatik

The pinned public Apache-2.0 model is `chaoliangUNSW/Jev-Style-0.8B-Decision-v3-GGUF`, revision `30bfb1168258226bdc658c9d3a9e2fa98bd2e9f5`, file `Jev-Style-0.8B-Decision-v3-Q4_K_M.gguf` (529296864 bytes), SHA-256 `0a19bc29bacc33e0d871146c8612b24dd14c2ed2e61cedeb7a928b0852628bac`.

Llamatik 2.0.0 exposes generation but no verdict logits in Kotlin. Generating JSON is not the model's decision protocol. The small JNI adapter links the exact `libllama.so` and `libggml-base.so` from its pinned AAR, with matching headers from llama.cpp `b29c606e28a01b1bc8c1351026a0fa6e616bf6c4`. It does not compile or ship another llama runtime. Preparation verifies the AAR hash. It renders chunks separately, verifies the three reserved token IDs, requests logits at the arrow slots, and returns logit(9542) minus logit(874). The phone budget is 4096 tokens with a 2048-token question head; over-budget input fails without truncating options. No generated speech is accepted as a decision.

Build with the NDK version pinned in the version catalog:

```sh
python3 training/decision/build_native.py --ndk /local/android-sdk/ndk/28.2.13676358
```

The script builds arm64 and x86_64 adapters into ignored jniLibs paths. APK CI invokes it after installing that NDK. Source-level C++ syntax was checked against matching headers locally; Android linking, actual GGUF inference, parity, latency and memory remain unmeasured here because the NDK and model are not installed. CI has not run for these local commits.

`DecisionPolicy` requires a model-specific final precision certificate of at least 95%, a positive final auto-action count, a fitted temperature and a selected threshold. Without those it asks. The publisher's global temperature is not a Kawkab precision certificate. Do not insert fabricated measurements or enable auto-action based only on a high softmax score. Actual phone evaluation must freeze three disjoint partitions (temperature, threshold selection, final test) before fitting, then record precision and coverage for contact/app/command/act-or-ask decisions. This remaining evaluation is mandatory before claiming rule 8's measured gate.
