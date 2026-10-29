package app.kawkab.android.data.tools.local.executors

import app.kawkab.android.domain.models.RegisteredTools
import app.kawkab.android.domain.models.ToolExecutionContext
import app.kawkab.android.domain.repositories.LocalToolExecutor
import app.kawkab.android.domain.safety.ActionRunner
import app.kawkab.android.hands.PhoneHands
import javax.inject.Inject

/**
 * Reads the text of the screen the owner is looking at. It does not tap.
 */
class ReadScreenExecutor @Inject constructor(private val hands: PhoneHands, private val runner: ActionRunner) :
    LocalToolExecutor {
    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String, context: ToolExecutionContext): String = runner.run(
        asked = "",
        ran = TOOL_NAME,
        risk = RegisteredTools.risk(TOOL_NAME),
        entryPoint = "tool",
        confirmed = true,
    ) { hands.readScreen() }

    companion object {
        /** Tool name as exposed to the model and the DI map. */
        const val TOOL_NAME = "read_screen"

        /** When to read the screen. */
        const val DESCRIPTION: String = "Reads the text currently on screen. It does not change anything."

        /** JSON arguments. */
        const val PARAMETERS: String = """{"type":"object","properties":{}}"""
    }
}
