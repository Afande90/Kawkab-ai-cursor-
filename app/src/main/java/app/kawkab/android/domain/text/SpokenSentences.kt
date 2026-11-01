package app.kawkab.android.domain.text

/**
 * Splits a reply into sentences so the voice can start on the first while the rest wait (task 28,
 * spec §7.3). Arabic and Latin end marks both count, and a line break ends a sentence too.
 */
object SpokenSentences {
    /**
     * @param text The reply.
     * @return Its sentences in order, trimmed, without empty ones. Markdown bullets and emphasis
     *   marks are dropped because they would be read out as symbols.
     */
    fun split(text: String): List<String> = SENTENCE_END.split(text.replace(DECORATION, ""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

/** After `.`, `!`, `?`, `؟` or `…` followed by space, or at a line break. */
private val SENTENCE_END = Regex("(?<=[.!?؟…])\\s+|\\n+")

/** Markdown marks a voice would read aloud: `*`, `_`, `#`, backticks and leading list dashes. */
private val DECORATION = Regex("[*_#`]|(?m)^\\s*[-•]\\s+")
