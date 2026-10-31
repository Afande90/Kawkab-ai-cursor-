package app.kawkab.android.domain.usecases

import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.models.AppError
import app.kawkab.android.domain.models.LocalModel
import app.kawkab.android.domain.models.Result
import app.kawkab.android.domain.repositories.LocalModelRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Task 23e: a model is never marked active unless its file exists and it loaded once. */
class ActivateModelUseCaseTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val repository = mockk<LocalModelRepository>(relaxed = true)
    private val engine = mockk<LlmInferenceEngine>()
    private val useCase = ActivateModelUseCase(repository, engine)

    @Test
    fun `a GGUF that is not downloaded is not activated`() = runTest {
        every { repository.getAllModels() } returns flowOf(listOf(model(1, "/nowhere/Qwen3-1.7B-Q4_K_M.gguf")))

        val outcome = useCase(1)

        assertTrue(outcome is Result.Error && outcome.message!!.contains("not downloaded"))
        coVerify(exactly = 0) { repository.setActiveModel(any()) }
    }

    @Test
    fun `a GGUF that does not load is not activated`() = runTest {
        val file = folder.newFile("broken.gguf")
        every { repository.getAllModels() } returns flowOf(listOf(model(2, file.absolutePath)))
        coEvery { engine.initialize(file.absolutePath, false, false) } returns
            Result.Error(error = Fail, message = "bad")

        val outcome = useCase(2)

        assertTrue(outcome is Result.Error && outcome.message!!.contains("previous model stays active"))
        coVerify(exactly = 0) { repository.setActiveModel(any()) }
    }

    @Test
    fun `a model that loads becomes active`() = runTest {
        val file = folder.newFile("ok.gguf")
        every { repository.getAllModels() } returns flowOf(listOf(model(3, file.absolutePath)))
        coEvery { engine.initialize(file.absolutePath, false, false) } returns Result.Success(Unit)

        assertTrue(useCase(3) is Result.Success)
        coVerify(exactly = 1) { repository.setActiveModel(3) }
    }

    private fun model(id: Long, path: String) =
        LocalModel(id = id, name = path.substringAfterLast('/'), path = path, size = 1, isActive = false)

    private object Fail : AppError.System
}
