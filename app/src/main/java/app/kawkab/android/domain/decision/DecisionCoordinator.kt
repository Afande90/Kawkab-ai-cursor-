package app.kawkab.android.domain.decision

import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Two explicit verdict calls keep generated speech out of action selection. */
class DecisionCoordinator @Inject constructor(private val judge: TypedJudge) {
    /** A high-confidence chat needs no action verdict; a command does. */
    suspend fun route(text: String, sessionId: String = ""): DecisionResult = withContext(DecisionTrace(sessionId)) {
        val kind = judge.choose(text, "Is this a command or conversation?", listOf("command", "chat"))
        if (kind !is DecisionResult.Chosen) return@withContext DecisionResult.Ask(INTENT_QUESTION)
        if (kind.option == "chat") return@withContext kind
        if (kind.option != "command") return@withContext DecisionResult.Ask(INTENT_QUESTION)
        val action = judge.choose(
            text,
            "Are the requested action and necessary details clear enough to act?",
            listOf("act", "ask"),
        )
        if (action is DecisionResult.Chosen && action.option == "act") {
            action
        } else {
            DecisionResult.Ask("What details should I use for that action?")
        }
    }

    /** A short spoken clarification, with no buttons. */
    companion object {
        /** Spoken classification clarification. */
        const val INTENT_QUESTION = "Is that a command or a question?"
    }
}

/** The dispatch gate honors the owner's conversational intent independently of generated tool calls. */
@Singleton
class DecisionIntents @Inject constructor() {
    private val allowed = ConcurrentHashMap<String, Boolean>()

    /** A new owner turn replaces the preceding turn's intent. No content is persisted. */
    fun remember(session: String, mayAct: Boolean) {
        allowed[session] = mayAct
    }

    /** An unclassified turn uses the existing structured planner fallback. */
    fun mayAct(session: String): Boolean = allowed[session] != false
}
