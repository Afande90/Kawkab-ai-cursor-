package app.kawkab.android.data.tools.local.executors

import app.kawkab.android.domain.models.RegisteredTools
import app.kawkab.android.domain.models.ToolExecutionContext
import app.kawkab.android.domain.repositories.LocalToolExecutor
import app.kawkab.android.domain.safety.ActionRunner
import app.kawkab.android.hands.HistoryRow
import app.kawkab.android.hands.MacroStore
import app.kawkab.android.hands.ToolArgs
import javax.inject.Inject

/**
 * Records a successful hands step, and saves the run when a name is given.
 */
class RecordMacroExecutor @Inject constructor(private val store: MacroStore, private val runner: ActionRunner) :
    LocalToolExecutor {
    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String, context: ToolExecutionContext): String {
        val json = ToolArgs.parse(arguments) ?: return "Error: arguments are not a JSON object."
        val tool = ToolArgs.text(json, "tool")
        val name = ToolArgs.text(json, "name")
        if (tool.isBlank() && name.isBlank()) return "Error: missing tool or name."
        return runner.run(
            asked = name,
            ran = TOOL_NAME,
            risk = RegisteredTools.risk(TOOL_NAME),
            entryPoint = "macro",
            confirmed = true,
        ) {
            if (tool.isNotBlank()) {
                store.add(HistoryRow(tool = tool, params = emptyMap(), success = true, order = json.optInt("order", 0)))
            }
            if (name.isBlank()) {
                "Recorded $tool."
            } else {
                val saved = store.save(name)
                if (saved ==
                    null
                ) {
                    "Nothing successful to save."
                } else {
                    "Saved macro '$name' with ${saved.steps.size} steps."
                }
            }
        }
    }

    companion object {
        /** Tool name as exposed to the model and the DI map. */
        const val TOOL_NAME = "record_macro"

        /** When to record a macro. */
        const val DESCRIPTION: String =
            "Records hands steps and saves them under a name. Replaying them is a separate outbound action."

        /** JSON arguments. */
        val PARAMETERS: String = """
            {"type":"object","properties":{"tool":{"type":"string"},"name":{"type":"string"},"order":{"type":"integer"}}}
        """.trimIndent()
    }
}
