package app.kawkab.android.domain.fastpath

/**
 * One typed command the fast path understood.
 *
 * @property tool Executor name, such as call_phone.
 * @property args String arguments. Hours, minutes, and durations are decimal text.
 */
data class PlannedCommand(val tool: String, val args: Map<String, String>)

/**
 * Deterministic Arabic and English grammar for the five core commands.
 *
 * A line it does not recognise is null. It does not call a model, and it does
 * not run the action: the caller still goes through the outbound tap and the denylist.
 */
object CommandFastPath {
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3_600
    private const val HALF_HOUR_MINUTES = 30
    private const val FIVE_MINUTES = 5
    private const val MAX_CONTACT_WORDS = 4

    private val whitespace = Regex("""\s+""")
    private val indicDigits = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
    )
    private val appAliases = mapOf(
        "camra" to "Camera",
        "clock" to "Clock",
        "messages" to "Messages",
    )
    private val timerWords = mapOf("خمس" to "5", "عشر" to "10")
    private val timerUnits = mapOf(
        "دقائق" to SECONDS_PER_MINUTE,
        "دقيقة" to SECONDS_PER_MINUTE,
        "دقايق" to SECONDS_PER_MINUTE,
        "minutes" to SECONDS_PER_MINUTE,
        "ثواني" to 1,
        "ثانية" to 1,
        "ساعة" to SECONDS_PER_HOUR,
    )
    private val calendarEn = Regex(
        """^(?:add (?:an )?(?:event|evnet)|calendar) (.+?) at (.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val calendarAr = Regex("""^(?:أضف|ضيف) (?:موعد )?(.+?) الساعة (.+)$""")
    private val calendarRecord = Regex("""^سجّ?ل موعد (.+?) الساعة (.+)$""")
    private val calendarEnd = Regex("""^(.+?) ضيفو الساعة (.+)$""")
    private val calendarMeet = Regex("""^موعد (.+?) الساعة (.+)$""")
    private val calendarPut = Regex("""^حط (.+?) بكرة الساعة (.+)$""")
    private val smsEn = Regex("""^(?:text|sms)(?:\s+to)? (\S+) (.+)$""", RegexOption.IGNORE_CASE)
    private val smsAr = Regex("""^(?:يا \S+ )?(?:رسل|ابع[ثت]|راسل) ل?(\S+) (.+)$""")
    private val smsSay = Regex("""^قول ل(\S+) (.+)$""")
    private val smsLet = Regex("""^let (\S+) know (.+)$""", RegexOption.IGNORE_CASE)
    private val smsOrder = Regex("""^(\S+) (.+) text$""", RegexOption.IGNORE_CASE)
    private val smsHer = Regex("""^قولي ليها (\S+) (.+)$""")
    private val smsLate = Regex("""^(.+)، قول ل(\S+)$""")
    private val alarmEn = Regex(
        """^(?:set an al(?:ar|ra)m for|set an? alarm for|set alarm|wake me at|alarm at) (.+?)(?:\s+please)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val alarmAr = Regex("""^(?:.*\s)?(?:صحيني|اضبط المنبه|فزعني) (?:الساعة|على|at) (.+)$""")
    private val alarmNoun = Regex("""^(?:المنبه على|منبه الساعة) (.+)$""")
    private val timerEn = Regex(
        """^(?:set a tim+er for|timer for|time me for) (.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val timerStart = Regex("""^start a (\d+) minute timer$""", RegexOption.IGNORE_CASE)
    private val timerGive = Regex("""^give me (\d+) hours on a timer$""", RegexOption.IGNORE_CASE)
    private val timerAr = Regex("""^مؤقت (\S+) (دقائق|دقيقة|دقايق|ثواني|ثانية|ساعة|minutes)$""")
    private val timerCount = Regex("""^(?:ضع )?عداد ل?(\S+) (دقائق|دقيقة|دقايق|ثواني|ثانية|ساعة)$""")
    private val timerHalf = Regex("""^مؤقت لنصف ساعة$""")
    private val timerKeep = Regex("""^خلّي الساعة تعد خمس دقايق$""")
    private val amountUnit = Regex("""^(\S+) (دقائق|دقيقة|دقايق|ثواني|ثانية|ساعة|minutes)$""")
    private val openEn = Regex(
        """^(?:can you )?(?:open|opne|launch) (?:the )?(.+?)(?: app)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val openAr = Regex("""^(?:افتح|شغّ?ل(?: لي)?) (.+)$""")
    private val openPhrase = Regex("""^(?:قم بفتح|ادخل على) (.+)$""")
    private val openWant = Regex("""^داير ال?(.+)$""")
    private val callEn = Regex(
        """^(?:please\s+)?(?:calll?|phone|dial|ring(?:\s+up)?)\s+(.+?)(?:\s+for me|\s+right away)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val callAr = Regex("""^(?:.*\s)?(?:اتصل (?:على |ب)|كلم )(.+)$""")
    private val callTalk = Regex("""^i need to talk to (.+)$""", RegexOption.IGNORE_CASE)
    private val callGet = Regex("""^get (.+) on the phone$""", RegexOption.IGNORE_CASE)
    private val callNamed = Regex("""^(\S+), give him a ring$""", RegexOption.IGNORE_CASE)
    private val callSudan = Regex("""^يا أخوي داير (\S+) في التلفون$""")
    private val callBack = Regex("""^(\S+) اتصل عليه$""")
    private val callHit = Regex("""^(?:ا?ضرب|دق) (?:لي )?(?:على |ل|لـ)?(.+?)$""")
    private val callGive = Regex("""^(?:أ|ا)ديني (.+?) (?:في|ف) التلفون$""")
    private val callLink = Regex("""^(?:وصلني|وصّلني) (?:ب|بـ|مع )(.+)$""")
    private val callWant = Regex("""^(?:داير|عايز|عاوز)ة? (?:أ|ا)(?:كلم |تكلم مع |تصل ب|تصل على )(.+)$""")
    private val callWish = Regex("""^(?:ابغى|ابغي|أبغى|بدي|خليني) (?:أ|ا)(?:كلم |تكلم مع |تصل ب|تصل على )(.+)$""")
    private val callWould = Regex("""^ممكن (?:ت|)تصل(?: لي)? (?:ب|على )(.+)$""")
    private val callTo = Regex("""^اتصلي? (?:ب|على )(.+?)$""")
    private val callRing = Regex("""^give (.+) a ring$""", RegexOption.IGNORE_CASE)
    private val callConnect = Regex("""^(?:please )?connect me (?:to|with) (.+)$""", RegexOption.IGNORE_CASE)
    private val callKalim = Regex("""^(?:kalim|kallim|kalam) (.+)$""", RegexOption.IGNORE_CASE)
    private val smsSend = Regex("""^send (.+?) an? (?:message|text|sms)[:,]? (.+)$""", RegexOption.IGNORE_CASE)
    private val smsColon = Regex("""^(?:message|text|send) (.+?): (.+)$""", RegexOption.IGNORE_CASE)
    private val smsWrite = Regex("""^(?:أكتب|اكتب|ارسل|أرسل) ل(\S+) (.+)$""")
    private val alarmWake = Regex(
        """^(?:wake me up at|wake me at|set alarm for|set an alarm at|alarm|alarm at) (.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val alarmPut = Regex("""^(?:(?:حط|اضبط|عاوز|عايز|داير)(?: لي)? )?منبه (?:الساعة |على )?(.+)$""")
    private val alarmWakeAr = Regex("""^(?:صحيني|قومني|فزعني)(?: الساعة)? (.+)$""")
    private val wakePrefix = Regex("""^(?:(?:يا )?(?:kawkab|كوكب)[,،]?\s+)""", RegexOption.IGNORE_CASE)
    private val fillerEn = Regex("""(?:\s+(?:now|please|right now|for me))+$""", RegexOption.IGNORE_CASE)
    private val fillerAr = Regex("""(?:\s+(?:هس[يعهاة]|هسّع|لو سمحت|لو سمحتي|ضروري|بسرعة|يا زول|يا أخوي))+$""")
    private val notContacts = setOf(
        "ي", "لي", "نا", "ها", "هو", "يهو", "ليهو", "ليها", "يها", "ليك", "ك", "مسج", "رسالة", "رساله", "message",
        "msg", "text", "her", "him", "them", "me", "it", "her back", "him back", "them back", "back",
    )
    private val notApps = listOf(
        "سكرين", "صورة شاشة", "لقطة شاشة", "لقطة الشاشة", "screenshot", "screen shot", "فلاش", "كشاف",
        "بلوتوث", "bluetooth", "flashlight", "torch", "بنك", "bank",
    )
    private val unsure = Regex("""(ولا |،|\?|؟|ما متأكد|not sure|(^|\s)or\s)""", RegexOption.IGNORE_CASE)
    private val minutesEn = Regex("""^(\d+)\s*(?:minutes|minute|mins|min)$""", RegexOption.IGNORE_CASE)
    private val secondsEn = Regex("""^(\d+)\s*(?:seconds|second|secs|sec)$""", RegexOption.IGNORE_CASE)
    private val hoursEn = Regex("""^(\d+)\s*(?:hours|hour|hrs|hr)$""", RegexOption.IGNORE_CASE)
    private val wordMinutes = Regex("""^([A-Za-z]+) minutes$""", RegexOption.IGNORE_CASE)
    private val politePrefixes = listOf(
        "please ",
        "can you ",
        "could you ",
        "i need to ",
        "i want to ",
    )
    private val VERBS = listOf("call", "phone", "dial", "ring", "text", "sms", "open", "launch", "alarm", "timer")
    private val directSms = listOf(smsSend, smsColon, smsEn, smsAr, smsSay, smsLet, smsOrder, smsHer, smsWrite)
    private val directCalls = listOf(
        callEn, callAr, callTalk, callGet, callNamed, callSudan, callBack,
        callHit, callGive, callLink, callWant, callWish, callWould, callTo, callRing, callConnect, callKalim,
    )
    private val alarmShapes = listOf(alarmEn, alarmAr, alarmNoun, alarmWake, alarmPut, alarmWakeAr)
    private val timedEvents = listOf(calendarEn, calendarAr, calendarRecord, calendarEnd, calendarMeet, calendarPut)

    /**
     * Reads [raw] as one core command, or null when the line is not one of those shapes.
     */
    fun parse(raw: String): PlannedCommand? {
        val text = wakePrefix.replace(normalize(raw), "")
        if (text.isEmpty()) return null
        // Money, secrets and bulk deletes are never planned here; the router refuses them (SpokenRefusal).
        if (SpokenRefusal.reason(text) != null) return null
        val direct = read(text)
        if (direct != null) return direct
        val relaxed = relax(text) ?: return null
        return read(relaxed)
    }

    private fun read(text: String): PlannedCommand? =
        calendar(text) ?: message(text) ?: alarm(text) ?: timer(text) ?: open(text) ?: call(text)

    /**
     * A second reading for a line the strict grammar missed: drop a polite wrapper,
     * or fix a one-letter slip in the first word. Already-matched lines never reach this.
     */
    private fun relax(text: String): String? {
        val lower = text.lowercase()
        val prefix = politePrefixes.firstOrNull { lower.startsWith(it) }
        if (prefix != null) return text.substring(prefix.length)
        val splitAt = text.indexOf(' ')
        val head = if (splitAt < 0) text else text.substring(0, splitAt)
        val fixed = verbSlip(head.lowercase()) ?: return null
        val tail = if (splitAt < 0) "" else text.substring(splitAt)
        return fixed + tail
    }

    private fun verbSlip(word: String): String? = VERBS.firstOrNull { candidate -> oneSlip(word, candidate) }

    private fun oneSlip(word: String, candidate: String): Boolean {
        if (word == candidate) return false
        if (kotlin.math.abs(word.length - candidate.length) > 1) return false
        var i = 0
        var j = 0
        var slips = 0
        while (i < word.length && j < candidate.length) {
            if (word[i] == candidate[j]) {
                i += 1
                j += 1
            } else {
                slips += 1
                if (slips > 1) return false
                if (word.length > candidate.length) {
                    i += 1
                } else if (word.length < candidate.length) {
                    j += 1
                } else {
                    i += 1
                    j += 1
                }
            }
        }
        return slips + (word.length - i) + (candidate.length - j) == 1
    }

    private fun normalize(raw: String): String {
        val mapped = buildString(raw.length) {
            CommandText.stripDecoration(raw).forEach { character -> append(indicDigits[character] ?: character) }
        }
        return whitespace.replace(mapped, " ").trim()
    }

    private fun calendar(text: String): PlannedCommand? {
        val match = timedEvents.firstNotNullOfOrNull { pattern -> pattern.matchEntire(text) } ?: return null
        val time = SpokenClock.read(match.groupValues[2]) ?: return null
        return PlannedCommand(
            tool = "add_calendar_event",
            args = mapOf("title" to match.groupValues[1], "hour" to time.first, "minute" to time.second),
        )
    }

    private fun message(text: String): PlannedCommand? {
        val direct = directSms.firstNotNullOfOrNull { pattern -> pattern.matchEntire(text) }
        if (direct != null) return sms(direct.groupValues[1], direct.groupValues[2])
        val late = smsLate.matchEntire(text) ?: return null
        return sms(late.groupValues[2], late.groupValues[1])
    }

    /** A text needs a real name and a body; "قول لي نكتة" or "ابعت مسج لأحمد" is not one. */
    private fun sms(contact: String, body: String): PlannedCommand? {
        val who = contactName(contact) ?: return null
        if (body.isBlank()) return null
        return PlannedCommand(tool = "send_sms", args = mapOf("contact" to who, "body" to body))
    }

    /**
     * The contact words with polite or time fillers removed, or null when they are a pronoun, a
     * placeholder, or the owner is unsure ("ود عبدالله ولا عبدالله التاني"). Null lets the model ask.
     */
    private fun contactName(raw: String): String? {
        var name = raw.trim()
        repeat(2) { name = fillerAr.replace(fillerEn.replace(name, ""), "").trim() }
        if (name.isEmpty() || name.lowercase() in notContacts || unsure.containsMatchIn(name)) return null
        if (name.split(' ').size > MAX_CONTACT_WORDS) return null
        return name
    }

    private fun alarm(text: String): PlannedCommand? {
        // The first shape whose time reads wins: "set alarm for 9:15" is not "set alarm" + "for 9:15".
        val time = alarmShapes.firstNotNullOfOrNull { shape ->
            shape.matchEntire(text)?.let { match -> SpokenClock.read(match.groupValues[1]) }
        } ?: return null
        return PlannedCommand(
            tool = "set_alarm",
            args = mapOf("hour" to time.first, "minute" to time.second),
        )
    }

    private fun timer(text: String): PlannedCommand? = englishTimer(text) ?: arabicTimer(text)

    private fun englishTimer(text: String): PlannedCommand? {
        val started = timerStart.matchEntire(text)?.groupValues?.get(1)?.toIntOrNull()?.times(SECONDS_PER_MINUTE)
        val given = timerGive.matchEntire(text)?.groupValues?.get(1)?.toIntOrNull()?.times(SECONDS_PER_HOUR)
        val fixed = when {
            timerHalf.matches(text) -> HALF_HOUR_MINUTES * SECONDS_PER_MINUTE
            timerKeep.matches(text) -> FIVE_MINUTES * SECONDS_PER_MINUTE
            else -> null
        }
        val spoken = timerEn.matchEntire(text)?.groupValues?.get(1)?.let { body ->
            englishSeconds(body) ?: arabicSeconds(body)
        }
        val seconds = started ?: given ?: fixed ?: spoken ?: return null
        return plannedTimer(seconds)
    }

    private fun arabicTimer(text: String): PlannedCommand? {
        val arabic = timerAr.matchEntire(text) ?: timerCount.matchEntire(text) ?: return null
        val seconds = amountSeconds(arabic.groupValues[1], arabic.groupValues[2]) ?: return null
        return plannedTimer(seconds)
    }

    private fun open(text: String): PlannedCommand? {
        val match = openEn.matchEntire(text)
            ?: openAr.matchEntire(text)
            ?: openPhrase.matchEntire(text)
            ?: openWant.matchEntire(text)
            ?: return null
        // A screenshot, the torch or Bluetooth is not an app; the model routes those.
        if (notApps.any { it in match.groupValues[1].lowercase() }) return null
        return PlannedCommand(tool = "open_app", args = mapOf("name" to appName(match.groupValues[1])))
    }

    private fun call(text: String): PlannedCommand? {
        val match = directCalls.firstNotNullOfOrNull { pattern -> pattern.matchEntire(text) } ?: return null
        val who = contactName(match.groupValues[1]) ?: return null
        return PlannedCommand(tool = "call_phone", args = mapOf("contact" to who))
    }

    private fun appName(raw: String): String = appAliases[raw.lowercase()] ?: raw

    private fun arabicSeconds(text: String): Int? {
        val match = amountUnit.matchEntire(text) ?: return null
        return amountSeconds(match.groupValues[1], match.groupValues[2])
    }

    private fun amountSeconds(amountText: String, unitText: String): Int? {
        val amount = amountText.toIntOrNull() ?: timerWords[amountText]?.toIntOrNull() ?: return null
        val unit = timerUnits[unitText] ?: return null
        return amount * unit
    }

    private fun englishSeconds(text: String): Int? {
        minutesEn.matchEntire(text)?.let { return it.groupValues[1].toInt() * SECONDS_PER_MINUTE }
        secondsEn.matchEntire(text)?.let { return it.groupValues[1].toInt() }
        hoursEn.matchEntire(text)?.let { return it.groupValues[1].toInt() * SECONDS_PER_HOUR }
        val words = wordMinutes.matchEntire(text)?.let { SpokenClock.englishNumber(it.groupValues[1]) }
        if (words != null) return words * SECONDS_PER_MINUTE
        return null
    }

    private fun plannedTimer(seconds: Int): PlannedCommand =
        PlannedCommand(tool = "set_timer", args = mapOf("seconds" to seconds.toString()))
}
