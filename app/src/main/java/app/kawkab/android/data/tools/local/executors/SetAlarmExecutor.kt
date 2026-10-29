package app.kawkab.android.data.tools.local.executors

import app.kawkab.android.domain.models.RegisteredTools
import app.kawkab.android.domain.models.ToolExecutionContext
import app.kawkab.android.domain.repositories.LocalToolExecutor
import app.kawkab.android.domain.safety.ActionRunner
import app.kawkab.android.hands.PhoneHands
import app.kawkab.android.hands.ToolArgs
import javax.inject.Inject

/**
 * Sets an alarm through the clock app.
 */
class SetAlarmExecutor @Inject constructor(private val hands: PhoneHands, private val runner: ActionRunner) :
    LocalToolExecutor {
    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String, context: ToolExecutionContext): String {
        val json = ToolArgs.parse(arguments) ?: return "Error: arguments are not a JSON object."
        val hour = ToolArgs.int(json, "hour") ?: return "Error: missing hour."
        val minute = ToolArgs.int(json, "minute") ?: 0
        if (hour !in 0..MAX_HOUR || minute !in 0..MAX_MINUTE) return "Error: time is out of range."
        val label = ToolArgs.text(json, "label")
        return runner.run(
            asked = label,
            ran = TOOL_NAME,
            risk = RegisteredTools.risk(TOOL_NAME),
            entryPoint = "tool",
            confirmed = true,
        ) { hands.setAlarm(hour, minute, label) }
    }

    companion object {
        /** Tool name as exposed to the model and the DI map. */
        const val TOOL_NAME = "set_alarm"

        /** When to set an alarm. */
        const val DESCRIPTION: String = "Sets an alarm in the clock app. Hour is 0 to 23."

        /** JSON arguments. */
        val PARAMETERS: String = """
            {"type":"object","properties":{"hour":{"type":"integer"},"minute":{"type":"integer"},"label":{"type":"string"}},"required":["hour"]}
        """.trimIndent()

        private const val MAX_HOUR = 23
        private const val MAX_MINUTE = 59
    }
}
