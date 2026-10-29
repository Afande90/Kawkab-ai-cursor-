package app.kawkab.android.domain.assistant

/**
 * Tool-calling brains and the scores Kawkab has actually measured.
 *
 * A score this object returns is a Kawkab measurement. A figure published by a donor is not stored here.
 */
object ToolCallReport {
    /**
     * Written when a brain has not been scored on a frozen command set.
     */
    const val NOT_MEASURED: String = "not measured"

    /**
     * The frozen v1 command set.
     */
    const val EVAL_V1: String = "v1"

    /**
     * The frozen v2 command set.
     */
    const val EVAL_V2: String = "v2"

    /**
     * A candidate tool-calling brain.
     */
    enum class Brain {
        /** Gemma 4 E2B, the current tool-call default. */
        GEMMA_4_E2B,

        /** Gemma 4 E4B. Also the image-capable Gemma. */
        GEMMA_4_E4B,

        /** Qwen3 1.7B. A candidate only, until a Kawkab score exists. */
        QWEN3_1_7B,
    }

    /**
     * The Kawkab score of [brain] on [evalSet].
     *
     * @param brain Which candidate.
     * @param evalSet [EVAL_V1] or [EVAL_V2].
     * @return [NOT_MEASURED] until a run on a device records a real score.
     */
    fun score(brain: Brain, evalSet: String): String {
        check(brain in Brain.entries)
        check(evalSet == EVAL_V1 || evalSet == EVAL_V2)
        return NOT_MEASURED
    }

    /**
     * The brain used for tool calls.
     *
     * No candidate has a Kawkab score, so this stays [Brain.GEMMA_4_E2B].
     */
    fun toolDefault(): Brain = Brain.GEMMA_4_E2B

    /**
     * Whether images stay on Gemma.
     *
     * @return `true` while the tool-call default is a Gemma brain.
     */
    fun keepsGemmaForImages(): Boolean = toolDefault() != Brain.QWEN3_1_7B
}
