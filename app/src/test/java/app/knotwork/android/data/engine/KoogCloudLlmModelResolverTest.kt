package app.knotwork.android.data.engine

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import app.knotwork.android.domain.models.CloudProvider
import app.knotwork.android.domain.repositories.ApiKeyRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [KoogCloudLlmModelResolver] — the model object a Cloud node sends, resolved from the
 * saved id through [KoogCloudModelCatalog], the same answer the screens show.
 */
class KoogCloudLlmModelResolverTest {

    private fun resolver(
        provider: CloudProvider,
        saved: String?,
        ollamaContext: Int = 8192,
    ): KoogCloudLlmModelResolver {
        val keys = mockk<ApiKeyRepository> {
            every { getModel(provider) } returns flowOf(saved)
            every { getOllamaContextWindowSize() } returns flowOf(ollamaContext)
        }
        return KoogCloudLlmModelResolver(keys, KoogCloudModelCatalog())
    }

    @Test
    fun `given an offered model saved then Koog's own constant is sent, capabilities and all`() = runTest {
        // The constant, not a look-alike: Koog routes a Responses-only model by its capabilities.
        val model = resolver(CloudProvider.OPENAI, OpenAIModels.Chat.GPT5_2Codex.id).resolveModel(CloudProvider.OPENAI)

        assertSame(OpenAIModels.Chat.GPT5_2Codex, model)
    }

    @Test
    fun `given nothing saved then the provider's default constant is sent`() = runTest {
        assertSame(OpenAIModels.Chat.GPT5_4, resolver(CloudProvider.OPENAI, null).resolveModel(CloudProvider.OPENAI))
        assertSame(
            AnthropicModels.Sonnet_4_5,
            resolver(CloudProvider.ANTHROPIC, null).resolveModel(CloudProvider.ANTHROPIC),
        )
    }

    @Test
    fun `given a saved model no longer offered then the default is sent, as the screen says`() = runTest {
        val model = resolver(CloudProvider.ANTHROPIC, "claude-3-opus").resolveModel(CloudProvider.ANTHROPIC)

        assertSame(AnthropicModels.Sonnet_4_5, model)
    }

    @Test
    fun `given Ollama then the typed model, or its default, is sent with the configured context`() = runTest {
        val typed = resolver(CloudProvider.OLLAMA, "qwen3:8b", ollamaContext = 16_384)
            .resolveModel(CloudProvider.OLLAMA) as LLModel
        val blank = resolver(CloudProvider.OLLAMA, "").resolveModel(CloudProvider.OLLAMA) as LLModel

        assertEquals("qwen3:8b", typed.id)
        assertEquals(LLMProvider.Ollama, typed.provider)
        assertEquals(16_384L, typed.contextLength)
        assertEquals("A blank field asks for the default, not an empty id.", "llama3", blank.id)
    }

    @Test
    fun `given a server the user runs then its model is asked for through Chat Completions`() = runTest {
        val model = resolver(CloudProvider.OPENAI_COMPATIBLE, "qwen2.5-7b-instruct")
            .resolveModel(CloudProvider.OPENAI_COMPATIBLE) as LLModel

        assertEquals("qwen2.5-7b-instruct", model.id)
        assertTrue(model.supports(LLMCapability.OpenAIEndpoint.Completions))
    }

    @Test
    fun `given a provider without a default and nothing saved then resolving fails rather than guessing`() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { resolver(CloudProvider.GROQ, null).resolveModel(CloudProvider.GROQ) }
        }
    }
}
