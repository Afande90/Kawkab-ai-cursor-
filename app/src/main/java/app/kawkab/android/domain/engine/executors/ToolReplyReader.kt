package app.kawkab.android.domain.engine.executors

import app.kawkab.android.domain.engine.structured.JsonPayloadExtractor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * What a model reply is, when it might be a tool call written as text (task 23h).
 */
sealed interface ToolReply {
    /**
     * A tool call with arguments, ready for the tool gate.
     *
     * @property tool The tool name.
     * @property argumentsJson The arguments as a JSON object.
     */
    data class Call(val tool: String, val argumentsJson: String) : ToolReply

    /**
     * The reply names a tool but has no usable arguments.
     *
     * @property tool The tool name.
     */
    data class NamedOnly(val tool: String) : ToolReply

    /** Ordinary text. */
    data object Text : ToolReply
}

/**
 * Reads a tool call out of a model reply, so a tool name or JSON never reaches the owner as text.
 *
 * It reads Gemma 4's own call markup (`<start_function_call>call:set_alarm{hour:6}…`), a
 * fenced or plain JSON object (`{"tool": …, "arguments": …}` or `{"name": …, "parameters": …}`),
 * and a call written as `set_alarm(hour=6)`. A reply that is only a known tool name, or a JSON
 * object naming one without arguments, is [ToolReply.NamedOnly].
 */
object ToolReplyReader {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val gemmaCall = Regex("call:([A-Za-z_][A-Za-z0-9_]*)\\s*\\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL)
    private val gemmaPair =
        Regex(
            "([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*(<escape>(.*?)<escape>|\"([^\"]*)\"|[^,}]+)",
            RegexOption.DOT_MATCHES_ALL,
        )
    private val pythonCall = Regex("^\\s*`*([a-z_][a-z0-9_]*)\\((.*)\\)`*\\s*$", RegexOption.DOT_MATCHES_ALL)
    private val pythonPair = Regex("([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|[^,]+)")
    private val bareName = Regex("[`'\"\\s.!?:]+")

    private val nameKeys = listOf("tool", "name", "function", "tool_name")
    private val argumentKeys = listOf("arguments", "args", "parameters", "params", "input")

    /**
     * @param reply The model's whole reply.
     * @param knownTools The tool names that exist.
     * @return What the reply is.
     */
    fun read(reply: String, knownTools: Set<String>): ToolReply {
        if (reply.isBlank()) return ToolReply.Text
        val bare = reply.replace(bareName, "")
        return gemma(reply, knownTools)
            ?: jsonCall(reply, knownTools)
            ?: python(reply, knownTools)
            ?: if (bare in knownTools) ToolReply.NamedOnly(bare) else ToolReply.Text
    }

    private fun gemma(reply: String, known: Set<String>): ToolReply? {
        val match = gemmaCall.find(reply) ?: return null
        val tool = match.groupValues[1]
        if (tool !in known) return null
        val args = buildJsonObject {
            gemmaPair.findAll(match.groupValues[2]).forEach { pair ->
                put(pair.groupValues[KEY], primitive(valueOf(pair)))
            }
        }
        return ToolReply.Call(tool, args.toString())
    }

    private fun jsonCall(reply: String, known: Set<String>): ToolReply? {
        val payload = JsonPayloadExtractor.extract(reply)
        val element = try {
            json.parseToJsonElement(payload)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val objectJson = element as? JsonObject ?: return null
        val tool = nameKeys.firstNotNullOfOrNull { key -> (objectJson[key] as? JsonPrimitive)?.content } ?: return null
        if (tool !in known) return null
        val arguments = argumentKeys.firstNotNullOfOrNull { key -> objectJson[key] }
        return when (arguments) {
            is JsonObject -> ToolReply.Call(tool, arguments.toString())
            is JsonPrimitive -> parsedObject(arguments.content)?.let { ToolReply.Call(tool, it.toString()) }
                ?: ToolReply.NamedOnly(tool)
            else -> ToolReply.NamedOnly(tool)
        }
    }

    private fun python(reply: String, known: Set<String>): ToolReply? {
        val match = pythonCall.find(reply) ?: return null
        val tool = match.groupValues[1]
        if (tool !in known) return null
        val args = buildJsonObject {
            pythonPair.findAll(match.groupValues[2]).forEach { pair ->
                put(pair.groupValues[KEY], primitive(valueOf(pair)))
            }
        }
        return if (args.isEmpty()) ToolReply.NamedOnly(tool) else ToolReply.Call(tool, args.toString())
    }

    /** The value of a `key: value` or `key=value` pair: the escaped or quoted text, else the raw text. */
    private fun valueOf(pair: MatchResult): String = pair.groupValues[ESCAPED]
        .ifEmpty { pair.groupValues[QUOTED] }
        .ifEmpty { pair.groupValues[RAW].trim() }

    private const val KEY = 1
    private const val RAW = 2
    private const val ESCAPED = 3
    private const val QUOTED = 4

    private fun parsedObject(text: String): JsonObject? = try {
        json.parseToJsonElement(text) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun primitive(value: String): JsonElement =
        value.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(value)

    /**
     * A command the owner can type for [tool], shown when a call could not be made.
     *
     * @param tool The tool the model named.
     * @return A short example, such as "set an alarm for 6".
     */
    fun example(tool: String): String = examples[tool] ?: examples.getValue("set_alarm")

    private val examples = mapOf(
        "set_alarm" to "set an alarm for 6",
        "set_timer" to "set a timer for 5 minutes",
        "call_phone" to "call Ahmad",
        "send_sms" to "text Ahmad I'm late",
        "send_message" to "WhatsApp Ahmad I'm late",
        "open_app" to "open WhatsApp",
        "add_calendar_event" to "add lunch tomorrow at 1",
    )

    /**
     * The one re-ask after a reply that only named [tool]: the model must answer with the JSON
     * call and nothing else.
     *
     * @param tool The tool the model named.
     * @return The instruction appended to the prompt.
     */
    fun reAsk(tool: String): String =
        "\nSYSTEM: Your last reply named $tool without its arguments. Reply with only this JSON and " +
            "nothing else: {\"tool\":\"$tool\",\"arguments\":{...}} with every argument $tool needs.\nAGENT: "
}
