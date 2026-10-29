package app.kawkab.android.domain.harness

/**
 * Whether a biometric check is required on top of the outbound tap.
 * The tap is never replaced by this check.
 */
object BiometricRisk {
    /**
     * True only when the tool tier and the words both say the step is risky, and the tap already happened.
     * A missing tap returns false so the caller still has to show the tap card.
     */
    fun ask(toolRisky: Boolean, wordsRisky: Boolean, tapApproved: Boolean): Boolean =
        toolRisky && wordsRisky && tapApproved

    /**
     * True when [text] names an outbound action in English or Arabic.
     */
    fun wordsLookOutbound(text: String): Boolean =
        englishWords.containsMatchIn(text) || arabicWords.containsMatchIn(text)

    private val englishWords: Regex = Regex("(?i)(?:^|\\s)(send|call|pay|share|post|submit|delete|transfer)(?:\\s|$)")
    private val arabicWords: Regex = Regex("ارسال|إرسال|ابعت|دفع")
}
