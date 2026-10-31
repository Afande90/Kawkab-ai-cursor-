package app.kawkab.android.domain.text

/**
 * The language and dialect a message is in, and a guard that keeps one reply in it (task 23g).
 */
enum class ReplyLanguage {
    /** Sudanese Arabic. */
    SUDANESE,

    /** Modern Standard Arabic. */
    MSA,

    /** English. */
    ENGLISH,
    ;

    /** Detection and the label guard. */
    companion object {
        private val arabicLetter = Regex("[\\u0600-\\u06FF]")
        private val latinLetter = Regex("[A-Za-z]")
        private val wordSplit = Regex("[^\\p{L}]+")

        /** Words that mark Sudanese Arabic rather than the standard language. */
        private val sudaneseWords = setOf(
            "زول", "شنو", "هسي", "هسع", "عايز", "عاوز", "داير", "دايرة", "ياخ", "كدة", "كده", "ساي",
            "دا", "دي", "ياتو", "بتاع", "منو", "وين", "قول", "شديد", "حسي", "جنس", "ياهو", "كيفن", "اتلم",
        )

        /** Names a small model writes before each translation, as "English: …". */
        private val labels = setOf(
            "modern standard arabic", "standard arabic", "sudanese arabic", "sudanese", "arabic", "english", "msa",
            "العربية الفصحى", "الفصحى", "العامية السودانية", "السودانية", "العربية", "الإنجليزية", "الانجليزية",
        )

        private const val LABEL_MAX = 24

        /** The text after a language label at the start of [line], or `null` when there is none. */
        private fun afterLabel(line: String): String? {
            val colon = line.indexOfFirst { it == ':' || it == '：' }
            if (colon < 0 || colon > LABEL_MAX) return null
            val name = line.substring(0, colon).replace("*", "").trim().lowercase()
            return if (name in labels) line.substring(colon + 1).trim() else null
        }

        /**
         * @param text A message.
         * @return Its language: Arabic script decides Arabic, and Sudanese words decide the dialect.
         */
        fun of(text: String): ReplyLanguage {
            val arabic = arabicLetter.findAll(text).count()
            val latin = latinLetter.findAll(text).count()
            if (arabic <= latin) return ENGLISH
            val words = text.split(wordSplit).map { it.removePrefix("و") }
            return if (words.any { it in sudaneseWords }) SUDANESE else MSA
        }

        /**
         * Keeps one reply when the model wrote the same answer in several languages, each under a
         * label: the part in the user's script, without its label. A reply with no labels is
         * returned as it was.
         *
         * @param reply The model's reply.
         * @param userText What the user wrote.
         * @return The reply in the user's language, with no language label.
         */
        fun keepOne(reply: String, userText: String): String {
            val lines = reply.lines()
            if (lines.none { afterLabel(it) != null }) return reply
            val sections = mutableListOf<StringBuilder>()
            for (line in lines) {
                val rest = afterLabel(line)
                if (rest != null || sections.isEmpty()) sections += StringBuilder()
                sections.last().appendLine(rest ?: line)
            }
            val wanted = of(userText)
            val texts = sections.map { it.toString().trim() }.filter { it.isNotEmpty() }
            val arabicWanted = wanted != ENGLISH
            return texts.firstOrNull { (of(it) != ENGLISH) == arabicWanted } ?: texts.firstOrNull().orEmpty()
        }
    }
}
