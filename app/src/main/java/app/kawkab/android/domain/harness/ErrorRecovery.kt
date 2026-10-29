package app.kawkab.android.domain.harness

/**
 * How a failed step is treated. Each tier has its own recovery.
 */
enum class ErrorTier {
    /** A timeout, an empty screen, or a momentary miss. Retry the same step once. */
    TRANSIENT,

    /** The tool was known and the arguments were not. Ask the planner for one new decision. */
    ARGUMENTS,

    /** The planner named a tool this app does not have. Do not run it. */
    UNKNOWN_TOOL,

    /** Safety, a denial, or anything that should not be retried. Stop and tell the owner. */
    STOP,
}

/**
 * Classifies a failure sentence and names the recovery for that tier.
 */
object ErrorRecovery {
    /**
     * The tier for [message]. An empty sentence is transient.
     */
    fun classify(message: String): ErrorTier {
        val text = message.lowercase()
        return when {
            text.isBlank() || text.contains("timeout") || text.contains("empty") || text.contains("unavailable") ->
                ErrorTier.TRANSIENT
            text.contains("unknown tool") -> ErrorTier.UNKNOWN_TOOL
            text.contains("argument") || text.contains("missing") || text.contains("invalid") ->
                ErrorTier.ARGUMENTS
            else -> ErrorTier.STOP
        }
    }

    /**
     * What the caller should do for [tier].
     */
    fun recovery(tier: ErrorTier): String = when (tier) {
        ErrorTier.TRANSIENT -> "Retry the same step once."
        ErrorTier.ARGUMENTS -> "Send the error back to the planner and ask for one new decision."
        ErrorTier.UNKNOWN_TOOL -> "Do not run it. Count it toward the unknown-tool breaker."
        ErrorTier.STOP -> "Stop and tell the owner."
    }
}
