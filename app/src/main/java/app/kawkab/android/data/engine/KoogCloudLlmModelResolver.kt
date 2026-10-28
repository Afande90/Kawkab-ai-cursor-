package app.kawkab.android.data.engine

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import app.kawkab.android.domain.engine.CloudLlmModelResolver
import app.kawkab.android.domain.engine.CloudModelCatalog
import app.kawkab.android.domain.models.CloudProvider
import app.kawkab.android.domain.repositories.ApiKeyRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data-layer implementation of [CloudLlmModelResolver].
 *
 * Reads the user-configured model id for each provider from [ApiKeyRepository] and asks
 * [CloudModelCatalog] which model that means — the same answer the provider's screen, its row in
 * Settings and the node sheet show. Centralising this here keeps `CloudLlmNodeExecutor` free of
 * both data-layer constants (`KoogModelMapper`, `OpenAIModels`, …) and the per-provider model
 * lookup.
 */
@Singleton
class KoogCloudLlmModelResolver @Inject constructor(
    private val apiKeyRepository: ApiKeyRepository,
    private val modelCatalog: CloudModelCatalog,
) : CloudLlmModelResolver {

    /**
     * Resolves the Koog [LLModel] to use for a given provider. Exhaustive on
     * [CloudProvider] so the compiler flags any unhandled future provider.
     *
     * @param provider The typed [CloudProvider] to resolve a model object for.
     * @return The Koog [LLModel] cast to [Any] for the domain boundary.
     */
    override suspend fun resolveModel(provider: CloudProvider): Any {
        val inUse = modelCatalog.modelInUse(provider, apiKeyRepository.getModel(provider).first())
        return when (provider) {
            CloudProvider.OPENAI, CloudProvider.ANTHROPIC, CloudProvider.GOOGLE, CloudProvider.DEEPSEEK ->
                builtInModel(provider, checkNotNull(inUse) { "No default model for '${provider.id}'" }.id)
            CloudProvider.OLLAMA -> LLModel(
                provider = LLMProvider.Ollama,
                id = checkNotNull(inUse) { "No default model for '${provider.id}'" }.id,
                capabilities = listOf(LLMCapability.Completion),
                contextLength = apiKeyRepository.getOllamaContextWindowSize().first().toLong(),
            )
            // No default: the factory refuses a client while no model is chosen
            // (`MissingModel`), so reaching here without one means it was cleared in between.
            CloudProvider.OPENROUTER, CloudProvider.GROQ, CloudProvider.OPENAI_COMPATIBLE ->
                openAiCompatibleModel(checkNotNull(inUse) { "No model is selected for '${provider.id}'" }.id)
        }
    }

    /**
     * Koog's constant for [modelId] among the models offered for [provider]. The catalog only
     * answers with an offered id for these providers, so a miss is a broken invariant.
     */
    private fun builtInModel(provider: CloudProvider, modelId: String): LLModel =
        checkNotNull(KoogModelMapper.offeredModels(provider)?.firstOrNull { it.id == modelId }) {
            "'$modelId' is not offered for '${provider.id}'"
        }

    /**
     * A model asked for through the OpenAI API by an id the app has no catalogue entry for,
     * with exactly what a chat needs: `Completion` and the Chat Completions endpoint (without
     * the latter Koog refuses the request before sending it).
     *
     * Nothing the server does not declare is claimed. The app sends these models no tool
     * definitions, no images and no response format, so claiming more would change nothing —
     * or, for a JSON schema, switch off the app's own repair of structured output.
     *
     * @param modelId The chosen id, sent to the server as it is.
     * @return The Koog model.
     */
    private fun openAiCompatibleModel(modelId: String): LLModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = modelId,
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.OpenAIEndpoint.Completions,
            LLMCapability.Temperature,
        ),
    )
}
