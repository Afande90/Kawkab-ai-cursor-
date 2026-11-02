#!/usr/bin/env python3
"""Read frozen decision fixtures on an attached Android phone, using the same renderer as the app.

No app data, contacts, recordings or conversations are read. Only public model weights,
fixture text and a temporary check runtime are copied over USB. No inference server is used.
"""
import argparse
import hashlib
import json
import math
import pathlib
import shlex
import struct
import subprocess
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODEL = '0a19bc29bacc33e0d871146c8612b24dd14c2ed2e61cedeb7a928b0852628bac'
LIBRARIES = ['libllama.so', 'libggml.so', 'libggml-cpu.so', 'libggml-base.so', 'libomp.so']

def digest(path):
    with pathlib.Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def field(text):
    value = text.encode('utf-8')
    if len(value) > 1024 * 1024:
        raise ValueError('Fixture field exceeds native input budget')
    return struct.pack('<I', len(value)) + value

def payload(rows):
    result = bytearray()
    for row in rows:
        result += field(row['state']) + field(row['question']) + struct.pack('<I', len(row['options']))
        for option in row['options']:
            result += field(option)
    return bytes(result)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=pathlib.Path, default=ROOT / 'evals/decisions/v1/manifest.jsonl')
    parser.add_argument('--model-file', required=True, type=pathlib.Path, help='Exact public GGUF on the build computer')
    parser.add_argument('--output', required=True, type=pathlib.Path)
    parser.add_argument('--runtime-dir', type=pathlib.Path)
    parser.add_argument('--serial', help='Optional adb device serial')
    args = parser.parse_args()
    fingerprint = digest(args.manifest)
    if fingerprint != args.manifest.with_name('SHA256SUMS').read_text().split()[0]:
        raise ValueError('Frozen fixture checksum mismatch')
    subprocess.run(['git', 'ls-files', '--error-unmatch', str(args.manifest)], cwd=ROOT, check=True,
                   stdout=subprocess.DEVNULL)
    subprocess.run(['git', 'diff', '--exit-code', 'HEAD', '--', str(args.manifest)], cwd=ROOT, check=True,
                   stdout=subprocess.DEVNULL)
    rows = [json.loads(line) for line in args.manifest.read_text().splitlines()]
    if len({r['id'] for r in rows}) != len(rows):
        raise ValueError('Duplicate fixture identity')
    if digest(args.model_file) != MODEL:
        raise ValueError('This is not the exact pinned public decision model')
    adb = ['adb'] + (['-s', args.serial] if args.serial else [])
    abi = subprocess.check_output(adb + ['shell', 'getprop', 'ro.product.cpu.abi'], text=True).strip()
    if abi not in ('arm64-v8a', 'x86_64'):
        raise ValueError('No verdict runtime for this Android ABI')
    runtime = args.runtime_dir or ROOT / 'build/decision' / abi
    names = ['kawkab_jev_check'] + LIBRARIES
    if any(not (runtime / name).is_file() for name in names):
        raise ValueError('Build the pinned native runtime with build_native.py first')
    remote = '/data/local/tmp/kawkab-jevm2v-' + uuid.uuid4().hex
    subprocess.run(adb + ['shell', 'mkdir', remote], check=True)
    try:
        for name in names:
            subprocess.run(adb + ['push', str(runtime / name), remote + '/' + name], check=True,
                           stdout=subprocess.DEVNULL)
        subprocess.run(adb + ['push', str(args.model_file), remote + '/model.gguf'], check=True,
                       stdout=subprocess.DEVNULL)
        actual = subprocess.check_output(adb + ['shell', 'sha256sum', remote + '/model.gguf'], text=True).split()[0]
        if actual != MODEL:
            raise ValueError('On-phone model hash mismatch')
        subprocess.run(adb + ['shell', 'chmod', '700', remote + '/kawkab_jev_check'], check=True)
        command = f'LD_LIBRARY_PATH={shlex.quote(remote)} {shlex.quote(remote + "/kawkab_jev_check")} ' + shlex.quote(remote + '/model.gguf')
        completed = subprocess.run(adb + ['shell', '-T', command], input=payload(rows), capture_output=True, check=True)
        lines = completed.stdout.decode().splitlines()
        if len(lines) != len(rows):
            raise ValueError('The phone did not return exactly one verdict vector per fixture')
        records = []
        runtime_sha = digest(runtime / 'libllama.so')
        for row, line in zip(rows, lines):
            scores = [float(value) for value in line.split()]
            if len(scores) != len(row['options']) or any(not math.isfinite(value) for value in scores):
                raise ValueError('Malformed or non-finite on-phone readout')
            records.append(dict(id=row['id'], scores=scores, model_sha256=MODEL, fixtures_sha256=fingerprint,
                                runtime='Llamatik 2.0.0 Android verdict reader', abi=abi, runtime_sha256=runtime_sha))
        args.output.write_text(''.join(json.dumps(record) + '\n' for record in records))
        print(f'Wrote {len(records)} actual Android verdict vectors. Precision is not established until final scoring.')
    finally:
        # Only this invocation's UUID directory is removed; no app or owner data is touched.
        subprocess.run(adb + ['shell', 'rm', '-rf', remote], check=False, stdout=subprocess.DEVNULL)

if __name__ == '__main__':
    main()
