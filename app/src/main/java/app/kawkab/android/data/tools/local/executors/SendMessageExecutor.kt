package app.kawkab.android.data.tools.local.executors

import app.kawkab.android.domain.models.RegisteredTools
import app.kawkab.android.domain.models.ToolExecutionContext
import app.kawkab.android.domain.repositories.LocalToolExecutor
import app.kawkab.android.domain.safety.ActionRunner
import app.kawkab.android.hands.PhoneHands
import app.kawkab.android.hands.ToolArgs
import javax.inject.Inject

/**
 * Sends a message inside another app, including the stub messenger, by tapping that app's send control.
 */
class SendMessageExecutor @Inject constructor(private val hands: PhoneHands, private val runner: ActionRunner) :
    LocalToolExecutor {
    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String, context: ToolExecutionContext): String {
        val json = ToolArgs.parse(arguments) ?: return "Error: arguments are not a JSON object."
        val packageName = ToolArgs.text(json, "package")
        val body = ToolArgs.text(json, "body")
        if (packageName.isBlank() || body.isBlank()) return "Error: missing package or body."
        return runner.run(
            asked = body,
            ran = TOOL_NAME,
            risk = RegisteredTools.risk(TOOL_NAME),
            entryPoint = "tool",
            confirmed = true,
            intentPackage = packageName,
        ) { hands.sendInApp(packageName, body) }
    }

    companion object {
        /** Tool name as exposed to the model and the DI map. */
        const val TOOL_NAME = "send_message"

        /** When to send inside a messaging app. */
        const val DESCRIPTION: String =
            "Sends a message inside an installed app by opening it and tapping send. Outbound. Always waits for a tap."

        /** JSON arguments. */
        val PARAMETERS: String = """
            {"type":"object","properties":{"package":{"type":"string"},"body":{"type":"string"}},"required":["package","body"]}
        """.trimIndent()
    }
}
