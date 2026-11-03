"""Combine J-lens shards into a J-space basis per layer, then measure how much each fine-tune's update lands inside it.

    python analyse.py <basis_out.pt> <report.json> <shard.pt>...

Basis: at each layer, the top right-singular vectors of the matrix of concept J-lens vectors, enough to hold 90% of
their energy (capped at 128). Diagnosis (per expert, per layer): share of the update's squared norm that falls inside
J-space, divided by what a random subspace of the same size would get (r/d). Above 1 = the patch concentrates on the
workspace; below 1 = it mostly writes elsewhere.
  writers into the residual stream at layer l: self_attn.o_proj, mlp.down_proj, per_layer_projection (column space)
  readers from the residual stream into layer l: q/k/v_proj, gate/up_proj, per_layer_input_gate (row space, J-space of
  layer l-1)
"""
import json
import sys
from pathlib import Path

import torch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "merge"))
from merge import Remote  # noqa: E402

EXPERTS = {"ringg": "RinggAI/ringg-router-e2b",
           "yemeni": "yemeni-ai-lab/gemma-4-e2b-yemeni-arabic-assistant-merged",
           "xlam": "ermiaazarkhalili/Gemma4-E2B-Function-Calling-xLAM-Unsloth"}
WRITERS = ("self_attn.o_proj", "mlp.down_proj", "per_layer_projection")
READERS = ("self_attn.q_proj", "self_attn.k_proj", "self_attn.v_proj", "mlp.gate_proj", "mlp.up_proj",
           "per_layer_input_gate")
ENERGY, MAX_RANK = 0.90, 128


def basis(shards):
    total, count, tokens = None, 0, None
    for s in shards:
        d = torch.load(s)
        total = d["sum"] if total is None else total + d["sum"]
        count += d["count"]
        tokens = d["tokens"]
    u = total / max(1, count)  # [layers, tokens, d]
    bases, info = [], []
    for li in range(u.shape[0]):
        m = u[li]
        _, s, vh = torch.linalg.svd(m, full_matrices=False)
        e = (s ** 2).cumsum(0) / (s ** 2).sum()
        r = min(MAX_RANK, int((e < ENERGY).sum()) + 1)
        bases.append(vh[:r].T.contiguous())  # [d, r]
        top = [tokens[i] for i in m.norm(dim=1).topk(5).indices.tolist()]
        info.append({"layer": li, "rank90": r, "mean_norm": float(m.norm(dim=1).mean()), "strongest": top})
    return bases, info, count


def share(delta, b, side):
    if side == "write":
        inside = (b.T @ delta).pow(2).sum()
    else:
        inside = (delta @ b).pow(2).sum()
    tot = delta.pow(2).sum()
    return float(inside / tot) if tot > 0 else 0.0


def main(basis_out, report_out, *shards):
    bases, info, count = basis(shards)
    d = bases[0].shape[0]
    torch.save({"bases": bases, "info": info}, basis_out)
    report = {"prompts": count, "layers": info, "experts": {}}
    base = Remote("google/gemma-4-E2B-it")
    for label, repo in EXPERTS.items():
        ex = Remote(repo)
        rows = []
        for li in range(len(bases) + 1):
            for kind, names in (("write", WRITERS), ("read", READERS)):
                bi = li if kind == "write" else li - 1
                if bi < 0 or bi >= len(bases):
                    continue
                b = bases[bi]
                for n in names:
                    name = f"model.language_model.layers.{li}.{n}.weight"
                    info_b, info_e = base.info(name), ex.info(name)
                    if info_b is None or info_e is None or info_b["shape"] != info_e["shape"]:
                        continue
                    rows_n = info_b["shape"][0]
                    delta = ex.rows(name, 0, rows_n) - base.rows(name, 0, rows_n)
                    sh = share(delta, b, kind)
                    rows.append({"layer": li, "tensor": n, "side": kind, "share": sh,
                                 "focus": sh / (b.shape[1] / d), "norm": float(delta.norm())})
        tot_in = sum(r["share"] * r["norm"] ** 2 for r in rows)
        tot = sum(r["norm"] ** 2 for r in rows)
        rand = sum((bases[min(len(bases) - 1, max(0, r["layer"] - (r["side"] == "read")))].shape[1] / d) * r["norm"] ** 2
                   for r in rows)
        report["experts"][label] = {"share_in_jspace": tot_in / tot, "random_expectation": rand / tot,
                                    "focus": tot_in / rand, "by_tensor": rows}
        print(label, "share in J-space", round(tot_in / tot, 4), "random", round(rand / tot, 4), "focus",
              round(tot_in / rand, 2), flush=True)
    json.dump(report, open(report_out, "w"), indent=1)


if __name__ == "__main__":
    main(*sys.argv[1:])
