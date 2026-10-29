package app.kawkab.android.domain.usecases

import app.kawkab.android.domain.constants.BundledPresetCatalog
import app.kawkab.android.domain.constants.DefaultPrompts
import app.kawkab.android.domain.models.NodeModel
import app.kawkab.android.domain.models.NodeType
import app.kawkab.android.domain.models.PipelineGraph
import app.kawkab.android.domain.models.PipelineRun
import app.kawkab.android.domain.models.PipelineRunStatus
import app.kawkab.android.domain.models.RunOrigin
import app.kawkab.android.domain.models.RunTerminationReason
import app.kawkab.android.domain.repositories.PendingInteractionRepository
import app.kawkab.android.domain.repositories.PipelineRepository
import app.kawkab.android.domain.repositories.PipelineRunRepository
import app.kawkab.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InitializeAppUseCaseTest {

    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val pipelineRepository: PipelineRepository = mockk(relaxed = true)
    private val loadPipelineFromPresetUseCase: LoadPipelineFromPresetUseCase = mockk(relaxed = true)
    private val pipelineRunRepository: PipelineRunRepository = mockk(relaxed = true)
    private val pendingInteractionRepository: PendingInteractionRepository = mockk(relaxed = true) {
        coEvery { getAllRunIds() } returns emptySet()
    }
    private val seedBundledSkillsUseCase: SeedBundledSkillsUseCase = mockk(relaxed = true)
    private val useCase = InitializeAppUseCase(
        appStateSettings = settingsRepository,
        entryPointSettings = settingsRepository,
        generationSettings = settingsRepository,
        pipelineRepository = pipelineRepository,
        loadPipelineFromPresetUseCase = loadPipelineFromPresetUseCase,
        pipelineRunRepository = pipelineRunRepository,
        pendingInteractionRepository = pendingInteractionRepository,
        seedBundledSkillsUseCase = seedBundledSkillsUseCase,
    )

    @Test
    fun `invoke seeds the assistant preset and sets it as default on first launch`() = runTest {
        // Given — first launch, and the bundled assistant preset materialises cleanly.
        every { settingsRepository.isFirstLaunch } returns flowOf(true)
        coEvery { loadPipelineFromPresetUseCase(ASSISTANT_PRESET_ID) } returns Result.success(SEEDED_ID)

        // When
        useCase()

        // Then — the default prompt is persisted, the seed is materialised from
        // the assistant preset (not the code-level factory), the returned id
        // becomes the application default, and first launch is cleared.
        coVerify { settingsRepository.setSystemPromptPrefix(DefaultPrompts.SYSTEM_PROMPT_PREFIX) }
        coVerify { loadPipelineFromPresetUseCase(ASSISTANT_PRESET_ID) }
        coVerify { settingsRepository.setDefaultPipelineId(SEEDED_ID) }
        coVerify { settingsRepository.setFirstLaunch(false) }
        // The factory fallback must not run when the preset loads.
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `invoke falls back to factory pipeline when the assistant preset fails`() = runTest {
        // Given — first launch, but the assistant preset cannot be materialised.
        every { settingsRepository.isFirstLaunch } returns flowOf(true)
        coEvery { loadPipelineFromPresetUseCase(ASSISTANT_PRESET_ID) } returns
            Result.failure(IllegalStateException("missing asset"))
        val savedPipeline = slot<PipelineGraph>()

        // When
        useCase()

        // Then — the code-level factory pipeline is persisted, is structurally
        // valid, and its id becomes the application default so the user still
        // lands on a runnable pipeline.
        coVerify { pipelineRepository.savePipeline(capture(savedPipeline)) }
        assertEquals(emptyList<Any>(), savedPipeline.captured.validate())
        coVerify { settingsRepository.setDefaultPipelineId(savedPipeline.captured.id) }
        coVerify { settingsRepository.setFirstLaunch(false) }
    }

    @Test
    fun `invoke should do nothing if not first launch`() = runTest {
        every { settingsRepository.isFirstLaunch } returns flowOf(false)
        stubUnchangedDefault()

        useCase()

        coVerify(exactly = 0) { settingsRepository.setSystemPromptPrefix(any()) }
        coVerify(exactly = 0) { loadPipelineFromPresetUseCase(any()) }
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
        coVerify(exactly = 0) { settingsRepository.setDefaultPipelineId(any()) }
        coVerify(exactly = 0) { settingsRepository.setFirstLaunch(any()) }
    }

    @Test
    fun `invoke seeds bundled skills on every launch regardless of first launch`() = runTest {
        // Given — NOT a first launch (the first-launch gate must not apply to
        // the skill seed, so upgrading users still receive the bundled set).
        every { settingsRepository.isFirstLaunch } returns flowOf(false)
        stubUnchangedDefault()

        // When
        useCase()

        // Then — the idempotent seed runs anyway.
        coVerify(exactly = 1) { seedBundledSkillsUseCase() }
    }

    @Test
    fun `invoke sweeps orphaned runs to INTERRUPTED on every launch`() = runTest {
        // Given — NOT a first launch, with three runs stranded by a dead
        // process, including one suspended on a HITL approval (its in-memory
        // deferred died with the process, so nothing else can settle it).
        every { settingsRepository.isFirstLaunch } returns flowOf(false)
        stubUnchangedDefault()
        coEvery { pipelineRunRepository.getOrphanedRuns() } returns listOf(
            orphanRun("run-1", PipelineRunStatus.RUNNING),
            orphanRun("run-2", PipelineRunStatus.QUEUED),
            orphanRun("run-3", PipelineRunStatus.WAITING_APPROVAL),
        )

        // When
        useCase()

        // Then — both records are finalised as INTERRUPTED with the canonical reason.
        coVerify {
            pipelineRunRepository.finishRun(
                "run-1",
                PipelineRunStatus.INTERRUPTED,
                "Process terminated during execution",
                RunTerminationReason.ProcessDied,
            )
        }
        coVerify {
            pipelineRunRepository.finishRun(
                "run-2",
                PipelineRunStatus.INTERRUPTED,
                "Process terminated during execution",
                RunTerminationReason.ProcessDied,
            )
        }
        coVerify {
            pipelineRunRepository.finishRun(
                "run-3",
                PipelineRunStatus.INTERRUPTED,
                "Process terminated during execution",
                RunTerminationReason.ProcessDied,
            )
        }
    }

    @Test
    fun `invoke sweeps orphaned runs on first launch too`() = runTest {
        // Given — first launch AND an orphaned run (e.g. data restored from backup).
        every { settingsRepository.isFirstLaunch } returns flowOf(true)
        coEvery { loadPipelineFromPresetUseCase(ASSISTANT_PRESET_ID) } returns Result.success(SEEDED_ID)
        coEvery { pipelineRunRepository.getOrphanedRuns() } returns listOf(
            orphanRun("run-1", PipelineRunStatus.RUNNING),
        )

        // When
        useCase()

        // Then — the sweep is not gated behind the first-launch branch.
        coVerify {
            pipelineRunRepository.finishRun(
                "run-1",
                PipelineRunStatus.INTERRUPTED,
                any(),
                RunTerminationReason.ProcessDied,
            )
        }
    }

    @Test
    fun `invoke leaves run store untouched when nothing is orphaned`() = runTest {
        every { settingsRepository.isFirstLaunch } returns flowOf(false)
        stubUnchangedDefault()
        coEvery { pipelineRunRepository.getOrphanedRuns() } returns emptyList()

        useCase()

        coVerify(exactly = 0) { pipelineRunRepository.finishRun(any(), any(), any(), any()) }
    }

    /**
     * Builds an orphaned run fixture in the given non-terminal [status].
     */
    private fun orphanRun(id: String, status: PipelineRunStatus): PipelineRun = PipelineRun(
        id = id,
        sessionId = "session-1",
        pipelineId = null,
        origin = RunOrigin.CHAT,
        status = status,
        currentNodeId = null,
        startedAt = 0L,
        finishedAt = null,
        errorMessage = null,
        graphContentHash = null,
    )

    /**
     * A later launch whose default is not the retired companion. The migration
     * reads the default id and the saved graph, then leaves both alone.
     */
    private fun stubUnchangedDefault() {
        every { settingsRepository.defaultPipelineId } returns flowOf("kept-id")
        coEvery { pipelineRepository.getPipelineById("kept-id") } returns PipelineGraph(
            id = "kept-id",
            name = "Kept",
            nodes = listOf(
                NodeModel(id = "answer", type = NodeType.LITE_RT, x = 0f, y = 0f, systemPrompt = "Answer."),
            ),
        )
    }

    private companion object {
        const val ASSISTANT_PRESET_ID = BundledPresetCatalog.KAWKAB_ASSISTANT_ID
        const val SEEDED_ID = "seeded-pipeline-id"
    }

    @Test
    fun `invoke replaces a companion default with the assistant`() = runTest {
        every { settingsRepository.isFirstLaunch } returns flowOf(false)
        every { settingsRepository.defaultPipelineId } returns flowOf("companion-id")
        coEvery { pipelineRepository.getPipelineById("companion-id") } returns PipelineGraph(
            id = "companion-id",
            name = "Kawkab",
            nodes = listOf(
                NodeModel(
                    id = "persona",
                    type = NodeType.LITE_RT,
                    x = 0f,
                    y = 0f,
                    systemPrompt = "You are Mila, the user's warm girlfriend.",
                ),
            ),
        )
        coEvery { loadPipelineFromPresetUseCase(ASSISTANT_PRESET_ID) } returns Result.success(SEEDED_ID)

        useCase()

        coVerify { loadPipelineFromPresetUseCase(ASSISTANT_PRESET_ID) }
        coVerify { settingsRepository.setDefaultPipelineId(SEEDED_ID) }
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `invoke exempts runs parked on a pending interaction from the orphan sweep`() = runTest {
        every { settingsRepository.isFirstLaunch } returns flowOf(false)
        stubUnchangedDefault()
        coEvery { pendingInteractionRepository.getAllRunIds() } returns setOf("run-parked")
        coEvery { pipelineRunRepository.getOrphanedRuns() } returns listOf(
            orphanRun("run-parked", PipelineRunStatus.WAITING_APPROVAL),
            orphanRun("run-dead", PipelineRunStatus.RUNNING),
        )

        useCase()

        coVerify(exactly = 0) { pipelineRunRepository.finishRun("run-parked", any(), any(), any()) }
        coVerify(exactly = 1) {
            pipelineRunRepository.finishRun(
                "run-dead",
                PipelineRunStatus.INTERRUPTED,
                any(),
                RunTerminationReason.ProcessDied,
            )
        }
    }
}
