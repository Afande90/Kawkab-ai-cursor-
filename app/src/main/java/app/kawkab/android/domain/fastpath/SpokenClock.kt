package app.kawkab.android.domain.fastpath

/**
 * Reads a spoken clock time in English, Modern Standard Arabic or Sudanese and Gulf dialect:
 * "6:30 pm", "half past six", "ستة ونص", "خمسة إلا ربع", "عشرة بالليل".
 *
 * @see CommandFastPath for the commands that carry the time.
 */
object SpokenClock {
    private const val NOON = 12
    private const val LAST_HOUR = 23
    private const val LAST_MINUTE = 59
    private const val MERIDIEM_GROUP = 3
    private const val HALF_HOUR_MINUTES = 30
    private const val QUARTER = 15
    private const val HOUR_MINUTES = 60
    private const val HOURS_A_DAY = 24

    private val morningWords = setOf("الصباح", "الصبح", "صباحا", "صباحاً", "الفجر", "am", "in the morning")
    private val eveningWords = setOf(
        "بالليل", "الليل", "مساء", "مساءً", "المسا", "المساء", "العصر", "بعد الضهر", "pm", "tonight", "in the evening",
    )
    private val fraction = Regex("""^(\S+) (ونص|ونصف|والنص|وربع|والربع|إلا ربع|الا ربع|إلّا ربع)$""")
    private val halfWords = setOf("ونص", "ونصف", "والنص")
    private val quarterWords = setOf("وربع", "والربع")
    private val clockEn = Regex("""^(\d{1,2})(?::(\d{1,2}))?\s*(am|pm)?$""", RegexOption.IGNORE_CASE)
    private val halfPast = Regex("""^half past ([A-Za-z]+)$""", RegexOption.IGNORE_CASE)
    private val arabicHalf = Regex("""^(.+?) والنصف$""")
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
        "تاسعة" to "9",
        "عشرة" to "10",
        "رابعة" to "4",
        "حداشر" to "11",
        "اتناشر" to "12",
    )
    private val englishNumbers = mapOf(
        "one" to "1",
        "two" to "2",
        "three" to "3",
        "four" to "4",
        "five" to "5",
        "six" to "6",
        "seven" to "7",
        "eight" to "8",
        "nine" to "9",
        "ten" to "10",
    )

    /**
     * @param said The time words, such as "ستة ونص الصباح".
     * @return Hour (0-23) and minute as decimal text, or null when the words are not a clock time.
     */
    fun read(said: String): Pair<String, String>? {
        val lower = said.trim().lowercase()
        val part = (morningWords + eveningWords)
            .filter { word -> lower.endsWith(" $word") }
            .maxByOrNull { word -> word.length }
        val text = if (part == null) lower else lower.removeSuffix(part).trim()
        val base = fractionClock(text) ?: spokenClock(text) ?: digitClock(text) ?: return null
        val hour = base.first.toInt()
        val shifted = when {
            part == null -> hour
            part in eveningWords && hour < NOON -> hour + NOON
            part in morningWords && hour == NOON -> 0
            else -> hour
        }
        return shifted.toString() to base.second
    }

    /** @return The number for an English word from one to ten, or null. */
    fun englishNumber(word: String): Int? = englishNumbers[word.lowercase()]?.toInt()

    private fun fractionClock(text: String): Pair<String, String>? {
        val match = fraction.matchEntire(text) ?: return null
        val word = match.groupValues[1]
        val hour = (arabicHour(word) ?: word.toIntOrNull()?.toString())?.toInt() ?: return null
        val part = match.groupValues[2]
        return when (part) {
            in halfWords -> hour.toString() to HALF_HOUR_MINUTES.toString()
            in quarterWords -> hour.toString() to QUARTER.toString()
            else -> ((hour + LAST_HOUR) % HOURS_A_DAY).toString() to (HOUR_MINUTES - QUARTER).toString()
        }
    }

    private fun digitClock(text: String): Pair<String, String>? {
        val match = clockEn.matchEntire(text) ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val hour = hourOnClock(match.groupValues[1].toInt(), match.groupValues[MERIDIEM_GROUP])
        if (hour !in 0..LAST_HOUR || minute !in 0..LAST_MINUTE) return null
        return hour.toString() to minute.toString()
    }

    private fun spokenClock(text: String): Pair<String, String>? {
        val halfWord = halfPast.matchEntire(text)?.groupValues?.get(1)?.let { englishNumber(it) }
        if (halfWord != null) return halfWord.toString() to HALF_HOUR_MINUTES.toString()
        val arabicHalfHour = arabicHalf.matchEntire(text)?.groupValues?.get(1)?.let { arabicHour(it) }
        if (arabicHalfHour != null) return arabicHalfHour to HALF_HOUR_MINUTES.toString()
        val hour = arabicHour(text) ?: return null
        return hour to "0"
    }

    private fun arabicHour(text: String): String? = hourWords[text] ?: hourWords[text.removePrefix("ال")]

    private fun hourOnClock(hour: Int, meridiem: String): Int = when (meridiem.lowercase()) {
        "pm" -> if (hour < NOON) hour + NOON else hour
        "am" -> if (hour == NOON) 0 else hour
        else -> hour
    }
}
