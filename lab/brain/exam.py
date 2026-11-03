"""Kawkab exam + Sudanese understanding for one GGUF served by llama-server. Prints and writes a JSON score."""
import json, re, sys, time, concurrent.futures as cf, urllib.request

URL = "http://127.0.0.1:8080/v1/chat/completions"
SYSTEM = """You are Kawkab, a phone assistant. The owner speaks English, Arabic (Sudanese or other dialects) or a mix.
Reply with ONLY one JSON object, no other text. Schema:
{"action": one of ["call","sms","alarm","reminder","open_app","flashlight","bluetooth","show_screenshot","read_screenshot","chat","ask","refuse"],
 "contact": string or null, "text": string or null, "time": "HH:MM" or null, "when": string or null,
 "app": string or null, "state": "on"/"off" or null, "reply": string or null}
Contacts: Mum, Ahmed, Omar, Sara (sister), Ali (brother), Abdalla Mubadala, Wad Abdalla.
Rules: use "ask" with a short question in "reply" when the contact, time or message is missing or ambiguous (there are two Abdallas).
Use "refuse" for money transfers, banking, passwords, or deleting data. For conversation or questions use "chat" and put a short answer in "reply"."""

ALIASES = {
    "Abdalla Mubadala": [["abdall", "abdull", "عبدالله", "عبد الله"], ["mubadala", "مبادلة", "مباداله"]],
    "Mum": [["mum", "mom", "mother", "أمي", "امي", "ماما"]],
    "Ahmed": [["ahmed", "ahmad", "أحمد", "احمد"]],
    "Omar": [["omar", "عمر"]],
    "sister": [["sara", "sister", "أخت", "اخت"]],
    "brother": [["ali", "brother", "أخو", "اخو", "أخي", "علي"]],
    "Abdalla": [["abdall", "abdull", "عبدالله", "عبد الله"]],
}
APPS = {"WhatsApp": ["whatsapp", "واتساب", "واتس"], "YouTube": ["youtube", "يوتيوب", "اليوتيوب"], "Google Maps": ["map", "خرائط"]}
AR = str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")


def ask(messages, max_tokens=450):
    body = {"messages": messages, "max_tokens": max_tokens, "temperature": 0.2, "top_p": 0.9,
            "chat_template_kwargs": {"enable_thinking": False}}
    req = urllib.request.Request(URL, json.dumps(body).encode(), {"Content-Type": "application/json"})
    t = time.time()
    with urllib.request.urlopen(req, timeout=900) as r:
        d = json.load(r)
    return (d["choices"][0]["message"].get("content") or ""), time.time() - t


def parse(text):
    m = re.search(r"\{.*\}", text, re.S)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except Exception:
        return None


def low(v):
    return str(v or "").lower()


def contact_ok(got, want):
    g = low(got)
    return all(any(a in g for a in group) for group in ALIASES[want])


def time_ok(got, want):
    g = low(got).translate(AR)
    nums = re.findall(r"\d+", g)
    if not nums:
        return False
    h = int(nums[0]); mnt = int(nums[1]) if len(nums) > 1 else 0
    wh, wm = map(int, want.split(":"))
    return (h % 12, mnt) == (wh % 12, wm)


def grade(item, out):
    e = item["expect"]; a = e["action"]
    if out is None:
        return False
    act = low(out.get("action"))
    if a == "chat_or_lookup":
        return act in ("chat", "ask")
    if act != a:
        return False
    if "contact" in e and not contact_ok(out.get("contact"), e["contact"]):
        return False
    if "time" in e and not time_ok(out.get("time") or out.get("when"), e["time"]):
        return False
    if "app" in e and not any(x in low(out.get("app")) for x in APPS[e["app"]]):
        return False
    if "state" in e and low(out.get("state")) != e["state"]:
        return False
    if a in ("sms", "reminder") and not (out.get("text") or out.get("reply")):
        return False
    if a == "chat" and not out.get("reply"):
        return False
    return True


def chrf(hyp, ref, n=6):
    def grams(s, k):
        s = s.replace(" ", "")
        return [s[i:i + k] for i in range(len(s) - k + 1)]
    ps, rs = [], []
    for k in range(1, n + 1):
        h, r = grams(hyp, k), grams(ref, k)
        if not h or not r:
            continue
        rc = {}
        for x in r:
            rc[x] = rc.get(x, 0) + 1
        match = 0
        for x in h:
            if rc.get(x, 0):
                match += 1; rc[x] -= 1
        ps.append(match / len(h)); rs.append(match / len(r))
    if not ps:
        return 0.0
    p, r = sum(ps) / len(ps), sum(rs) / len(rs)
    return 0.0 if p + r == 0 else 100 * 5 * p * r / (4 * p + r)


def main(label, exam_path, flores_path, out_path):
    exam = json.load(open(exam_path))

    def one(item):
        text, sec = ask([{"role": "system", "content": SYSTEM}, {"role": "user", "content": item["prompt"]}])
        out = parse(text)
        return {"prompt": item["prompt"], "ok": grade(item, out), "raw": text.strip()[:300], "seconds": round(sec, 2)}

    with cf.ThreadPoolExecutor(2) as ex:
        rows = list(ex.map(one, exam))
    flores = [json.loads(l)["translation"] for l in open(flores_path)][:60]

    def tr(p):
        text, _ = ask([{"role": "user", "content": "Rewrite this Sudanese Arabic sentence in Modern Standard Arabic. Output only the sentence.\n\n" + p["Sud"]}], 300)
        return chrf(text.strip(), p["Arb"])

    with cf.ThreadPoolExecutor(2) as ex:
        scores = list(ex.map(tr, flores))
    res = {"label": label, "exam_score": sum(r["ok"] for r in rows) / len(rows),
           "exam_median_seconds": sorted(r["seconds"] for r in rows)[len(rows) // 2],
           "sudanese_chrf": sum(scores) / len(scores), "exam": rows}
    json.dump(res, open(out_path, "w"), ensure_ascii=False, indent=1)
    print(label, "exam", round(res["exam_score"], 3), "median_s", res["exam_median_seconds"], "sudanese_chrF", round(res["sudanese_chrf"], 1))
    for r in rows:
        if not r["ok"]:
            print("  MISS:", r["prompt"], "=>", r["raw"].replace("\n", " ")[:200])


if __name__ == "__main__":
    main(*sys.argv[1:5])
