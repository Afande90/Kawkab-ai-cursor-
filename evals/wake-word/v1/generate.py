#!/usr/bin/env python3
"""Builds the frozen wake-word set v1 (task 27, spec section 12.3).

Run once; the audio and manifest it writes are committed in their own commit
and never regenerated in place (the TTS is not bit-for-bit reproducible).

Splits:
  calibration  Piper libritts_r speakers 0-39 and amy. Used to choose the
               keyword tokens and the threshold. Nothing else.
  test         Piper libritts_r speakers 500-659, ryan, alan and kareem
               (Arabic). Voices disjoint from calibration.
Kinds:
  positive        "Kawkab" / "كوكب"
  hard_negative   Kawab, Karkab, Kawkaba, كوكبة, كباب (spec 12.3)
Every clip has 0.4 s of silence each side and Gaussian noise at a seeded SNR
of 10-25 dB. Real negative audio (LibriSpeech) is not stored here; see
README.md.

Generated with sherpa-onnx 1.13.8 (pip). Usage: python3 generate.py <dir with the vits-piper-* folders>
"""
import json
import pathlib
import random
import sys

import numpy as np
import sherpa_onnx
import soundfile as sf

RATE = 16000
OUT = pathlib.Path(__file__).resolve().parent
SPEEDS_CAL = [0.9, 1.0, 1.15]
SPEEDS_TEST = [0.85, 1.0, 1.2]
SPEEDS_SOLO = [0.8, 0.9, 1.0, 1.1, 1.2, 1.3]

EN_POS = ["Kawkab", "Kawkab!", "Kawkab?"]
AR_POS = ["كوكب", "كوكب!", "كوكب؟"]
EN_NEG = ["Kawab", "Karkab", "Kawkaba", "Kebab"]
AR_NEG = ["كوكبة", "كباب", "كواب", "كركب"]


def tts(root, name):
    d = root / name
    model = next(d.glob("*.onnx"))
    cfg = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=str(model), tokens=str(d / "tokens.txt"), data_dir=str(d / "espeak-ng-data")
            ),
            num_threads=4,
        )
    )
    return sherpa_onnx.OfflineTts(cfg)


def resample(x, src):
    if src == RATE:
        return x
    n = int(round(len(x) * RATE / src))
    return np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x).astype(np.float32)


def finish(x, rng):
    pad = np.zeros(int(0.4 * RATE), dtype=np.float32)
    x = np.concatenate([pad, x, pad])
    snr = rng.uniform(10, 25)
    power = np.mean(x**2) + 1e-12
    noise = rng.normal(0, np.sqrt(power / (10 ** (snr / 10))), len(x)).astype(np.float32)
    y = x + noise
    return y / max(1.0, float(np.max(np.abs(y)))), snr


def main():
    root = pathlib.Path(sys.argv[1])
    rng = np.random.default_rng(20261101)
    random.seed(20261101)
    plan = []
    for sid in range(40):
        for sp in SPEEDS_CAL:
            plan.append(("calibration", "positive", "vits-piper-en_US-libritts_r-medium", sid, sp, "Kawkab"))
        if sid < 10:
            for w in EN_NEG:
                plan.append(("calibration", "hard_negative", "vits-piper-en_US-libritts_r-medium", sid, 1.0, w))
    for sp in SPEEDS_SOLO:
        plan.append(("calibration", "positive", "vits-piper-en_US-amy-medium", 0, sp, "Kawkab"))
    for sid in range(500, 660):
        for sp in SPEEDS_TEST:
            plan.append(("test", "positive", "vits-piper-en_US-libritts_r-medium", sid, sp, "Kawkab"))
        if sid < 530:
            plan.append(("test", "hard_negative", "vits-piper-en_US-libritts_r-medium", sid, 1.0, EN_NEG[sid % 4]))
    for voice in ["vits-piper-en_US-ryan-medium", "vits-piper-en_GB-alan-medium"]:
        for sp in SPEEDS_SOLO:
            for t in EN_POS:
                plan.append(("test", "positive", voice, 0, sp, t))
            for w in EN_NEG:
                plan.append(("test", "hard_negative", voice, 0, sp, w))
    for sp in SPEEDS_SOLO:
        for t in AR_POS:
            plan.append(("test", "positive", "vits-piper-ar_JO-kareem-medium", 0, sp, t))
        for w in AR_NEG:
            plan.append(("test", "hard_negative", "vits-piper-ar_JO-kareem-medium", 0, sp, w))

    engines = {}
    rows = []
    for i, (split, kind, voice, sid, sp, text) in enumerate(plan):
        eng = engines.setdefault(voice, tts(root, voice))
        audio = eng.generate(text, sid=sid, speed=sp)
        x = resample(np.asarray(audio.samples, dtype=np.float32), audio.sample_rate)
        y, snr = finish(x, rng)
        rel = f"audio/{split}/{kind}/{i:04d}.flac"
        path = OUT / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        sf.write(path, y, RATE, subtype="PCM_16")
        rows.append({"file": rel, "split": split, "kind": kind, "voice": voice, "speaker": sid,
                     "speed": sp, "text": text, "snr_db": round(snr, 1)})
    with open(OUT / "manifest.jsonl", "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(len(rows), "clips")


if __name__ == "__main__":
    main()
