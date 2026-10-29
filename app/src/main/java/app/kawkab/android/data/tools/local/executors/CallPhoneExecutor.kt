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
 * Places a phone call with ACTION_CALL after the outbound card has been tapped.
 */
class CallPhoneExecutor @Inject constructor(
    private val hands: PhoneHands,
    private val contacts: ContactBook,
    private val runner: ActionRunner,
) : LocalToolExecutor {
    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String, context: ToolExecutionContext): String {
        val json = ToolArgs.parse(arguments) ?: return "Error: arguments are not a JSON object."
        val spoken = ToolArgs.text(json, "contact")
        var failure = ""
        val number = ContactLookup.numberOrNull(spoken, contacts) { failure = it } ?: return failure
        return runner.run(
            asked = spoken,
            ran = TOOL_NAME,
            risk = RegisteredTools.risk(TOOL_NAME),
            entryPoint = "tool",
            confirmed = true,
            intentUri = "tel:$number",
        ) { hands.placeCall(number) }
    }

    companion object {
        /** Tool name as exposed to the model and the DI map. */
        const val TOOL_NAME = "call_phone"

        /** When to place a call. */
        const val DESCRIPTION: String =
            "Places a phone call to a contact or number. This is outbound and always waits for a tap. " +
                "It uses the phone app. It does not send a message."

        /** JSON arguments. */
        val PARAMETERS: String = """
            {"type":"object","properties":{"contact":{"type":"string"}},"required":["contact"]}
        """.trimIndent()
    }
}
