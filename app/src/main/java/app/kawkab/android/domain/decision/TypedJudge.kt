package app.kawkab.android.domain.decision

/** Decisions are structured separately from anything spoken by the language model. */
fun interface TypedJudge {
    /** Options are the complete allowlist; unavailable inference asks the supplied short question. */
    suspend fun choose(state: String, question: String, options: List<String>): DecisionResult

    /** Test default and conservative fallback. */
    companion object {
        /** Missing model or calibration never guesses a contact or app. */
        val NONE = TypedJudge { _, question, _ -> DecisionResult.Ask(question) }
    }
}
