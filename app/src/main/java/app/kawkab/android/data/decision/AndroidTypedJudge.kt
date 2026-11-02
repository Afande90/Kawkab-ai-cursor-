package app.kawkab.android.data.decision

import app.kawkab.android.domain.decision.DecisionAudit
import app.kawkab.android.domain.decision.DecisionPolicy
import app.kawkab.android.domain.decision.DecisionRecord
import app.kawkab.android.domain.decision.DecisionResult
import app.kawkab.android.domain.decision.DecisionTrace
import app.kawkab.android.domain.decision.TypedJudge
import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.engine.TaskQueueManager
import app.kawkab.android.domain.models.isBusy
import app.kawkab.android.domain.repositories.LocalModelRepository
import app.kawkab.android.voice.JevRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Automatic typed decisions at the owner-selected threshold, with private outcome evidence. */
@Singleton
class AndroidTypedJudge @Inject constructor(
    private val models: LocalModelRepository,
    private val language: LlmInferenceEngine,
    private val tasks: TaskQueueManager,
    private val audit: DecisionAudit,
) : TypedJudge {
    private val lock = Mutex()

    override suspend fun ready(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (tasks.globalState.value.isBusy || !JevRuntime.available) return@withContext false
            val model = models.findByFileName(FILE_NAME) ?: return@withContext false
            models.currentFileHash(model.path) == SHA256
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            false
        }
    }

    override fun begin(sessionId: String, input: String) = audit.begin(sessionId, input)

    override suspend fun choose(state: String, question: String, options: List<String>): DecisionResult =
        withContext(Dispatchers.Default) {
            lock.withLock {
                val started = System.nanoTime()
                val sessionId = currentCoroutineContext()[DecisionTrace]?.sessionId
                val ask = DecisionResult.Ask(question)
                var logits: DoubleArray? = null
                var reason = "unavailable"
                val result = try {
                    val model = models.findByFileName(FILE_NAME)
                    if (tasks.globalState.value.isBusy ||
                        !JevRuntime.available ||
                        model == null ||
                        models.currentFileHash(model.path) != SHA256
                    ) {
                        ask
                    } else {
                        language.close()
                        try {
                            logits = JevRuntime.scores(model.path, state, question, options.toTypedArray())
                            reason = "model"
                            when (val choice = DecisionPolicy.choose(options, logits)) {
                                is DecisionResult.Chosen -> choice
                                is DecisionResult.Ask -> ask
                            }
                        } finally {
                            JevRuntime.release()
                        }
                    }
                } catch (cancel: CancellationException) {
                    audit.decision(
                        DecisionRecord(sessionId, state, question, options, logits, ask, "cancelled", elapsed(started)),
                    )
                    throw cancel
                } catch (_: LinkageError) {
                    reason = "runtime_unavailable"
                    ask
                } catch (_: Exception) {
                    reason = "inference_failed"
                    ask
                }
                val saved = audit.decision(
                    DecisionRecord(sessionId, state, question, options, logits, result, reason, elapsed(started)),
                )
                if (saved) result else ask
            }
        }

    private fun elapsed(started: Long): Long = (System.nanoTime() - started) / NANOS_PER_MS

    /** Pinned public, ungated, Apache-2.0 weights; downloaded only at the owner's request. */
    companion object {
        private const val NANOS_PER_MS = 1_000_000L

        /** Internal file identity; hidden from ordinary Settings. */
        const val FILE_NAME = "Jev-Style-0.8B-Decision-v3-Q4_K_M.gguf"

        /** Exact immutable public model bytes. */
        const val SHA256 = "0a19bc29bacc33e0d871146c8612b24dd14c2ed2e61cedeb7a928b0852628bac"

        /** No floating revision or sign-in. */
        const val URL = "https://huggingface.co/chaoliangUNSW/Jev-Style-0.8B-Decision-v3-GGUF/resolve/" +
            "30bfb1168258226bdc658c9d3a9e2fa98bd2e9f5/" + FILE_NAME
    }
}
