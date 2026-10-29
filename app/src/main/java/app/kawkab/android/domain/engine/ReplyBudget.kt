package app.kawkab.android.domain.engine

/**
 * How long a local reply may run, decided from the prompt it was given.
 *
 * A simple turn switches thinking off and does not cap the answer. A tool or
 * JSON turn also switches thinking off, and stops after
 * [TOOL_OR_JSON_MAX_OUTPUT_TOKENS] so the phone is not held by a long scratchpad.
 */
object ReplyBudget {
    /** Output cap for a tool call or a JSON object, in tokens. */
    const val TOOL_OR_JSON_MAX_OUTPUT_TOKENS: Int = 300

    /** Characters at the end of the prompt that decide the kind. */
    const val TAIL_CHARS: Int = 500

    /**
     * A prose answer, or a tool or JSON answer.
     */
    enum class Kind {
        /** Thinking off. The answer is not capped at the tool limit. */
        SIMPLE,

        /** Thinking off, and the answer stops at [TOOL_OR_JSON_MAX_OUTPUT_TOKENS]. */
        TOOL_OR_JSON,
    }

    /**
     * Classifies [prompt] from its ending.
     *
     * The tool advertisement lives at the start of a chat prompt, so only the
     * tail is read. A tail that says not to emit JSON stays simple.
     *
     * @param prompt The full text about to be sent.
     * @return [Kind.TOOL_OR_JSON] when the tail asks for JSON, otherwise [Kind.SIMPLE].
     */
    fun kindOf(prompt: String): Kind {
        val tail = if (prompt.length <= TAIL_CHARS) prompt else prompt.substring(prompt.length - TAIL_CHARS)
        if (tail.contains("no JSON")) return Kind.SIMPLE
        return if (JSON_ASK.containsMatchIn(tail)) Kind.TOOL_OR_JSON else Kind.SIMPLE
    }

    private val JSON_ASK: Regex = Regex(
        "JSON OUTPUT:|RESPONSE \\(JSON only\\):|STRICTLY valid JSON|strictly valid JSON|JSON array of",
    )
}
