#!/usr/bin/env python3
"""Reproduce independent Whisper feature fixtures in a NEW folder; never overwrites frozen data."""
import argparse
import ast
import hashlib
import pathlib
import typing
import urllib.request
import warnings
import numpy as np

URL = 'https://raw.githubusercontent.com/huggingface/transformers/v4.57.1/src/transformers/audio_utils.py'
SHA256 = 'c038450307a8dbc9a95eed8e413770f34814fa38058121ca9f2eee8dd0dfa958'
FUNCTIONS = {'hertz_to_mel', 'mel_to_hertz', '_create_triangular_filter_bank', 'mel_filter_bank',
             'spectrogram', 'window_function', 'power_to_db', 'amplitude_to_db', 'optimal_fft_length'}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=pathlib.Path, help='New or empty output folder')
    args = parser.parse_args()
    if args.output.exists() and any(args.output.iterdir()):
        raise ValueError('Never overwrite a frozen version; choose a new empty directory')
    with urllib.request.urlopen(URL, timeout=30) as response:
        raw = response.read()
    if hashlib.sha256(raw).hexdigest() != SHA256:
        raise ValueError('Upstream reference source changed')
    source = raw.decode()
    functions = [node for node in ast.parse(source).body if isinstance(node, ast.FunctionDef) and node.name in FUNCTIONS]
    namespace = vars(typing).copy()
    namespace.update(np=np, warnings=warnings)
    # Only the checksum-verified reference math functions execute, on synthetic signals.
    exec('from __future__ import annotations\n' + '\n'.join(ast.get_source_segment(source, node) for node in functions), namespace)
    mel = namespace['mel_filter_bank'](num_frequency_bins=201, num_mel_filters=80, min_frequency=0,
                                      max_frequency=8000, sampling_rate=16000, norm='slaney', mel_scale='slaney')
    args.output.mkdir(parents=True, exist_ok=True)
    checksums = []
    for name, size in [('long', 144000), ('short', 15360), ('silence', 16000)]:
        t = np.arange(size, dtype=np.float64) / 16000
        audio = (0.1 * np.sin(2 * np.pi * (200*t + 60*t*t)) + 0.03*np.cos(2*np.pi*1700*t)).astype(np.float32)
        if name == 'silence': audio = np.zeros(size, np.float32)
        padded = np.pad(audio, (max(0, 128000-len(audio)), 0))[-128000:]
        normalized = (padded-padded.mean()) / np.sqrt(padded.var()+1e-7)
        features = namespace['spectrogram'](normalized, namespace['window_function'](400, 'hann'), frame_length=400,
                                            hop_length=160, power=2.0, mel_filters=mel, log_mel='log10')[:, :-1]
        features = (np.maximum(features, features.max()-8)+4)/4
        target = args.output / (name+'.f32')
        target.write_bytes(features.astype('<f4').tobytes())
        checksums.append(hashlib.sha256(target.read_bytes()).hexdigest()+'  '+target.name+'\n')
    (args.output / 'SHA256SUMS').write_text(''.join(checksums))
    print('Synthetic reference fixtures written; no owner audio used and no accuracy claimed.')

if __name__ == '__main__':
    main()
