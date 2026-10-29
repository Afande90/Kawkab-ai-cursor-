package app.kawkab.android.domain.safety

import app.kawkab.android.domain.models.ToolRisk

/**
 * The only way a hands action runs.
 *
 * Order: denylist on the current screen, denylist on the intent, then the outbound tap.
 * A refusal is logged and the body is not called. A completed action is logged with the
 * confirmation that was given. The log stays on the device.
 */
class ActionRunner(
    private val denylist: ActionDenylist,
    private val log: ActivityLog,
    private val screen: ScreenWatch,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Runs [perform] when the screen, the intent, and the outbound rule allow it.
     * Returns the result, a block, or the sentence that a tap is still required.
     */
    fun run(
        asked: String,
        ran: String,
        risk: ToolRisk,
        entryPoint: String,
        confirmed: Boolean,
        intentPackage: String? = null,
        intentUri: String? = null,
        perform: () -> String,
    ): String {
        val blocked = denylist.inspectScreen(screen.latest)
            ?: denylist.inspectIntent(intentPackage, intentUri)
        if (blocked != null) {
            logRefusal(asked, ran, entryPoint, blocked)
            return "Blocked (${blocked.layer}): ${blocked.why}"
        }
        if (risk == ToolRisk.OUTBOUND && !confirmed) {
            return NEEDS_TAP
        }
        val result = perform()
        val confirmation = if (risk == ToolRisk.OUTBOUND) "tap" else "none"
        log.record(ActivityLogEntry(asked, ran, entryPoint, result, confirmation, clock()))
        return result
    }

    private fun logRefusal(asked: String, ran: String, entryPoint: String, blocked: BlockReason) {
        log.record(
            ActivityLogEntry(
                asked = asked,
                ran = ran,
                entryPoint = entryPoint,
                result = "blocked: ${blocked.why}",
                confirmation = blocked.layer.name,
                atEpochMs = clock(),
            ),
        )
    }

    /** The sentence returned when a tap is still required. */
    companion object {
        /** Returned when an outbound action has not been confirmed with a tap. */
        const val NEEDS_TAP: String = "Needs a tap before this outbound action can run."
    }
}
