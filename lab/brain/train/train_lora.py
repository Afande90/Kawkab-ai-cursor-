"""Train one Kawkab skill patch (LoRA) on Gemma 4 E2B, on a CPU, inside a time budget.

    python train_lora.py <base_dir> <data.jsonl> <skills,comma|all> <out_dir> <budget_minutes>

Loss is on the answer only. The short system prompt (system_prompt_short.txt) is used, because a model that has
learned the format does not need the long rules, and every token saved is speed on the phone.
"""
import json
import math
import random
import sys
import time
from pathlib import Path

import torch
from peft import LoraConfig, get_peft_model
from transformers import AutoModelForImageTextToText, AutoTokenizer

HERE = Path(__file__).resolve().parent.parent
TARGETS = r".*language_model.*\.(q_proj|k_proj|v_proj|o_proj|gate_proj|up_proj|down_proj)"


def encode(tok, system, prompt, answer):
    msgs = [{"role": "system", "content": system}, {"role": "user", "content": prompt}]
    head = tok.apply_chat_template(msgs, tokenize=False, add_generation_prompt=True, enable_thinking=False)
    full = tok.apply_chat_template(msgs + [{"role": "assistant", "content": answer}], tokenize=False,
                                   enable_thinking=False)
    if not full.startswith(head):  # some templates rewrite the last turn; fall back to head + answer
        full = head + answer + tok.eos_token
    h = tok(head, add_special_tokens=False)["input_ids"]
    f = tok(full, add_special_tokens=False)["input_ids"]
    labels = [-100] * len(h) + f[len(h):]
    return torch.tensor([f]), torch.tensor([labels])


def main(base, data, skills, out, budget_min):
    t_start = time.time()
    torch.set_num_threads(4)
    torch.manual_seed(7)
    system = (HERE / "system_prompt_short.txt").read_text()
    rows = [json.loads(l) for l in open(data)]
    if skills != "all":
        keep = set(skills.split(","))
        rows = [r for r in rows if r["skill"] in keep or r["skill"] == "chat"]
    random.Random(7).shuffle(rows)
    tok = AutoTokenizer.from_pretrained(base)
    model = AutoModelForImageTextToText.from_pretrained(base, torch_dtype=torch.bfloat16, low_cpu_mem_usage=True)
    model.config.use_cache = False
    cfg = LoraConfig(r=16, lora_alpha=32, lora_dropout=0.05, target_modules=TARGETS, task_type="CAUSAL_LM")
    model = get_peft_model(model, cfg)
    for n, p in model.named_parameters():
        if p.requires_grad:
            p.data = p.data.float()
    model.print_trainable_parameters()
    model.gradient_checkpointing_enable()
    model.enable_input_require_grads()
    params = [p for p in model.parameters() if p.requires_grad]
    opt = torch.optim.AdamW(params, lr=2e-4, weight_decay=0.0)
    accum, step, seen, losses = 8, 0, 0, []
    budget = budget_min * 60
    epochs = 2
    total = math.ceil(len(rows) * epochs / accum)
    print(f"{len(rows)} rows, up to {total} optimizer steps, budget {budget_min} min", flush=True)
    model.train()
    stop = False
    for ep in range(epochs):
        for i, r in enumerate(rows):
            answer = json.dumps(r["answer"], ensure_ascii=False)
            ids, labels = encode(tok, system, r["prompt"], answer)
            with torch.autocast("cpu", dtype=torch.bfloat16):
                loss = model(input_ids=ids, labels=labels).loss / accum
            loss.backward()
            losses.append(loss.item() * accum)
            seen += 1
            if seen % accum == 0:
                lr = 2e-4 * min(1.0, (step + 1) / 10) * max(0.1, 1 - step / total)
                for g in opt.param_groups:
                    g["lr"] = lr
                torch.nn.utils.clip_grad_norm_(params, 1.0)
                opt.step()
                opt.zero_grad(set_to_none=True)
                step += 1
                el = time.time() - t_start
                if step % 5 == 0:
                    print(f"step {step}/{total} ep {ep} loss {sum(losses[-40:]) / len(losses[-40:]):.3f} "
                          f"{el / 60:.1f} min ({el / seen:.1f} s/row)", flush=True)
                if el > budget:
                    stop = True
                    break
        if stop:
            break
    model.save_pretrained(out)
    json.dump({"skills": skills, "rows": len(rows), "rows_seen": seen, "steps": step,
               "minutes": round((time.time() - t_start) / 60, 1),
               "final_loss": sum(losses[-40:]) / max(1, len(losses[-40:]))}, open(f"{out}/train.json", "w"))
    print("saved", out, flush=True)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], float(sys.argv[5]))
