"""Weight-space edit for one habit: ask when a request is unclear.

1. Run Gemma 4 E2B (transformers, CPU) on contrast prompts that are NOT in the exam:
   unclear requests (should ask) vs clear twins (should act). Record each decoder layer's
   residual at the last prompt token and each down_proj input.
2. Pick the layer whose ask-vs-act separation is largest; u = unit residual difference,
   m = mean down_proj input on unclear prompts.
3. Write W' = W + alpha * d * u (m^T / |m|^2) into that layer's ffn_down tensor inside copies of the
   BF16 GGUF (after checking the GGUF tensor matches the transformers weight), one copy per alpha.
"""
import json, re, shutil, sys
import numpy as np
import torch

HF_DIR, GGUF_IN, OUT_PREFIX = sys.argv[1], sys.argv[2], sys.argv[3]
ALPHAS = [float(a) for a in sys.argv[4].split(",")]
SYSTEM = open("lab/brain/system_prompt.txt").read()
EXAM = {e["prompt"] for e in json.load(open("lab/brain/kawkab_exam.json"))}

UNCLEAR = ["اتصل عليه", "كلمه", "ring her", "call him", "رسل ليها", "message them", "ابعت رسالة", "send a text",
           "ذكرني", "remind me", "حط لي تذكير", "set a reminder", "صحيني", "wake me up", "set an alarm", "حط منبه بكرة",
           "اتصل على الزول داك", "call that guy", "رسل للجماعة", "text her back", "كلم صاحبي", "ring my friend",
           "ذكرني بالموضوع", "remind me about it later", "حط منبه بدري شوية", "alarm please", "اتصل بأي واحد",
           "send it to him", "قول ليهو", "tell her", "اتصل على عبدالله بكرة", "message Abdalla", "كلم عبد الله هسي",
           "ring Abdalla now", "ذكرني بعدين", "remind me sometime", "صحيني بدري بكرة", "wake me early", "رسل ليهو الكلام داك",
           "send him that"]
CLEAR = ["اتصل على عمر", "كلم أحمد", "ring Sara", "call Ali", "رسل لسارة قول ليها جاية", "message Omar: on my way",
         "ابعت رسالة لأحمد إنو وصلت", "send a text to Mum saying hi", "ذكرني الساعة تلاتة أشرب موية", "remind me at 4 to stretch",
         "حط لي تذكير بكرة الساعة عشرة أكلم البنك", "set a reminder at noon to eat", "صحيني الساعة ستة", "wake me up at 6:30",
         "set an alarm for 8", "حط منبه بكرة الساعة سبعة", "اتصل على علي", "call Omar", "رسل لعمر قول ليهو تمام",
         "text Sara back: yes", "كلم أمي", "ring Ahmed", "ذكرني الساعة خمسة بالموضوع", "remind me at 9 pm about the bill",
         "حط منبه خمسة ونص", "alarm at 7", "اتصل على أحمد", "send Omar the address", "قول لعلي في رسالة جاي",
         "tell Sara in a message I'm home", "اتصل على عبدالله مبادلة بكرة", "message Abdalla Mubadala: ok", "كلم ود عبدالله هسي",
         "ring Wad Abdalla now", "ذكرني بعد ساعة أطلع", "remind me in 10 minutes", "صحيني بكرة الساعة ستة", "wake me at 5:45",
         "رسل لأحمد الكلام داك: الاجتماع الساعة تسعة", "send Ali: see you"]
assert len(UNCLEAR) == len(CLEAR) and not (set(UNCLEAR) | set(CLEAR)) & EXAM

from transformers import AutoProcessor, AutoModelForImageTextToText
proc = AutoProcessor.from_pretrained(HF_DIR)
tok = proc.tokenizer if hasattr(proc, "tokenizer") else proc
model = AutoModelForImageTextToText.from_pretrained(HF_DIR, torch_dtype=torch.bfloat16)
model.eval()
layers = {int(m.group(1)): mod for name, mod in model.named_modules()
          if (m := re.fullmatch(r".*language_model\.layers\.(\d+)", name))}
downs = {int(m.group(1)): mod for name, mod in model.named_modules()
         if (m := re.fullmatch(r".*language_model\.layers\.(\d+)\.mlp\.down_proj", name))}
assert layers and downs, "decoder layers not found"
resid, dins = {}, {}
hooks = [layers[i].register_forward_hook(lambda mod, a, out, i=i: resid.__setitem__(i, (out[0] if isinstance(out, tuple) else out)[0, -1].float().clone())) for i in layers]
hooks += [downs[i].register_forward_hook(lambda mod, a, out, i=i: dins.__setitem__(i, a[0][0, -1].float().clone())) for i in downs]


def run(text):
    msgs = [{"role": "system", "content": SYSTEM}, {"role": "user", "content": text}]
    ids = tok.apply_chat_template(msgs, add_generation_prompt=True, return_tensors="pt", enable_thinking=False)
    if isinstance(ids, dict) or hasattr(ids, "input_ids"):
        ids = ids["input_ids"]
    resid.clear(); dins.clear()
    with torch.no_grad():
        model(input_ids=ids)
    return {i: resid[i] for i in resid}, {i: dins[i] for i in dins}


A = [run(t) for t in UNCLEAR]
B = [run(t) for t in CLEAR]
for h in hooks:
    h.remove()
best, report = None, []
n = len(layers)
for i in sorted(layers):
    if i < n * 0.25 or i > n * 0.85 or i not in downs:
        continue
    a = torch.stack([x[0][i] for x in A]); b = torch.stack([x[0][i] for x in B])
    diff = a.mean(0) - b.mean(0)
    u = diff / diff.norm()
    pa, pb = a @ u, b @ u
    sep = float((pa.mean() - pb.mean()) / torch.sqrt((pa.var() + pb.var()) / 2 + 1e-6))
    report.append((i, round(sep, 2)))
    if best is None or sep > best[1]:
        best = (i, sep, u, float(diff.norm()))
L, sep, u, d = best
m = torch.stack([x[1][L] for x in A]).mean(0)
print("layer separation:", report)
print(f"chosen layer {L} separation {sep:.2f} |diff| {d:.3f} |m| {float(m.norm()):.3f}")
W = downs[L].weight.detach().float().numpy()
delta_unit = (d * np.outer(u.numpy(), m.numpy() / float(m.norm() ** 2))).astype(np.float32)
del model
json.dump({"layer": L, "separation": sep, "layers": report}, open(OUT_PREFIX + "-edit.json", "w"))

import gguf


def bf16_to_f32(bits):
    return (bits.astype(np.uint32) << 16).view(np.float32)


def f32_to_bf16(x):
    b = x.astype(np.float32).view(np.uint32)
    return ((b + 0x7FFF + ((b >> 16) & 1)) >> 16).astype(np.uint16)


for alpha in ALPHAS:
    out = f"{OUT_PREFIX}-a{alpha:g}.bf16.gguf"
    shutil.copyfile(GGUF_IN, out)
    r = gguf.GGUFReader(out, "r+")
    t = next(t for t in r.tensors if t.name == f"blk.{L}.ffn_down.weight")
    assert t.tensor_type == gguf.GGMLQuantizationType.BF16, t.tensor_type
    raw = np.asarray(t.data)
    bits = raw.view(np.uint16).reshape(W.shape) if raw.dtype != np.uint16 else raw.reshape(W.shape)
    base = bf16_to_f32(bits)
    err = float(np.abs(base - W).max())
    assert err < 1e-2, f"GGUF tensor does not match the transformers weight (max err {err})"
    bits[...] = f32_to_bf16(base + alpha * delta_unit)
    del r
    print(f"alpha {alpha}: wrote {out} (base match err {err:.2e}, relative change {alpha * np.linalg.norm(delta_unit) / np.linalg.norm(W):.4f})")
