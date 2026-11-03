"""Fold one or more trained LoRA skill patches into the base checkpoint, in place, with the same task-vector math as
lab/brain/merge/merge.py (each patch's update B@A*alpha/r is a task vector).

    python merge_lora.py <base_dir> <recipe.json>
recipe: {"method": "linear"|"ties", "density": .., "lambda": .., "patches": [{"dir": "...", "weight": 1.0}, ...]}
"""
import json
import struct
import sys
from pathlib import Path

import torch
from safetensors.torch import load_file

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "merge"))
from merge import TORCH_DT, combine  # noqa: E402


def updates(patch_dir):
    cfg = json.load(open(f"{patch_dir}/adapter_config.json"))
    scale = cfg["lora_alpha"] / cfg["r"]
    sd = load_file(f"{patch_dir}/adapter_model.safetensors")
    out = {}
    for k, a in sd.items():
        if ".lora_A." not in k:
            continue
        b = sd[k.replace(".lora_A.", ".lora_B.")]
        name = k.replace("base_model.model.", "", 1).split(".lora_A.")[0] + ".weight"
        out[name] = (b.float() @ a.float()) * scale
    return out


def main(base_dir, recipe_path):
    recipe = json.load(open(recipe_path))
    ups = [updates(p["dir"]) for p in recipe["patches"]]
    weights = [float(p.get("weight", 1.0)) for p in recipe["patches"]]
    path = f"{base_dir}/model.safetensors"
    with open(path, "rb") as f:
        n = struct.unpack("<Q", f.read(8))[0]
        head = json.loads(f.read(n))
    head.pop("__metadata__", None)
    names = set().union(*[u.keys() for u in ups])
    missing = [x for x in names if x not in head]
    print(len(names), "patched tensors,", len(missing), "not found", missing[:3], flush=True)
    with open(path, "r+b") as out:
        for name in sorted(names - set(missing)):
            info = head[name]
            dt = TORCH_DT[info["dtype"]]
            a, b = info["data_offsets"]
            out.seek(8 + n + a)
            base = torch.frombuffer(bytearray(out.read(b - a)), dtype=dt).reshape(info["shape"]).float()
            idx = [i for i, u in enumerate(ups) if name in u]
            delta = combine([ups[i][name] for i in idx], [weights[i] for i in idx], recipe, torch.Generator().manual_seed(7))
            out.seek(8 + n + a)
            out.write((base + delta).to(dt).contiguous().view(torch.uint8).numpy().tobytes())
    print("merged", recipe_path, flush=True)


if __name__ == "__main__":
    main(*sys.argv[1:3])
