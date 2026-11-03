"""A/B a base and an edited GGUF on llama-server: practice GSM8K accuracy, answer rate, speed, plus Arabic and action samples."""
import json, random, re, sys, time, concurrent.futures as cf, urllib.request

URL = "http://127.0.0.1:8080/v1/chat/completions"

def ask(messages, think, max_tokens):
    body = {"messages": messages, "max_tokens": max_tokens, "temperature": 0.6, "top_p": 0.95, "top_k": 20,
            "presence_penalty": 1.5, "chat_template_kwargs": {"enable_thinking": think}}
    req = urllib.request.Request(URL, json.dumps(body).encode(), {"Content-Type": "application/json"})
    t = time.time()
    with urllib.request.urlopen(req, timeout=1800) as r:
        d = json.load(r)
    m = d["choices"][0]["message"]
    text = (m.get("content") or "")
    return text, d["choices"][0].get("finish_reason"), d.get("usage", {}).get("completion_tokens", 0), time.time() - t

def last_number(text):
    b = re.findall(r"\\boxed\{([^{}]*)\}", text)
    s = b[-1] if b else text
    n = re.findall(r"-?\d[\d,]*\.?\d*", s)
    return n[-1].replace(",", "").rstrip(".") if n else None

def main(label, practice, out):
    rows = [json.loads(l) for l in open(practice)]
    random.Random(7).shuffle(rows)
    probs = rows[:40]
    res = {"label": label}
    for think, cap in ((False, 768), (True, 3072)):
        def one(p):
            q = p["problem"] + "\nPlease reason step by step, and put your final answer within \\boxed{}."
            text, fin, toks, sec = ask([{"role": "user", "content": q}], think, cap)
            got = last_number(text)
            ok = got is not None and abs(float(got) - float(p["answer"].replace(",", ""))) < 1e-6 if got else False
            return ok, fin == "length", toks, sec
        t0 = time.time()
        with cf.ThreadPoolExecutor(4) as ex:
            r = list(ex.map(one, probs))
        wall = time.time() - t0
        key = "think" if think else "nothink"
        res[key] = {"accuracy": sum(x[0] for x in r) / len(r), "hit_token_limit": sum(x[1] for x in r) / len(r),
                    "avg_tokens": sum(x[2] for x in r) / len(r), "tokens_per_s": sum(x[2] for x in r) / wall}
        print(label, key, res[key], flush=True)
    samples = [
        "ذكرني بكرة الصباح أكلم أحمد",
        "يا كوكب، كدي قول لي الطقس بكرة في أبوظبي حيكون كيف؟",
        "Kawkab, text Omar that I'm running 10 minutes late.",
        'You control a phone. Reply ONLY with JSON {"action": ..., "contact": ..., "text": ...}. Owner said: "call Abdalla Mubadala"',
        "Explain in two sentences why the sky is blue.",
    ]
    res["samples"] = []
    for s in samples:
        text, fin, toks, sec = ask([{"role": "user", "content": s}], False, 256)
        res["samples"].append({"prompt": s, "reply": text.strip(), "seconds": round(sec, 1)})
        print(f"--- {label} | {s}\n{text.strip()}\n", flush=True)
    json.dump(res, open(out, "w"), ensure_ascii=False, indent=1)

if __name__ == "__main__":
    main(*sys.argv[1:4])
