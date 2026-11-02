#!/usr/bin/env python3
"""Prepare the exact Llamatik 2.0.0 runtime headers; never rebuild a second llama runtime."""
import argparse
import pathlib
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
COMMIT = 'b29c606e28a01b1bc8c1351026a0fa6e616bf6c4'
HEADERS = ['include/llama.h', 'ggml/include/ggml.h', 'ggml/include/ggml-cpu.h',
           'ggml/include/ggml-backend.h', 'ggml/include/ggml-opt.h', 'ggml/include/gguf.h',
           'ggml/include/ggml-alloc.h', 'ggml/include/ggml-cpp.h']

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('aar', type=pathlib.Path, help='The resolved com.llamatik:core-android:2.0.0 AAR')
    args = parser.parse_args()
    import hashlib
    expected = '34d7d0dca9b9b4810ef871cd923f8017eb9713d47b2a7569b0359b7a9ac700c1'
    if hashlib.sha256(args.aar.read_bytes()).hexdigest() != expected:
        raise ValueError('The runtime AAR does not match the pinned Llamatik artifact')
    target = ROOT / 'app/src/main/cpp/decision/prepared'
    (target / 'include').mkdir(parents=True, exist_ok=True)
    for name in HEADERS:
        data = urllib.request.urlopen(f'https://raw.githubusercontent.com/ggml-org/llama.cpp/{COMMIT}/{name}').read()
        (target / 'include' / pathlib.Path(name).name).write_bytes(data)
    with zipfile.ZipFile(args.aar) as archive:
        for abi in ('arm64-v8a', 'x86_64'):
            (target / abi).mkdir(exist_ok=True)
            for name in ('libllama.so', 'libggml.so', 'libggml-cpu.so', 'libggml-base.so', 'libomp.so'):
                (target / abi / name).write_bytes(archive.read(f'jni/{abi}/{name}'))
    print(target)

if __name__ == '__main__':
    main()
