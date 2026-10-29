package app.kawkab.android.hands

import org.json.JSONObject

/**
 * Reads string and integer arguments from a tool's JSON object.
 * A broken payload yields null so the executor can report it instead of throwing.
 */
object ToolArgs {
    /**
     * The object, or null when [arguments] is not a JSON object.
     */
    fun parse(arguments: String): JSONObject? = try {
        JSONObject(arguments)
    } catch (_: Exception) {
        null
    }

    /** [key] as a string, or an empty string when it is absent. */
    fun text(json: JSONObject, key: String): String = json.optString(key, "")

    /** [key] as an int, or null when it is absent or not a number. */
    fun int(json: JSONObject, key: String): Int? {
        if (!json.has(key)) return null
        return when (val raw = json.opt(key)) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }
}
