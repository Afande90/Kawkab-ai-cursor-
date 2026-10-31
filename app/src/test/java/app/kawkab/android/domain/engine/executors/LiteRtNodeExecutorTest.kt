package app.kawkab.android.domain.engine.executors

import app.kawkab.android.domain.engine.EngineWindow
import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.models.AgentOrchestratorState
import app.kawkab.android.domain.models.AppError
import app.kawkab.android.domain.models.ExecutionScope
import app.kawkab.android.domain.models.LocalModel
import app.kawkab.android.domain.models.ModelPerformanceSample
import app.kawkab.android.domain.models.NodeExecutionResult
import app.kawkab.android.domain.models.NodeModel
import app.kawkab.android.domain.models.NodeOutput
import app.kawkab.android.domain.models.NodeType
import app.kawkab.android.domain.models.Result
import app.kawkab.android.domain.models.RunNoticeCause
import app.kawkab.android.domain.repositories.ChatRepository
import app.kawkab.android.domain.repositories.MetricsRepository
import app.kawkab.android.domain.repositories.ModelPerformanceRepository
import app.kawkab.android.domain.repositories.SettingsRepository
import app.kawkab.android.domain.repositories.ToolRepository
import app.kawkab.android.domain.services.NativeMemorySampler
import app.kawkab.android.domain.usecases.LoadModelUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LiteRtNodeExecutorTest {

    private lateinit var llmEngine: LlmInferenceEngine
    private lateinit var toolRepository: ToolRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var metricsRepository: MetricsRepository
    private lateinit var modelPerformanceRepository: ModelPerformanceRepository
    private lateinit var nativeMemorySampler: NativeMemorySampler
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var executor: LiteRtNodeExecutor

    @Before
    fun setup() {
        llmEngine = mockk()
        toolRepository = mockk()
        chatRepository = mockk(relaxed = true)
        settingsRepository = mockk()
        metricsRepository = mockk(relaxed = true)
        modelPerformanceRepository = mockk(relaxed = true)
        nativeMemorySampler = mockk(relaxed = true)
        loadModelUseCase = mockk()

        // The executor reads the engine's concrete loaded path after load to key
        // the performance sample.
        every { llmEngine.currentModelPath } returns "/models/active.litertlm"

        executor = LiteRtNodeExecutor(
            llmEngine,
            settingsRepository,
            metricsRepository,
            modelPerformanceRepository,
            nativeMemorySampler,
            loadModelUseCase,
        )
    }

    @Test
    fun `execute forms prompt using node systemPrompt`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f, systemPrompt = "Custom Node Prompt")

        every { settingsRepository.systemPromptPrefix } returns flowOf("Prefix")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)

        val promptSlot = slot<String>()
        every { llmEngine.generateResponseStream(capture(promptSlot)) } returns flowOf("Result")

        executor.execute(node, "input", "session-1", "prompt").toList()

        val capturedPrompt = promptSlot.captured
        assertTrue(capturedPrompt.contains("Custom Node Prompt"))
        // The executor no longer wraps inputText in "USER/INPUT: " — it now passes the
        // already-assembled context produced upstream by NodeContextBuilder verbatim.
        assertTrue(capturedPrompt.contains("input"))
        assertTrue(capturedPrompt.endsWith("AGENT: "))
    }

    @Test
    fun `execute forms prompt using default systemPrompt when node prompt is empty`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f)

        every { settingsRepository.systemPromptPrefix } returns flowOf("Prefix")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)

        val promptSlot = slot<String>()
        every { llmEngine.generateResponseStream(capture(promptSlot)) } returns flowOf("Result")

        executor.execute(node, "input", "session-1", "prompt").toList()

        val capturedPrompt = promptSlot.captured
        assertTrue(capturedPrompt.contains("You are Kawkab"))
        assertTrue(capturedPrompt.contains("Owner:"))
    }

    @Test
    fun `execute does not refetch chat history or long-term memory inside the node`() = runTest {
        // Defect 1 regression guard: the engine assembles the context upstream and the
        // executor must consume `inputText` verbatim. The legacy executor refetched
        // `getContextWindowUseCase` and `retrieveRelevantMemoryUseCase`, doubly inflating
        // the prompt and silently overriding NodeContextConfig.
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f, systemPrompt = "Sys")
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)

        val promptSlot = slot<String>()
        every { llmEngine.generateResponseStream(capture(promptSlot)) } returns flowOf("ok")

        val assembledContext = "--- Original Task ---\nthe task\n\n--- Previous Node Output ---\nupstream"
        executor.execute(node, assembledContext, "session-1", "the task").toList()

        val capturedPrompt = promptSlot.captured
        // The executor must forward the assembled context verbatim and must not re-inject
        // "RELEVANT LONG-TERM MEMORIES:" headers or any chat history block of its own.
        assertTrue(capturedPrompt.contains(assembledContext))
        assertFalse(capturedPrompt.contains("RELEVANT LONG-TERM MEMORIES:"))
    }

    @Test
    fun `execute records tokenCount equal to number of stream emissions`() = runTest {
        // Defect 7 regression guard: each streamed token from LiteRT is one model token,
        // not a string of arbitrary length. Counting by `+= 1` keeps the metric truthful.
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f)
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)

        // Emit five tokens of varying lengths — the count must equal 5, not the sum of lengths.
        every { llmEngine.generateResponseStream(any()) } returns flowOf("a", "bb", "ccc", "dddd", "eeeee")

        val outputs = executor.execute(node, "input", "session-1", "prompt").toList()

        val result = outputs.filterIsInstance<NodeOutput.Result>().single().result
        assertEquals(5, result.tokenCount)
    }

    @Test
    fun `given scope carries an image path when execute then engine and loader run in vision mode`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f, modelPath = "model-path")
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any(), any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any(), any(), any()) } returns flowOf("ok")

        executor.execute(
            node = node,
            inputText = "input",
            sessionId = "session-1",
            originalPrompt = "prompt",
            scope = ExecutionScope(imagePath = "/abs/photo.jpg"),
        ).toList()

        // The image path the engine chose for this node must reach both the model
        // loader (so it initialises in vision mode) and the generation call.
        coVerify { loadModelUseCase("model-path", requireVision = true) }
        verify { llmEngine.generateResponseStream(any(), "/abs/photo.jpg", any()) }
    }

    @Test
    fun `given no image path when execute then engine and loader run in text mode`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f, modelPath = "model-path")
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any(), any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any(), any(), any()) } returns flowOf("ok")

        // Default scope → no image: a text node must never request vision nor pass
        // an image to the engine.
        executor.execute(node, "input", "session-1", "prompt").toList()

        coVerify { loadModelUseCase("model-path", requireVision = false) }
        verify { llmEngine.generateResponseStream(any(), null, any()) }
    }

    @Test
    fun `execute records a performance sample keyed by the loaded model path`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f)
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf("a", "b", "c")

        executor.execute(node, "input", "session-1", "prompt").toList()

        val sampleSlot = slot<ModelPerformanceSample>()
        coVerify { modelPerformanceRepository.record(capture(sampleSlot)) }
        val sample = sampleSlot.captured
        assertEquals("/models/active.litertlm", sample.modelPath)
        assertEquals(3, sample.tokenCount)
        assertFalse(sample.isBenchmark)
    }

    @Test
    fun `execute does not record a sample when the generation produced no tokens`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f)
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        // Empty stream → zero tokens → no useful timing → skip recording.
        every { llmEngine.generateResponseStream(any()) } returns flowOf()

        executor.execute(node, "input", "session-1", "prompt").toList()

        coVerify(exactly = 0) { modelPerformanceRepository.record(any()) }
    }

    @Test
    fun `execute does not record a sample when the engine reports no loaded path`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f)
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf("token")
        every { llmEngine.currentModelPath } returns null

        executor.execute(node, "input", "session-1", "prompt").toList()

        coVerify(exactly = 0) { modelPerformanceRepository.record(any()) }
    }

    @Test
    fun `given a reasoning block in the stream when execute then the result carries only the answer`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f)
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        // Streamed in pieces, the way a model actually produces it — the split
        // has to survive the block arriving across token boundaries.
        every { llmEngine.generateResponseStream(any()) } returns
            flowOf("<think>The user", " is home. Be warm.</think>", "\n\nПривет!")

        val outputs = executor.execute(node, "input", "session-1", "prompt").toList()

        // The result is what OUTPUT persists as the chat message and what the next
        // turn replays under `--- Chat History ---`. It must not carry scratchpad.
        val result = outputs.filterIsInstance<NodeOutput.Result>().single().result
        assertEquals("Привет!", result.outputText)
        // The removal is reported rather than silent: an answer that came back
        // shorter than the model produced should be explainable from the console.
        val console = outputs.filterIsInstance<NodeOutput.Console>()
        assertTrue("the split is announced: $console", console.any { it.message.contains("reasoning block") })
    }

    @Test
    fun `given no reasoning block when execute then the answer is untouched and nothing is announced`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f)
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf("Just an answer.")

        val outputs = executor.execute(node, "input", "session-1", "prompt").toList()

        val result = outputs.filterIsInstance<NodeOutput.Result>().single().result
        assertEquals("Just an answer.", result.outputText)
        assertTrue(
            "a model that never reasons must not produce console noise",
            outputs.filterIsInstance<NodeOutput.Console>().none { it.message.contains("reasoning block") },
        )
    }

    @Test
    fun `execute terminates with a NodeOutput Result and emits at least one State`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f)
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf("token")

        val outputs = executor.execute(node, "input", "session-1", "prompt").toList()

        // The terminal element must always be a NodeOutput.Result so the engine can
        // transition to the next node deterministically.
        assertTrue(outputs.last() is NodeOutput.Result)
        assertTrue(outputs.any { it is NodeOutput.State })
    }

    @Test
    fun `an allocation failure reopens the model with the reduced window and answers`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f, systemPrompt = "Sys")
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.activeContextLength } returns 4_096
        coEvery { llmEngine.reopenWithWindow(EngineWindow.REDUCED_RETRY_TOKENS) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
            flow { throw IllegalStateException("Status Code 13 Failed to allocate tensors") },
            flowOf("hello"),
        )

        val outputs = executor.execute(node, "hello", "session-1", "hello").toList()

        val result = outputs.filterIsInstance<NodeOutput.Result>().single().result
        assertEquals("hello", result.outputText)
        coVerify(exactly = 1) { llmEngine.reopenWithWindow(EngineWindow.REDUCED_RETRY_TOKENS) }
        coVerify(exactly = 0) { loadModelUseCase.loadFallbackBrain(any()) }
    }

    @Test
    fun `when the reduced window fails too, another Gemma answers and a notice says why`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f, systemPrompt = "Sys")
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.currentModelPath } returns "/models/gemma-4-E4B-it.litertlm"
        every { llmEngine.activeContextLength } returns 4_096
        coEvery { llmEngine.reopenWithWindow(any()) } returns Result.Error(error = TestError, message = "no")
        val e2b = LocalModel(id = 2, name = "gemma-4-E2B-it.litertlm", path = "/models/e2b", size = 1, isActive = false)
        coEvery { loadModelUseCase.loadFallbackBrain("/models/gemma-4-E4B-it.litertlm") } returns e2b
        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
            flow { throw IllegalStateException("Failed to allocate tensors") },
            flowOf("hi"),
        )

        val outputs = executor.execute(node, "hello", "session-1", "hello").toList()

        val notice = outputs.filterIsInstance<NodeOutput.State>()
            .map { it.state }
            .filterIsInstance<AgentOrchestratorState.RunNotice>()
            .single()
        assertEquals(
            RunNoticeCause.ModelSwitched(
                failedModel = "gemma-4-E4B-it.litertlm",
                usedModel = "gemma-4-E2B-it.litertlm",
            ),
            notice.cause,
        )
        assertEquals("hi", outputs.filterIsInstance<NodeOutput.Result>().single().result.outputText)
    }

    @Test
    fun `with no recovery left the error is in plain words, not a stack line`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f, systemPrompt = "Sys")
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.activeContextLength } returns EngineWindow.REDUCED_RETRY_TOKENS
        coEvery { loadModelUseCase.loadFallbackBrain(any()) } returns null
        every { llmEngine.generateResponseStream(any()) } returns
            flow { throw IllegalStateException("llm_litert_compiled_model_executor.cc:743 Failed to allocate tensors") }

        val outputs = executor.execute(node, "hello", "session-1", "hello").toList()

        val error = outputs.filterIsInstance<NodeOutput.Result>().single().result.error!!
        assertTrue(error.contains("ran out of working memory"))
        assertFalse(error.contains(".cc:"))
        coVerify(exactly = 0) { llmEngine.reopenWithWindow(any()) }
    }

    @Test
    fun `a reply that only names a tool is re-asked once and then becomes a plain sentence`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f, systemPrompt = "Sys")
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(flowOf("set_alarm"), flowOf("set_alarm"))

        val outputs = executor.execute(node, "Set an alarm for 6", "session-1", "Set an alarm for 6").toList()

        val text = outputs.filterIsInstance<NodeOutput.Result>().single().result.outputText
        assertEquals("I couldn't do that, try: set an alarm for 6", text)
        verify(exactly = 2) { llmEngine.generateResponseStream(any()) }
    }

    @Test
    fun `a JSON tool call runs through the tool gate and its result is the reply`() = runTest {
        val gate = mockk<ToolInvocationGate>()
        val withGate = LiteRtNodeExecutor(
            llmEngine,
            settingsRepository,
            metricsRepository,
            modelPerformanceRepository,
            nativeMemorySampler,
            loadModelUseCase,
            toolGate = gate,
        )
        coEvery { gate.dispatch(any(), any(), any(), any(), any(), "set_alarm", any(), false) } coAnswers {
            firstArg<FlowCollector<NodeOutput>>().emit(
                NodeOutput.Result(
                    NodeExecutionResult(outputText = "Alarm set for 6:00 AM.", resolvedToolName = "set_alarm"),
                ),
            )
        }
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f, systemPrompt = "Sys")
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns
            flowOf("{\"tool\": \"set_alarm\", \"arguments\": {\"hour\": 6}}")

        val outputs = withGate.execute(node, "wake me at 6", "session-1", "wake me at 6").toList()

        assertEquals("Alarm set for 6:00 AM.", outputs.filterIsInstance<NodeOutput.Result>().single().result.outputText)
        coVerify(exactly = 1) {
            gate.dispatch(any(), any(), any(), "session-1", any(), "set_alarm", "{\"hour\":6}", false)
        }
    }

    private object TestError : AppError.System
}
