package app.kawkab.android.data.tools.local.executors

import app.kawkab.android.domain.models.RegisteredTools
import app.kawkab.android.domain.models.ToolExecutionContext
import app.kawkab.android.domain.repositories.LocalToolExecutor
import app.kawkab.android.domain.safety.ActionRunner
import app.kawkab.android.hands.PhoneHands
import app.kawkab.android.hands.ToolArgs
import javax.inject.Inject

/**
 * Generic accessibility tap. A target that matches the outbound pattern list is still gated inside the service.
 */
class SubmitTapExecutor @Inject constructor(private val hands: PhoneHands, private val runner: ActionRunner) :
    LocalToolExecutor {
    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String, context: ToolExecutionContext): String {
        val json = ToolArgs.parse(arguments) ?: return "Error: arguments are not a JSON object."
        val text = ToolArgs.text(json, "text")
        val viewId = ToolArgs.text(json, "view_id")
        if (text.isBlank() && viewId.isBlank()) return "Error: missing text or view_id."
        return runner.run(
            asked = text.ifBlank { viewId },
            ran = TOOL_NAME,
            risk = RegisteredTools.risk(TOOL_NAME),
            entryPoint = "tap",
            confirmed = true,
        ) { hands.tap(text, viewId, confirmed = true) }
    }

    companion object {
        /** Tool name as exposed to the model and the DI map. */
        const val TOOL_NAME = "submit_tap"

        /** When to tap a control on screen. */
        const val DESCRIPTION: String =
            "Taps a control by its label or view id. Outbound, because a tap can submit, send, pay, or share. " +
                "Always waits for a tap on the confirmation card."

        /** JSON arguments. */
        val PARAMETERS: String = """
            {"type":"object","properties":{"text":{"type":"string"},"view_id":{"type":"string"}}}
        """.trimIndent()
    }
}
