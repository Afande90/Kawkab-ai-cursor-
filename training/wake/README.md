# Wake-word retraining, local CPU

The Android detector supports a learned generic head, ten owner keyword templates and the existing sherpa keyword spotter as a second opinion. A separate sherpa speaker check must accept the owner before any wake or command is delivered. The owner templates and PCM never leave the phone.

The generic head is trained from synthetic audio with log-spectral features identical to the Android implementation. It is deliberately small enough for the wake worker; no hosted inference or paid GPU is required. No weights are supplied pretending to pass the target.

## Reproducible stages

Install the pinned Python requirements and the free `espeak-ng` and Piper command-line tools. Piper is a build-time personal-use tool, not code bundled in the app. Supply a licensed local Sudanese-accented Arabic Piper voice plus its licence. Ordinary Arabic must not be relabelled Sudanese. The plan includes separate English and Arabic synthetic voices, speeds and noise levels. The frozen evaluation voices are checked against all training speakers.

Run from the repository root:

```sh
python3 -m pip install -r training/wake/requirements.txt
python3 training/wake/pipeline.py prepare --output training/wake/data \
  --sudanese-voice /local/licensed-sudanese.onnx --voice-license /local/VOICE-LICENSE.txt
# Commit only the generated manifest and its SHA in a separate data-freeze commit.
# Keep the generated audio in the local data directory; its hash is pinned per row.
git add -f training/wake/data/manifest.jsonl training/wake/data/manifest.sha256
git commit -m 'data: freeze synthetic wake training inputs'
python3 training/wake/pipeline.py train --manifest training/wake/data/manifest.jsonl --output /tmp/kws-trained.json
python3 training/wake/pipeline.py calibrate --model /tmp/kws-trained.json --output /tmp/kws-calibrated.json
python3 training/wake/fetch_negatives.py /local/librispeech
python3 training/wake/pipeline.py evaluate --model /tmp/kws-calibrated.json --negatives /local/librispeech --output /tmp/kws-report.json
# Both mutations must exit nonzero:
python3 training/wake/pipeline.py evaluate --model /tmp/kws-calibrated.json --negatives /local/librispeech --constant yes --output /tmp/kws-yes.json
python3 training/wake/pipeline.py evaluate --model /tmp/kws-calibrated.json --negatives /local/librispeech --constant no --output /tmp/kws-no.json
python3 training/wake/pipeline.py export --model /tmp/kws-calibrated.json --report /tmp/kws-report.json
```

Training and temperature fitting use disjoint synthetic partitions. Threshold selection uses only v1 calibration; final scoring uses only v1 test and at least ten hours of real negative audio. The immutable v1 hash is pinned in the plan. Export refuses changed model bytes, constant predictors, missing negatives or failed gates. The thresholds remain recall ≥90% and ≤0.1 false wakes per hour. Record the per-language results, the model SHA and the reference-runtime label before committing exported weights. Then measure latency and battery on the actual phone.

## What could not be run here

No licensed Sudanese Piper voice, eSpeak/Piper executable or ten-hour real negative corpus is installed in this workspace. These are required inputs, not grounds to train on the final test set or claim a pass. The six pipeline mutation/integrity tests run without them. The existing frozen v1 audio and its checksum files remain unchanged. The new generic head therefore remains absent until it passes the evaluation. On the phone, the enrolled owner's ten templates provide the personal detector; sherpa remains the second opinion. Neither its recall nor its false-wake rate is claimed measured here.
