package app.kawkab.android.domain.usecases

import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.models.LocalBackend
import app.kawkab.android.domain.models.Result
import app.kawkab.android.domain.repositories.GenerationSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Records the on-device backend for a first install and warms the model on it.
 *
 * A stored choice is honoured, including GPU. A phone with no stored choice
 * stays on CPU. The old first-install path probed the GPU and that is what
 * froze a mid-range phone, so this use case does not probe.
 *
 * The callback [invoke] still accepts is unused. Onboarding passes it so a
 * later check can show "checking acceleration" without changing the call.
 *
 * @property generationSettings Reads the raw backend preference and records CPU
 *   when nothing is stored.
 * @property loadModelUseCase Warm-up path.
 * @property llmInferenceEngine Torn down before a fallback reload so a failed
 *   write does not leave a handle from a previous attempt.
 */
class PrepareInferenceBackendUseCase @Inject constructor(
    private val generationSettings: GenerationSettings,
    private val loadModelUseCase: LoadModelUseCase,
    private val llmInferenceEngine: LlmInferenceEngine,
) {

    /**
     * Terminal outcome of the preparation, as far as the caller's UI cares.
     */
    sealed interface Outcome {

        /**
         * The model is loaded and ready to answer.
         *
         * @property backend The backend the handle ended up running on.
         */
        data class Warmed(val backend: LocalBackend) : Outcome

        /**
         * Warm-up failed; the caller surfaces [message] and offers a retry.
         *
         * @property message Human-readable failure text, or `null` when the
         *   underlying error carried none.
         */
        data class Failed(val message: String?) : Outcome
    }

    /**
     * Resolves the backend (when it has never been chosen) and warms the model.
     *
     * @param modelPath Absolute path of the model to warm up.
     * @param onAccelerationCheckStarted Kept for the onboarding screen. Not
     *   called: first install does not run an acceleration check.
     * @return [Outcome.Warmed] with the backend actually in use, or
     *   [Outcome.Failed] when the model could not load.
     */
    @Suppress("UnusedParameter") // Onboarding still passes this; a CPU default does not call it.
    suspend operator fun invoke(modelPath: String, onAccelerationCheckStarted: () -> Unit = {}): Outcome =
        withContext(Dispatchers.IO) {
            try {
                resolveAndWarmUp(modelPath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Deciding must never end worse than not deciding at all. The
                // decision path writes to settings, which can fail on its own
                // (a full or unwritable data dir) — and this runs inside the
                // onboarding warm-up, where an escaping exception would take
                // the user's very first run down with it.
                Timber.w(e, "Backend preparation failed — falling back to CPU.")
                fallBackToCpu(modelPath)
            }
        }

    private suspend fun resolveAndWarmUp(modelPath: String): Outcome {
        val stored = generationSettings.localModelBackendPreference.first()
        if (stored != null) {
            val backend = LocalBackend.runnableFromKey(stored)
            Timber.i("Stored backend '%s' warms on %s.", stored, backend.key)
            return warmUp(modelPath, backend)
        }
        Timber.i("No stored backend. First install stays on CPU.")
        generationSettings.setLocalModelBackend(LocalBackend.CPU.key)
        return warmUp(modelPath, LocalBackend.CPU)
    }

    /**
     * Records CPU, drops any live handle, and warms up again.
     *
     * The teardown is load-bearing: the engine may hold a handle built on
     * another backend, and its reuse check compares model path and modalities
     * but not the backend — so without it the reload would be a no-op.
     */
    private suspend fun fallBackToCpu(modelPath: String): Outcome {
        persistBackendQuietly(LocalBackend.CPU)
        llmInferenceEngine.unload()
        return warmUp(modelPath, LocalBackend.CPU)
    }

    /**
     * Records the resolved backend, absorbing a storage failure: by this point
     * the fallback is already happening, and failing to *write down* the safe
     * choice is no reason to deny the user a working handle. The cost of the
     * lost write is one repeated probe on the next journey.
     */
    private suspend fun persistBackendQuietly(backend: LocalBackend) {
        try {
            generationSettings.setLocalModelBackend(backend.key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not persist the resolved backend.")
        }
    }

    private suspend fun warmUp(modelPath: String, backend: LocalBackend): Outcome =
        when (val result = loadModelUseCase(modelPath)) {
            is Result.Success -> Outcome.Warmed(backend)
            is Result.Error -> Outcome.Failed(result.message)
        }
}
