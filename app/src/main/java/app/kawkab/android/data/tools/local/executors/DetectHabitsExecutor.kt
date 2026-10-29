package app.kawkab.android.data.tools.local.executors

import app.kawkab.android.domain.models.RegisteredTools
import app.kawkab.android.domain.models.ToolExecutionContext
import app.kawkab.android.domain.repositories.LocalToolExecutor
import app.kawkab.android.domain.safety.ActionRunner
import app.kawkab.android.hands.HabitRoutineEngine
import app.kawkab.android.hands.ToolArgs
import javax.inject.Inject

/**
 * Records an action and reports routines that have repeated. It does not run them.
 */
class DetectHabitsExecutor @Inject constructor(
    private val habits: HabitRoutineEngine,
    private val runner: ActionRunner,
) : LocalToolExecutor {
    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String, context: ToolExecutionContext): String {
        val json = ToolArgs.parse(arguments) ?: return "Error: arguments are not a JSON object."
        val action = ToolArgs.text(json, "action")
        return runner.run(
            asked = action,
            ran = TOOL_NAME,
            risk = RegisteredTools.risk(TOOL_NAME),
            entryPoint = "routine",
            confirmed = true,
        ) {
            if (action.isNotBlank()) habits.recordAction(action, System.currentTimeMillis())
            val found = habits.detect()
            if (found.isEmpty()) "No routine yet." else found.joinToString { "${it.name} (${it.repetitions})" }
        }
    }

    companion object {
        /** Tool name as exposed to the model and the DI map. */
        const val TOOL_NAME = "detect_habits"

        /** When to look for a repeated routine. */
        const val DESCRIPTION: String =
            "Notes an action and lists routines that have repeated. Suggests only. It does not run them."

        /** JSON arguments. */
        val PARAMETERS: String = """
            {"type":"object","properties":{"action":{"type":"string"}}}
        """.trimIndent()
    }
}
