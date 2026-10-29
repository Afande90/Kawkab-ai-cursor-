package app.kawkab.android.domain.fastpath

/**
 * A fixed skill whose model writes only the words, never the tool call.
 */
sealed class TierTwoRecipe {
    /**
     * A reply the model writes.
     *
     * A named [contact] is sent as a message after the tap card. A blank contact
     * means the phrase was "reply to a message", so the written text is the answer
     * and nothing is sent.
     *
     * @property contact Who receives the message, or empty when nobody was named.
     */
    data class Reply(val contact: String) : TierTwoRecipe()

    /** A short reading of the messages already in this chat. */
    data object ReadLast : TierTwoRecipe()

    /** A reminder title taken from an attached screenshot, saved an hour ahead. */
    data object ScreenshotReminder : TierTwoRecipe()
}

/**
 * Matches the three skill phrases. Anything else is left for the agent loop.
 */
object TierTwoRecipes {
    private val replyNamedEn = Regex("""^(?:reply to|answer) (\S+)$""", RegexOption.IGNORE_CASE)
    private val replyNamedAr = Regex("""^(?:رد على|رد ل)\s*(\S+)$""")
    private val replyGenericEn = Regex("""^reply to a message$""", RegexOption.IGNORE_CASE)
    private val replyGenericAr = Regex("""^رد على رسالة$""")
    private val readEn = Regex("""^read my last messages$""", RegexOption.IGNORE_CASE)
    private val readAr = Regex("""^(?:اقرأ|اقرا) آخر (?:رسائلي|رسايلي)$""")
    private val reminderEn = Regex("""^set a reminder from (?:a |this |the )?screenshot$""", RegexOption.IGNORE_CASE)
    private val reminderAr = Regex("""^(?:ذكّرني|ذكرني) من (?:الصورة|لقطة الشاشة)$""")

    /**
     * The skill [text] asks for, or null when it is not one of the three.
     *
     * A reminder matches only when [hasImage] is true. The same words without a
     * picture stay on the agent loop.
     *
     * @param text The trimmed composer text.
     * @param hasImage Whether the send carries a picture.
     * @return The skill, or null.
     */
    fun match(text: String, hasImage: Boolean): TierTwoRecipe? {
        val line = text.trim()
        if (line.isEmpty()) return null
        val named = replyNamedEn.matchEntire(line) ?: replyNamedAr.matchEntire(line)
        if (named != null) return TierTwoRecipe.Reply(named.groupValues[1])
        if (replyGenericEn.matches(line) || replyGenericAr.matches(line)) {
            return TierTwoRecipe.Reply(contact = "")
        }
        if (readEn.matches(line) || readAr.matches(line)) return TierTwoRecipe.ReadLast
        val reminder = hasImage && (reminderEn.matches(line) || reminderAr.matches(line))
        return if (reminder) TierTwoRecipe.ScreenshotReminder else null
    }
}
