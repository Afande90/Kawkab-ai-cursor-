package app.kawkab.android.domain.engine

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import app.kawkab.android.domain.models.CloudProvider

/**
 * Whether a cloud answer that streamed to its end was whole, or was cut off.
 *
 * A provider whose connection dies mid-answer does not always raise: the OpenAI-compatible
 * clients end the stream normally and simply omit the finish reason, so a half-written answer
 * would otherwise pass as a complete one. Measured on a stub that cut the socket mid-stream:
 * identical frames in both cases apart from `End.finishReason` (`null` when cut, `"stop"` when
 * complete). Every caller that turns a cloud stream into an answer — a Cloud node, a structured
 * step on a cloud model, a delegated task — asks this one rule, so they cannot disagree.
 */
object CloudStreamEnd {

    /**
     * Whether a stream from [provider] answering with [model] was cut off: it ended without a
     * finish reason, and this provider is one whose absence of a finish reason was measured to
     * mean a cut. A stream that ended without any end frame has no finish reason either.
     *
     * @param provider The provider that answered.
     * @param model The model it answered with.
     * @param finishReason The end frame's finish reason, or `null` when it had none or never came.
     * @return `true` when the answer must be rejected as incomplete.
     */
    fun cutOff(provider: CloudProvider, model: LLModel, finishReason: String?): Boolean =
        finishReason == null && reportsFinishReason(provider, model)

    /**
     * Whether an absent finish reason is evidence of a truncated answer for [provider]
     * answering with [model].
     *
     * Only true where it was actually measured against a stub, because the inverse
     * mistake — treating a healthy stream as truncated — fails working runs:
     *
     * - `DEEPSEEK` / `OPENAI` — **checked**. Measured on DeepSeek: a socket cut mid-stream
     *   ends the flow with `End(finishReason = null)` and no exception, while a complete
     *   stream ends with `End(finishReason = "stop")`. OpenAI shares the same streaming
     *   implementation (`AbstractOpenAILLMClient`), so the signal is the same one —
     *   **except for a model Koog streams through the Responses API** (see
     *   [streamsThroughResponsesApi]). That path ends every healthy answer with
     *   `End(finishReason = null)`, because a Responses stream carries a status rather
     *   than a finish reason, and Koog itself fails a stream that stops before its
     *   completion event (`requireEndFrame`). Checking there rejected every answer of the
     *   Codex and Pro models as cut off; measured against MockWebServer.
     * - `GOOGLE` — **checked**, and already correct without help: its client throws
     *   `IncompleteStreamException` on a cut, so the check never fires for it.
     * - `OLLAMA` — **excluded**. Its client never emits a finish reason at all, so absence
     *   carries no information; enabling the check would fail every healthy Ollama run.
     * - `OPENROUTER` / `GROQ` / `OPENAI_COMPATIBLE` — **excluded pending measurement**. The
     *   stream parser is OpenAI's, but the signal is the server's: a server that omits the
     *   finish reason would have every healthy answer discarded as cut off.
     *
     * @param provider The provider that answers.
     * @param model The model it answers with.
     * @return `true` when a missing finish reason means the answer was cut off.
     */
    fun reportsFinishReason(provider: CloudProvider, model: LLModel): Boolean = when (provider) {
        CloudProvider.OPENAI -> !model.streamsThroughResponsesApi()
        CloudProvider.DEEPSEEK, CloudProvider.GOOGLE -> true
        CloudProvider.OLLAMA -> false
        // Same client as OpenAI, but whether each *server* sends the finish reason is what
        // matters, and none has been measured yet; off until the device check says otherwise.
        CloudProvider.OPENROUTER, CloudProvider.GROQ, CloudProvider.OPENAI_COMPATIBLE -> false
    }

    /**
     * What a caller says when it rejects a cut-off answer.
     *
     * @param providerId The wire id of the provider that stopped sending.
     * @return One sentence naming the provider and saying the partial answer was discarded.
     */
    fun truncatedMessage(providerId: String): String =
        "The response from '$providerId' was cut off before it finished — the connection " +
            "ended without a completion signal. The partial answer was discarded; try again."

    /**
     * Whether Koog's OpenAI client sends this model through the Responses API rather than
     * Chat Completions: it prefers Chat Completions whenever the model supports it, and
     * falls back to Responses only for a model that supports nothing else
     * (`OpenAILLMClient.determineParams`). `OpenAiResponsesStreamEndToEndTest` holds this
     * in step with the client, for every OpenAI model the app offers.
     *
     * @receiver The model about to be called.
     * @return `true` for a Responses-only model, such as the Codex and Pro lines.
     */
    private fun LLModel.streamsThroughResponsesApi(): Boolean =
        !supports(LLMCapability.OpenAIEndpoint.Completions) && supports(LLMCapability.OpenAIEndpoint.Responses)
}

/**
 * A cloud answer that streamed to its end without the finish reason its provider always sends:
 * it was cut off, and the partial text is discarded. Thrown where the answer is returned rather
 * than emitted, so the caller's error path shows it as it shows any provider failure.
 *
 * @param providerId The wire id of the provider that stopped sending.
 */
class CloudStreamCutOffException(providerId: String) : Exception(CloudStreamEnd.truncatedMessage(providerId))
