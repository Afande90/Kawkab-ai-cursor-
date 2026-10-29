package app.kawkab.android.data.tools.local.executors

import app.kawkab.android.domain.models.RegisteredTools
import app.kawkab.android.domain.models.ToolExecutionContext
import app.kawkab.android.domain.models.ToolRisk
import app.kawkab.android.domain.repositories.LocalToolExecutor
import app.kawkab.android.domain.safety.ActionRunner
import app.kawkab.android.hands.HandsDispatch
import app.kawkab.android.hands.MacroStore
import app.kawkab.android.hands.StepReplay
import app.kawkab.android.hands.ToolArgs
import javax.inject.Inject

/**
 * Replays a saved macro. The tool is outbound, so the card is shown first.
 * Each outbound step is checked again and is not marked always-allowed.
 */
class MacroSendExecutor @Inject constructor(
    private val store: MacroStore,
    private val dispatch: HandsDispatch,
    private val runner: ActionRunner,
) : LocalToolExecutor {
    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String, context: ToolExecutionContext): String {
        val json = ToolArgs.parse(arguments) ?: return "Error: arguments are not a JSON object."
        val name = ToolArgs.text(json, "name")
        val macro = store.find(name) ?: return "No macro named '$name'."
        val replay = StepReplay(risks = ::riskOf, runner = runner)
        return replay.play(macro.steps, confirmed = true, entryPoint = "macro", perform = dispatch::perform)
    }

    private fun riskOf(tool: String): ToolRisk =
        RegisteredTools.all.firstOrNull { it.name == tool }?.risk ?: ToolRisk.OUTBOUND

    companion object {
        /** Tool name as exposed to the model and the DI map. */
        const val TOOL_NAME = "macro_send"

        /** When to replay a macro that may send. */
        const val DESCRIPTION: String =
            "Replays a recorded macro. Outbound, because a step can send or call. Always waits for a tap."

        /** JSON arguments. */
        val PARAMETERS: String = """
            {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
        """.trimIndent()
    }
}
