package app.kawkab.android.domain.fastpath

/**
 * Cleans a typed command before the grammar reads it (task 23h).
 */
object CommandText {
    /**
     * Removes emoji, surrounding quotes and trailing punctuation, so a pasted `Set an alarm for 6"`
     * or "set an alarm for 6 ⏰!" still matches (task 23h).
     *
     * @param raw The typed line.
     * @return The line without that decoration.
     */
    fun stripDecoration(raw: String): String {
        var text = emoji.replace(raw, " ").trim()
        while (text.isNotEmpty()) {
            val trimmed = text.trimStart { it in quotes }.trimEnd { it in quotes || it in trailing }.trim()
            if (trimmed == text) break
            text = trimmed
        }
        return text
    }

    private val quotes: Set<Char> = setOf('"', '\'', '“', '”', '«', '»', '‘', '’', '„', '`')
    private val trailing: Set<Char> = setOf('.', '!', '?', '،', '؟', ',', '؛', ';', '…')
    private val emoji: Regex = Regex(
        "[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}\\x{2B00}-\\x{2BFF}\\x{2300}-\\x{23FF}]+",
    )
}
