#!/usr/bin/env python3
"""Isolate the Java ORT core without replacing sherpa's differently versioned core.

Only ELF dependency/SONAME metadata changes. Java classes and machine instructions
are retained. Both original artifacts are checked by Gradle and this script.
"""
import argparse
import copy
import struct
import hashlib
import pathlib
import platform
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request
import zipfile

AAR_SHA256 = 'f351a0638696f54b35184290dbc001d66daae17281ad0b548d2c70347d53b8a9'
TOOL_URL = 'https://github.com/NixOS/patchelf/releases/download/0.18.0/patchelf-0.18.0-x86_64.tar.gz'
TOOL_SHA256 = 'ce84f2447fb7a8679e58bc54a20dc2b01b37b5802e12c57eece772a6f14bf3f0'
WIN_URL = 'https://github.com/NixOS/patchelf/releases/download/0.18.0/patchelf-win64-0.18.0.exe'
WIN_SHA256 = 'ea5293833b6a547612ce4b073ac84fd603011ce3455f488a1017fabc8bd170ff'
CORE = 'libkawkab_smart_turn_ort.so'

def digest(path):
    with pathlib.Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def version_needs(path):
    """Read ELF32/ELF64 version requirements without needing host binutils."""
    data = pathlib.Path(path).read_bytes()
    if data[:4] != b'\x7fELF' or data[5] != 1:
        raise ValueError('Expected little-endian Android ELF')
    bits = data[4]
    if bits == 2:
        offset = struct.unpack_from('<Q', data, 40)[0]
        stride, count = struct.unpack_from('<HH', data, 58)
        layout = '<IIQQQQIIQQ'
    elif bits == 1:
        offset = struct.unpack_from('<I', data, 32)[0]
        stride, count = struct.unpack_from('<HH', data, 46)
        layout = '<IIIIIIIIII'
    else:
        raise ValueError('Unknown ELF class')
    sections = [struct.unpack_from(layout, data, offset + index*stride) for index in range(count)]
    requirements = {}
    for section in sections:
        if section[1] != 0x6ffffffe: continue  # SHT_GNU_verneed
        strings = sections[section[6]]
        def text(index):
            start = strings[4] + index
            return data[start:data.index(b'\0', start)].decode()
        position = section[4]
        while True:
            _, items, filename, auxiliary, following = struct.unpack_from('<HHIII', data, position)
            cursor = position + auxiliary
            names = []
            for _ in range(items):
                _, _, _, name, advance = struct.unpack_from('<IHHII', data, cursor)
                names.append(text(name))
                cursor += advance
            requirements[text(filename)] = names
            if not following: break
            position += following
    return requirements

def patch_tool(folder):
    system = platform.system()
    if platform.machine() not in ('x86_64', 'AMD64') or system not in ('Linux', 'Windows'):
        raise RuntimeError('Use a Linux x86_64 or Windows x64 build host for the pinned ELF tool')
    folder.mkdir(parents=True, exist_ok=True)
    windows = system == 'Windows'
    archive = folder / ('patchelf-0.18.0.exe' if windows else 'patchelf-0.18.0.tar.gz')
    expected = WIN_SHA256 if windows else TOOL_SHA256
    if not archive.is_file() or digest(archive) != expected:
        part = archive.with_suffix('.part')
        with urllib.request.urlopen(WIN_URL if windows else TOOL_URL, timeout=60) as source, part.open('wb') as target:
            shutil.copyfileobj(source, target)
        if digest(part) != expected:
            part.unlink()
            raise ValueError('Native build tool checksum mismatch')
        part.replace(archive)
    if windows: return archive
    executable = folder / 'patchelf'
    with tarfile.open(archive) as source:
        member = next(m for m in source if m.name.lstrip('./') == 'bin/patchelf')
        executable.write_bytes(source.extractfile(member).read())
    executable.chmod(0o755)
    return executable

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=pathlib.Path)
    parser.add_argument('output', type=pathlib.Path)
    parser.add_argument('--tools', required=True, type=pathlib.Path)
    args = parser.parse_args()
    if digest(args.input) != AAR_SHA256:
        raise ValueError('Unexpected Java ORT artifact')
    tool = str(patch_tool(args.tools))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    partial = args.output.with_suffix('.part')
    try:
        with tempfile.TemporaryDirectory(prefix='kawkab-ort-') as working:
            work = pathlib.Path(working)
            with zipfile.ZipFile(args.input) as source, zipfile.ZipFile(partial, 'w', zipfile.ZIP_DEFLATED) as target:
                cores = [name for name in source.namelist() if name.startswith('jni/') and name.endswith('/libonnxruntime.so')]
                if len(cores) != 4:
                    raise ValueError('Unexpected native ABI inventory')
                replaced = set()
                for name in cores:
                    abi = name.split('/')[1]
                    directory = work / abi
                    directory.mkdir()
                    core = directory / CORE
                    bridge = directory / 'libonnxruntime4j_jni.so'
                    bridge_name = f'jni/{abi}/{bridge.name}'
                    core.write_bytes(source.read(name))
                    bridge.write_bytes(source.read(bridge_name))
                    subprocess.run([tool, '--page-size', '16384', '--set-soname', CORE, str(core)], check=True)
                    subprocess.run([tool, '--page-size', '16384', '--replace-needed', 'libonnxruntime.so', CORE, str(bridge)], check=True)
                    needed = subprocess.check_output([tool, '--print-needed', str(bridge)], text=True).splitlines()
                    soname = subprocess.check_output([tool, '--print-soname', str(core)], text=True).strip()
                    if CORE not in needed or 'libonnxruntime.so' in needed or soname != CORE:
                        raise ValueError('Runtime isolation did not bind the Java bridge to its own core')
                    # Check the version-need file too: preserving VERS_1.28.0 alone is insufficient.
                    versions = version_needs(bridge)
                    if versions.get(CORE) != ['VERS_1.28.0'] or 'libonnxruntime.so' in versions:
                        raise ValueError('Java bridge lost its exact versioned runtime binding')
                    core_info = copy.copy(source.getinfo(name))
                    core_info.filename = f'jni/{abi}/{CORE}'
                    target.writestr(core_info, core.read_bytes())
                    target.writestr(source.getinfo(bridge_name), bridge.read_bytes())
                    replaced.update((name, bridge_name))
                    print(f'{abi}: Java JNI -> {CORE} @ VERS_1.28.0; sherpa core untouched')
                for info in source.infolist():
                    if info.filename not in replaced:
                        target.writestr(info, source.read(info.filename))
            with zipfile.ZipFile(partial) as archive:
                if any(name.endswith('/libonnxruntime.so') for name in archive.namelist()):
                    raise ValueError('The Java artifact still competes for sherpa runtime ownership')
        partial.replace(args.output)
    finally:
        partial.unlink(missing_ok=True)

if __name__ == '__main__':
    main()
