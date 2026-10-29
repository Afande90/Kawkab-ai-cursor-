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
    private const val NOON = 12
    private const val LAST_HOUR = 23
    private const val LAST_MINUTE = 59
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3_600
    private const val MERIDIEM_GROUP = 3

    private val whitespace = Regex("""\s+""")
    private val indicDigits = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
    )
    private val hourWords = mapOf(
        "واحدة" to "1",
        "واحد" to "1",
        "اثنين" to "2",
        "اتنين" to "2",
        "ثلاثة" to "3",
        "تلاتة" to "3",
        "أربعة" to "4",
        "اربعة" to "4",
        "خمسة" to "5",
        "ستة" to "6",
        "سبعة" to "7",
        "ثمانية" to "8",
        "تمانية" to "8",
        "تسعة" to "9",
        "عشرة" to "10",
        "حداشر" to "11",
        "اتناشر" to "12",
    )
    private val timerWords = mapOf("خمس" to "5", "عشر" to "10")
    private val timerUnits = mapOf(
        "دقائق" to SECONDS_PER_MINUTE,
        "دقيقة" to SECONDS_PER_MINUTE,
        "ثواني" to 1,
        "ثانية" to 1,
        "ساعة" to SECONDS_PER_HOUR,
    )
    private val calendarEn = Regex("""^add (?:an )?event (.+?) at (.+)$""", RegexOption.IGNORE_CASE)
    private val calendarAr = Regex("""^(?:أضف|ضيف) موعد (.+?) الساعة (.+)$""")
    private val smsEn = Regex("""^(?:text|sms) (\S+) (.+)$""", RegexOption.IGNORE_CASE)
    private val smsAr = Regex("""^(?:رسل|ابعت) ل(\S+) (.+)$""")
    private val alarmEn = Regex("""^(?:set an alarm for|wake me at) (.+)$""", RegexOption.IGNORE_CASE)
    private val alarmAr = Regex("""^(?:صحيني|اضبط المنبه) الساعة (.+)$""")
    private val timerEn = Regex("""^(?:set a timer for|timer for) (.+)$""", RegexOption.IGNORE_CASE)
    private val timerAr = Regex("""^مؤقت (\S+) (دقائق|دقيقة|ثواني|ثانية|ساعة)$""")
    private val openEn = Regex("""^(?:open|launch) (.+)$""", RegexOption.IGNORE_CASE)
    private val openAr = Regex("""^(?:افتح|شغل) (.+)$""")
    private val callEn = Regex("""^(?:please\s+)?(?:call|phone|dial|ring)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val callAr = Regex("""^(?:اتصل على|كلم) (.+)$""")
    private val clockEn = Regex("""^(\d{1,2})(?::(\d{1,2}))?(?:\s+(am|pm))?$""", RegexOption.IGNORE_CASE)
    private val minutesEn = Regex("""^(\d+)\s*(?:minutes|minute|mins|min)$""", RegexOption.IGNORE_CASE)
    private val secondsEn = Regex("""^(\d+)\s*(?:seconds|second|secs|sec)$""", RegexOption.IGNORE_CASE)
    private val hoursEn = Regex("""^(\d+)\s*(?:hours|hour|hrs|hr)$""", RegexOption.IGNORE_CASE)

    /**
     * Reads [raw] as one core command, or null when the line is not one of those shapes.
     */
    fun parse(raw: String): PlannedCommand? {
        val text = normalize(raw)
        if (text.isEmpty()) return null
        return calendar(text) ?: message(text) ?: alarm(text) ?: timer(text) ?: open(text) ?: call(text)
    }

    private fun normalize(raw: String): String {
        val mapped = buildString(raw.length) {
            raw.trim().forEach { character -> append(indicDigits[character] ?: character) }
        }
        return whitespace.replace(mapped, " ").trim()
    }

    private fun calendar(text: String): PlannedCommand? {
        val match = calendarEn.matchEntire(text) ?: calendarAr.matchEntire(text) ?: return null
        val time = clock(match.groupValues[2]) ?: return null
        return PlannedCommand(
            tool = "add_calendar_event",
            args = mapOf("title" to match.groupValues[1], "hour" to time.first, "minute" to time.second),
        )
    }

    private fun message(text: String): PlannedCommand? {
        val match = smsEn.matchEntire(text) ?: smsAr.matchEntire(text) ?: return null
        return PlannedCommand(
            tool = "send_sms",
            args = mapOf("contact" to match.groupValues[1], "body" to match.groupValues[2]),
        )
    }

    private fun alarm(text: String): PlannedCommand? {
        val match = alarmEn.matchEntire(text) ?: alarmAr.matchEntire(text) ?: return null
        val time = clock(match.groupValues[1]) ?: return null
        return PlannedCommand(
            tool = "set_alarm",
            args = mapOf("hour" to time.first, "minute" to time.second),
        )
    }

    private fun timer(text: String): PlannedCommand? = englishTimer(text) ?: arabicTimer(text)

    private fun englishTimer(text: String): PlannedCommand? {
        val match = timerEn.matchEntire(text) ?: return null
        val seconds = englishSeconds(match.groupValues[1]) ?: return null
        return plannedTimer(seconds)
    }

    private fun arabicTimer(text: String): PlannedCommand? {
        val arabic = timerAr.matchEntire(text) ?: return null
        val amount = arabic.groupValues[1].toIntOrNull()
            ?: timerWords[arabic.groupValues[1]]?.toIntOrNull()
            ?: return null
        val unit = timerUnits[arabic.groupValues[2]] ?: return null
        return plannedTimer(amount * unit)
    }

    private fun open(text: String): PlannedCommand? {
        val match = openEn.matchEntire(text) ?: openAr.matchEntire(text) ?: return null
        return PlannedCommand(tool = "open_app", args = mapOf("name" to match.groupValues[1]))
    }

    private fun call(text: String): PlannedCommand? {
        val match = callEn.matchEntire(text) ?: callAr.matchEntire(text) ?: return null
        return PlannedCommand(tool = "call_phone", args = mapOf("contact" to match.groupValues[1]))
    }

    private fun clock(text: String): Pair<String, String>? {
        hourWords[text]?.let { hour -> return hour to "0" }
        val match = clockEn.matchEntire(text) ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val hour = hourOnClock(match.groupValues[1].toInt(), match.groupValues[MERIDIEM_GROUP])
        if (hour !in 0..LAST_HOUR || minute !in 0..LAST_MINUTE) return null
        return hour.toString() to minute.toString()
    }

    private fun hourOnClock(hour: Int, meridiem: String): Int = when (meridiem.lowercase()) {
        "pm" -> if (hour < NOON) hour + NOON else hour
        "am" -> if (hour == NOON) 0 else hour
        else -> hour
    }

    private fun englishSeconds(text: String): Int? {
        minutesEn.matchEntire(text)?.let { return it.groupValues[1].toInt() * SECONDS_PER_MINUTE }
        secondsEn.matchEntire(text)?.let { return it.groupValues[1].toInt() }
        hoursEn.matchEntire(text)?.let { return it.groupValues[1].toInt() * SECONDS_PER_HOUR }
        return null
    }

    private fun plannedTimer(seconds: Int): PlannedCommand =
        PlannedCommand(tool = "set_timer", args = mapOf("seconds" to seconds.toString()))
}
