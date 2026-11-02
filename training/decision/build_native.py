#!/usr/bin/env python3
"""Build only the verdict JNI adapter; link the existing pinned Llamatik libraries."""
import argparse
import hashlib
import pathlib
import shutil
import subprocess
import sys
import tempfile
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
AAR_SHA256 = '34d7d0dca9b9b4810ef871cd923f8017eb9713d47b2a7569b0359b7a9ac700c1'
AAR_URL = 'https://repo.maven.apache.org/maven2/com/llamatik/core-android/2.0.0/core-android-2.0.0.aar'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ndk', required=True, type=pathlib.Path)
    parser.add_argument('--aar', type=pathlib.Path)
    args = parser.parse_args()
    toolchain = args.ndk / 'build/cmake/android.toolchain.cmake'
    if not toolchain.is_file():
        parser.error('The pinned Android NDK must be installed before building the verdict adapter')
    with tempfile.TemporaryDirectory(prefix='kawkab-decision-') as folder:
        temporary = pathlib.Path(folder)
        aar = args.aar or temporary / 'core-release.aar'
        if not args.aar:
            with urllib.request.urlopen(AAR_URL) as source, aar.open('wb') as target:
                shutil.copyfileobj(source, target)
        if hashlib.sha256(aar.read_bytes()).hexdigest() != AAR_SHA256:
            raise ValueError('Llamatik artifact checksum mismatch')
        subprocess.run([sys.executable, str(ROOT / 'training/decision/prepare_native.py'), str(aar)], check=True)
        for abi in ('arm64-v8a', 'x86_64'):
            output = temporary / abi
            subprocess.run(['cmake', '-S', str(ROOT / 'app/src/main/cpp/decision'), '-B', str(output),
                            f'-DCMAKE_TOOLCHAIN_FILE={toolchain}', f'-DANDROID_ABI={abi}',
                            '-DANDROID_PLATFORM=android-34', '-DANDROID_STL=c++_shared',
                            '-DCMAKE_BUILD_TYPE=Release'], check=True)
            subprocess.run(['cmake', '--build', str(output), '--parallel', '2'], check=True)
            target = ROOT / 'app/src/main/jniLibs' / abi
            target.mkdir(parents=True, exist_ok=True)
            shutil.copy2(output / 'libkawkab_decision.so', target / 'libkawkab_decision.so')

if __name__ == '__main__':
    main()
