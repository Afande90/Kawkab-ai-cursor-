package app.kawkab.android.domain.memory

/** Talorys history: a similarity score cannot silently rewrite a single-valued life fact. */
object MemoryHistoryRules {
    private val topics = listOf(
        listOf("home", "house", "live", "lives", "بيت", "بيتي", "ساكن", "منزل"),
        listOf("job", "work", "employer", "وظيفة", "شغل", "شغلي"),
        listOf("car", "vehicle", "عربية", "العربية", "سيارة", "السيارة"),
        listOf("phone", "handset", "تلفون", "تلفوني", "هاتف"),
    )
    private val changes = Regex("\\b(sold|moved|changed|replaced|no longer|instead)\\b|بعت|اتنقلت|ما عاد|غيرت")
    private val uncertain = Regex("\\b(maybe|might|perhaps|unsure|possibly|if)\\b|يمكن|احتمال|مش متأكد|لو ")

    /** These topics keep both conflicting statements unless the owner clearly states a change. */
    fun needsExplicitChange(old: String, incoming: String): Boolean = topics.any { words ->
        words.any { word -> contains(old, word) } && words.any { word -> contains(incoming, word) }
    }

    /** A date is never inferred here; the exact statements remain in the existing history store. */
    fun explicitChange(old: String, incoming: String): Boolean = needsExplicitChange(old, incoming) &&
        changes.containsMatchIn(incoming.lowercase()) &&
        !uncertain.containsMatchIn(incoming.lowercase())

    private fun contains(text: String, word: String): Boolean =
        Regex("(?<![\\p{L}])${Regex.escape(word)}(?![\\p{L}])", RegexOption.IGNORE_CASE).containsMatchIn(text)
}
