package app.kawkab.android.domain.harness

/**
 * One typed planner decision. Plain text is not a decision.
 */
sealed interface HarnessDecision {
    /**
     * Run one tool. A turn may contain only this one call.
     *
     * @property tool Hands tool name.
     * @property args Arguments for that tool.
     */
    data class Act(val tool: String, val args: Map<String, String>) : HarnessDecision

    /**
     * Speak to the owner and do not call a tool.
     *
     * @property text The reply.
     */
    data class Reply(val text: String) : HarnessDecision

    /**
     * The plan is done.
     *
     * @property text What to tell the owner.
     */
    data class Finish(val text: String) : HarnessDecision

    /**
     * The planner did not return one typed decision.
     */
    data object Invalid : HarnessDecision
}

/**
 * Reads one planner line. Anything that is not a single Act, Reply, or Finish is invalid.
 */
object HarnessParser {
    /**
     * Parses [raw]. Two tool calls, or any plain sentence, is [HarnessDecision.Invalid].
     */
    fun parse(raw: String): HarnessDecision {
        val lines = raw.trim().lines().filter { it.isNotBlank() }
        if (lines.size != 1) return HarnessDecision.Invalid
        val line = lines[0]
        return when {
            line.startsWith(ACT) -> act(line.removePrefix(ACT).trim())
            line.startsWith(REPLY) -> text(line.removePrefix(REPLY).trim()) { HarnessDecision.Reply(it) }
            line.startsWith(FINISH) -> text(line.removePrefix(FINISH).trim()) { HarnessDecision.Finish(it) }
            else -> HarnessDecision.Invalid
        }
    }

    private fun act(body: String): HarnessDecision {
        val parts = body.split(" ").filter { it.isNotEmpty() }
        val tool = parts.firstOrNull().orEmpty()
        if (tool.isEmpty() || tool.contains("=")) return HarnessDecision.Invalid
        val args = linkedMapOf<String, String>()
        parts.drop(1).forEach { token ->
            val cut = token.indexOf("=")
            if (cut <= 0 || cut == token.lastIndex) return HarnessDecision.Invalid
            args[token.substring(0, cut)] = token.substring(cut + 1)
        }
        return HarnessDecision.Act(tool, args)
    }

    private fun text(body: String, build: (String) -> HarnessDecision): HarnessDecision =
        if (body.isEmpty()) HarnessDecision.Invalid else build(body)

    private const val ACT: String = "ACT "
    private const val REPLY: String = "REPLY "
    private const val FINISH: String = "FINISH "
}
