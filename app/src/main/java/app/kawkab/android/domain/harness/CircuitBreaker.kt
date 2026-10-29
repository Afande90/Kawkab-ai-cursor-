package app.kawkab.android.domain.harness

/**
 * Stops the loop after five failures in a row, or after the same unknown tool three times.
 */
class CircuitBreaker {
    private var failures: Int = 0
    private val unknown = mutableMapOf<String, Int>()

    /**
     * True once either breaker has opened. It stays open.
     */
    var open: Boolean = false
        private set

    /**
     * Counts one failure. Five in a row opens the breaker. A later [recordSuccess] clears the row.
     *
     * @return True when the breaker is open after this failure.
     */
    fun recordFailure(): Boolean {
        failures += 1
        if (failures >= FAILURES_IN_A_ROW) open = true
        return open
    }

    /**
     * Clears the failure row. Unknown-tool counts stay.
     */
    fun recordSuccess() {
        failures = 0
    }

    /**
     * Counts one request for an unknown [tool]. The third request for that same name opens the breaker.
     *
     * @return True when the breaker is open after this request.
     */
    fun recordUnknown(tool: String): Boolean {
        val count = (unknown[tool] ?: 0) + 1
        unknown[tool] = count
        if (count >= SAME_UNKNOWN_TOOL) open = true
        return open
    }

    private companion object {
        const val FAILURES_IN_A_ROW: Int = 5
        const val SAME_UNKNOWN_TOOL: Int = 3
    }
}
