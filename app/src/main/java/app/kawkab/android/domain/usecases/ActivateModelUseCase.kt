package app.kawkab.android.domain.usecases

import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.models.AppError
import app.kawkab.android.domain.models.Result
import app.kawkab.android.domain.repositories.LocalModelRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Makes a model the active one only after its file exists and it loaded once (task 23e).
 *
 * Selecting a GGUF brain that was not downloaded, or that llama.cpp could not open, used to leave
 * chat with "No active model" while the header still named it. Now the previous model stays
 * active and the owner reads why.
 */
class ActivateModelUseCase @Inject constructor(
    private val localModelRepository: LocalModelRepository,
    private val engine: LlmInferenceEngine,
) {
    /** Why a model was not activated. */
    object NotActivated : AppError.System

    /**
     * @param modelId The model to activate.
     * @return [Result.Success] once it is active, or an error with a sentence for the owner.
     */
    suspend operator fun invoke(modelId: Long): Result<Unit, AppError> = withContext(Dispatchers.IO) {
        val model = localModelRepository.getAllModels().first().firstOrNull { it.id == modelId }
            ?: return@withContext Result.Error(error = NotActivated, message = "That model is not installed.")
        if (!File(model.path).isFile) {
            return@withContext Result.Error(
                error = NotActivated,
                message = "${model.name} is not downloaded yet. Download it first.",
            )
        }
        val loaded = try {
            engine.initialize(model.path, enableVision = false, enableAudio = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(error = NotActivated, message = e.message, throwable = e)
        }
        if (loaded is Result.Error) {
            return@withContext Result.Error(
                error = NotActivated,
                message = "${model.name} did not load on this phone, so the previous model stays active.",
            )
        }
        localModelRepository.setActiveModel(modelId)
        Result.Success(Unit)
    }
}
