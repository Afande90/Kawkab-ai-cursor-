package app.kawkab.android.data.tools.local.executors

import app.kawkab.android.domain.models.RegisteredTools
import app.kawkab.android.domain.models.ToolExecutionContext
import app.kawkab.android.domain.repositories.LocalToolExecutor
import app.kawkab.android.domain.safety.ActionRunner
import app.kawkab.android.hands.ContactBook
import app.kawkab.android.hands.ContactLookup
import app.kawkab.android.hands.PhoneHands
import app.kawkab.android.hands.ToolArgs
import javax.inject.Inject

/**
 * Opens the SMS app with the message filled in and taps send. There is no SEND_SMS permission.
 */
class SendSmsExecutor @Inject constructor(
    private val hands: PhoneHands,
    private val contacts: ContactBook,
    private val runner: ActionRunner,
) : LocalToolExecutor {
    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String, context: ToolExecutionContext): String {
        val json = ToolArgs.parse(arguments) ?: return "Error: arguments are not a JSON object."
        val spoken = ToolArgs.text(json, "contact")
        val body = ToolArgs.text(json, "body")
        if (body.isBlank()) return "Error: missing message body."
        var failure = ""
        val number = ContactLookup.numberOrNull(spoken, contacts) { failure = it } ?: return failure
        return runner.run(
            asked = spoken,
            ran = TOOL_NAME,
            risk = RegisteredTools.risk(TOOL_NAME),
            entryPoint = "tool",
            confirmed = true,
            intentUri = "smsto:$number",
        ) { hands.composeSms(number, body) }
    }

    companion object {
        /** Tool name as exposed to the model and the DI map. */
        const val TOOL_NAME = "send_sms"

        /** When to send a text message. */
        const val DESCRIPTION: String =
            "Sends an SMS by opening the message app and tapping send. Outbound. Always waits for a tap."

        /** JSON arguments. */
        val PARAMETERS: String = """
            {"type":"object","properties":{"contact":{"type":"string"},"body":{"type":"string"}},"required":["contact","body"]}
        """.trimIndent()
    }
}
