"""Map Gemma 4 E2B's J-space (the "global workspace" of Anthropic's July 2026 workspace paper), written from the
method as the paper describes it, not from anyone's code.

For a concept token t, its J-lens vector at layer l is  u_l(t) = J_l^T w_t,  where w_t is the token's unembedding row
and J_l = E[ d h_final / d h_l ] is the average Jacobian from the layer-l residual stream to the final residual stream,
summed over the current and every later position and averaged over source positions and prompts. u_l(t) is the
residual-stream direction at layer l that most pushes the model towards eventually saying t. J-space at layer l is
spanned by these vectors.

    python fit.py <model_dir> <shard> <n_shards> <out.pt> <budget_minutes>

Each shard fits on its own slice of prompts; shards are summed later (sum and count are stored).
"""
import json
import sys
import time

import torch
from transformers import AutoModelForImageTextToText, AutoTokenizer

SEQ = 64
SKIP = 16
BATCH = 16  # probe tokens per backward pass

CONCEPTS = (
    "call phone message text time morning night tomorrow alarm remind mother brother sister friend name number "
    "money bank password delete open app camera map video music picture screen light weather food water home work "
    "school doctor hospital medicine car road city country Sudan Khartoum Arabic English language translate yes no "
    "maybe question answer help problem happy sad tired angry love family child father house door key book letter "
    "write read count math number plus minus price buy sell market day week month year hour minute second late early "
    "sleep wake eat drink pray mosque God peace thanks sorry please good bad big small hot cold fast slow new old"
).split() + "اتصل رسالة وقت صباح ليل بكرة منبه ذكرني أم أخ أخت صديق اسم رقم فلوس بنك كلمة افتح تطبيق صورة شاشة نور طقس أكل موية بيت شغل مدرسة دكتور مستشفى دواء عربية طريق مدينة السودان الخرطوم عربي ترجمة نعم لا سؤال جواب مساعدة مشكلة فرحان زعلان تعبان حب أسرة ولد أب باب مفتاح كتاب يوم أسبوع شهر سنة ساعة دقيقة نوم أكل صلاة الله شكرا".split()


def concept_ids(tok):
    ids = []
    for w in CONCEPTS:
        for form in (" " + w, w):
            t = tok(form, add_special_tokens=False)["input_ids"]
            if t:
                ids.append(t[0])
                break
    return sorted(set(ids))


def prompts(tok, shard, n_shards):
    import pandas as pd
    df = pd.read_parquet("aya.parquet", columns=["inputs", "targets", "language_code"])
    df = df[df["language_code"].isin(["eng", "arb", "ars", "arz", "apc", "acq", "ary"])]
    texts = (df["inputs"] + "\n" + df["targets"]).tolist()
    out = []
    for i, t in enumerate(texts):
        if i % n_shards != shard:
            continue
        ids = tok(t, add_special_tokens=True)["input_ids"]
        if len(ids) >= SEQ:
            out.append(ids[:SEQ])
        if len(out) >= 400:
            break
    return out


def main(model_dir, shard, n_shards, out_path, budget_min):
    t0 = time.time()
    torch.set_num_threads(4)
    shard, n_shards = int(shard), int(n_shards)
    tok = AutoTokenizer.from_pretrained(model_dir)
    full = AutoModelForImageTextToText.from_pretrained(model_dir, torch_dtype=torch.bfloat16, low_cpu_mem_usage=True)
    full.eval()
    for p in full.parameters():
        p.requires_grad_(False)
    text = full.model.language_model
    unembed = full.get_output_embeddings().weight  # [vocab, d]
    layers = text.layers[: text.config.num_hidden_layers]
    n_layers = len(layers)
    acts = {}

    def keep(i):
        def hook(_m, _inp, out):
            h = out[0] if isinstance(out, tuple) else out
            acts[i] = h
        return hook

    def grad_embed(_m, _inp, out):
        return out.detach().requires_grad_(True)

    hooks = [layer.register_forward_hook(keep(i)) for i, layer in enumerate(layers)]
    hooks.append(text.embed_tokens.register_forward_hook(grad_embed))
    cids = concept_ids(tok)
    d = text.config.hidden_size
    total = torch.zeros(n_layers - 1, len(cids), d, dtype=torch.float32)
    count = 0
    data = prompts(tok, shard, n_shards)
    print(f"shard {shard}: {len(data)} prompts, {len(cids)} concept tokens, {n_layers} layers, d={d}", flush=True)
    valid = torch.zeros(SEQ, dtype=torch.bool)
    valid[SKIP:SEQ - 1] = True
    for ids in data:
        acc = torch.zeros_like(total)
        for b in range(0, len(cids), BATCH):
            chunk = cids[b:b + BATCH]
            x = torch.tensor([ids] * len(chunk))
            acts.clear()
            text(input_ids=x, use_cache=False)
            final = acts[n_layers - 1]
            cot = torch.zeros_like(final)
            w = unembed[chunk].to(final.dtype)  # [k, d]
            cot[:, valid, :] = w[:, None, :]
            srcs = [acts[i] for i in range(n_layers - 1)]
            for s in srcs:
                s.retain_grad()
            grads = torch.autograd.grad(final, srcs, grad_outputs=cot, allow_unused=True)
            for li, g in enumerate(grads):
                if g is not None:
                    acc[li, b:b + len(chunk)] = g[:, valid, :].float().mean(1)
        total += acc
        count += 1
        el = (time.time() - t0) / 60
        print(f"prompt {count}: {el:.1f} min", flush=True)
        if el > budget_min:
            break
    torch.save({"sum": total, "count": count, "token_ids": cids, "tokens": [tok.decode([c]) for c in cids]}, out_path)
    print(json.dumps({"shard": shard, "prompts": count, "minutes": round((time.time() - t0) / 60, 1)}))


if __name__ == "__main__":
    main(*sys.argv[1:5], float(sys.argv[5]))
