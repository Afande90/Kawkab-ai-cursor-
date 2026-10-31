#!/usr/bin/env python3
"""Task 23d gate: the median frame time of the tab-switch benchmark is under 16 ms.

Reads every *benchmarkData.json under the directory given as the first argument,
prints the median (P50) of frameDurationCpuMs for each benchmark, and exits 1
when any is 16 ms or more, or when no result was written.
"""
import glob
import json
import os
import sys

LIMIT_MS = 16.0


def main() -> int:
    root = sys.argv[1]
    files = glob.glob(os.path.join(root, "**", "*benchmarkData.json"), recursive=True)
    if not files:
        print(f"No benchmark result under {root}.")
        return 1
    failed = False
    for path in files:
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle)
        for bench in data.get("benchmarks", []):
            metrics = bench.get("sampledMetrics", {}) or bench.get("metrics", {})
            frame = metrics.get("frameDurationCpuMs")
            if frame is None:
                print(f"{bench.get('name')}: no frameDurationCpuMs")
                failed = True
                continue
            median = float(frame.get("P50", frame.get("median", 0.0)))
            print(f"{bench.get('name')}: median frame {median:.2f} ms (P90 {frame.get('P90')}, P99 {frame.get('P99')})")
            if median >= LIMIT_MS:
                failed = True
    if failed:
        print(f"Gate: median frame time must be under {LIMIT_MS:.0f} ms.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
