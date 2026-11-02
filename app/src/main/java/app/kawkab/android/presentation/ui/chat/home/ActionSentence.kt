package app.kawkab.android.presentation.ui.chat.home

import app.kawkab.android.R
import app.kawkab.android.presentation.ui.common.UiText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The confirmation card in the owner's words (task 23i): "Call Ahmad?", "Send to Omar: I'm late",
 * instead of `call_phone` and raw JSON. The raw arguments stay behind Details.
 */
internal object ActionSentence {
    private val json = Json { isLenient = true }

    /**
     * The tool's arguments as plain strings.
     *
     * @param raw The arguments JSON.
     * @return Key to value, without JSON quotes; empty when it is not an object.
     */
    fun arguments(raw: String): Map<String, String> = try {
        (json.parseToJsonElement(raw) as? JsonObject)
            ?.mapValues { (_, value) -> (value as? JsonPrimitive)?.content ?: value.toString() }
            .orEmpty()
    } catch (_: IllegalArgumentException) {
        emptyMap()
    }

    /**
     * @param tool The tool asking.
     * @param args Its arguments, from [arguments].
     * @return The sentence, or `null` for a tool without one (the developer card stays).
     */
    fun of(tool: String, args: Map<String, String>): UiText? {
        val contact = args["contact"].orEmpty()
        val body = args["body"].orEmpty()
        return when (tool) {
            "call_phone" -> UiText.Resource(R.string.action_card_call, listOf(contact))
            "send_sms", "send_message", "macro_send" -> UiText.Resource(
                R.string.action_card_send,
                listOf(contact, body),
            )
            "delete_file" -> UiText.Resource(R.string.action_card_delete, listOf(args["path"].orEmpty()))
            "http_request" -> UiText.Resource(R.string.action_card_http, listOf(args["url"].orEmpty()))
            "submit_tap" -> UiText.Resource(R.string.action_card_tap, listOf(args["text"].orEmpty()))
            else -> null
        }
    }

    /**
     * What Kawkab says before an announced call or message runs (vision rule 1): "Calling Ahmad",
     * "Texting Omar: I'm late".
     *
     * @param tool The tool about to run.
     * @param args Its arguments.
     * @return The sentence.
     */
    fun announcement(tool: String, args: Map<String, String>): UiText {
        val who = args["contact"].orEmpty()
        return when (tool) {
            "call_phone" -> UiText.Resource(R.string.announce_call, listOf(who))
            "send_sms", "send_message", "macro_send" ->
                UiText.Resource(R.string.announce_send, listOf(who, args["body"].orEmpty()))
            else -> of(tool, args) ?: UiText.Resource(R.string.announce_generic)
        }
    }

    /**
     * @param tool The tool asking.
     * @return The primary button label, or `null` for the default.
     */
    fun primary(tool: String): UiText? = when (tool) {
        "call_phone" -> UiText.Resource(R.string.action_card_primary_call)
        "send_sms", "send_message", "macro_send" -> UiText.Resource(R.string.action_card_primary_send)
        else -> null
    }

    /**
     * The command Change puts back into the composer, so the owner can edit it.
     *
     * @param tool The tool asking.
     * @param args Its arguments.
     * @return A line such as "call Ahmad", or empty when there is none.
     */
    fun editableCommand(tool: String, args: Map<String, String>): String = when (tool) {
        "call_phone" -> "call ${args["contact"].orEmpty()}"
        "send_sms" -> "text ${args["contact"].orEmpty()} ${args["body"].orEmpty()}"
        "send_message" -> "message ${args["contact"].orEmpty()} ${args["body"].orEmpty()}"
        else -> ""
    }.trim()
}
