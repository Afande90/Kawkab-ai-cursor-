package app.kawkab.android.data.engine

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import app.kawkab.android.domain.engine.CloudLlmClientFactory
import app.kawkab.android.domain.engine.CloudLlmModelResolver
import app.kawkab.android.domain.engine.CloudStreamCutOffException
import app.kawkab.android.domain.engine.structured.StructuredOutputGate
import app.kawkab.android.domain.models.CloudProvider
import app.kawkab.android.domain.repositories.NetworkActivityTracker
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [KoogStructuredInferenceClientFactory] — the cloud-backed
 * [app.kawkab.android.domain.engine.structured.StructuredInferenceClient]
 * seam for the structured-output gate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KoogStructuredInferenceClientFactoryTest {

    private val clientFactory = mockk<CloudLlmClientFactory>()
    private val modelResolver = mockk<CloudLlmModelResolver>()
    private val networkTracker = mockk<NetworkActivityTracker>(relaxed = true)
    private val factory = KoogStructuredInferenceClientFactory(clientFactory, modelResolver, networkTracker)

    @Test
    fun `given no configured client when create then returns null`() = runTest {
        coEvery { clientFactory.createClient(CloudProvider.OPENAI, any()) } returns null

        val result = factory.create(CloudProvider.OPENAI) { }

        assertNull(result)
    }

    @Test
    fun `given a model that advertises JSON when infer then the request carries no response schema`() = runTest {
        // The gate keeps its repair budget on a cloud engine because nothing in the
        // request constrains the answer to JSON — whatever the model advertises.
        val client = FakeStreamingClient(flowOf(StreamFrame.TextDelta("{}"), STOP))
        coEvery { clientFactory.createClient(CloudProvider.OPENAI, any()) } returns client
        coEvery { modelResolver.resolveModel(CloudProvider.OPENAI) } returns
            LLModel(LLMProvider.OpenAI, "gpt", listOf(LLMCapability.Schema.JSON.Standard))

        factory.create(CloudProvider.OPENAI) { }!!.infer("prompt", temperature = null)

        assertNull(client.lastPrompt?.params?.schema)
    }

    @Test
    fun `given a repair asking for a lowered temperature when infer then the request carries none`() = runTest {
        // Claude 4.7 and later answer a non-default temperature with a 400, which would fail the
        // repair that is meant to fix the answer; every provider gets its own default instead.
        val client = FakeStreamingClient(flowOf(StreamFrame.TextDelta("{}")))
        coEvery { clientFactory.createClient(CloudProvider.ANTHROPIC, any()) } returns client
        coEvery { modelResolver.resolveModel(CloudProvider.ANTHROPIC) } returns
            LLModel(LLMProvider.Anthropic, "claude-opus-5", listOf(LLMCapability.Temperature))

        factory.create(CloudProvider.ANTHROPIC) {
        }!!.infer("prompt", temperature = StructuredOutputGate.REPAIR_TEMPERATURE)

        assertNull(client.lastPrompt?.params?.temperature)
    }

    @Test
    fun `given streamed deltas when infer then concatenates and forwards each token`() = runTest {
        coEvery { clientFactory.createClient(CloudProvider.OPENAI, any()) } returns
            FakeStreamingClient(flowOf(StreamFrame.TextDelta("Hel"), StreamFrame.TextDelta("lo"), STOP))
        coEvery { modelResolver.resolveModel(CloudProvider.OPENAI) } returns
            LLModel(LLMProvider.OpenAI, "gpt", emptyList())

        val tokens = mutableListOf<String>()
        val result = factory.create(CloudProvider.OPENAI) { tokens.add(it) }
        val output = result!!.infer("prompt", temperature = null)

        assertEquals("Hello", output)
        assertEquals(listOf("Hel", "lo"), tokens)
        verify { networkTracker.recordOutbound() }
    }

    @Test
    fun `given a streamed answer when infer then the privacy indicator is told for as long as it streams`() = runTest {
        // Once before the call and once per frame, as on the free-form CLOUD path.
        coEvery { clientFactory.createClient(CloudProvider.OPENAI, any()) } returns
            FakeStreamingClient(flowOf(StreamFrame.TextDelta("{"), StreamFrame.TextDelta("}"), STOP))
        coEvery { modelResolver.resolveModel(CloudProvider.OPENAI) } returns
            LLModel(LLMProvider.OpenAI, "gpt", emptyList())

        factory.create(CloudProvider.OPENAI) {}!!.infer("prompt", temperature = null)

        verify(exactly = 4) { networkTracker.recordOutbound() }
    }

    @Test
    fun `given a stream that ends without a finish reason when infer then it fails as cut off`() = runTest {
        // A dropped connection ends an OpenAI-compatible stream normally: half a JSON array, or the
        // first word of a router's answer, could otherwise pass the gate as the whole answer.
        coEvery { clientFactory.createClient(CloudProvider.DEEPSEEK, any()) } returns
            FakeStreamingClient(flowOf(StreamFrame.TextDelta("[\"step one\", "), CUT))
        coEvery { modelResolver.resolveModel(CloudProvider.DEEPSEEK) } returns
            LLModel(LLMProvider.DeepSeek, "deepseek-chat", emptyList())
        val tokens = mutableListOf<String>()

        val error = try {
            factory.create(CloudProvider.DEEPSEEK) { tokens.add(it) }!!.infer("prompt", temperature = null)
            null
        } catch (e: CloudStreamCutOffException) {
            e
        }

        assertTrue(error?.message.orEmpty().contains("'deepseek' was cut off"))
        assertEquals(listOf("[\"step one\", "), tokens)
    }

    @Test
    fun `given a provider whose missing finish reason proves nothing when infer then the answer is returned`() =
        runTest {
            // Ollama's client never sends a finish reason, so a healthy answer looks exactly like this.
            coEvery { clientFactory.createClient(CloudProvider.OLLAMA, any()) } returns
                FakeStreamingClient(flowOf(StreamFrame.TextDelta("TRUE"), CUT))
            coEvery { modelResolver.resolveModel(CloudProvider.OLLAMA) } returns
                LLModel(LLMProvider.Ollama, "llama3", emptyList())

            assertEquals("TRUE", factory.create(CloudProvider.OLLAMA) { }!!.infer("prompt", temperature = null))
        }

    private companion object {
        /** The end frame a complete Chat Completions answer carries. */
        val STOP = StreamFrame.End(finishReason = "stop", metaInfo = ResponseMetaInfo.Empty)

        /** The end frame an OpenAI-compatible stream carries when its connection was cut. */
        val CUT = StreamFrame.End(finishReason = null, metaInfo = ResponseMetaInfo.Empty)
    }

    /** [LLMClient] returning a scripted streaming flow and keeping the prompt it got; other members are unused. */
    private class FakeStreamingClient(private val frames: Flow<StreamFrame>) : LLMClient() {
        var lastPrompt: Prompt? = null

        override fun llmProvider(): LLMProvider = LLMProvider.OpenAI
        override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant =
            error("unused")
        override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> {
            lastPrompt = prompt
            return frames
        }
        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("unused")
        override fun close() = Unit
    }
}
