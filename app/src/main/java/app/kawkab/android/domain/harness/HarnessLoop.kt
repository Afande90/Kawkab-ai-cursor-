package app.kawkab.android.domain.harness

/**
 * Applies the planner rule and the breakers. It does not call a model.
 */
object HarnessLoop {
    /**
     * Accepts one planner reply.
     *
     * An open breaker finishes the loop. An invalid reply counts as a failure.
     * An unknown tool is counted and is not treated as a successful step.
     */
    fun accept(raw: String, knownTools: Set<String>, breaker: CircuitBreaker): HarnessDecision {
        if (breaker.open) return HarnessDecision.Finish(STOPPED)
        val decision = HarnessParser.parse(raw)
        when (decision) {
            is HarnessDecision.Invalid -> breaker.recordFailure()
            is HarnessDecision.Act -> if (decision.tool !in knownTools) breaker.recordUnknown(decision.tool)
            is HarnessDecision.Reply, is HarnessDecision.Finish -> Unit
        }
        return if (breaker.open) HarnessDecision.Finish(STOPPED) else decision
    }

    private const val STOPPED: String = "Stopped. The breaker is open."
}
