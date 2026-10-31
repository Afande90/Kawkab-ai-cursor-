package app.kawkab.android.domain.engine

import app.kawkab.android.domain.constants.SettingsDefaults

/**
 * Sizes the LiteRT-LM engine window and recognises the error a too-small or too-large window gives.
 *
 * The engine window (`EngineConfig.maxNumTokens`) is the whole KV budget. The system prompt, the
 * tool list, the injected context and the reply must all fit in it, or every turn fails with
 * "Failed to allocate tensors" (task 23f).
 */
object EngineWindow {
    /** Tokens kept for the reply. At least the tool or JSON cap of [ReplyBudget]. */
    const val REPLY_RESERVE_TOKENS: Int = 512

    /**
     * The smallest window the engine is ever opened with. A stored setting below it is raised to it.
     * The Kawkab prompt, the phone tools, the injected budget and the reply fit in it.
     */
    const val MIN_ENGINE_TOKENS: Int = 3_584

    /** The window of the one retry after an allocation failure. */
    const val REDUCED_RETRY_TOKENS: Int = MIN_ENGINE_TOKENS

    /** Rough characters per token for a size check. Arabic packs fewer characters per token, so 3 not 4. */
    const val CHARS_PER_TOKEN_ESTIMATE: Int = 3

    /**
     * The window to open the engine with, from the stored setting.
     *
     * @param stored The `maxContextLength` setting.
     * @return [stored], raised to [MIN_ENGINE_TOKENS].
     */
    fun engineTokens(stored: Int): Int = stored.coerceAtLeast(MIN_ENGINE_TOKENS)

    /**
     * The window to read from storage. Task 15 wrote 1,500 as the default. The Settings slider moves
     * in steps of 512, so the owner cannot have chosen 1,500, and it becomes the 4,096 default again.
     *
     * @param stored The stored value, or `null` when nothing is stored.
     * @return The window to use.
     */
    fun migrated(stored: Int?): Int = when (stored) {
        null, SettingsDefaults.LEGACY_TASK15_CONTEXT_LENGTH -> SettingsDefaults.MAX_CONTEXT_LENGTH_DEFAULT
        else -> stored
    }

    /**
     * A rough token count for [text], high rather than low.
     *
     * @param text Any prompt text.
     * @return The estimate.
     */
    fun estimateTokens(text: String): Int = (text.length + CHARS_PER_TOKEN_ESTIMATE - 1) / CHARS_PER_TOKEN_ESTIMATE

    /**
     * Tokens a turn needs: the fixed prompt, the tool list, the injected context and the reply.
     *
     * @param systemPromptTokens The rendered system prompt without injected context.
     * @param toolSchemaTokens The tool list sent with the prompt.
     * @return The total.
     */
    fun requiredTokens(systemPromptTokens: Int, toolSchemaTokens: Int): Int =
        systemPromptTokens + toolSchemaTokens + SettingsDefaults.INJECTED_CONTEXT_TOKEN_BUDGET + REPLY_RESERVE_TOKENS

    /**
     * Whether [throwable] or [message] is LiteRT-LM failing to allocate its tensors.
     *
     * @param message The error text.
     * @param throwable The error, if any. Its causes are read too.
     * @return `true` for "Failed to allocate tensors" and "Failed to invoke the compiled model".
     */
    fun isAllocationFailure(message: String?, throwable: Throwable? = null): Boolean {
        if (message != null && ALLOCATION.containsMatchIn(message)) return true
        var cause = throwable
        var depth = 0
        while (cause != null && depth < MAX_CAUSE_DEPTH) {
            if (cause.message?.let { ALLOCATION.containsMatchIn(it) } == true) return true
            cause = cause.cause
            depth += 1
        }
        return false
    }

    private const val MAX_CAUSE_DEPTH = 8

    private val ALLOCATION = Regex("allocate tensors|invoke the compiled model", RegexOption.IGNORE_CASE)
}
