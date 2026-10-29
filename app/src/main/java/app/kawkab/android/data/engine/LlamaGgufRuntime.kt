package app.kawkab.android.data.engine

import com.llamatik.core.platform.LlamaBridge

/**
 * llama.cpp on the phone, through Llamatik, for a GGUF brain.
 *
 * Gemma stays the image model. This runtime reads text only, on CPU, and it
 * does not ship any weights.
 */
object LlamaGgufRuntime {
    const val SUFFIX: String = ".gguf"

    private const val TEMPERATURE = 0f
    private const val MAX_TOKENS = 256
    private const val TOP_P = 1f
    private const val TOP_K = 1
    private const val REPEAT_PENALTY = 1f
    private const val CONTEXT_TOKENS = 1024
    private const val CPU_THREADS = 4
    private const val BATCH = 64
    private const val GPU_LAYERS = 0

    /**
     * Whether [path] is a GGUF weight file.
     */
    fun isGguf(path: String): Boolean = path.endsWith(SUFFIX)

    /**
     * Loads [path] for CPU generation.
     *
     * @return `true` when llama.cpp accepted the file.
     */
    fun load(path: String): Boolean {
        LlamaBridge.updateGenerateParams(
            temperature = TEMPERATURE,
            maxTokens = MAX_TOKENS,
            topP = TOP_P,
            topK = TOP_K,
            repeatPenalty = REPEAT_PENALTY,
            contextLength = CONTEXT_TOKENS,
            numThreads = CPU_THREADS,
            useMmap = true,
            flashAttention = false,
            batchSize = BATCH,
            gpuLayers = GPU_LAYERS,
        )
        return LlamaBridge.initGenerateModel(path)
    }

    /**
     * One completion of [prompt]. The caller already built the tier-3 prompt.
     */
    fun complete(prompt: String): String = LlamaBridge.generate(prompt)

    /**
     * Drops the loaded GGUF.
     */
    fun unload() {
        LlamaBridge.shutdown()
    }
}
