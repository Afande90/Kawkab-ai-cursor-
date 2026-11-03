"""Quick GSM8K check (thinking off) so a merge that wrecks reasoning is caught: 25 fixed problems, exact final number."""
import json, random, re, sys, concurrent.futures as cf, urllib.request

URL = "http://127.0.0.1:8080/v1/chat/completions"


def ask(q):
    body = {"messages": [{"role": "user", "content": q}], "max_tokens": 512, "temperature": 0.0,
            "chat_template_kwargs": {"enable_thinking": False}}
    req = urllib.request.Request(URL, json.dumps(body).encode(), {"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=900) as r:
        return json.load(r)["choices"][0]["message"].get("content") or ""


def last_number(text):
    b = re.findall(r"\\boxed\{([^{}]*)\}", text)
    n = re.findall(r"-?\d[\d,]*\.?\d*", b[-1] if b else text)
    return n[-1].replace(",", "").rstrip(".") if n else None


def main(practice, out):
    rows = [json.loads(l) for l in open(practice)]
    random.Random(11).shuffle(rows)
    probs = rows[:25]

    def one(p):
        got = last_number(ask(p["problem"] + "\nReason step by step, then put the final answer in \\boxed{}."))
        try:
            return got is not None and abs(float(got) - float(p["answer"].replace(",", ""))) < 1e-6
        except ValueError:
            return False

    with cf.ThreadPoolExecutor(2) as ex:
        ok = list(ex.map(one, probs))
    res = {"gsm8k_25": sum(ok) / len(ok)}
    json.dump(res, open(out, "w"))
    print(res)


if __name__ == "__main__":
    main(*sys.argv[1:3])
