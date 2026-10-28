#!/usr/bin/env python3
"""Compliance checks from spec section 0.1.3.

This directory is excluded from the source scan, so the patterns live here.
"""

from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

import re

NAME_PATTERN = re.compile(r"anthropic|claude", re.IGNORECASE)
EXACT_ROOT_FILES = {
    "KAWKAB_SPEC.md",
    "review-v1.md",
    "DECISIONS.md",
    "THIRD_PARTY.md",
    "NOTICE",
    "LICENSE",
    "MODELS.md",
}
BINARY_SUFFIXES = {
    ".png", ".jpg", ".jpeg", ".webp", ".gif", ".so", ".jar", ".aar", ".apk",
    ".zip", ".ttf", ".otf", ".wav", ".mp3", ".mp4", ".pdf", ".bin", ".class",
    ".webp", ".ico", ".woff", ".woff2",
}


def excluded(rel: str) -> bool:
    """True when spec 0.1.3 says this repository-relative path is not scanned."""
    rel = rel.replace("\\", "/").lstrip("./")
    name = rel.rsplit("/", 1)[-1]
    if "/" not in rel and name in EXACT_ROOT_FILES:
        return True
    if rel == "docs" or rel.startswith("docs/"):
        return True
    if rel == "tests/compliance" or rel.startswith("tests/compliance/"):
        return True
    parts = rel.split("/")
    if parts[0] == "third_party" and (name.startswith("LICENSE") or name == "METADATA"):
        return True
    return False


def tracked_files(root: Path) -> list[str]:
    """Files the scan covers: tracked files plus untracked files that are not ignored."""
    try:
        out = subprocess.check_output(
            ["git", "ls-files", "-co", "--exclude-standard"],
            cwd=root,
            text=True,
        )
    except (subprocess.CalledProcessError, FileNotFoundError):
        found = []
        for path in root.rglob("*"):
            if path.is_file():
                found.append(str(path.relative_to(root)))
        return found
    return [line for line in out.splitlines() if line]


def source_hits(root: Path, files: list[str] | None = None) -> list[str]:
    hits = []
    for rel in files if files is not None else tracked_files(root):
        if excluded(rel):
            continue
        path = root / rel
        if not path.is_file():
            continue
        if path.suffix.lower() in BINARY_SUFFIXES:
            continue
        try:
            data = path.read_bytes()
        except OSError:
            continue
        if b"\0" in data[:8192]:
            continue
        text = data.decode("utf-8", errors="ignore")
        for number, line in enumerate(text.splitlines(), 1):
            if NAME_PATTERN.search(line):
                hits.append(f"{rel}:{number}:{line.strip()[:160]}")
    return hits


def domain_hits(root: Path, extra_roots: list[Path]) -> list[str]:
    domains = [
        line.strip()
        for line in (root / "tests/compliance/forbidden-domains.txt").read_text().splitlines()
        if line.strip()
    ]
    bases = [
        root / "app/src",
        root / "catalog/src",
        root / "tools-probe/src",
        *extra_roots,
    ]
    targets: list[Path] = []
    for base in bases:
        if not base.exists():
            continue
        for path in base.rglob("*"):
            if not path.is_file():
                continue
            rel = str(path.relative_to(root)) if path.is_relative_to(root) else str(path)
            name = path.name
            if name == "AndroidManifest.xml" or name == "network_security_config.xml":
                targets.append(path)
            elif name == "strings.xml" or (name.startswith("strings") and path.suffix == ".xml"):
                targets.append(path)
            elif "values" in path.parts and path.suffix == ".xml" and "string" in name:
                targets.append(path)
    hits = []
    seen = set()
    for path in targets:
        if path in seen:
            continue
        seen.add(path)
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        lower = text.lower()
        for domain in domains:
            if domain.lower() in lower:
                rel = path
                hits.append(f"{rel}: contains {domain}")
    return hits


def self_test() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / "src").mkdir()
        (root / "src" / "Bad.kt").write_text("val x = \"anthropic\"\n")
        (root / "docs").mkdir()
        (root / "docs" / "note.md").write_text("claude stays here\n")
        (root / "DECISIONS.md").write_text("anthropic\n")
        (root / "tests" / "compliance").mkdir(parents=True)
        (root / "tests" / "compliance" / "needle.txt").write_text("claude\n")
        (root / "third_party" / "lib").mkdir(parents=True)
        (root / "third_party" / "lib" / "LICENSE").write_text("anthropic\n")
        (root / "third_party" / "lib" / "Code.kt").write_text("claude\n")
        files = [
            "src/Bad.kt",
            "docs/note.md",
            "DECISIONS.md",
            "tests/compliance/needle.txt",
            "third_party/lib/LICENSE",
            "third_party/lib/Code.kt",
        ]
        hits = source_hits(root, files)
        if hits != ["src/Bad.kt:1:val x = \"anthropic\"", "third_party/lib/Code.kt:1:claude"]:
            raise SystemExit(f"self-test failed: {hits}")
        if excluded("docs/a/b.md") is not True or excluded("NOTICE") is not True:
            raise SystemExit("exclude rules failed")
        if excluded("app/src/NOTICE") is not False:
            raise SystemExit("only a root NOTICE is excluded")


def main(argv: list[str]) -> int:
    if len(argv) < 2 or argv[1] not in {"source", "domains", "self-test"}:
        print("usage: check_compliance.py source|domains|self-test", file=sys.stderr)
        return 2
    if argv[1] == "self-test":
        self_test()
        print("compliance self-test passed")
        return 0
    root = Path.cwd()
    if argv[1] == "source":
        self_test()
        hits = source_hits(root)
        if hits:
            print("Forbidden name outside the exclude list:", file=sys.stderr)
            print("\n".join(hits), file=sys.stderr)
            return 1
        print(f"Compliance source scan passed ({len(tracked_files(root))} files).")
        return 0
    extra = [root / arg for arg in argv[2:]]
    hits = domain_hits(root, extra)
    if hits:
        print("Forbidden domain in a manifest, network security config, or string resource:", file=sys.stderr)
        print("\n".join(hits), file=sys.stderr)
        return 1
    print("Compliance domain scan passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
