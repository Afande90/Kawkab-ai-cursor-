"""Kawkab merge lab: fold several Gemma 4 E2B fine-tunes into ONE model with task-vector math.

    python merge.py <base_dir> <candidate.json> [autopsy_out.json]

The base checkpoint in <base_dir>/model.safetensors is rewritten IN PLACE, tensor by tensor (the output has the same
names, shapes and dtypes, so every byte offset stays the same). Expert checkpoints are never downloaded whole: each
tensor is range-read from the Hugging Face Hub when it is needed, so a merge of four 10 GB models needs only the base
on disk.

Every expert i gives a task vector  t_i = expert_i - base.  Methods (all per tensor, float32):
  linear     base + sum_i w_i * t_i                                   (task arithmetic, Ilharco et al. 2023)
  ties       trim each t_i to its top `density` magnitudes, elect a sign per entry by summed mass, average only the
             entries that agree with it, scale by `lambda`                  (TIES-Merging, Yadav et al. 2023)
  dare_ties  drop each t_i entry with probability 1-density and rescale the rest by 1/density, then TIES sign
             election                                                    (DARE, Yu et al. 2024)
Options:
  svd_rank   before merging, replace every 2-D t_i by its best rank-r approximation. A LoRA fine-tune merged into a
             4-bit (QLoRA) base carries full-rank quantisation noise on top of its low-rank update; truncation keeps
             the update and drops the noise (the autopsy measures how much energy sits in the top ranks).
  layers     {"from": a, "to": b} only merge decoder layers a..b (embeddings and other layers stay base).
Only language-model tensors are merged; the vision and audio towers stay exactly the base.
"""
import json
import struct
import sys
import time

import requests
import torch
from huggingface_hub import hf_hub_download, hf_hub_url

ROWS_PER_CHUNK = 8192
SAMPLE = 2_000_000
TORCH_DT = {"BF16": torch.bfloat16, "F16": torch.float16, "F32": torch.float32}
SESSION = requests.Session()


def fetch(url, start, length):
    """Bytes [start, start+length) of a Hub file by HTTP range request, with retries."""
    for attempt in range(8):
        try:
            r = SESSION.get(url, headers={"Range": f"bytes={start}-{start + length - 1}"}, timeout=600)
            r.raise_for_status()
            if len(r.content) == length:
                return r.content
        except requests.RequestException:
            pass
        time.sleep(min(60, 2 ** attempt))
    raise IOError(f"range read failed: {url} {start}+{length}")


def header_of(read8, readn):
    n = struct.unpack("<Q", read8())[0]
    head = json.loads(readn(n))
    head.pop("__metadata__", None)
    return head, 8 + n


class Remote:
    """Lazy reader for a (possibly sharded) safetensors checkpoint on the Hub."""

    def __init__(self, repo, revision="main"):
        self.repo, self.files, self.where = repo, {}, {}
        try:
            index = json.load(open(hf_hub_download(repo, "model.safetensors.index.json", revision=revision)))
            shards = sorted(set(index["weight_map"].values()))
        except Exception:
            shards = ["model.safetensors"]
        for shard in shards:
            url = hf_hub_url(repo, shard, revision=revision)
            n = struct.unpack("<Q", fetch(url, 0, 8))[0]
            head = json.loads(fetch(url, 8, n))
            head.pop("__metadata__", None)
            self.files[shard] = (url, 8 + n)
            for name, info in head.items():
                self.where[name] = (shard, info)

    def info(self, name):
        return self.where.get(name, (None, None))[1]

    def rows(self, name, start, stop):
        shard, info = self.where[name]
        url, off = self.files[shard]
        shape, dt = (info["shape"] or [1]), TORCH_DT[info["dtype"]]
        row = 1
        for d in shape[1:]:
            row *= d
        size = torch.tensor([], dtype=dt).element_size()
        a = info["data_offsets"][0] + start * row * size
        buf = fetch(url, off + a, (stop - start) * row * size)
        return torch.frombuffer(bytearray(buf), dtype=dt).reshape([stop - start] + shape[1:]).float()


def trim(t, density):
    if density >= 1:
        return t
    flat = t.abs().flatten()
    sample = flat[torch.randint(0, flat.numel(), (min(SAMPLE, flat.numel()),))] if flat.numel() > SAMPLE else flat
    cut = torch.quantile(sample.float(), 1 - density)
    return t * (t.abs() >= cut)


def dare(t, density, gen):
    keep = torch.rand(t.shape, generator=gen) < density
    return t * keep / density


def low_rank(t, rank):
    if t.dim() != 2 or min(t.shape) <= rank * 2:
        return t
    u, s, v = torch.svd_lowrank(t, q=rank + 8, niter=3)
    return (u[:, :rank] * s[:rank]) @ v[:, :rank].T


def combine(deltas, weights, cand, gen):
    method = cand["method"]
    if cand.get("svd_rank"):
        deltas = [low_rank(d, cand["svd_rank"]) for d in deltas]
    if method == "linear":
        return sum(w * d for w, d in zip(weights, deltas))
    density = cand.get("density", 0.3)
    if method == "ties":
        kept = [w * trim(d, density) for w, d in zip(weights, deltas)]
    elif method == "dare_ties":
        kept = [w * dare(d, density, gen) for w, d in zip(weights, deltas)]
    else:
        raise ValueError(method)
    stack = torch.stack(kept)
    sign = torch.sign(stack.sum(0))
    agree = (torch.sign(stack) == sign) & (stack != 0)
    total = (stack * agree).sum(0)
    count = agree.sum(0).clamp(min=1)
    return cand.get("lambda", 1.0) * total / count


def layer_of(name):
    parts = name.split(".")
    if "layers" in parts:
        try:
            return int(parts[parts.index("layers") + 1])
        except (ValueError, IndexError):
            return None
    return None


def energy(t, ranks=(16, 64)):
    """Share of a 2-D tensor's squared Frobenius norm that sits in its top-r singular values."""
    total = float((t * t).sum())
    if total == 0:
        return {f"top{r}": 1.0 for r in ranks}
    _, s, _ = torch.svd_lowrank(t, q=max(ranks) + 8, niter=4)
    return {f"top{r}": float((s[:r] ** 2).sum()) / total for r in ranks}


def main(base_dir, cand_path, autopsy_path=None):
    cand = json.load(open(cand_path))
    experts = [Remote(e["repo"], e.get("revision", "main")) for e in cand["experts"]]
    weights = [float(e.get("weight", 1.0)) for e in cand["experts"]]
    gen = torch.Generator().manual_seed(cand.get("seed", 7))
    span = cand.get("layers")
    path = f"{base_dir}/model.safetensors"
    with open(path, "rb") as f:
        head, off = header_of(lambda: f.read(8), lambda n: f.read(n))
    stats = {e["repo"]: {"groups": {}, "skipped": [], "spectrum": {}} for e in cand["experts"]}
    probe = {f"model.language_model.layers.{i}.{p}" for i in (0, 8, 17, 26, 34)
             for p in ("self_attn.q_proj.weight", "mlp.down_proj.weight")}
    changed, t0 = 0, time.time()
    with open(path, "r+b") as out:
        for name in sorted(head):
            info = head[name]
            if "language_model" not in name or info["dtype"] not in TORCH_DT:
                continue
            lay = layer_of(name)
            if span and (lay is None or not span["from"] <= lay <= span["to"]):
                continue
            usable = []
            for i, ex in enumerate(experts):
                ei = ex.info(name)
                if ei is None or ei["shape"] != info["shape"]:
                    stats[cand["experts"][i]["repo"]]["skipped"].append(name)
                else:
                    usable.append(i)
            if not usable:
                continue
            shape, dt = (info["shape"] or [1]), TORCH_DT[info["dtype"]]
            rows_total = shape[0]
            row = 1
            for d in shape[1:]:
                row *= d
            size = torch.tensor([], dtype=dt).element_size()
            group = "ple" if "per_layer" in name and lay is None else (
                "embed" if "embed" in name else "attn" if "self_attn" in name else "mlp" if "mlp" in name else "other")
            step = min(rows_total, ROWS_PER_CHUNK)
            for start in range(0, rows_total, step):
                stop = min(rows_total, start + step)
                a = off + info["data_offsets"][0] + start * row * size
                out.seek(a)
                raw = out.read((stop - start) * row * size)
                base = torch.frombuffer(bytearray(raw), dtype=dt).reshape([stop - start] + shape[1:]).float()
                deltas = [experts[i].rows(name, start, stop).reshape(base.shape) - base for i in usable]
                for i, d in zip(usable, deltas):
                    g = stats[cand["experts"][i]["repo"]]["groups"].setdefault(group, [0.0, 0.0])
                    g[0] += float((d * d).sum())
                    g[1] += float((base * base).sum())
                    if autopsy_path and name in probe:
                        stats[cand["experts"][i]["repo"]]["spectrum"][name] = energy(d)
                merged = base + combine(deltas, [weights[i] for i in usable], cand, gen)
                out.seek(a)
                out.write(merged.to(dt).contiguous().view(torch.uint8).numpy().tobytes())
            changed += 1
            if changed % 100 == 0:
                print(f"{changed} tensors merged, {time.time() - t0:.0f}s", flush=True)
    for repo, s in stats.items():
        s["relative_change"] = {g: (v[0] / v[1]) ** 0.5 if v[1] else None for g, v in s["groups"].items()}
        s["skipped_count"] = len(s["skipped"])
        s["skipped"] = s["skipped"][:20]
        del s["groups"]
    print(json.dumps({"tensors": changed, "seconds": round(time.time() - t0), "experts": stats}, indent=1))
    if autopsy_path:
        json.dump({"candidate": cand, "tensors": changed, "experts": stats}, open(autopsy_path, "w"), indent=1)


if __name__ == "__main__":
    main(*sys.argv[1:])
