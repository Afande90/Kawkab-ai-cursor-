package app.kawkab.android.domain.constants

/**
 * Engine-level log-size constants consumed by
 * [app.kawkab.android.domain.engine.GraphExecutionEngine] and the
 * `*NodeExecutor` implementations.
 *
 * These values are intentionally **not** user-tunable: they cap how much
 * text a node dumps into the agent log to keep `Timber` callsites under
 * control.
 *
 * The engine waits nowhere to pace the screen. It once paused 500 ms before
 * every live node and a second after cloud, summary and system nodes; neither
 * pause had a measured reason, both slowed every run — background runs, which
 * have no screen, included — and the states they held back reach the UI
 * through a buffered flow that drops none. A screen that needs a minimum time
 * on a stage paces it itself, in presentation.
 */
object PipelineExecutionDefaults {
    /**
     * Maximum number of characters of a node's input or output that is
     * mirrored into the structured `Timber` log. Larger payloads are
     * truncated with a `…` suffix to keep logcat readable.
     */
    const val NODE_IO_LOG_CHAR_LIMIT: Int = 1_000
}
