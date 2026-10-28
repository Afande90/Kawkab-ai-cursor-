package app.kawkab.android.data.engine

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import app.kawkab.android.domain.engine.CloudLlmClientFactory
import app.kawkab.android.domain.engine.CloudLlmModelResolver
import app.kawkab.android.domain.engine.CloudStreamCutOffException
import app.kawkab.android.domain.engine.CloudStreamEnd
import app.kawkab.android.domain.engine.structured.CloudStructuredInferenceClientFactory
import app.kawkab.android.domain.engine.structured.StructuredInferenceClient
import app.kawkab.android.domain.models.CloudProvider
import app.kawkab.android.domain.repositories.NetworkActivityTracker
import javax.inject.Inject
import javax.inject.Singleton
import ai.koog.prompt.dsl.prompt as buildPrompt

/**
 * Data-layer [CloudStructuredInferenceClientFactory] backed by the Koog cloud
 * client.
 *
 * Reuses the retry-wrapped client from [CloudLlmClientFactory] and the model
 * resolution from [CloudLlmModelResolver], so a cloud structured-output call
 * inherits the same transient-failure resilience and per-provider model
 * defaults as a free-form CLOUD node. The returned [StructuredInferenceClient]
 * collapses the streamed response to a single string for the
 * [app.kawkab.android.domain.engine.structured.StructuredOutputGate] to
 * validate.
 *
 * **No JSON mode.** The request carries no response schema and switches no
 * JSON mode on, whatever the model advertises: the gate is invoked with three
 * different output shapes (a JSON object, a top-level JSON array, and a bare
 * constrained token), and no single response schema fits all three. A cloud
 * answer can therefore be prose like a local one, and the gate validates and
 * repairs it with the same configured budget.
 *
 * **No temperature, on repairs either.** The gate asks for a lowered temperature on a
 * repair, and the request does not carry it. A hosted provider can reject a temperature
 * other than its default, so a repair would fail where it should fix the answer. Each
 * provider's own default applies, as on a first attempt; the on-device path keeps the
 * lowered temperature.
 *
 * **A cut stream is a failure.** An answer that ends without the finish reason its provider
 * always sends ([CloudStreamEnd]) is thrown as a [CloudStreamCutOffException] rather than
 * returned: the gate would otherwise validate half an answer, and a truncated token or list can
 * still parse.
 *
 * @property cloudLlmClientFactory Builds the retry-wrapped Koog client.
 * @property cloudLlmModelResolver Resolves the configured model per provider.
 * @property networkActivityTracker Records the outbound network pulse for the
 *   privacy footer, symmetric to the free-form CLOUD path.
 */
@Singleton
class KoogStructuredInferenceClientFactory @Inject constructor(
    private val cloudLlmClientFactory: CloudLlmClientFactory,
    private val cloudLlmModelResolver: CloudLlmModelResolver,
    private val networkActivityTracker: NetworkActivityTracker,
) : CloudStructuredInferenceClientFactory {

    override suspend fun create(
        provider: CloudProvider,
        onToken: suspend (String) -> Unit,
    ): StructuredInferenceClient? {
        val client = cloudLlmClientFactory.createClient(provider) as? LLMClient ?: return null
        val model = cloudLlmModelResolver.resolveModel(provider) as LLModel
        return KoogStructuredInferenceClient(provider, client, model, networkActivityTracker, onToken)
    }
}

/**
 * [StructuredInferenceClient] that runs one structured inference through a Koog
 * cloud client and returns the concatenated streamed text.
 *
 * @property provider The provider the client calls, for the cut-off rule and its message.
 * @property client The retry-wrapped Koog client to call.
 * @property model The resolved model descriptor.
 * @property networkActivityTracker Records the outbound network pulse.
 * @property onToken Invoked with each streamed token for the live "Thinking" UI.
 */
private class KoogStructuredInferenceClient(
    private val provider: CloudProvider,
    private val client: LLMClient,
    private val model: LLModel,
    private val networkActivityTracker: NetworkActivityTracker,
    private val onToken: suspend (String) -> Unit,
) : StructuredInferenceClient {

    /**
     * Runs one structured inference in the cloud.
     *
     * @param prompt The rendered prompt.
     * @param temperature The gate's override; deliberately not sent (see the factory's KDoc).
     * @return The concatenated streamed text.
     * @throws CloudStreamCutOffException When the stream ended without the finish reason this
     *   provider always sends.
     */
    override suspend fun infer(prompt: String, temperature: Float?): String {
        networkActivityTracker.recordOutbound()
        val builtPrompt = buildPrompt(id = "structured") { user(prompt) }
        val accumulated = StringBuilder()
        var finishReason: String? = null
        client.executeStreaming(builtPrompt, model).collect { frame ->
            // Told again per frame, so a long answer keeps the indicator "online".
            networkActivityTracker.recordOutbound()
            when (frame) {
                is StreamFrame.TextDelta -> {
                    accumulated.append(frame.text)
                    onToken(frame.text)
                }
                is StreamFrame.End -> finishReason = frame.finishReason
                else -> Unit
            }
        }
        if (CloudStreamEnd.cutOff(provider, model, finishReason)) throw CloudStreamCutOffException(provider.id)
        return accumulated.toString()
    }
}
