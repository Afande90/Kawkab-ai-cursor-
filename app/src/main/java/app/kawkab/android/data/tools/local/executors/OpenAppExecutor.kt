package app.kawkab.android.data.tools.local.executors

import app.kawkab.android.domain.models.RegisteredTools
import app.kawkab.android.domain.models.ToolExecutionContext
import app.kawkab.android.domain.repositories.LocalToolExecutor
import app.kawkab.android.domain.safety.ActionRunner
import app.kawkab.android.hands.PhoneHands
import app.kawkab.android.hands.ToolArgs
import javax.inject.Inject

/**
 * Opens an installed app by its label or package name.
 */
class OpenAppExecutor @Inject constructor(private val hands: PhoneHands, private val runner: ActionRunner) :
    LocalToolExecutor {
    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String, context: ToolExecutionContext): String {
        val json = ToolArgs.parse(arguments) ?: return "Error: arguments are not a JSON object."
        val name = ToolArgs.text(json, "name")
        if (name.isBlank()) return "Error: missing name."
        return runner.run(
            asked = name,
            ran = TOOL_NAME,
            risk = RegisteredTools.risk(TOOL_NAME),
            entryPoint = "tool",
            confirmed = true,
        ) { hands.openApp(name) }
    }

    companion object {
        /** Tool name as exposed to the model and the DI map. */
        const val TOOL_NAME = "open_app"

        /** When to open an app. */
        const val DESCRIPTION: String = "Opens an installed app by the name the owner uses."

        /** JSON arguments. */
        val PARAMETERS: String = """
            {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
        """.trimIndent()
    }
}
