package app.kawkab.android.hands

/**
 * A row of the phone book before matching.
 *
 * @property name Display name.
 * @property phone Number as stored.
 * @property label Phone type label, such as "Mobile", or empty.
 */
data class ContactEntry(val name: String, val phone: String, val label: String = "")

/**
 * Matches a spoken name against the phone book (task 23j).
 *
 * Every word the owner said must be a whole name word or the start of one, so "him" never
 * matches "Ibrahim" and "abdalla" matches "Abdallah". Numbers that are the same phone written
 * twice merge into one. The owner's own numbers are never offered.
 */
object ContactMatcher {
    /** At most this many choices are shown. */
    const val MAX_CHOICES: Int = 5

    private const val PHONE_KEY_DIGITS = 9
    private const val LAST_DIGITS = 4

    private val fillers = setOf("the", "my", "to", "a", "call", "ring", "text", "سيد", "يا")
    private val pronouns =
        setOf("him", "her", "he", "she", "them", "هو", "هي", "ليه", "ليها", "له", "لها", "معاه", "معاها")
    private val diacritics = Regex("[\\u064B-\\u0652\\u0640]")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Whether [text] is only a pronoun, which means the last resolved contact.
     *
     * @param text What the owner said for the contact.
     * @return `true` for him, her, هو, هي and the like.
     */
    fun isPronoun(text: String): Boolean {
        val words = tokens(text).filterNot { it in fillers }
        return words.size == 1 && words.single() in pronouns
    }

    /**
     * Matches [spoken] against [entries].
     *
     * @param spoken What the owner said.
     * @param entries The phone book rows to search.
     * @param ownNumbers The owner's own numbers, never returned.
     * @return Matches, the closest first, one per phone number.
     */
    fun match(
        spoken: String,
        entries: List<ContactEntry>,
        ownNumbers: Collection<String> = emptyList(),
    ): List<ContactMatch> {
        val query = tokens(spoken).filterNot { it in fillers }.map(::stripArticle)
        if (query.isEmpty()) return emptyList()
        val own = ownNumbers.map(::phoneKey).filter { it.isNotEmpty() }.toSet()
        return entries
            .asSequence()
            .filter { phoneKey(it.phone) !in own }
            .mapNotNull { entry -> score(query, entry)?.let { entry to it } }
            .sortedWith(compareByDescending<Pair<ContactEntry, Int>> { it.second }.thenBy { it.first.name })
            .distinctBy { phoneKey(it.first.phone) }
            .map { (entry, _) -> ContactMatch(entry.name, entry.phone, entry.label) }
            .toList()
    }

    /**
     * The key two spellings of one number share: its last 9 digits, so `+249 912 345 678`,
     * `00249912345678` and `0912345678` are one number.
     *
     * @param phone A number as stored.
     * @return The key, or the digits when there are fewer than 9.
     */
    fun phoneKey(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length > PHONE_KEY_DIGITS) digits.takeLast(PHONE_KEY_DIGITS) else digits
    }

    /**
     * The last four digits, shown on a chip instead of the whole number.
     *
     * @param phone A number as stored.
     * @return Up to four digits.
     */
    fun lastDigits(phone: String): String = phone.filter { it.isDigit() }.takeLast(LAST_DIGITS)

    /**
     * Lower-case words with Arabic letter forms unified and diacritics removed.
     *
     * @param text Any name or sentence.
     * @return Its words.
     */
    fun tokens(text: String): List<String> = normalize(text).split(separators).filter { it.isNotBlank() }

    private fun normalize(text: String): String = text.lowercase()
        .replace(diacritics, "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ة', 'ه').replace('ى', 'ي')

    private fun stripArticle(word: String): String =
        if (word.length > ARTICLE_MIN_LENGTH && word.startsWith("ال")) word.substring(2) else word

    /** Higher is closer; `null` when a spoken word is not a name word or the start of one. */
    private fun score(query: List<String>, entry: ContactEntry): Int? {
        val words = tokens(entry.name).flatMap { listOf(it, stripArticle(it)) }
        var score = 0
        for (spoken in query) {
            score += when {
                words.any { it == spoken } -> EXACT_WORD
                words.any { it.startsWith(spoken) } -> WORD_START
                else -> return null
            }
        }
        if (tokens(entry.name).size == query.size) score += WHOLE_NAME
        return score
    }

    private const val ARTICLE_MIN_LENGTH = 3
    private const val EXACT_WORD = 3
    private const val WORD_START = 2
    private const val WHOLE_NAME = 1
}
