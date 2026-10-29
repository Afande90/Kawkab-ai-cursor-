package app.kawkab.android.domain.usecases

import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.models.AppError
import app.kawkab.android.domain.models.LocalBackend
import app.kawkab.android.domain.models.Result
import app.kawkab.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Covers the first-install backend decision.
 *
 * A stored choice is warmed as stored. A phone with no choice stays on CPU,
 * even when a GPU is present. A failed settings write still leaves a CPU handle.
 */
class PrepareInferenceBackendUseCaseTest {

    private val modelPath = "/data/model.litertlm"

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var llmInferenceEngine: LlmInferenceEngine
    private lateinit var useCase: PrepareInferenceBackendUseCase

    @Before
    fun setUp() {
        settingsRepository = mockk(relaxed = true)
        every { settingsRepository.localModelBackendPreference } returns flowOf(null)
        loadModelUseCase = mockk()
        coEvery { loadModelUseCase.invoke(any(), any(), any()) } returns Result.Success(Unit)
        llmInferenceEngine = mockk(relaxed = true)
        useCase = PrepareInferenceBackendUseCase(
            generationSettings = settingsRepository,
            loadModelUseCase = loadModelUseCase,
            llmInferenceEngine = llmInferenceEngine,
        )
    }

    @Test
    fun `given a stored preference when preparing then the choice is honoured and nothing is probed`() = runTest {
        every { settingsRepository.localModelBackendPreference } returns flowOf(LocalBackend.CPU.key)

        val outcome = useCase(modelPath)

        assertEquals(PrepareInferenceBackendUseCase.Outcome.Warmed(LocalBackend.CPU), outcome)
        // An explicit CPU choice must look nothing like "never chosen": no write.
        coVerify(exactly = 0) { settingsRepository.setLocalModelBackend(any()) }
        coVerify(exactly = 1) { loadModelUseCase.invoke(modelPath, any(), any()) }
    }

    @Test
    fun `given an NPU choice stored by an earlier version when preparing then it warms up on CPU`() = runTest {
        every { settingsRepository.localModelBackendPreference } returns flowOf(LocalBackend.NPU.key)

        val outcome = useCase(modelPath)

        assertEquals(PrepareInferenceBackendUseCase.Outcome.Warmed(LocalBackend.CPU), outcome)
    }

    @Test
    fun `given an unreadable stored key when preparing then it is treated as an explicit choice`() = runTest {
        every { settingsRepository.localModelBackendPreference } returns flowOf("QPU")

        val outcome = useCase(modelPath)

        // A value we cannot parse is still a value the user (or a future build)
        // wrote — falling back to CPU is safe, silently re-probing is not.
        assertEquals(PrepareInferenceBackendUseCase.Outcome.Warmed(LocalBackend.CPU), outcome)
    }

    @Test
    fun `given a stored GPU choice when preparing then it warms on GPU and writes nothing`() = runTest {
        every { settingsRepository.localModelBackendPreference } returns flowOf(LocalBackend.GPU.key)

        val outcome = useCase(modelPath)

        assertEquals(PrepareInferenceBackendUseCase.Outcome.Warmed(LocalBackend.GPU), outcome)
        coVerify(exactly = 0) { settingsRepository.setLocalModelBackend(any()) }
        coVerify(exactly = 1) { loadModelUseCase.invoke(modelPath, any(), any()) }
    }

    @Test
    fun `given no preference when preparing then CPU is recorded and warmed`() = runTest {
        var checkAnnounced = false

        val outcome = useCase(modelPath) { checkAnnounced = true }

        assertEquals(PrepareInferenceBackendUseCase.Outcome.Warmed(LocalBackend.CPU), outcome)
        coVerify(exactly = 1) { settingsRepository.setLocalModelBackend(LocalBackend.CPU.key) }
        coVerify(exactly = 1) { loadModelUseCase.invoke(modelPath, any(), any()) }
        // Nothing was checked, so nothing is announced to the user.
        assertEquals(false, checkAnnounced)
    }

    @Test
    fun `given a settings write that fails when preparing then the handle is still warmed on CPU`() = runTest {
        coEvery { settingsRepository.setLocalModelBackend(any()) } throws IOException("no space left on device")

        val outcome = useCase(modelPath)

        // Failing to record the decision must not cost the user a working
        // handle — this runs inside onboarding, where an escaping exception
        // would break the first run entirely.
        assertEquals(PrepareInferenceBackendUseCase.Outcome.Warmed(LocalBackend.CPU), outcome)
        coVerify(exactly = 1) { loadModelUseCase.invoke(modelPath, any(), any()) }
    }

    @Test
    fun `given the CPU fallback also fails when preparing then the failure surfaces to the caller`() = runTest {
        coEvery { loadModelUseCase.invoke(any(), any(), any()) } returns Result.Error(
            error = object : AppError.System {},
            message = "model file is gone",
        )

        val outcome = useCase(modelPath)

        assertEquals(PrepareInferenceBackendUseCase.Outcome.Failed("model file is gone"), outcome)
    }
}
