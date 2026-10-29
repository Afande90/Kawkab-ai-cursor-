package app.kawkab.android.data.tools.local.executors

import app.kawkab.android.domain.models.RegisteredTools
import app.kawkab.android.domain.models.ToolExecutionContext
import app.kawkab.android.domain.repositories.LocalToolExecutor
import app.kawkab.android.domain.safety.ActionRunner
import app.kawkab.android.hands.PhoneHands
import app.kawkab.android.hands.ToolArgs
import javax.inject.Inject

/**
 * Adds a calendar event through the calendar app.
 */
class AddCalendarEventExecutor @Inject constructor(private val hands: PhoneHands, private val runner: ActionRunner) :
    LocalToolExecutor {
    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String, context: ToolExecutionContext): String {
        val json = ToolArgs.parse(arguments) ?: return "Error: arguments are not a JSON object."
        val title = ToolArgs.text(json, "title")
        val start = json.optLong("start", 0L)
        if (title.isBlank() || start <= 0L) return "Error: missing title or start."
        return runner.run(
            asked = title,
            ran = TOOL_NAME,
            risk = RegisteredTools.risk(TOOL_NAME),
            entryPoint = "tool",
            confirmed = true,
        ) { hands.addCalendarEvent(title, start) }
    }

    companion object {
        /** Tool name as exposed to the model and the DI map. */
        const val TOOL_NAME = "add_calendar_event"

        /** When to add an event. */
        const val DESCRIPTION: String = "Adds a calendar event. start is unix time in milliseconds."

        /** JSON arguments. */
        val PARAMETERS: String = """
            {"type":"object","properties":{"title":{"type":"string"},"start":{"type":"integer"}},"required":["title","start"]}
        """.trimIndent()
    }
}
