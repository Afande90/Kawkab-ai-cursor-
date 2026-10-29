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
     * These scores were measured with llama.cpp on this build machine, not on a phone.
     */
    const val REFERENCE_RUNTIME: String = "reference runtime, not device"

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
        /** Gemma 4 E2B. */
        GEMMA_4_E2B,

        /** Gemma 4 E4B. The measured tool-call default, and the image model. */
        GEMMA_4_E4B,

        /** Qwen3 1.7B. Selectable. Scored below both Gemma brains. */
        QWEN3_1_7B,
    }

    /**
     * The Kawkab score of [brain] on [evalSet].
     *
     * @param brain Which candidate.
     * @param evalSet [EVAL_V1] or [EVAL_V2].
     * @return Exact matches on the test split, labelled [REFERENCE_RUNTIME].
     */
    fun score(brain: Brain, evalSet: String): String {
        check(brain in Brain.entries)
        check(evalSet == EVAL_V1 || evalSet == EVAL_V2)
        val hits = when (brain) {
            Brain.GEMMA_4_E2B -> if (evalSet == EVAL_V1) "87/100" else "55/78"
            Brain.GEMMA_4_E4B -> if (evalSet == EVAL_V1) "91/100" else "66/78"
            Brain.QWEN3_1_7B -> if (evalSet == EVAL_V1) "61/100" else "41/78"
        }
        return "$hits, $REFERENCE_RUNTIME"
    }

    /**
     * The brain used for tool calls.
     *
     * Gemma 4 E4B scored highest on the v1 and v2 test splits.
     */
    fun toolDefault(): Brain = Brain.GEMMA_4_E4B

    /**
     * Whether images stay on Gemma.
     *
     * A GGUF tool-call brain does not take images. Gemma stays the image model
     * even when [toolDefault] is Qwen.
     */
    fun keepsGemmaForImages(): Boolean = true
}
