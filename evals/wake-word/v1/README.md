# Wake-word set v1 (task 27)

Frozen on 1 Nov 2026 before any keyword or threshold choice (spec §12.3). Do not edit; make v2.

- `manifest.jsonl`: one row per clip (file, split, kind, voice, speaker, speed, text, SNR).
- `audio/`: 16 kHz mono FLAC, 0.4 s silence each side, Gaussian noise at 10–25 dB SNR.
- `SHA256SUMS`: the hash of every clip. Its own SHA-256 is the set hash recorded in `DECISIONS.md`.
- `generate.py`: how the clips were made (sherpa-onnx 1.13.8, Piper voices). The TTS is not bit-for-bit reproducible, so the committed audio is the set, not the script.

| Split | Positives | Hard negatives | Voices |
|---|---|---|---|
| calibration | 126 | 40 | libritts_r speakers 0–39, amy |
| test | 534 | 102 | libritts_r speakers 500–659, ryan, alan, kareem (Arabic) |

Hard negatives: Kawab, Karkab, Kawkaba, Kebab, كوكبة, كباب, كواب, كركب.

Real negative audio is not stored here. It is LibriSpeech `test-clean` and `dev-clean` (openslr.org/12, CC BY 4.0, about 10.7 hours of English read speech), downloaded by `score.py --negatives`. Common Voice Arabic needs a login and MUSAN is 11 GB, so neither is used; Arabic false wakes are therefore not measured on real speech.

Licences of the voices (eval data only, never shipped): libritts_r CC BY 4.0; ryan CC BY-NC-SA 4.0; alan, amy (Mimic 3 datasets) and kareem (arabicttstrain) under their dataset licences. The owner's 29 Oct 2026 licence decision covers non-commercial use.
