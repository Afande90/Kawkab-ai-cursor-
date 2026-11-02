#!/usr/bin/env python3
"""Fit verdict temperatures and thresholds on separate frozen partitions; final rows only score."""
import argparse
import collections
import hashlib
import json
import math
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODEL = '0a19bc29bacc33e0d871146c8612b24dd14c2ed2e61cedeb7a928b0852628bac'

def probability(scores, temperature):
    if len(scores) < 2 or any(not math.isfinite(x) for x in scores):
        raise ValueError('Invalid verdict scores')
    maximum = max(scores)
    weights = [math.exp((x - maximum) / temperature) for x in scores]
    total = sum(weights)
    return [x / total for x in weights]

def measurements(rows, scores, temperature, threshold):
    acted = correct = 0
    for row in rows:
        p = probability(scores[row['id']], temperature)
        chosen = max(range(len(p)), key=p.__getitem__)
        if p[chosen] >= threshold:
            acted += 1
            correct += row['options'][chosen] == row['label']
    return {'precision': correct / acted if acted else 0.0, 'acted': acted,
            'correct': correct, 'coverage': acted / len(rows) if rows else 0.0}

def production_eligible(rows):
    kinds = collections.Counter(row['kind'] for row in rows)
    languages = collections.Counter('ar' if any('\u0600' <= char <= '\u06ff' for char in row['state']) else 'en'
                                    for row in rows)
    return len(rows) >= 100 and all(kinds[kind] >= 25 for kind in ('contact', 'app', 'command_or_chat', 'act_or_ask')) \
        and all(languages[language] >= 25 for language in ('en', 'ar'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('scores', type=pathlib.Path)
    parser.add_argument('output', type=pathlib.Path)
    parser.add_argument('--manifest', type=pathlib.Path, default=ROOT / 'evals/decisions/v1/manifest.jsonl')
    parser.add_argument('--production', action='store_true', help='Requires a larger independently frozen bilingual final set')
    args = parser.parse_args()
    manifest = args.manifest
    folder = manifest.parent
    actual = hashlib.sha256(manifest.read_bytes()).hexdigest()
    if actual != (folder / 'SHA256SUMS').read_text().split()[0]:
        raise ValueError('Frozen decision fixtures changed')
    rows = [json.loads(line) for line in manifest.read_text().splitlines()]
    records = [json.loads(line) for line in args.scores.read_text().splitlines()]
    if len(records) != len(rows) or len({r['id'] for r in records}) != len(rows):
        raise ValueError('Every fixture needs exactly one score record')
    if any(r.get('fixtures_sha256') != actual for r in records):
        raise ValueError('Verdicts belong to another frozen fixture set')
    if any(r['model_sha256'] != MODEL for r in records):
        raise ValueError('Model hash mismatch')
    scores = {r['id']: r['scores'] for r in records}
    for row in rows:
        if len(scores[row['id']]) != len(row['options']): raise ValueError('Option count mismatch')
    if args.production and not production_eligible([r for r in rows if r['split'] == 'test']):
        raise ValueError('Production needs at least 100 independent final cases, 25 per kind and 25 per language')
    calibration = [r for r in rows if r['split'] == 'calibration']
    selection = [r for r in rows if r['split'] == 'threshold']
    final = [r for r in rows if r['split'] == 'test']
    def loss(t):
        return -sum(math.log(max(1e-12, probability(scores[r['id']], t)[r['options'].index(r['label'])]))
                    for r in calibration)
    temperature = min([0.3 + i * 0.047 for i in range(101)], key=loss)
    candidates = []
    for threshold in [0.95 + i * 0.0005 for i in range(100)]:
        result = measurements(selection, scores, temperature, threshold)
        if result['precision'] >= 0.95 and result['acted']:
            candidates.append((result['coverage'], threshold))
    if not candidates: raise ValueError('No threshold passes selection; the judge must ask')
    threshold = max(candidates)[1]
    result = measurements(final, scores, temperature, threshold)
    per_kind = {kind: measurements([r for r in final if r['kind'] == kind], scores, temperature, threshold)
                for kind in ('contact', 'app', 'command_or_chat', 'act_or_ask')}
    per_language = {language: measurements([r for r in final if ('ar' if any('\u0600' <= c <= '\u06ff' for c in r['state']) else 'en') == language], scores, temperature, threshold)
                    for language in ('en', 'ar')}
    groups_pass = all(value['precision'] >= 0.95 and value['acted'] > 0
                      for value in list(per_kind.values()) + list(per_language.values()))
    report = dict(model_sha256=MODEL, temperature=temperature, threshold=threshold,
                  final_precision=result['precision'], final_acted_count=result['acted'],
                  final_coverage=result['coverage'], fixtures_sha256=actual,
                  passed=result['precision'] >= 0.95 and result['acted'] > 0,
                  final_per_kind=per_kind, final_per_language=per_language,
                  production_ready=args.production and groups_pass and result['precision'] >= 0.95 and result['acted'] > 0,
                  runtime='phone verdict reader; ' + ('production final set' if args.production else 'smoke fixtures only'))
    args.output.write_text(json.dumps(report, indent=2))
    print(json.dumps(report))
    if not report['passed']: raise SystemExit(1)

if __name__ == '__main__':
    main()
