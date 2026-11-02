# Frozen smart-turn preprocessing reference, version 1

Frozen before the Android extractor is implemented. These are synthetic mathematical signals, not owner recordings and not a turn-detection accuracy dataset.

Reference: Transformers v4.57.1 audio_utils.py (Apache-2.0) and the published WhisperFeatureExtractor convention, using NumPy 2.3.5. Pipecat smart-turn inference reference commit: 4786657e242dfe77dd138699ac564ee074a2a543 (BSD-2-Clause). Model: smart-turn-v3.2-cpu.onnx, HF revision f766f81d3cfdf7737ac64aad813d91bbfd56bf93.

For short (15360 samples) and long (144000 samples), t = i/16000 and PCM = float32(0.1*sin(2*pi*(200*t+60*t*t)) + 0.03*cos(2*pi*1700*t)). Silence is 16000 zeros. Keep the last 128000 samples, pad at the beginning, normalize with epsilon 1e-7, use 400-point centered reflect-padding periodic-Hann STFT with hop 160, 80 area-normalized Slaney mel filters, log10 floor 1e-10, drop the last frame, clamp to max minus eight and scale (value+4)/4. Each little-endian float32 file is shape 80x800 in mel-major order. Compare every value with maximum absolute error <= 0.0005. Do not regenerate or relax this frozen version to make a failing implementation pass.
