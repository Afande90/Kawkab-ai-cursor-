package app.kawkab.android.domain.fastpath

/**
 * Refuses a spoken or typed line that asks Kawkab to move money, reveal a secret, or delete in bulk,
 * before any model reads it. The screen denylist still guards every tap; this stops the request at
 * the words, so no model can be talked into it (brain lab rounds 2-5: Gemma 4 E2B turned two money
 * transfers into a chat reply and an "open the bank app" action).
 */
object SpokenRefusal {
    private val ignoreCase = setOf(RegexOption.IGNORE_CASE)

    private val currencyWords = listOf("درهم", "دراهم", "دولار", "جنيه", "ريال", "يورو", "قروش", "فلوس")
    private val currencyEn = Regex(
        """(\baed\b|dirhams?|dollars?|euros?|pounds?|riyals?|\bmoney\b|\$\s?\d|\d\s?\$)""",
        ignoreCase,
    )
    private val moveVerb = Regex(
        """(حو+ّ?ل|تحويل|ادفع|سدد|سدّد|اشحن|\btransfer\b|\bwire\b|\bpay\b|send money)""",
        ignoreCase,
    )
    private val moneyPlace = Regex(
        """(البنك|بنك|حسابي|الحساب|البطاقة|بطاقتي|الكرت|\bbank\b|\baccount\b|\bcard\b)""",
        ignoreCase,
    )
    private val sendVerb = Regex("""^(ابعت|ابعث|رسل|ارسل|أرسل|send|give)\s""", ignoreCase)
    private val secretWords = listOf(
        "باسورد", "باسوورد", "كلمة سر", "كلمة السر", "كلمة المرور", "رمز سري",
        "الرمز السري",
    )
    private val secretEn = Regex("""(\bpasswords?\b|\bpasscode\b|\bpin\b|\bcvv\b|\botp\b)""", ignoreCase)
    private val wipeVerb = Regex("""(امسح|إمسح|احذف|إحذف|مسح|حذف|\bdelete\b|\berase\b|\bwipe\b)""", ignoreCase)
    private val everything = Regex("""((^|\s|و)(كل|كلها|جميع)(\s|$)|\ball\b|\bevery\b|\beverything\b)""", ignoreCase)

    /** Said back when a line asks to move money. */
    const val MONEY: String = "I don't move money. Please do that yourself in your bank app."

    /** Said back when a line asks for a password, PIN or code. */
    const val SECRET: String = "I don't read out or send passwords, PINs or codes."

    /** Said back when a line asks to delete in bulk. */
    const val WIPE: String = "I won't delete everything at once. Delete what you want yourself."

    /**
     * @param raw The owner's line.
     * @return The sentence to say instead of acting, or null when the line is not one of these.
     */
    fun reason(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return when {
            secretWords.any { it in text } || secretEn.containsMatchIn(text) -> SECRET
            movesMoney(text) -> MONEY
            wipeVerb.containsMatchIn(text) && everything.containsMatchIn(text) -> WIPE
            else -> null
        }
    }

    private fun movesMoney(text: String): Boolean {
        val hasCurrency = hasCurrency(text)
        val moves = moveVerb.containsMatchIn(text)
        if (moves && (hasCurrency || moneyPlace.containsMatchIn(text))) return true
        // "ابعت ميتين درهم لعلي" / "send 100 dollars to Omar": the amount is what is being sent.
        val opening = text.split(Regex("""\s+""")).take(OPENING_WORDS).joinToString(" ")
        return sendVerb.containsMatchIn(text) && hasCurrency(opening)
    }

    private fun hasCurrency(text: String): Boolean =
        currencyWords.any { it in text } || currencyEn.containsMatchIn(text)

    private const val OPENING_WORDS = 4
}
