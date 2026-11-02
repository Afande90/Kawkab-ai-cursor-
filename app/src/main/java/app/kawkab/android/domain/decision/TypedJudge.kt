package app.kawkab.android.domain.decision

/** Decisions are structured separately from anything spoken by the language model. */
fun interface TypedJudge {
    /** Options are the complete allowlist; unavailable inference asks the supplied short question. */
    suspend fun choose(state: String, question: String, options: List<String>): DecisionResult

    /** Starts a new owner turn so follow-up answers and abandoned choices can be logged. */
    fun begin(sessionId: String, input: String) = Unit

    /** True when the native runtime and exact installed model are available. */
    suspend fun ready(): Boolean = false

    /** Test default and conservative fallback. */
    companion object {
        /** Missing model never guesses a contact or app. */
        val NONE = TypedJudge { _, question, _ -> DecisionResult.Ask(question) }
    }
}
