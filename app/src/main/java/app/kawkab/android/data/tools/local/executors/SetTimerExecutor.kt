package app.kawkab.android.data.tools.local.executors

import app.kawkab.android.domain.models.RegisteredTools
import app.kawkab.android.domain.models.ToolExecutionContext
import app.kawkab.android.domain.repositories.LocalToolExecutor
import app.kawkab.android.domain.safety.ActionRunner
import app.kawkab.android.hands.PhoneHands
import app.kawkab.android.hands.ToolArgs
import javax.inject.Inject

/**
 * Starts a countdown in the clock app.
 */
class SetTimerExecutor @Inject constructor(private val hands: PhoneHands, private val runner: ActionRunner) :
    LocalToolExecutor {
    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String, context: ToolExecutionContext): String {
        val json = ToolArgs.parse(arguments) ?: return "Error: arguments are not a JSON object."
        val seconds = ToolArgs.int(json, "seconds") ?: return "Error: missing seconds."
        if (seconds <= 0) return "Error: seconds must be positive."
        return runner.run(
            asked = "",
            ran = TOOL_NAME,
            risk = RegisteredTools.risk(TOOL_NAME),
            entryPoint = "tool",
            confirmed = true,
        ) { hands.setTimer(seconds) }
    }

    companion object {
        /** Tool name as exposed to the model and the DI map. */
        const val TOOL_NAME = "set_timer"

        /** When to start a timer. */
        const val DESCRIPTION: String = "Starts a timer in the clock app. The length is in seconds."

        /** JSON arguments. */
        val PARAMETERS: String = """
            {"type":"object","properties":{"seconds":{"type":"integer"}},"required":["seconds"]}
        """.trimIndent()
    }
}
