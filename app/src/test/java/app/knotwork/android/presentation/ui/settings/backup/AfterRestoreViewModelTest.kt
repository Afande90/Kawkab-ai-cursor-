package app.knotwork.android.presentation.ui.settings.backup

import android.content.Context
import app.knotwork.android.R
import app.knotwork.android.domain.backup.BackupModelEntry
import app.knotwork.android.domain.backup.BackupRestorer
import app.knotwork.android.domain.backup.RestoreReport
import app.knotwork.android.domain.models.AppError
import app.knotwork.android.domain.models.DownloadState
import app.knotwork.android.domain.repositories.ModelDownloadManager
import app.knotwork.android.presentation.state.TransientMessageRelay
import app.knotwork.design.screens.settings.backup.AfterRestoreModel
import app.knotwork.design.screens.settings.backup.AfterRestoreState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
 * starts — one at a time — and *Done*.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AfterRestoreViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val restorer = mockk<BackupRestorer>(relaxed = true)
    private val download = MutableSharedFlow<DownloadState>(extraBufferCapacity = 8)
    private val downloads = mockk<ModelDownloadManager>(relaxed = true) {
        every { downloadModel(KNOWN.sourceUrl!!, KNOWN.name, any()) } returns download
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

    private fun viewModel() = AfterRestoreViewModel(restorer, downloads, relay, context) { "on $it" }

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
