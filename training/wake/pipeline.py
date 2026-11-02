#!/usr/bin/env python3
"""Local CPU wake training: synthesize, freeze, train, calibrate, evaluate, gated export.

No personal recordings, service keys, paid compute, or external inference APIs are used.
The immutable v1 final test set is never used by train or calibrate.
"""
import argparse
import hashlib
import json
import math
import pathlib
import subprocess
import sys
import wave

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[2]
RATE, STEPS, BANDS, FRAME = 16000, 32, 16, 320


def digest(path):
    return hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest()


def read_audio(path):
    import soundfile as sf
    audio, rate = sf.read(path, dtype="float32", always_2d=True)
    audio = audio.mean(axis=1)
    if rate != RATE:
        audio = np.interp(np.arange(0, len(audio), rate / RATE), np.arange(len(audio)), audio)
    return audio


def features(audio):
    """Goertzel log-spectral features, identical to WakeFeatures.kt; duration normalized."""
    audio = np.asarray(audio, dtype=np.float64)
    audible = np.flatnonzero(np.abs(audio) > 0.01)
    if not len(audible):
        return np.zeros(STEPS * BANDS, dtype=np.float64)
    first, last = int(audible[0]), int(audible[-1])
    coefficients = [2 * math.cos(2 * math.pi * (100 * (1.27 ** band)) / RATE) for band in range(BANDS)]
    result = []
    for step in range(STEPS):
        start = int(first + (last - first) * step / (STEPS - 1)) - FRAME // 2
        samples = [audio[i] if 0 <= i < len(audio) else 0 for i in range(start, start + FRAME)]
        for coefficient in coefficients:
            s1 = s2 = 0.0
            for sample in samples:
                s0 = sample + coefficient * s1 - s2
                s2, s1 = s1, s0
            power = max(0.0, s1 * s1 + s2 * s2 - coefficient * s1 * s2)
            result.append(math.log1p(power / FRAME))
    result = np.asarray(result)
    result -= result.mean()
    return result / max(float(np.linalg.norm(result)), 1e-12)


def check_disjoint(training, evaluation):
    train = {(row["voice"], row.get("speaker", 0)) for row in training}
    test = {(row["voice"], row.get("speaker", 0)) for row in evaluation}
    if train & test:
        raise ValueError("Training and frozen evaluation speakers overlap")


def gates(hits, positives, false_wakes, hours):
    recall = hits / positives if positives else 0.0
    false_rate = false_wakes / hours if hours else float("inf")
    return {"recall": recall, "false_wakes_per_hour": false_rate, "negative_hours": hours,
            "passed": recall >= 0.90 and false_rate <= 0.10 and hours >= 10.0}


def synthesize(args):
    config = json.loads((ROOT / "training/wake/voices.json").read_text())
    voices = list(config["training_voices"])
    if not args.voice_license or not pathlib.Path(args.voice_license).is_file():
        raise ValueError("A real local license document for the Sudanese voice is required")
    if not args.sudanese_voice or not args.voice_license:
        raise ValueError("Provide a local licensed Sudanese Piper model and its license; do not relabel MSA as Sudanese")
    voices.append({"id": "local-piper-ar-SD", "voice": args.sudanese_voice, "engine": "piper", "language": "ar-SD"})
    out = pathlib.Path(args.output)
    out.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(config["seed"])
    rows = []
    for voice in voices:
        words = ["كوكب" if voice["language"].startswith("ar") else "Kawkab"] + config["negative_words"]
        for word_index, word in enumerate(words):
            for speed in config["speed"]:
                base = out / "synthesis.wav"
                if voice["engine"] == "espeak-ng":
                    subprocess.run(["espeak-ng", "-v", voice["voice"], "-s", str(speed), "-w", str(base), word], check=True)
                else:
                    subprocess.run(["piper", "--model", voice["voice"], "--output_file", str(base)], input=word.encode(), check=True)
                audio = read_audio(base)
                for snr in config["snr_db"]:
                    noise = rng.normal(size=len(audio))
                    scale = np.sqrt(np.mean(audio ** 2) / max(np.mean(noise ** 2), 1e-12)) / (10 ** (snr / 20))
                    mixed = np.clip(audio + scale * noise, -1, 1)
                    file = out / (str(len(rows)).zfill(6) + ".wav")
                    with wave.open(str(file), "wb") as stream:
                        stream.setnchannels(1); stream.setsampwidth(2); stream.setframerate(RATE)
                        stream.writeframes((mixed * 32767).astype("<i2").tobytes())
                    rows.append({"file": file.name, "sha256": digest(file), "voice": voice["id"], "speaker": 0,
                                 "label": int(word_index == 0), "split": "temperature" if len(rows) % 5 == 0 else "train"})
    manifest = out / "manifest.jsonl"
    manifest.write_text("".join(json.dumps(row) + "\n" for row in rows))
    (out / "manifest.sha256").write_text(digest(manifest) + "\n")
    print("Freeze manifest.jsonl and manifest.sha256 in their own commit before training.")


def load_training(path):
    path = pathlib.Path(path)
    if digest(path) != path.with_name("manifest.sha256").read_text().strip():
        raise ValueError("Training manifest changed after freezing")
    subprocess.run(["git", "ls-files", "--error-unmatch", str(path.resolve())], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
    subprocess.run(["git", "diff", "--exit-code", "HEAD", "--", str(path.resolve())], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
    rows = [json.loads(line) for line in path.read_text().splitlines()]
    for row in rows:
        row["file"] = str(path.parent / row["file"])
    config = json.loads((ROOT / "training/wake/voices.json").read_text())
    if digest(ROOT / "evals/wake-word/v1/SHA256SUMS") != config["frozen_eval_sha256"]:
        raise ValueError("The immutable evaluation set hash changed")
    evaluation = [json.loads(line) for line in (ROOT / "evals/wake-word/v1/manifest.jsonl").read_text().splitlines()]
    check_disjoint(rows, evaluation)
    for row in rows:
        if digest(row["file"]) != row["sha256"]:
            raise ValueError("Training audio changed: " + row["file"])
    return rows


def sigmoid(value):
    return 1.0 / (1.0 + np.exp(-np.clip(value, -40, 40)))


def train(args):
    rows = load_training(args.manifest)
    selected = [r for r in rows if r["split"] == "train"]
    x = np.stack([features(read_audio(row["file"])) for row in selected])
    y = np.array([row["label"] for row in selected])
    weights = np.zeros(x.shape[1]); bias = 0.0
    positive_weight = max(1, float((y == 0).sum()) / max(1, (y == 1).sum()))
    sample_weight = np.where(y == 1, positive_weight, 1.0)
    for _ in range(1000):
        error = (sigmoid(x @ weights + bias) - y) * sample_weight
        weights -= 0.1 * ((x.T @ error) / len(x) + 0.001 * weights)
        bias -= 0.1 * error.mean()
    held = [r for r in rows if r["split"] == "temperature"]
    hx = np.stack([features(read_audio(row["file"])) for row in held]); hy = np.array([r["label"] for r in held])
    def loss(t):
        p = np.clip(sigmoid((hx @ weights + bias) / t), 1e-8, 1 - 1e-8)
        return float(-(hy * np.log(p) + (1 - hy) * np.log(1 - p)).mean())
    temperature = min(np.linspace(0.3, 5, 100), key=loss)
    model = {"format": "pocket-kws-v1", "weights": weights.tolist(), "bias": bias,
             "temperature": float(temperature), "threshold": 1.0, "training_sha256": digest(args.manifest),
             "frozen_eval_sha256": digest(ROOT / "evals/wake-word/v1/SHA256SUMS")}
    pathlib.Path(args.output).write_text(json.dumps(model))


def probability(model, audio):
    return float(sigmoid((np.dot(features(audio), model["weights"]) + model["bias"]) / model["temperature"]))


def verify_frozen():
    frozen = ROOT / "evals/wake-word/v1"
    config = json.loads((ROOT / "training/wake/voices.json").read_text())
    if digest(frozen / "SHA256SUMS") != config["frozen_eval_sha256"]:
        raise ValueError("Frozen checksum inventory changed")
    if digest(frozen / "manifest.jsonl") != config["frozen_manifest_sha256"]:
        raise ValueError("Frozen labels or splits changed")
    for line in (frozen / "SHA256SUMS").read_text().splitlines():
        expected, name = line.split(maxsplit=1)
        target = (frozen / name).resolve()
        if not target.is_relative_to(frozen.resolve()) or digest(target) != expected:
            raise ValueError("Frozen input changed: " + name)
    return frozen


def calibrate(args):
    model = json.loads(pathlib.Path(args.model).read_text())
    frozen = verify_frozen()
    rows = [json.loads(line) for line in (frozen / "manifest.jsonl").read_text().splitlines()]
    rows = [row for row in rows if row["split"] == "calibration"]
    scores = [(probability(model, read_audio(frozen / row["file"])), row["kind"] == "positive") for row in rows]
    candidates = []
    for threshold in np.linspace(0.50, 0.999, 500):
        hits = sum(score >= threshold and positive for score, positive in scores)
        false = sum(score >= threshold and not positive for score, positive in scores)
        candidates.append((false == 0, hits, threshold))
    valid = [item for item in candidates if item[0]]
    if not valid:
        raise ValueError("No calibration threshold avoids hard-negative wakes; do not relax the gate")
    model["threshold"] = float(max(valid, key=lambda item: (item[1], item[2]))[2])
    pathlib.Path(args.output).write_text(json.dumps(model))


def evaluate(args):
    import soundfile as sf
    model = json.loads(pathlib.Path(args.model).read_text())
    frozen = verify_frozen()
    if digest(frozen / "SHA256SUMS") != model["frozen_eval_sha256"]:
        raise ValueError("Frozen evaluation changed")
    rows = [json.loads(line) for line in (frozen / "manifest.jsonl").read_text().splitlines()]
    positives = [r for r in rows if r["split"] == "test" and r["kind"] == "positive"]
    def heard(audio):
        if args.constant: return args.constant == "yes"
        return probability(model, audio) >= model["threshold"]
    decisions = [(row, heard(read_audio(frozen / row["file"]))) for row in positives]
    hits = sum(hit for _, hit in decisions)
    per_voice = {}
    for row, hit in decisions:
        entry = per_voice.setdefault(row["voice"], {"hits": 0, "positives": 0})
        entry["hits"] += int(hit)
        entry["positives"] += 1
    for entry in per_voice.values():
        entry["recall"] = entry["hits"] / entry["positives"]
    hard_negatives = [r for r in rows if r["split"] == "test" and r["kind"] != "positive"]
    hard_hits = sum(heard(read_audio(frozen / row["file"])) for row in hard_negatives)
    false = 0; seconds = 0.0
    directory = pathlib.Path(args.negatives)
    manifest = directory / "corpus-manifest.json"
    frozen_negative_hash = ROOT / "training/wake/negative-corpus.sha256"
    if not frozen_negative_hash.is_file() or digest(manifest) != frozen_negative_hash.read_text().strip():
        raise ValueError("Freeze the downloaded real-negative manifest hash before final evaluation")
    subprocess.run(["git", "ls-files", "--error-unmatch", str(frozen_negative_hash)], cwd=ROOT,
                   check=True, stdout=subprocess.DEVNULL)
    subprocess.run(["git", "diff", "--exit-code", "HEAD", "--", str(frozen_negative_hash)], cwd=ROOT,
                   check=True, stdout=subprocess.DEVNULL)
    negative_rows = json.loads(manifest.read_text())
    files = []
    for row in negative_rows:
        file = (directory / row["file"]).resolve()
        if not file.is_relative_to(directory.resolve()) or digest(file) != row["sha256"]:
            raise ValueError("Real negative recording changed")
        if row["source"] not in {"https://www.openslr.org/resources/12/test-clean.tar.gz",
                                  "https://www.openslr.org/resources/12/dev-clean.tar.gz"}:
            raise ValueError("Unknown real negative corpus provenance")
        files.append(file)
    if len(files) != len(set(files)):
        raise ValueError("Duplicate recordings cannot inflate negative hours")
    if not files: raise ValueError("Provide the licensed LibriSpeech test-clean and dev-clean corpus")
    for file in files:
        audio = read_audio(file); seconds += len(audio) / RATE
        cooldown = 0
        for offset in range(0, len(audio), RATE // 4):
            if cooldown: cooldown -= 1; continue
            clip = audio[max(0, offset - RATE):offset + RATE]
            if heard(clip): false += 1; cooldown = 8
    report = gates(hits, len(positives), false, seconds / 3600)
    report.update({"model_sha256": digest(args.model), "hits": hits, "positives": len(positives), "false_wakes": false,
                   "per_voice": per_voice, "hard_negative_wakes": hard_hits,
                   "hard_negatives": len(hard_negatives), "runtime": "CPU reference, not phone", "constant": args.constant,
                   "frozen_eval_sha256": model["frozen_eval_sha256"]})
    pathlib.Path(args.output).write_text(json.dumps(report, indent=2))
    print(json.dumps(report))
    if not report["passed"]: raise SystemExit(1)


def export(args):
    report = json.loads(pathlib.Path(args.report).read_text())
    if not report["passed"] or report.get("constant") or report["model_sha256"] != digest(args.model):
        raise ValueError("Only the exact model that passed frozen final-test gates can ship")
    target = ROOT / "app/src/main/assets/wake/custom_detector.json"
    target.write_bytes(pathlib.Path(args.model).read_bytes())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="stage", required=True)
    prepare = sub.add_parser("prepare"); prepare.add_argument("--output", required=True)
    prepare.add_argument("--sudanese-voice"); prepare.add_argument("--voice-license")
    fit = sub.add_parser("train"); fit.add_argument("--manifest", required=True); fit.add_argument("--output", required=True)
    cal = sub.add_parser("calibrate"); cal.add_argument("--model", required=True); cal.add_argument("--output", required=True)
    score = sub.add_parser("evaluate"); score.add_argument("--model", required=True); score.add_argument("--negatives", required=True)
    score.add_argument("--output", required=True); score.add_argument("--constant", choices=["yes", "no"])
    ship = sub.add_parser("export"); ship.add_argument("--model", required=True); ship.add_argument("--report", required=True)
    args = parser.parse_args()
    {"prepare": synthesize, "train": train, "calibrate": calibrate, "evaluate": evaluate, "export": export}[args.stage](args)


if __name__ == "__main__":
    main()
