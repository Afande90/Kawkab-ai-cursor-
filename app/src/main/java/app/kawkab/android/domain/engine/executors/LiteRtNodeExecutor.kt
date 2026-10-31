package app.kawkab.android.domain.engine.executors

import app.kawkab.android.domain.constants.DefaultPrompts
import app.kawkab.android.domain.engine.EngineWindow
import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.engine.StreamInferenceMeter
import app.kawkab.android.domain.engine.structured.ReasoningBlockSplitter
import app.kawkab.android.domain.harness.HarnessFacts
import app.kawkab.android.domain.harness.HarnessPrompt
import app.kawkab.android.domain.models.AgentOrchestratorState
import app.kawkab.android.domain.models.ConsoleEventType
import app.kawkab.android.domain.models.ExecutionScope
import app.kawkab.android.domain.models.NodeExecutionResult
import app.kawkab.android.domain.models.NodeModel
import app.kawkab.android.domain.models.NodeOutput
import app.kawkab.android.domain.models.Result
import app.kawkab.android.domain.models.RunNoticeCause
import app.kawkab.android.domain.repositories.GenerationSettings
import app.kawkab.android.domain.repositories.MetricsRepository
import app.kawkab.android.domain.repositories.ModelPerformanceRepository
import app.kawkab.android.domain.services.NativeMemorySampler
import app.kawkab.android.domain.usecases.LoadModelUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

/**
 * Executor for [NodeType.LITE_RT][app.kawkab.android.domain.models.NodeType.LITE_RT] nodes.
 *
 * Runs inference on the local LiteRT engine: ensures the requested model is loaded (via
 * [LoadModelUseCase]), builds the full prompt from the node's system prompt plus the
 * upstream `inputText` produced by `NodeContextBuilder`, and streams tokens back as
 * orchestrator state updates. The final response is emitted as a
 * [NodeOutput.Result] together with the per-token count used by the metrics repository.
 *
 * Cancellation is rethrown explicitly so that structured concurrency keeps working — a
 * broad `catch (Exception)` would otherwise swallow it and leak coroutines.
 */
class LiteRtNodeExecutor @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val generationSettings: GenerationSettings,
    private val metricsRepository: MetricsRepository,
    private val modelPerformanceRepository: ModelPerformanceRepository,
    private val nativeMemorySampler: NativeMemorySampler,
    private val loadModelUseCase: LoadModelUseCase,
    private val facts: HarnessFacts = HarnessFacts(),
) : NodeExecutor {

    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
        runId: String?,
        scope: ExecutionScope,
    ): Flow<NodeOutput> = flow {
        val systemPromptPrefix = generationSettings.systemPromptPrefix.first()
        val nodeSystemPrompt = node.systemPrompt ?: DefaultPrompts.LiteRt.SYSTEM_FALLBACK
        val baseSystemPrompt = HarnessPrompt.top(
            ownerName = "",
            facts = facts.snapshot(),
            rest = systemPromptPrefix,
        ) + "\n$nodeSystemPrompt\n"

        // `inputText` is the assembled context produced upstream by NodeContextBuilder
        // according to the node's NodeContextConfig. Re-fetching chat history or
        // long-term memory here would silently override those flags and break the
        // per-node context-config contract.
        val fullPrompt = "$baseSystemPrompt\n\n$inputText\nAGENT: "

        val startTime = System.currentTimeMillis()

        // The engine only ever sees an image on the node the engine chose to
        // deliver it to (the first vision-eligible LITE_RT node); every other
        // node has `scope.imagePath == null`. An image-carrying node needs the
        // engine loaded in vision mode, so request it on the load.
        val imagePath = scope.imagePath
        val loadResult = loadModelUseCase(node.modelPath, requireVision = imagePath != null)
        if (loadResult is Result.Error) {
            // Even the reduced window did not fit: run this reply on another Gemma and say so.
            val outOfMemory = EngineWindow.isAllocationFailure(loadResult.message, loadResult.throwable)
            val fallback = if (imagePath == null && outOfMemory) {
                loadModelUseCase.loadFallbackBrain(failedPath = node.modelPath?.takeIf { it.isNotBlank() })
            } else {
                null
            }
            if (fallback == null) {
                val message = if (outOfMemory) OUT_OF_MEMORY_MESSAGE else "Error loading local model"
                emit(NodeOutput.State(AgentOrchestratorState.Error(message)))
                emit(NodeOutput.Result(NodeExecutionResult(error = message)))
                return@flow
            }
            emit(
                NodeOutput.State(
                    AgentOrchestratorState.RunNotice(
                        RunNoticeCause.ModelSwitched(failedModel = null, usedModel = fallback.name),
                    ),
                ),
            )
        }

        val accumulatedResponse = StringBuilder()
        var emittedThinking = false

        // Performance instrumentation: TTFT is measured from the start of stream
        // consumption (after the model is loaded), and peak native memory is
        // sampled at a throttled cadence across the generation window. The meter
        // is the single source shared with the benchmark.
        val meter = StreamInferenceMeter(nativeMemorySampler)

        // "Failed to allocate tensors" gets two recoveries before it is an error:
        // the same model with a smaller window, then another installed Gemma for
        // this reply only (task 23f). Any other failure ends the node at once.
        var recoveryStep = 0
        while (true) {
            try {
                scope.inference.local(llmEngine, fullPrompt, imagePath = imagePath).collect { token ->
                    meter.onToken(System.currentTimeMillis())

                    accumulatedResponse.append(token)
                    if (!emittedThinking) {
                        emit(NodeOutput.State(AgentOrchestratorState.Thinking(accumulatedResponse.toString())))
                        emittedThinking = true
                    } else {
                        emit(NodeOutput.State(AgentOrchestratorState.Answering(accumulatedResponse.toString())))
                    }
                }
                break
            } catch (e: CancellationException) {
                // Preserve structured-concurrency cancellation: a broad `catch (Exception)`
                // would silently swallow cancellation and leave the parent coroutine running.
                throw e
            } catch (e: Exception) {
                Timber.tag(
                    "PipelineDebug",
                ).e(e, "[NODE_ERR] type=%s id=%s error in LiteRtNodeExecutor generation", node.type.name, node.id)
                val allocation = EngineWindow.isAllocationFailure(e.message, e) && accumulatedResponse.isEmpty()
                if (!allocation) {
                    val message = e.message ?: "Unknown error during LLM generation"
                    emit(NodeOutput.State(AgentOrchestratorState.Error(message)))
                    emit(NodeOutput.Result(NodeExecutionResult(error = e.message)))
                    return@flow
                }
                var recovered = false
                while (!recovered && recoveryStep < RECOVERY_STEPS) {
                    recovered = recover(recoveryStep, imagePath) { notice ->
                        emit(NodeOutput.State(AgentOrchestratorState.RunNotice(notice)))
                    }
                    recoveryStep += 1
                }
                if (!recovered) {
                    emit(NodeOutput.State(AgentOrchestratorState.Error(OUT_OF_MEMORY_MESSAGE)))
                    emit(NodeOutput.Result(NodeExecutionResult(error = OUT_OF_MEMORY_MESSAGE)))
                    return@flow
                }
            }
        }

        val endTime = System.currentTimeMillis()
        metricsRepository.updateMetrics(endTime - startTime, meter.tokenCount)

        // Record a per-model performance sample for the Performance card's
        // rolling average, keyed by the engine's concrete loaded path (robust to
        // the blank "Active model" sentinel in `node.modelPath`). Skipped for a
        // generation that produced no tokens (no useful timing to record).
        // `record` is best-effort at the repository boundary — a metrics-write
        // failure never reaches here — so no defensive wrapper is needed.
        val resolvedModelPath = llmEngine.currentModelPath
        if (resolvedModelPath != null && meter.tokenCount > 0) {
            modelPerformanceRepository.record(
                meter.toSample(resolvedModelPath, endMs = endTime, isBenchmark = false, createdAt = endTime),
            )
        }

        // A reasoning model's `<think>` scratchpad is separated here rather than
        // anywhere downstream, because everything downstream treats this text as
        // the answer: OUTPUT persists it as the agent's chat message, the next
        // turn replays it under `--- Chat History ---`, and `JsonPayloadExtractor`
        // spans from its first brace to its last. The split is deliberately not
        // recorded into the node result — the trace's `outputText` is replayed
        // verbatim as the node result when an interrupted run resumes, so raw
        // text stored there would put the scratchpad straight back into the
        // pipeline. The console line below is the durable trace of what happened.
        val split = ReasoningBlockSplitter.split(accumulatedResponse.toString().trim())
        val fullResponseText = split.answer
        split.reasoning?.let { reasoning ->
            emit(
                NodeOutput.Console(
                    ConsoleEventType.NodeExecution,
                    "LITE_RT '${node.label}' removed a ${reasoning.length}-character reasoning block " +
                        "from its answer",
                ),
            )
        }

        // Record which model produced this answer so the root OUTPUT can attribute
        // the persisted chat message to it rather than to whatever model is active
        // at render time. The most-recent answering node wins (this is the last
        // LITE_RT before OUTPUT on the taken path).
        scope.run.generatingModel.apply {
            localModelPath = resolvedModelPath
            cloudLabel = null
        }

        emit(NodeOutput.Result(NodeExecutionResult(outputText = fullResponseText, tokenCount = meter.tokenCount)))
    }

    /**
     * One recovery from an allocation failure.
     *
     * @param step 0 reopens the same model with [EngineWindow.REDUCED_RETRY_TOKENS]; 1 loads another
     *   installed Gemma for this reply and reports it through [notice].
     * @param imagePath An image run cannot move to a text-only fallback.
     * @return `true` when the generation may be tried again.
     */
    private suspend fun recover(step: Int, imagePath: String?, notice: suspend (RunNoticeCause) -> Unit): Boolean {
        val failedPath = llmEngine.currentModelPath
        return when (step) {
            0 -> {
                val window = llmEngine.activeContextLength
                (window == null || window > EngineWindow.REDUCED_RETRY_TOKENS) &&
                    llmEngine.reopenWithWindow(EngineWindow.REDUCED_RETRY_TOKENS) is Result.Success
            }
            1 -> {
                if (imagePath != null) return false
                val fallback = loadModelUseCase.loadFallbackBrain(failedPath) ?: return false
                notice(
                    RunNoticeCause.ModelSwitched(
                        failedModel = failedPath?.substringAfterLast('/'),
                        usedModel = fallback.name,
                    ),
                )
                true
            }
            else -> false
        }
    }

    private companion object {
        const val RECOVERY_STEPS = 2
        const val OUT_OF_MEMORY_MESSAGE =
            "The model ran out of working memory on this phone, even with a smaller window, and no other " +
                "model could take over. Open Models and pick Gemma 4 E2B, or try the GPU backend."
    }
}
