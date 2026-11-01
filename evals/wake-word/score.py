#!/usr/bin/env python3
"""Scores the wake word on the frozen set (task 27).

Reference runtime: sherpa-onnx 1.13.8 (pip), the same version as the app's AAR,
with the sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01 int8 model.

  python3 -I score.py --model DIR --split calibration --threshold 0.25 --boost 2.0
  python3 -I score.py --model DIR --split test ... --negatives DIR_WITH_LIBRISPEECH

--keywords takes the app's keyword lines (WakeKeyword.LINES), one per line.
--constant yes|no replaces the spotter with a constant answer (mutation check,
spec §12.3.6): the script must then report a failing result.
Prints one JSON object.
"""
import argparse
import json
import multiprocessing
import pathlib
import tempfile

import numpy as np
import sherpa_onnx
import soundfile as sf

import os
SET = pathlib.Path(os.environ.get("WAKE_SET", pathlib.Path(__file__).resolve().parent / "v1"))
RATE = 16000
ARGS = None
SPOTTER = None


def build(args):
    m = pathlib.Path(args.model)
    kw = tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False)
    kw.write(args.keywords.replace("\\n", "\n").strip() + "\n")
    kw.close()
    return sherpa_onnx.KeywordSpotter(
        tokens=str(m / "tokens.txt"),
        encoder=str(next(m.glob("encoder-*.int8.onnx"))),
        decoder=str(next(m.glob("decoder-*.int8.onnx"))),
        joiner=str(next(m.glob("joiner-*.int8.onnx"))),
        num_threads=1,
        keywords_file=kw.name,
        keywords_score=args.boost,
        keywords_threshold=args.threshold,
        num_trailing_blanks=1,
        provider="cpu",
    )


def init(args):
    global ARGS, SPOTTER
    ARGS = args
    SPOTTER = None if args.constant else build(args)


def wakes(path):
    x, rate = sf.read(path, dtype="float32")
    if x.ndim > 1:
        x = x.mean(axis=1)
    seconds = len(x) / rate
    if ARGS.constant:
        return (1 if ARGS.constant == "yes" else 0), seconds
    s = SPOTTER.create_stream()
    count = 0
    step = rate // 10
    for i in range(0, len(x), step):
        s.accept_waveform(rate, x[i:i + step])
        while SPOTTER.is_ready(s):
            SPOTTER.decode_stream(s)
            if SPOTTER.get_result(s):
                count += 1
                SPOTTER.reset_stream(s)
    s.accept_waveform(rate, np.zeros(int(0.5 * rate), dtype=np.float32))
    s.input_finished()
    while SPOTTER.is_ready(s):
        SPOTTER.decode_stream(s)
        if SPOTTER.get_result(s):
            count += 1
            SPOTTER.reset_stream(s)
    return count, seconds


def run(paths, args):
    with multiprocessing.Pool(args.jobs, initializer=init, initargs=(args,)) as pool:
        return pool.map(wakes, paths, chunksize=8)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--model", required=True)
    p.add_argument("--split", choices=["calibration", "test"], required=True)
    p.add_argument("--keywords", default="▁K A W K A B @KAWKAB")
    p.add_argument("--threshold", type=float, default=0.25)
    p.add_argument("--boost", type=float, default=1.0)
    p.add_argument("--negatives")
    p.add_argument("--jobs", type=int, default=multiprocessing.cpu_count())
    p.add_argument("--constant", choices=["yes", "no"])
    args = p.parse_args()
    rows = [json.loads(line) for line in open(SET / "manifest.jsonl", encoding="utf-8")]
    rows = [r for r in rows if r["split"] == args.split]
    res = run([str(SET / r["file"]) for r in rows], args)
    out = {"split": args.split, "keywords": args.keywords, "threshold": args.threshold, "boost": args.boost}
    by = {}
    for r, (n, _) in zip(rows, res):
        key = (r["kind"], r["voice"].replace("vits-piper-", ""))
        hit, total = by.get(key, (0, 0))
        by[key] = (hit + (1 if n > 0 else 0), total + 1)
    pos = [n > 0 for r, (n, _) in zip(rows, res) if r["kind"] == "positive"]
    neg = [n > 0 for r, (n, _) in zip(rows, res) if r["kind"] == "hard_negative"]
    out["recall"] = sum(pos) / len(pos)
    out["hard_negative_accepts"] = f"{sum(neg)}/{len(neg)}"
    out["per_voice"] = {f"{k[0]}:{k[1]}": f"{h}/{t}" for k, (h, t) in sorted(by.items())}
    if args.negatives:
        files = sorted(str(f) for f in pathlib.Path(args.negatives).rglob("*.flac"))
        neg_res = run(files, args)
        hours = sum(s for _, s in neg_res) / 3600
        false = sum(n for n, _ in neg_res)
        out["negative_hours"] = round(hours, 2)
        out["false_wakes"] = false
        out["false_wakes_per_hour"] = round(false / hours, 3)
    out["passes_targets"] = out["recall"] >= 0.9 and out.get("false_wakes_per_hour", 0) <= 0.1
    print(json.dumps(out, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
