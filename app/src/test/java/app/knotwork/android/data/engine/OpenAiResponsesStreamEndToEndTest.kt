package app.knotwork.android.data.engine

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import app.knotwork.android.domain.engine.CloudLlmClientFactory
import app.knotwork.android.domain.engine.executors.CloudLlmNodeExecutor
import app.knotwork.android.domain.models.CloudProvider
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.repositories.ApiKeyRepository
import app.knotwork.android.domain.repositories.GenerationSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import kotlin.time.Duration.Companion.seconds

/**
 * A CLOUD node answered by OpenAI, end to end: Koog's real OpenAI client against a local server, the
 * real [CloudLlmNodeExecutor], and the model [KoogCloudLlmModelResolver] resolves for a saved id.
 *
 * Koog streams a model through Chat Completions when it supports that endpoint and through the
 * Responses API otherwise. The two end a healthy answer differently: Chat Completions with a finish
 * reason, Responses with a `response.completed` event that Koog turns into an end frame *without*
 * one. The executor's truncation check therefore has to know which path a model takes — judged
 * wrongly, it discarded every answer of the Codex and Pro models as cut off. The server here
 * answers each path in its own shape, so a model Koog routes differently from the executor's
 * assumption fails the per-model test below.
 */
class OpenAiResponsesStreamEndToEndTest {

    /** The request paths the server saw, in order. */
    private val paths: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Whether the server cuts each answer before it finishes. */
    @Volatile
    private var cutStreams = false

    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                paths += path
                return when (path) {
                    RESPONSES_PATH -> sse(if (cutStreams) RESPONSES_EVENTS.dropLast(1) else RESPONSES_EVENTS)
                    CHAT_PATH -> sse(if (cutStreams) CHAT_CHUNKS.dropLast(2) else CHAT_CHUNKS)
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `given each OpenAI model the app offers when its answer streams whole then the node delivers it`() {
        val offered = checkNotNull(KoogCloudModelCatalog().offeredModels(CloudProvider.OPENAI))

        val failures = offered.mapNotNull { id ->
            val result = runNode(id)
            "$id: ${result.error}".takeIf { result.outputText != ANSWER || result.error != null }
        }

        assertTrue("healthy answers rejected:\n${failures.joinToString("\n")}", failures.isEmpty())
        // Not vacuous: the list must exercise both of the client's paths, or the routing rule the
        // executor depends on is not being tested at all.
        assertTrue("no model went through the Responses API", RESPONSES_PATH in paths)
        assertTrue("no model went through Chat Completions", CHAT_PATH in paths)
    }

    @Test
    fun `given a Responses-only model when its answer streams whole then it is delivered, not called cut off`() {
        val result = runNode(OpenAIModels.Chat.GPT5_2Codex.id)

        assertEquals(listOf(RESPONSES_PATH), paths)
        assertNull(result.error)
        assertEquals(ANSWER, result.outputText)
    }

    @Test
    fun `given a Responses-only model when the stream stops before it completes then the node fails`() {
        cutStreams = true

        val result = runNode(OpenAIModels.Chat.GPT5_2Codex.id)

        assertEquals(listOf(RESPONSES_PATH), paths)
        assertNull("the partial text must not become node output", result.outputText)
        assertTrue("expected a failure, got none", result.error != null)
    }

    @Test
    fun `given a Chat Completions model when its answer streams whole then it is delivered`() {
        val result = runNode(OpenAIModels.Chat.GPT5_4.id)

        assertEquals(listOf(CHAT_PATH), paths)
        assertNull(result.error)
        assertEquals(ANSWER, result.outputText)
    }

    @Test
    fun `given a Chat Completions model when the stream stops without a finish reason then it is called cut off`() {
        cutStreams = true

        val result = runNode(OpenAIModels.Chat.GPT5_4.id)

        assertEquals(listOf(CHAT_PATH), paths)
        assertNull("the partial text must not become node output", result.outputText)
        assertTrue(result.error!!.contains("cut off"))
    }

    /**
     * Runs one CLOUD node set to OpenAI, with [modelId] saved as the provider's model, on a client
     * of its own that is closed afterwards.
     *
     * @param modelId The saved model id.
     * @return The node's result.
     */
    private fun runNode(modelId: String): NodeExecutionResult = client().use { client ->
        runNode(modelId, client)
    }

    /**
     * Runs one CLOUD node set to OpenAI on [client], with [modelId] saved as the provider's model.
     *
     * @param modelId The saved model id.
     * @param client The Koog client the node's factory hands out.
     * @return The node's result.
     */
    private fun runNode(modelId: String, client: OpenAILLMClient): NodeExecutionResult = runBlocking {
        withTimeout(NODE_DEADLINE) {
            val keys = mockk<ApiKeyRepository> {
                every { getModel(CloudProvider.OPENAI) } returns flowOf(modelId)
            }
            val factory = mockk<CloudLlmClientFactory> {
                coEvery { createClient(CloudProvider.OPENAI, any()) } returns client
            }
            val settings = mockk<GenerationSettings> {
                every { systemPromptPrefix } returns flowOf("")
            }
            val executor = CloudLlmNodeExecutor(
                settings,
                keys,
                mockk(relaxed = true),
                factory,
                KoogCloudLlmModelResolver(keys, KoogCloudModelCatalog()),
                mockk(relaxed = true),
            )
            val node = NodeModel("1", NodeType.CLOUD, 0f, 0f, cloudProvider = CloudProvider.OPENAI.id)
            executor.execute(node, "input", "s1", "Q").toList()
                .filterIsInstance<NodeOutput.Result>().single().result
        }
    }

    /** Koog's OpenAI client, sending to the local server, with deadlines that end a stuck test. */
    private fun client() = OpenAILLMClient(
        apiKey = "test-key",
        settings = OpenAIClientSettings(
            baseUrl = server.url("/").toString().trimEnd('/'),
            timeoutConfig = ConnectionTimeoutConfig(
                requestTimeoutMillis = 10_000,
                connectTimeoutMillis = 5_000,
                socketTimeoutMillis = 5_000,
            ),
        ),
        httpClientFactory = KtorKoogHttpClient.Factory(),
    )

    private companion object {
        const val RESPONSES_PATH = "/v1/responses"
        const val CHAT_PATH = "/v1/chat/completions"
        const val ANSWER = "Hello world"
        val NODE_DEADLINE = 30.seconds

        /** A Responses stream: two text deltas, then the completion event. */
        val RESPONSES_EVENTS: List<String> = listOf(
            event(
                "response.output_text.delta",
                """{"type":"response.output_text.delta","item_id":"msg_1","output_index":0,""" +
                    """"content_index":0,"delta":"Hello ","sequence_number":1}""",
            ),
            event(
                "response.output_text.delta",
                """{"type":"response.output_text.delta","item_id":"msg_1","output_index":0,""" +
                    """"content_index":0,"delta":"world","sequence_number":2}""",
            ),
            event(
                "response.completed",
                """{"type":"response.completed","sequence_number":3,"response":{"id":"resp_1",""" +
                    """"object":"response","created_at":1,"model":"m","output":[],""" +
                    """"parallel_tool_calls":true,"status":"completed","text":{"format":{"type":"text"}}}}""",
            ),
        )

        /** A Chat Completions stream: the text, the chunk with the finish reason, and `[DONE]`. */
        val CHAT_CHUNKS: List<String> = listOf(
            data(
                """{"id":"x","object":"chat.completion.chunk","created":1,"model":"m",""" +
                    """"choices":[{"index":0,"delta":{"role":"assistant","content":"$ANSWER"},""" +
                    """"finish_reason":null}]}""",
            ),
            data(
                """{"id":"x","object":"chat.completion.chunk","created":1,"model":"m",""" +
                    """"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""",
            ),
            data("[DONE]"),
        )

        fun event(name: String, json: String): String = "event: $name\ndata: $json\n\n"

        fun data(json: String): String = "data: $json\n\n"

        fun sse(parts: List<String>): MockResponse = MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "text/event-stream")
            .body(parts.joinToString(""))
            .build()
    }
}
