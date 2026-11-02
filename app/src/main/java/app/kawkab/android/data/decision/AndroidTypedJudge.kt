package app.kawkab.android.data.decision

import android.content.Context
import app.kawkab.android.domain.decision.DecisionCalibration
import app.kawkab.android.domain.decision.DecisionPolicy
import app.kawkab.android.domain.decision.DecisionResult
import app.kawkab.android.domain.decision.TypedJudge
import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.engine.TaskQueueManager
import app.kawkab.android.domain.models.isBusy
import app.kawkab.android.domain.repositories.LocalModelRepository
import app.kawkab.android.voice.JevRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The exact installed GGUF and a model-specific final-test certificate are both mandatory. */
@Singleton
class AndroidTypedJudge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: LocalModelRepository,
    private val language: LlmInferenceEngine,
    private val tasks: TaskQueueManager,
) : TypedJudge {
    private val lock = Mutex()

    override suspend fun ready(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (tasks.globalState.value.isBusy || !JevRuntime.available) return@runCatching false
            val model = models.findByFileName(FILE_NAME) ?: return@runCatching false
            val certificate = JSONObject(File(context.noBackupFilesDir, "decision/calibration.json").readText())
            models.currentFileHash(model.path) == SHA256 &&
                certificate.optBoolean("production_ready") &&
                certificate.optString("model_sha256") == SHA256
        }.getOrDefault(false)
    }

    override suspend fun choose(state: String, question: String, options: List<String>): DecisionResult =
        withContext(Dispatchers.Default) {
            lock.withLock {
                val ask = DecisionResult.Ask(question)
                try {
                    if (tasks.globalState.value.isBusy || !JevRuntime.available) return@withLock ask
                    val model = models.findByFileName(FILE_NAME) ?: return@withLock ask
                    if (models.currentFileHash(model.path) != SHA256) return@withLock ask
                    val certificate = JSONObject(File(context.noBackupFilesDir, "decision/calibration.json").readText())
                    // The bundled smoke fixtures test wiring; they cannot certify real phone precision.
                    if (!certificate.optBoolean("production_ready") ||
                        certificate.getString("model_sha256") != SHA256
                    ) {
                        return@withLock ask
                    }
                    val calibration = DecisionCalibration(
                        SHA256,
                        certificate.getDouble("temperature"),
                        certificate.getDouble("threshold"),
                        certificate.getDouble("final_precision"),
                        certificate.getInt("final_acted_count"),
                    )
                    language.close()
                    try {
                        val logits = JevRuntime.scores(model.path, state, question, options.toTypedArray())
                        when (val result = DecisionPolicy.choose(options, logits, calibration)) {
                            is DecisionResult.Chosen -> result
                            is DecisionResult.Ask -> ask
                        }
                    } finally {
                        JevRuntime.release()
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    ask
                }
            }
        }

    /** Pinned public, ungated, Apache-2.0 weights; downloaded only at the owner's request. */
    companion object {
        /** Internal file identity; hidden from ordinary Settings. */
        const val FILE_NAME = "Jev-Style-0.8B-Decision-v3-Q4_K_M.gguf"

        /** Exact immutable public model bytes. */
        const val SHA256 = "0a19bc29bacc33e0d871146c8612b24dd14c2ed2e61cedeb7a928b0852628bac"

        /** No floating revision or sign-in. */
        const val URL = "https://huggingface.co/chaoliangUNSW/Jev-Style-0.8B-Decision-v3-GGUF/resolve/" +
            "30bfb1168258226bdc658c9d3a9e2fa98bd2e9f5/" + FILE_NAME
    }
}
