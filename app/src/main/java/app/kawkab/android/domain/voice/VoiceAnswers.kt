package app.kawkab.android.domain.voice

/**
 * Short spoken answers in a hands-free conversation (task 29): "undo" after a local action, and
 * "yes" or "no" to a confirmation card.
 */
object VoiceAnswers {
    /** What a spoken reply to a card means. */
    enum class Answer {
        /** Yes, do it. */
        YES,

        /** No, cancel it. */
        NO,

        /** Anything else. */
        UNCLEAR,
    }

    /** What happens to a waiting card after a spoken answer. */
    enum class Decision {
        /** Approve the card, as a tap on its primary button would. */
        APPROVE,

        /** Deny the card. */
        DENY,

        /** Keep waiting for a tap. */
        NEED_TAP,
    }

    /**
     * @param text What the recognizer heard.
     * @return `true` for "undo", "cancel that", "تراجع", "الغيها" and similar, alone.
     */
    fun isUndo(text: String): Boolean = normalized(text) in UNDO

    /**
     * @param text What the recognizer heard.
     * @return The answer, judged on the whole utterance so "yes but not now" is not a yes.
     */
    fun answer(text: String): Answer = when (normalized(text)) {
        in YES -> Answer.YES
        in NO -> Answer.NO
        else -> Answer.UNCLEAR
    }

    /**
     * The safety rule of spec §0.4.3: a spoken yes approves an outbound action only when the owner
     * turned on "Confirm by voice" and the phone is in use, screen on and unlocked. A yes with the
     * phone locked never sends. A spoken no always cancels, because cancelling is safe.
     *
     * @param answer The spoken answer.
     * @param voiceConfirmOn The owner's "Confirm by voice" setting (off by default).
     * @param screenOn Whether the screen is on.
     * @param locked Whether the phone is locked.
     * @return What to do with the card.
     */
    fun decide(answer: Answer, voiceConfirmOn: Boolean, screenOn: Boolean, locked: Boolean): Decision = when {
        answer == Answer.NO -> Decision.DENY
        answer == Answer.YES && voiceConfirmOn && screenOn && !locked -> Decision.APPROVE
        else -> Decision.NEED_TAP
    }

    private fun normalized(text: String): String = text.lowercase()
        .replace(PUNCTUATION, " ")
        .split(' ')
        .filter { it.isNotBlank() && it != WAKE_EN && it != WAKE_AR }
        .joinToString(" ")

    private const val WAKE_EN = "kawkab"
    private const val WAKE_AR = "كوكب"

    private val UNDO = setOf(
        "undo", "undo that", "undo it", "cancel that", "take that back", "take it back",
        "تراجع", "الغيها", "الغيه", "الغي ده", "الغي دي", "ألغيها", "ألغيه", "رجعها", "خلاص الغيها",
    )

    private val YES = setOf(
        "yes", "yeah", "yep", "yes please", "do it", "go ahead", "confirm", "ok", "okay",
        "نعم", "ايوه", "أيوه", "ايوا", "أيوا", "اي", "تمام", "اوكي", "أكد", "اكد", "ارسل", "اتصل",
    )

    private val NO = setOf(
        "no", "nope", "cancel", "stop", "don't", "dont", "no thanks",
        "لا", "لأ", "لاء", "الغي", "ألغِ", "بلاش", "خلاص", "وقف",
    )
}

/** Punctuation that recognizers add: dropped before matching. */
private val PUNCTUATION = Regex("[\\p{Punct}،؛؟!.…]+")
