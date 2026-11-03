"""Add chat rows whose replies are written by the base model itself (served by llama-server on :8080), so training
on Kawkab's commands does not make it forget how to talk.

    python build_chat.py <commands.jsonl> <aya_train.parquet> <out.jsonl>
"""
import json
import random
import sys
import urllib.request
import concurrent.futures as cf

import pandas as pd

URL = "http://127.0.0.1:8080/v1/chat/completions"
ARABIC = {"arb", "ars", "arz", "apc", "acq", "ary"}


def reply(prompt):
    body = {"messages": [{"role": "system", "content": "Answer briefly, in the language the user used."},
                         {"role": "user", "content": prompt}],
            "max_tokens": 160, "temperature": 0.3, "chat_template_kwargs": {"enable_thinking": False}}
    req = urllib.request.Request(URL, json.dumps(body).encode(), {"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=600) as r:
        return (json.load(r)["choices"][0]["message"].get("content") or "").strip()


def main(commands, aya, out):
    rows = [json.loads(l) for l in open(commands)]
    df = pd.read_parquet(aya, columns=["inputs", "language_code"])
    df = df[df["inputs"].str.len().between(8, 240)]
    rng = random.Random(3)
    ar = df[df["language_code"].isin(ARABIC)]["inputs"].tolist()
    en = df[df["language_code"] == "eng"]["inputs"].tolist()
    prompts = rng.sample(ar, min(170, len(ar))) + rng.sample(en, min(110, len(en)))
    chat = [r for r in rows if r["skill"] == "chat"]
    rows = [r for r in rows if r["skill"] != "chat"]
    asks = [r["prompt"] for r in chat] + prompts
    with cf.ThreadPoolExecutor(2) as ex:
        answers = list(ex.map(reply, asks))
    for p, a in zip(asks, answers):
        if a:
            rows.append({"skill": "chat", "prompt": p, "answer": {"action": "chat", "reply": a[:600]}})
    rng.shuffle(rows)
    with open(out, "w") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(len(rows), "rows,", sum(r["skill"] == "chat" for r in rows), "chat")


if __name__ == "__main__":
    main(*sys.argv[1:4])
