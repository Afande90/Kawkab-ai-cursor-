package app.knotwork.android.presentation.ui.settings.backup

import android.content.Context
import app.knotwork.android.R
import app.knotwork.android.domain.backup.BackupModelEntry
import app.knotwork.android.domain.backup.BackupRestorer
import app.knotwork.android.domain.backup.RestoreReport
import app.knotwork.android.domain.models.AppError
import app.knotwork.android.domain.models.DownloadState
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.ModelDownloadManager
import app.knotwork.android.presentation.state.TransientMessageRelay
import app.knotwork.design.screens.settings.backup.AfterRestoreModel
import app.knotwork.design.screens.settings.backup.AfterRestoreState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * [AfterRestoreViewModel]: the sheet from the report, the snackbar when nothing is left to do, the downloads it
 * starts — one at a time — and *Done*; the sheet only while the chat is resumed, without the models installed
 * meanwhile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AfterRestoreViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val restorer = mockk<BackupRestorer>(relaxed = true)
    private val download = MutableSharedFlow<DownloadState>(extraBufferCapacity = 8)
    private val downloads = mockk<ModelDownloadManager>(relaxed = true) {
        every { downloadModel(KNOWN.sourceUrl!!, KNOWN.name, any()) } returns download
    }
    private val localModels = mockk<LocalModelRepository> {
        coEvery { isInstalled(any()) } returns false
    }
    private val relay = mockk<TransientMessageRelay>(relaxed = true)
    private val context = mockk<Context> {
        every { getString(R.string.backup_snack_restored) } returns "Restored"
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given a report with things left to do when the chat opens then the sheet lists them`() = runTest(dispatcher) {
        coEvery { restorer.report() } returns REPORT

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            AfterRestoreState(
                madeOnLabel = "on 5000",
                models = listOf(
                    AfterRestoreModel(KNOWN.name, KNOWN.sizeBytes, sourceKnown = true),
                    AfterRestoreModel(UNKNOWN.name, UNKNOWN.sizeBytes, sourceKnown = false),
                ),
                enterKeysAgain = true,
                tasksLeftBehind = true,
            ),
            viewModel.state.value,
        )
        verify(exactly = 0) { relay.post(any()) }
    }

    @Test
    fun `given a report with nothing left to do when the chat opens then it says Restored and forgets it`() =
        runTest(dispatcher) {
            coEvery { restorer.report() } returns
                RestoreReport(emptyList(), scheduledTasksLeftBehind = 0, secretsApplied = true)

            val viewModel = viewModel()
            advanceUntilIdle()

            assertNull(viewModel.state.value)
            verify { relay.post("Restored") }
            coVerify { restorer.dismissReport() }
        }

    @Test
    fun `given a report whose only task is the keys when the chat opens then the sheet still shows`() =
        runTest(dispatcher) {
            // A backup without keys, onto a phone with nothing else left: the keys are still the user's to enter.
            coEvery { restorer.report() } returns
                RestoreReport(emptyList(), scheduledTasksLeftBehind = 0, secretsApplied = false, secretsOnPhone = false)

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(true, viewModel.state.value?.enterKeysAgain)
            verify(exactly = 0) { relay.post(any()) }
        }

    @Test
    fun `given a backup without keys onto a phone that keeps its own when the chat opens then nothing is asked`() =
        runTest(dispatcher) {
            // The phone's own keys stay through a restore: telling the user to enter them again would be false.
            coEvery { restorer.report() } returns
                RestoreReport(emptyList(), scheduledTasksLeftBehind = 0, secretsApplied = false, secretsOnPhone = true)

            val viewModel = viewModel()
            advanceUntilIdle()

            assertNull(viewModel.state.value)
            verify { relay.post("Restored") }
        }

    @Test
    fun `given a report that cannot be read when the chat opens then nothing shows and nothing breaks`() =
        runTest(dispatcher) {
            coEvery { restorer.report() } throws IOException("disk")

            val viewModel = viewModel()
            advanceUntilIdle()

            assertNull(viewModel.state.value)
        }

    @Test
    fun `given a download cancelled from its notification when its flow ends then the row is free again`() =
        runTest(dispatcher) {
            // The download's flow ends with no last state when its notification's Cancel stops it.
            every { downloads.downloadModel(KNOWN.sourceUrl!!, KNOWN.name, any()) } returns
                flowOf(DownloadState.Downloading(30))
            coEvery { restorer.report() } returns REPORT
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onDownload(KNOWN.name)
            advanceUntilIdle()

            val rows = viewModel.state.value!!.models
            assertNull(rows.first { it.name == KNOWN.name }.progress)
            assertEquals(listOf(true, true), rows.map { it.canDownload })
        }

    @Test
    fun `given a running download when Done is tapped then the download goes on`() = runTest(dispatcher) {
        coEvery { restorer.report() } returns REPORT
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onDownload(KNOWN.name)
        advanceUntilIdle()

        viewModel.onDone()
        download.emit(DownloadState.Downloading(60))
        advanceUntilIdle()

        verify(exactly = 0) { downloads.cancelDownload(any()) }
        assertNull(viewModel.state.value)
    }

    @Test
    fun `given the chat not resumed when the report is read then the sheet waits, and hides again on pause`() =
        runTest(dispatcher) {
            // A back gesture from Discover draws the chat started, not resumed: a sheet then would take the gesture.
            coEvery { restorer.report() } returns REPORT
            val viewModel = hiddenViewModel()
            advanceUntilIdle()
            assertNull(viewModel.state.value)

            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(listOf(KNOWN.name, UNKNOWN.name), viewModel.state.value?.models?.map { it.name })

            viewModel.onPaused()
            assertNull(viewModel.state.value)
            coVerify(exactly = 0) { restorer.dismissReport() }
        }

    @Test
    fun `given a model installed meanwhile when the chat resumes then its row leaves`() = runTest(dispatcher) {
        // Given — the sheet showing both models, then Discover installs the one with no recorded source.
        coEvery { restorer.report() } returns REPORT
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPaused()
        val checked = CompletableDeferred<Boolean>()
        coEvery { localModels.isInstalled(UNKNOWN.name) } coAnswers { checked.await() }

        // When — back on the chat, the check under way.
        viewModel.onResumed()
        advanceUntilIdle()

        // Then — no sheet while it runs, and none listing the model just installed after it.
        assertNull(viewModel.state.value)
        checked.complete(true)
        advanceUntilIdle()
        assertEquals(listOf(KNOWN.name), viewModel.state.value?.models?.map { it.name })
    }

    @Test
    fun `given a model installed and then deleted again when the chat resumes then its row comes back`() =
        runTest(dispatcher) {
            coEvery { restorer.report() } returns REPORT
            coEvery { localModels.isInstalled(UNKNOWN.name) } returns true
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(listOf(KNOWN.name), viewModel.state.value?.models?.map { it.name })

            // Deleted on the Models screen while the chat was away.
            viewModel.onPaused()
            coEvery { localModels.isInstalled(UNKNOWN.name) } returns false
            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals(listOf(KNOWN.name, UNKNOWN.name), viewModel.state.value?.models?.map { it.name })
        }

    @Test
    fun `given every model already on the phone and nothing else left when the report is read then it says Restored`() =
        runTest(dispatcher) {
            // The models were installed in an earlier process, before Done: nothing is left but an empty sheet.
            coEvery { restorer.report() } returns
                RestoreReport(listOf(UNKNOWN), scheduledTasksLeftBehind = 0, secretsApplied = true)
            coEvery { localModels.isInstalled(UNKNOWN.name) } returns true

            val viewModel = viewModel()
            advanceUntilIdle()

            assertNull(viewModel.state.value)
            verify { relay.post("Restored") }
            coVerify { restorer.dismissReport() }
        }

    @Test
    fun `given the chat paused while its resume check runs when the check ends then no sheet shows`() =
        runTest(dispatcher) {
            // A back gesture that starts right after a resume pauses the chat before the check is back.
            coEvery { restorer.report() } returns REPORT
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onPaused()
            val checked = CompletableDeferred<Boolean>()
            coEvery { localModels.isInstalled(UNKNOWN.name) } coAnswers { checked.await() }
            viewModel.onResumed()
            advanceUntilIdle()

            viewModel.onPaused()
            checked.complete(false)
            advanceUntilIdle()

            assertNull(viewModel.state.value)
        }

    @Test
    fun `given a download that ends while the chat resume check runs then its model stays off the list`() =
        runTest(dispatcher) {
            // Given — the sheet's download running, the chat paused, the next check slow.
            coEvery { restorer.report() } returns REPORT
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onDownload(KNOWN.name)
            advanceUntilIdle()
            viewModel.onPaused()
            val checked = CompletableDeferred<Boolean>()
            coEvery { localModels.isInstalled(UNKNOWN.name) } coAnswers { checked.await() }
            viewModel.onResumed()
            advanceUntilIdle()

            // When — the download succeeds while the check waits.
            download.emit(DownloadState.Success("file:///models/gemma.litertlm"))
            advanceUntilIdle()
            checked.complete(false)
            advanceUntilIdle()

            // Then — the check does not write its stale view over the finished download.
            assertEquals(listOf(UNKNOWN.name), viewModel.state.value?.models?.map { it.name })
        }

    @Test
    fun `given download progress while the chat resume check runs then no sheet shows before it`() =
        runTest(dispatcher) {
            // Given — the sheet's download running; Discover installed the other model; the check slow.
            coEvery { restorer.report() } returns REPORT
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onDownload(KNOWN.name)
            advanceUntilIdle()
            viewModel.onPaused()
            val checked = CompletableDeferred<Boolean>()
            coEvery { localModels.isInstalled(UNKNOWN.name) } coAnswers { checked.await() }
            viewModel.onResumed()
            advanceUntilIdle()

            // When — progress arrives while the check waits.
            download.emit(DownloadState.Downloading(60))
            advanceUntilIdle()

            // Then — nothing shown with the installed model still listed; then the sheet without it.
            assertNull(viewModel.state.value)
            checked.complete(true)
            advanceUntilIdle()
            assertEquals(listOf(KNOWN.name), viewModel.state.value?.models?.map { it.name })
            assertEquals(60, viewModel.state.value?.models?.single()?.progress)
        }

    @Test
    fun `given a model installed before the report is read when the chat opens then it is not listed`() =
        runTest(dispatcher) {
            // The report waits for Done across restarts; a model installed in an earlier process stays installed.
            // The chat is resumed while the report is still being read, so the resume's own check finds no report.
            val reportRead = CompletableDeferred<Unit>()
            coEvery { restorer.report() } coAnswers {
                reportRead.await()
                REPORT
            }
            coEvery { localModels.isInstalled(UNKNOWN.name) } returns true
            val viewModel = viewModel()
            advanceUntilIdle()

            reportRead.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(KNOWN.name), viewModel.state.value?.models?.map { it.name })
        }

    @Test
    fun `given a download running here when its file is reported on the phone then its row stays until it ends`() =
        runTest(dispatcher) {
            // Given — the row's download under way when the chat is resumed again.
            coEvery { restorer.report() } returns REPORT
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onDownload(KNOWN.name)
            advanceUntilIdle()
            download.emit(DownloadState.Downloading(40))
            advanceUntilIdle()
            viewModel.onPaused()
            coEvery { localModels.isInstalled(KNOWN.name) } returns true

            // When
            viewModel.onResumed()
            advanceUntilIdle()

            // Then — the row and its Cancel stay while the download runs.
            assertEquals(40, viewModel.state.value?.models?.first { it.name == KNOWN.name }?.progress)
        }

    @Test
    fun `given the registry that cannot be read when the chat resumes then the rows stay`() = runTest(dispatcher) {
        coEvery { restorer.report() } returns REPORT
        coEvery { localModels.isInstalled(any()) } throws IllegalStateException("database unavailable")

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(KNOWN.name, UNKNOWN.name), viewModel.state.value?.models?.map { it.name })
    }

    @Test
    fun `given no report when the chat opens then nothing shows`() = runTest(dispatcher) {
        coEvery { restorer.report() } returns null

        val viewModel = viewModel()
        advanceUntilIdle()

        assertNull(viewModel.state.value)
        verify(exactly = 0) { relay.post(any()) }
    }

    @Test
    fun `given a model with a known source when downloaded then its row shows progress and leaves once done`() =
        runTest(dispatcher) {
            // Given
            coEvery { restorer.report() } returns REPORT
            val viewModel = viewModel()
            advanceUntilIdle()

            // When
            viewModel.onDownload(KNOWN.name)
            advanceUntilIdle()
            download.emit(DownloadState.Downloading(40))
            advanceUntilIdle()

            // Then — progress on its row, and no second download while it runs.
            val rows = viewModel.state.value!!.models
            assertEquals(40, rows.first { it.name == KNOWN.name }.progress)
            assertEquals(listOf(false, false), rows.map { it.canDownload })
            viewModel.onDownload(KNOWN.name)
            verify(exactly = 1) { downloads.downloadModel(any(), any(), any()) }
            download.emit(DownloadState.Success("file:///models/gemma.litertlm"))
            advanceUntilIdle()
            assertEquals(listOf(UNKNOWN.name), viewModel.state.value!!.models.map { it.name })
        }

    @Test
    fun `given a download that fails when it ends then the row offers Download again`() = runTest(dispatcher) {
        coEvery { restorer.report() } returns REPORT
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDownload(KNOWN.name)
        advanceUntilIdle()
        download.emit(DownloadState.Error(mockk<AppError.Network>(relaxed = true)))
        advanceUntilIdle()

        val row = viewModel.state.value!!.models.first { it.name == KNOWN.name }
        assertNull(row.progress)
        assertEquals(true, row.canDownload)
    }

    @Test
    fun `given a running download when cancelled then the worker is told and the row resets`() = runTest(dispatcher) {
        coEvery { restorer.report() } returns REPORT
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onDownload(KNOWN.name)
        advanceUntilIdle()

        viewModel.onCancelDownload(KNOWN.name)

        verify { downloads.cancelDownload(KNOWN.name) }
        assertNull(viewModel.state.value!!.models.first { it.name == KNOWN.name }.progress)
    }

    @Test
    fun `given a model whose source is unknown when Download is asked for then nothing starts`() = runTest(dispatcher) {
        coEvery { restorer.report() } returns REPORT
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDownload(UNKNOWN.name)

        verify(exactly = 0) { downloads.downloadModel(any(), any(), any()) }
    }

    @Test
    fun `given the sheet when Done is tapped then it closes and the report is forgotten`() = runTest(dispatcher) {
        coEvery { restorer.report() } returns REPORT
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDone()
        advanceUntilIdle()

        assertNull(viewModel.state.value)
        coVerify { restorer.dismissReport() }
    }

    /** The view model with the chat on screen, as the host reports it once the destination is resumed. */
    private fun viewModel() = hiddenViewModel().also { it.onResumed() }

    private fun hiddenViewModel() = AfterRestoreViewModel(restorer, downloads, localModels, relay, context) { "on $it" }

    private companion object {
        val KNOWN = BackupModelEntry("gemma.litertlm", 600, null, "https://example/gemma")
        val UNKNOWN = BackupModelEntry("custom.litertlm", 900, null, null)
        val REPORT = RestoreReport(
            modelsToDownload = listOf(KNOWN, UNKNOWN),
            scheduledTasksLeftBehind = 2,
            secretsApplied = false,
            backupCreatedAtEpochMillis = 5_000,
            secretsOnPhone = false,
        )
    }
}
