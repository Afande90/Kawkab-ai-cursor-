#!/usr/bin/env python3
"""Fetch licensed LibriSpeech test-clean + dev-clean, streaming archives without tar path traversal."""
import argparse
import hashlib
import json
import pathlib
import tarfile
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("output", type=pathlib.Path)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
rows = []
for split in ("test-clean", "dev-clean"):
    url = "https://www.openslr.org/resources/12/" + split + ".tar.gz"
    with urllib.request.urlopen(url, timeout=120) as response:
        with tarfile.open(fileobj=response, mode="r|gz") as archive:
            for item in archive:
                if not item.isfile() or not item.name.endswith(".flac"):
                    continue
                relative = pathlib.PurePosixPath(item.name)
                if relative.is_absolute() or ".." in relative.parts:
                    raise ValueError("Unsafe archive member")
                target = args.output.joinpath(*relative.parts)
                target.parent.mkdir(parents=True, exist_ok=True)
                data = archive.extractfile(item).read()
                target.write_bytes(data)
                rows.append({"file": str(relative), "sha256": hashlib.sha256(data).hexdigest(),
                             "source": url, "license": "CC-BY-4.0"})
manifest = args.output / "corpus-manifest.json"
manifest.write_text(json.dumps(rows, indent=2))
root = pathlib.Path(__file__).resolve().parents[2]
(root / "training/wake/negative-corpus.sha256").write_text(hashlib.sha256(manifest.read_bytes()).hexdigest() + "\n")
print("Downloaded", len(rows), "licensed real negative recordings. Commit training/wake/negative-corpus.sha256 before evaluation.")
