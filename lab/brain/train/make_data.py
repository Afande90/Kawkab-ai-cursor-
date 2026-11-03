"""Kawkab's own training set: spoken commands in English, Sudanese, Egyptian, Gulf, Levantine Arabic, MSA, Arabizi and
mixed speech, each with the exact JSON answer. Built from templates, so every label is known, not guessed.

    python make_data.py out.jsonl            -> {"skill": ..., "prompt": ..., "answer": {...}} per line

Nothing here may copy or nearly copy an exam prompt: every line is checked against all four exam files and dropped
if it is too similar (the exams stay a fair judge). Chat rows only carry prompts; their replies are written later by
the base model itself (self-distillation), so training keeps the brain's own way of talking.
"""
import difflib
import json
import random
import sys
from pathlib import Path

RNG = random.Random(2026)
HERE = Path(__file__).resolve().parent.parent

# canonical contact -> ways the owner says it
PEOPLE = {
    "Mum": ["أمي", "امي", "ماما", "الوالدة", "الحاجة", "mum", "mom", "my mother", "mama"],
    "Ahmed": ["أحمد", "احمد", "Ahmed", "Ahmad", "ahmed"],
    "Omar": ["عمر", "Omar", "omar", "عمورة"],
    "Sara": ["سارة", "ساره", "أختي", "اختي", "Sara", "my sister", "sis"],
    "Ali": ["علي", "أخوي", "اخوي", "أخويا", "Ali", "my brother", "bro"],
    "Abdalla Mubadala": ["عبدالله بتاع مبادلة", "عبدالله مبادلة", "Abdalla Mubadala", "Abdalla from Mubadala",
                         "عبدالله حق مبادلة", "عبدالله الشغل"],
    "Wad Abdalla": ["ود عبدالله", "Wad Abdalla", "wad abdalla", "ولد عبدالله"],
}
CALL = {
    "sd": ["اتصل على {c}", "اتصل ب{c}", "كلم {c}", "ضرب لي {c}", "اضرب ل{c}", "دق لي على {c}", "أديني {c} في التلفون",
           "وصلني ب{c}", "داير أكلم {c}", "عايز أتصل على {c}", "كلم لي {c} هسع", "اتصل لي ب{c} يا زول"],
    "eg": ["كلملي {c}", "اتصل ب{c} بسرعة", "عايز أكلم {c}", "رن على {c}", "اطلبلي {c}"],
    "gulf": ["ابغى اكلم {c}", "اتصل على {c} الحين", "دق على {c}", "ابي اتصل ب{c}"],
    "lev": ["بدي احكي مع {c}", "اتصل في {c}", "دقلي ل{c}"],
    "msa": ["اتصل بـ{c} من فضلك", "أجرِ مكالمة مع {c}", "هاتف {c}"],
    "en": ["call {c}", "phone {c}", "ring {c}", "give {c} a ring", "dial {c} please", "get {c} on the phone",
           "can you call {c}", "connect me to {c}", "video call {c}", "Kawkab, call {c}"],
    "mix": ["call {c} بسرعة", "اتصل على {c} please", "Kawkab كلم {c}", "ring {c} يا كوكب", "kalim {c}",
            "etasel 3ala {c}", "call {c} هسع"],
}
BODIES = [
    ("أنا في الطريق", "I'm on my way"), ("أنا متأخر شوية", "I'm running a bit late"), ("جيب معاك عيش", "bring bread"),
    ("الاجتماع اتأجل", "the meeting is postponed"), ("أنا وصلت", "I've arrived"), ("اتصل بي لما تفضى", "call me when free"),
    ("مبروك", "congratulations"), ("أنا بخير ما تقلق", "I'm fine don't worry"), ("الباب مفتوح", "the door is open"),
    ("ما تنسى المفتاح", "don't forget the key"), ("حجزت التذكرة", "I booked the ticket"), ("بكلمك بعدين", "I'll call you later"),
]
SMS = {
    "sd": ["رسل ل{c} قول ليهو {b}", "ابعت ل{c} رسالة {b}", "أكتب ل{c} إنو {b}", "قول ل{c} في مسج {b}", "رسل ل{c}: {b}"],
    "eg": ["ابعت ل{c} مسدج إن {b}", "قول ل{c} في رسالة {b}"],
    "gulf": ["ارسل ل{c} {b}", "طرش ل{c} رسالة {b}"],
    "lev": ["بعتلي ل{c} رسالة إنو {b}"],
    "msa": ["أرسل رسالة إلى {c} نصها {b}"],
    "en": ["text {c} that {e}", "message {c}: {e}", "send {c} a text saying {e}", "tell {c} by text {e}",
           "let {c} know {e}", "sms {c} {e}"],
    "mix": ["send ل{c} message {e}", "ابعت text ل{c} قول {e}", "text {c} قول ليهو {b}", "رسل ل{c} {e}"],
}
HOURS_AR = {1: "واحدة", 2: "اتنين", 3: "تلاتة", 4: "أربعة", 5: "خمسة", 6: "ستة", 7: "سبعة", 8: "تمانية", 9: "تسعة",
            10: "عشرة", 11: "حداشر", 12: "اتناشر"}
HOURS_EN = {1: "one", 2: "two", 3: "three", 4: "four", 5: "five", 6: "six", 7: "seven", 8: "eight", 9: "nine",
            10: "ten", 11: "eleven", 12: "twelve"}


def say_time(rng):
    """(spoken time, 'H:MM' 24h)."""
    h = rng.randint(1, 12)
    m = rng.choice([0, 0, 0, 15, 30, 30, 45, 10, 20])
    style = rng.randrange(7)
    evening = rng.random() < 0.4 and h != 12
    hh = h + 12 if evening and h < 12 else (0 if (not evening and h == 12) else h)
    if style == 0:
        s = f"{h}:{m:02d} {'pm' if evening else 'am'}"
    elif style == 1:
        s = f"الساعة {h}:{m:02d} {'بالليل' if evening else 'الصباح'}"
    elif style == 2 and m in (0, 15, 30, 45):
        frac = {0: "", 15: " وربع", 30: " ونص", 45: " إلا ربع"}[m]
        said_h = h % 12 + 1 if m == 45 else h
        s = f"الساعة {HOURS_AR[said_h]}{frac} {'بالليل' if evening else 'الصباح'}"
    elif style == 3 and m in (0, 30):
        s = f"{'half past ' + HOURS_EN[h] if m == 30 else HOURS_EN[h] + ' o clock'} {'in the evening' if evening else 'in the morning'}"
    elif style == 4:
        s = f"{h}{':%02d' % m if m else ''}{'pm' if evening else 'am'}"
    elif style == 5:
        s = f"الساعة {h}:{m:02d} {'مساءً' if evening else 'صباحاً'}"
    else:
        s = f"{hh}:{m:02d}"
    return s, f"{hh}:{m:02d}"


ALARM = ["صحيني {t}", "قومني {t}", "حط لي منبه {t}", "اضبط المنبه على {t}", "عايز منبه {t}", "فزعني {t}",
         "wake me up at {t}", "set an alarm for {t}", "alarm at {t}", "set alarm {t} please", "حط لي alarm {t}",
         "ابي منبه {t}", "صحيني بكرة {t}", "sahini {t}"]
TASKS = [("أشرب الدوا", "take the medicine"), ("أكلم أحمد", "call Ahmed"), ("أدفع الإيجار", "pay the rent"),
         ("أشيل الغسيل", "take the laundry out"), ("أصلي العصر", "pray asr"), ("الاجتماع", "the meeting"),
         ("أجيب الأولاد", "pick up the kids"), ("أرد على الإيميل", "answer the email"), ("أشحن العربية", "fuel the car")]
REMIND = ["ذكرني {k} {t}", "فكرني {k} {t}", "ذكرني بكرة {t} {k}", "remind me to {e} at {t}", "reminder at {t}: {e}",
          "remind me {t} to {e}", "ذكرني ب{k} {t}", "don't let me forget to {e} at {t}"]
APPS = {"WhatsApp": ["واتساب", "الواتس", "WhatsApp", "whatsapp", "الواتساب"], "YouTube": ["يوتيوب", "اليوتيوب", "YouTube"],
        "Google Maps": ["الخرائط", "خرائط جوجل", "Google Maps", "maps"], "Camera": ["الكاميرا", "كاميرا", "camera"]}
OPEN = ["افتح {a}", "افتح لي {a}", "شغل {a}", "وديني على {a}", "ورني {a}", "open {a}", "launch {a}", "start {a}",
        "Kawkab open {a}", "افتح الـ {a} بسرعة", "fta7 {a}"]
TOGGLE = {
    ("flashlight", "on"): ["شغل الكشاف", "ولع الفلاش", "ولّع لي النور بتاع التلفون", "turn on the flashlight", "torch on",
                           "شغل الفلاش يا كوكب", "افتح الكشاف"],
    ("flashlight", "off"): ["طفي الكشاف", "اقفل الفلاش", "turn off the torch", "flashlight off", "طفي النور بتاع التلفون"],
    ("bluetooth", "on"): ["شغل البلوتوث", "فتح البلوتوث", "turn bluetooth on", "enable bluetooth", "ولع البلوتوث"],
    ("bluetooth", "off"): ["اقفل البلوتوث", "طفي البلوتوث", "turn off bluetooth", "bluetooth off", "سكر البلوتوث"],
}
SHOW = ["وريني آخر سكرين شوت", "افتح آخر لقطة شاشة", "show me my last screenshot", "open the screenshot I just took",
        "كدي وريني الصورة اللي صورتها للشاشة", "اعرض آخر سكرين", "pull up the last screenshot"]
READ = ["السكرين ده مكتوب فيه شنو؟", "الفاتورة دي كم؟", "what does this screenshot say?", "read the text in this picture",
        "التاريخ في الصورة دي متين؟", "how much is the total on this bill?", "الرقم اللي في السكرين ده شنو؟"]
ASK = [("كلم عبدالله", "Which Abdalla — Abdalla Mubadala or Wad Abdalla?"),
       ("call Abdalla", "Which Abdalla — Abdalla Mubadala or Wad Abdalla?"),
       ("ابعت لعبدالله إني جاي", "Which Abdalla — Abdalla Mubadala or Wad Abdalla?"),
       ("اتصل بيها", "Who should I call?"), ("call her back", "Who should I call?"), ("كلمو", "Who should I call?"),
       ("ابعت رسالة", "Who should I send it to, and what should it say?"),
       ("text Omar", "What should the message say?"), ("رسل لأحمد", "What should the message say?"),
       ("صحيني", "What time should I wake you?"), ("set an alarm", "What time?"), ("حط لي منبه", "What time?"),
       ("ذكرني بعدين", "What should I remind you about, and when?"), ("remind me", "Remind you of what, and when?"),
       ("افتح التطبيق", "Which app?"), ("open it", "Which app should I open?"), ("اتصل", "Who should I call?")]
REFUSE = ["حول {n} درهم ل{c}", "transfer {n} dirhams to {c}", "send {n} dollars to {c}", "ادفع الفاتورة من البطاقة",
          "pay the rent from my account", "قول لي الباسورد بتاع الإيميل", "what's my bank PIN", "read me the OTP code",
          "امسح كل الرسائل", "delete all my contacts", "wipe everything on the phone", "ابعت كلمة السر لعمر"]
REFUSE_REPLY = "I can't do that for you. Please do it yourself."
CHAT_PROMPTS = ["إنت منو يا كوكب؟", "قول لي نكتة سودانية", "أنا زهجان شوية", "what's a quick dinner idea?",
                "اشرح لي يعني شنو ذكاء اصطناعي", "how do I make good karkade?", "أديني نصيحة للنوم",
                "Omar called me yesterday, he's so funny", "should I call Ahmed or wait till tomorrow?",
                "تفتكر أكلم أمي هسع ولا بعدين؟", "what's 15 percent of 240?", "translate good morning to Arabic",
                "I had a long day at work", "ليه السماء زرقاء؟", "give me a name for a cat",
                "أحمد زعلان مني، أعمل شنو؟", "what does inshallah mean?", "كم يوم في السنة الكبيسة؟"]


def pick(d):
    k = RNG.choice(list(d))
    return k, RNG.choice(d[k])


def rows():
    out = []
    for _ in range(700):
        who, said = pick(PEOPLE)
        if who == "Wad Abdalla" and RNG.random() < 0.5:
            continue
        _, tpl = pick(CALL)
        out.append(("call", tpl.format(c=said), {"action": "call", "contact": who}))
    for _ in range(450):
        who, said = pick(PEOPLE)
        _, tpl = pick(SMS)
        b, e = RNG.choice(BODIES)
        text = e if "{e}" in tpl and "{b}" not in tpl else b
        out.append(("sms", tpl.format(c=said, b=b, e=e), {"action": "sms", "contact": who, "text": text}))
    for _ in range(300):
        t, hm = say_time(RNG)
        out.append(("alarm", RNG.choice(ALARM).format(t=t), {"action": "alarm", "time": hm}))
    for _ in range(250):
        t, hm = say_time(RNG)
        k, e = RNG.choice(TASKS)
        tpl = RNG.choice(REMIND)
        out.append(("reminder", tpl.format(t=t, k=k, e=e), {"action": "reminder", "time": hm, "text": e}))
    for _ in range(220):
        app, said = pick(APPS)
        out.append(("open_app", RNG.choice(OPEN).format(a=said), {"action": "open_app", "app": app}))
    for (kind, state), says in TOGGLE.items():
        for s in says:
            out.append((kind, s, {"action": kind, "state": state}))
            out.append((kind, "يا كوكب " + s, {"action": kind, "state": state}))
    for s in SHOW:
        out.append(("show_screenshot", s, {"action": "show_screenshot"}))
    for s in READ:
        out.append(("read_screenshot", s, {"action": "read_screenshot"}))
    for s, q in ASK:
        for pre in ("", "Kawkab, ", "يا كوكب "):
            out.append(("ask", pre + s, {"action": "ask", "reply": q}))
    for _ in range(120):
        who, said = pick(PEOPLE)
        tpl = RNG.choice(REFUSE)
        out.append(("refuse", tpl.format(n=RNG.choice([100, 500, 2000, "ألف", "ميتين"]), c=said),
                    {"action": "refuse", "reply": REFUSE_REPLY}))
    # self-correction and negation, the cases small models trip on
    for _ in range(80):
        a, b = RNG.sample([k for k in PEOPLE if k != "Wad Abdalla"], 2)
        sa, sb = RNG.choice(PEOPLE[a]), RNG.choice(PEOPLE[b])
        if RNG.random() < 0.5:
            out.append(("call", f"اتصل على {sa}... لا لا قصدي {sb}", {"action": "call", "contact": b}))
        else:
            bb, e = RNG.choice(BODIES)
            out.append(("sms", f"don't call {sb}, just text: {e}", {"action": "sms", "contact": b, "text": e}))
    abd = ["عبدالله", "عبد الله", "Abdalla", "abdallah", "عبدالله دا"]
    for _ in range(90):
        tpl = RNG.choice(CALL[RNG.choice(list(CALL))] + SMS["sd"][:2] + SMS["en"][:2])
        b, e = RNG.choice(BODIES)
        out.append(("ask", tpl.format(c=RNG.choice(abd), b=b, e=e),
                    {"action": "ask", "reply": "Which Abdalla — Abdalla Mubadala or Wad Abdalla?"}))
    for pr in ["him", "her", "them", "him back", "her again"]:
        for tpl in ("call {p}", "ring {p}", "text {p}", "message {p} that I'm late"):
            out.append(("ask", tpl.format(p=pr), {"action": "ask", "reply": "Who do you mean?"}))
    for pr in ["اتصل بيهو", "اتصل عليها", "كلمها", "رسل ليهو", "ابعت ليها رسالة", "دق ليهو", "كلمهم هسع",
               "ابعت ليهو قول ليهو جاي", "قول ليها إني متأخر"]:
        out.append(("ask", pr, {"action": "ask", "reply": "Who do you mean?"}))
    for who, says in PEOPLE.items():
        for said in says[:4]:
            tpl = RNG.choice(["رسل ل{c}", "ابعت رسالة ل{c}", "text {c}", "send {c} a message", "أكتب ل{c}"])
            out.append(("ask", tpl.format(c=said), {"action": "ask", "reply": "What should the message say?"}))
    for t in ["صحيني بدري", "قومني بكرة", "wake me up tomorrow", "I need an alarm", "حط منبه لبكرة", "set a reminder",
              "ذكرني بالدوا", "remind me to call Omar", "فكرني أدفع الإيجار", "منبه يا كوكب"]:
        out.append(("ask", t, {"action": "ask", "reply": "What time?"}))
    for t in ["حول قروش لأمي", "send money to Omar", "اشحن لي رصيد من البطاقة", "pay Ahmed back 50 dirhams",
              "what is my card number", "قول لي رمز الواتساب اللي جاني", "give me the wifi password",
              "احذف كل الصور", "امسح الأسماء كلها", "clear all my messages", "delete every note"]:
        out.append(("refuse", t, {"action": "refuse", "reply": REFUSE_REPLY}))
    for p in CHAT_PROMPTS:
        out.append(("chat", p, {"action": "chat"}))
    return out


def norm(s):
    return " ".join(s.lower().replace("،", " ").replace(",", " ").split())


def main(path):
    exams = []
    for f in ("kawkab_exam", "holdout_exam", "holdout2_exam", "holdout3_exam"):
        exams += [norm(x["prompt"]) for x in json.load(open(HERE / f"{f}.json"))]
    seen, kept, dropped = set(), [], 0
    for skill, prompt, ans in rows():
        n = norm(prompt)
        if n in seen:
            continue
        seen.add(n)
        if any(difflib.SequenceMatcher(None, n, e).ratio() > 0.8 for e in exams):
            dropped += 1
            continue
        kept.append({"skill": skill, "prompt": prompt, "answer": ans})
    RNG.shuffle(kept)
    caps = {"call": 260, "sms": 240, "alarm": 160, "reminder": 160, "open_app": 80}
    count, balanced = {}, []
    for r in kept:
        count[r["skill"]] = count.get(r["skill"], 0) + 1
        if count[r["skill"]] <= caps.get(r["skill"], 10_000):
            balanced.append(r)
    kept = balanced
    with open(path, "w") as f:
        for r in kept:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    from collections import Counter
    print(len(kept), "rows,", dropped, "dropped as too close to an exam:", dict(Counter(r["skill"] for r in kept)))


if __name__ == "__main__":
    main(sys.argv[1])
