package app.kawkab.android.domain.decision

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Carries engine-owned session identity through nested native decision calls.
 * @property sessionId Engine-owned session, never model-controlled arguments.
 */
class DecisionTrace(val sessionId: String) : AbstractCoroutineContextElement(Key) {
    /** Coroutine lookup key, preserved through dispatcher changes. */
    companion object Key : CoroutineContext.Key<DecisionTrace>
}

/** One model attempt, including unavailable or invalid output.
 * @property sessionId Owning session when available.
 * @property state The local decision input.
 * @property question The structured choice question.
 * @property options The complete allowlist of choices.
 * @property logits Actual verdict-slot differences; absent on load failure.
 * @property result Selected option or spoken clarification.
 * @property reason Model, unavailable or failure classification.
 * @property elapsedMs Time spent in this attempt.
 */
data class DecisionRecord(
    val sessionId: String?,
    val state: String,
    val question: String,
    val options: List<String>,
    val logits: DoubleArray?,
    val result: DecisionResult,
    val reason: String,
    val elapsedMs: Long,
)

/** Private local decision evidence, never telemetry. */
interface DecisionAudit {
    /** Records the next owner turn and resolves earlier questions or unfinished selections. */
    fun begin(sessionId: String, input: String)

    /** False means storage failed; automatic action must ask instead. */
    fun decision(record: DecisionRecord): Boolean

    /** Whether this owner turn contains an automatic model selection needing a cancel window. */
    fun automatic(sessionId: String): Boolean = false

    /** The actual gate observation, including denial, failure and cancellation. */
    fun outcome(sessionId: String, tool: String, observation: String, error: String? = null)

    /** Defaults for test and non-Android callers. */
    companion object {
        /** Test and non-Android default; production uses the private file implementation. */
        val NONE: DecisionAudit = object : DecisionAudit {
            override fun begin(sessionId: String, input: String) = Unit
            override fun decision(record: DecisionRecord): Boolean = true
            override fun outcome(sessionId: String, tool: String, observation: String, error: String?) = Unit
        }
    }
}

/** Append-only JSON lines. No automatic deletion or upload; decision IDs join later observations. */
class FileDecisionAudit(private val file: File, private val clock: () -> Long = System::currentTimeMillis) :
    DecisionAudit {
    private val pending = linkedMapOf<String, MutableList<String>>()
    private val autoSessions = mutableSetOf<String>()

    @Synchronized
    override fun begin(sessionId: String, input: String) {
        finish(sessionId, "owner_follow_up", JSONObject().put("answer", input))
    }

    @Synchronized
    override fun decision(record: DecisionRecord): Boolean {
        val sessionId = record.sessionId
        val logits = record.logits
        val id = UUID.randomUUID().toString()
        val probabilities = logits?.takeIf { it.size == record.options.size }?.let(DecisionPolicy::probabilities)
        val event = JSONObject().put("kind", "decision").put("id", id).put("session", sessionId ?: JSONObject.NULL)
            .put("state", record.state).put("question", record.question).put("options", JSONArray(record.options))
            .put("logits", JSONArray(logits?.map { if (it.isFinite()) it else JSONObject.NULL } ?: emptyList<Any>()))
            .put("confidence", probabilities?.maxOrNull() ?: JSONObject.NULL)
            .put(
                "threshold",
                DecisionPolicy.CONFIDENCE_THRESHOLD,
            ).put("reason", record.reason).put("latency_ms", record.elapsedMs)
            .put("selected", (record.result as? DecisionResult.Chosen)?.option ?: JSONObject.NULL)
            .put("outcome", if (record.result is DecisionResult.Chosen) "selected" else "asked")
        if (!append(event)) return false
        if (!sessionId.isNullOrBlank()) {
            if (sessionId !in pending &&
                pending.size >= MAX_SESSIONS
            ) {
                finish(pending.keys.first(), "trace_limit", JSONObject())
            }
            if (record.result is DecisionResult.Chosen) autoSessions += sessionId
            val ids = pending.getOrPut(sessionId) { mutableListOf() }
            ids += id
            if (ids.size > MAX_PENDING) {
                append(
                    JSONObject().put("kind", "outcome")
                        .put("decision_id", ids.removeAt(0)).put("status", "trace_limit"),
                )
            }
        }
        return true
    }

    @Synchronized
    override fun automatic(sessionId: String): Boolean = sessionId in autoSessions

    @Synchronized
    override fun outcome(sessionId: String, tool: String, observation: String, error: String?) {
        val ids = pending[sessionId].orEmpty()
        append(
            JSONObject().put("kind", "tool_outcome").put("session", sessionId)
                .put("decision_ids", JSONArray(ids)).put("tool", tool).put("observation", observation)
                .put("error", error ?: JSONObject.NULL),
        )
    }

    private fun finish(sessionId: String, status: String, details: JSONObject) {
        autoSessions -= sessionId
        for (id in pending.remove(sessionId).orEmpty()) {
            append(
                JSONObject().put(
                    "kind",
                    "outcome",
                ).put("decision_id", id).put("session", sessionId).put("status", status).put("details", details),
            )
        }
    }

    private fun append(event: JSONObject): Boolean = runCatching {
        file.parentFile?.mkdirs()
        file.appendText(event.put("at_ms", clock()).toString() + "\n")
        true
    }.getOrDefault(false)

    private companion object {
        const val MAX_SESSIONS = 64
        const val MAX_PENDING = 32
    }
}
