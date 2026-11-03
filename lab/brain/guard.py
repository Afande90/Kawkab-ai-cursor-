"""Kawkab action guard: deterministic checks applied to the model's JSON after it answers.

The model understands most requests but sometimes mislabels them (asks a question while labelling
the action "call", or replies "chat" to a Sudanese call idiom). These rules use only what the owner
said and the model's own fields, so they are the same rules the app can run in Kotlin.
"""
import re

AMBIGUOUS = {"abdalla": ("mubadala", "مبادلة", "مبادله", "wad", "ود ")}
ABDALLA = ("عبدالله", "عبد الله", "abdalla", "abdullah")

MONEY = re.compile(r"(حول|حوّل|تحويل|ادفع|transfer|send money|pay)\b.*?|(\bبنك|البنك|bank|حسابي|account)", re.I)
MONEY_UNITS = re.compile(r"(درهم|جنيه|ريال|دولار|قروش|فلوس|aed|dirham|\$|money|bill|الفاتورة من حسابي)", re.I)
SECRET = re.compile(r"(كلمة سر|كلمة السر|باسورد|password|pin\b|رمز سري)", re.I)
DELETE = re.compile(r"(امسح كل|مسح كل|احذف كل|delete all|erase all|wipe)", re.I)

CALL_IDIOM = re.compile(r"(ضرب لي|اضرب لي|أديني|اديني|كلم|كلّم|اتصل|call|ring)", re.I)
SHOW = re.compile(r"(وريني|ورني|افتح|اعرض|عرض|show|open|display).*(سكرين|صورة شاشة|لقطة شاشة|لقطة الشاشة|screenshot|سكرين شوت|screen shot)", re.I)
READ = re.compile(r"(دي|ده|this|the screenshot|الصورة|الفاتورة|invoice|bill).*(فيها|شنو|كم|متين|تاريخ|say|what|how much|when)", re.I)
APP_OPEN = re.compile(r"^(وديني|وريني|افتح|open|go to)\s+(ال)?(\S+)", re.I)
APPS = {"واتساب": "WhatsApp", "الواتساب": "WhatsApp", "whatsapp": "WhatsApp", "خريطة": "Maps", "الخريطة": "Maps",
        "maps": "Maps", "كاميرا": "Camera", "الكاميرا": "Camera", "يوتيوب": "YouTube", "اليوتيوب": "YouTube"}
CONTACT_WORDS = {"أمي": "Mum", "امي": "Mum", "ماما": "Mum", "mama": "Mum", "mother": "Mum", "mum": "Mum", "mom": "Mum", "أحمد": "Ahmed", "احمد": "Ahmed",
                 "ahmed": "Ahmed", "عمر": "Omar", "omar": "Omar", "سارة": "Sara (sister)", "ساره": "Sara (sister)",
                 "أختي": "Sara (sister)", "اختي": "Sara (sister)", "my sister": "Sara (sister)", "sara": "Sara (sister)",
                 "علي": "Ali (brother)", "ali": "Ali (brother)", "الوالدة": "Mum", "والدتي": "Mum", "الوالده": "Mum", "brother": "Ali (brother)", "sister": "Sara (sister)", "أخوي": "Ali (brother)", "أخوي": "Ali (brother)", "اخوي": "Ali (brother)", "my brother": "Ali (brother)"}


def _ask(out, question):
    reply = out.get("reply") or out.get("text") or ""
    return {"action": "ask", "reply": reply if reply.strip().endswith(("?", "؟")) else question}


def _named_contact(said):
    low = said.lower()
    for word, name in CONTACT_WORDS.items():
        if re.search(r"(^|\s|ل|لـ|ب|بـ|و|ال)" + re.escape(word) + r"($|\s|[؟?.,:!،])", low):
            return name
    return None


def repair(raw):
    """Best-effort JSON repair for a reply the parser rejected (an unclosed string or brace)."""
    import json
    m = re.search(r"\{.*", raw or "", re.S)
    if not m:
        return None
    t = m.group(0).strip().rstrip("`").strip()
    for tail in ("", "}", '"}', '"}}'):
        try:
            return json.loads(t + tail)
        except Exception:
            pass
    return None


TIME_WORD = re.compile(r"(\d|[٠-٩]|واحدة|واحده|اتنين|تلاتة|تلاته|ثلاث|أربعة|اربعة|خمسة|خمسه|ستة|سته|سبعة|سبعه|تمانية|ثمانية|تسعة|تسعه|عشرة|عشره|حداشر|اطناشر|نص ساعة|ساعة|بعد|الصباح|الصبح|العصر|المغرب|بالليل|الليلة|tonight|morning|evening|noon|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|hour|minute|بكرة|بكره|باكر|يوم|السبت|الأحد|الاحد|الاتنين|الإثنين|التلات|الثلاثاء|الأربع|الاربعاء|الخميس|الجمعة|tomorrow|today|monday|tuesday|wednesday|thursday|friday|saturday|sunday|week)", re.I)
DOUBT = re.compile(r"(\?|؟)\s*$")
MUSING = re.compile(r"(ولا لا|ولا؟|ما رأيك|رايك|should i|do you think)", re.I)


def guard(said, out, raw=None):
    """Return the corrected action dict. `said` is the owner's words, `out` the parsed model JSON (may be None)."""
    out = dict(out or repair(raw) or {})
    low = said.lower()
    if out.get("action") == "open_app" and re.search(r"(bluetooth|بلوتوث|flash|كشاف)", low + str(out.get("app")).lower()):
        out["action"] = "bluetooth" if re.search(r"(bluetooth|بلوتوث)", low + str(out.get("app")).lower()) else "flashlight"
    # 1. Safety first: never let money, secrets or bulk deletion through, whatever the model said.
    if (MONEY.search(said) and MONEY_UNITS.search(said)) or re.search(r"(البنك|bank).*(حول|transfer)|(حول|transfer).*(البنك|bank)", said, re.I) \
            or SECRET.search(said) or DELETE.search(said):
        return {"action": "refuse", "reply": out.get("reply") if out.get("action") == "refuse" else "I can't do that one."}
    # 2. Screenshots: seeing one vs asking about its contents.
    if SHOW.search(said) and not READ.search(said):
        return {"action": "show_screenshot", "which": "latest"}
    if out.get("action") in ("chat", "ask", "show_screenshot") and READ.search(said) and re.search(
            r"(سكرين|صورة|الصورة|screenshot|الفاتورة|فاتورة|invoice|bill)", said, re.I):
        return {"action": "read_screenshot", "question": said}
    # 3. Opening an app the model answered with a question.
    m = APP_OPEN.search(said.strip())
    if m and out.get("action") in ("chat", "ask"):
        word = (m.group(3) or "").lower()
        app = APPS.get(word) or APPS.get("ال" + word)
        if app:
            return {"action": "open_app", "app": app}
    # 4. Sudanese call idioms the model answered as chat.
    who = _named_contact(said)
    texting = re.search(r"(رسل|ارسل|text|sms|message)", said, re.I)
    if out.get("action") == "chat" and CALL_IDIOM.search(said) and not texting and (not MUSING.search(said) or re.search(r"(بس|but|just) (كلمه|كلمو|اتصل|call)", said, re.I)):
        if who:
            out = {"action": "call", "contact": who}
        elif any(a in low for a in ABDALLA):
            out = {"action": "call", "contact": out.get("contact"), "reply": out.get("reply")}
    act = out.get("action")
    # 5. Two Abdallas: the model must not pick one for the owner.
    if act in ("call", "sms") and any(a in low for a in ABDALLA) and not any(k in low for k in AMBIGUOUS["abdalla"]):
        return _ask(out, "Which Abdalla, Mubadala or Wad Abdalla?")
    # 6. Missing slots: an action can't run without them.
    if act in ("call", "sms") and not out.get("contact"):
        return _ask(out, "Who should I contact?")
    if act == "sms" and not (out.get("text") or "").strip():
        return _ask(out, "What should the message say?")
    if act == "sms" and (out.get("text") or "").strip().endswith(("?", "؟")) and (out.get("text") == out.get("reply") or not out.get("contact")):
        return _ask(out, "What should the message say?")
    if act == "alarm" and not out.get("time"):
        return _ask(out, "What time?")
    if act == "reminder" and (not (out.get("time") or out.get("when")) or (out.get("text") or "").strip() in ("", said.strip())):
        return _ask(out, "What should I remind you about, and when?")
    # 7. The model filled a slot itself but then asked the owner about it: it is unsure, so ask.
    if act in ("call", "sms") and not who and not any(a in low for a in ABDALLA) and not re.search(r"(wad|ود |mubadala|مبادلة)", low):
        return _ask(out, "Who should I contact?")
    if act in ("alarm", "reminder") and not TIME_WORD.search(said):
        return _ask(out, "What time?")
    if act == "chat" and DOUBT.search(out.get("reply") or "") and re.search(
            r"(remind|forget|ذكرني|فكرني|تنساني|منبه|alarm|صحيني|قومني)", said, re.I):
        return {"action": "ask", "reply": out["reply"]}
    if act == "chat" and not out.get("contact") and re.search(r"^(رسل|ارسل|اتصل|text|call)\b", said.strip(), re.I) and not who:
        return _ask(out, "Who should I contact?")
    return out
