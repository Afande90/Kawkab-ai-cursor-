package app.knotwork.android.presentation.ui.settings.backup

import app.knotwork.android.domain.backup.BackupCounts
import app.knotwork.android.domain.backup.BackupDocuments
import app.knotwork.android.domain.backup.BackupExportFailure
import app.knotwork.android.domain.backup.BackupManifest
import app.knotwork.android.domain.backup.BackupProgress
import app.knotwork.android.domain.backup.BackupStep
import app.knotwork.android.domain.usecases.BackUpToDocumentUseCase
import app.knotwork.android.domain.usecases.BackupToDocumentOutcome
import app.knotwork.design.screens.settings.backup.BackupFailReason
import app.knotwork.design.screens.settings.backup.BackupFailed
import app.knotwork.design.screens.settings.backup.BackupForm
import app.knotwork.design.screens.settings.backup.BackupSaved
import app.knotwork.design.screens.settings.backup.BackupWorkStep
import app.knotwork.design.screens.settings.backup.BackupWorking
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [BackupViewModel]: the password rule as the designer wrote it, the picker, the work and its three endings —
 * saved, failed, cancelled — and Back inside a route.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val backUp = mockk<BackUpToDocumentUseCase>()
    private val documents = mockk<BackupDocuments> {
        coEvery { displayName(DOCUMENT) } returns FILE_NAME
        coEvery { discard(DOCUMENT) } returns true
    }
    private lateinit var viewModel: BackupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = BackupViewModel(backUp, documents)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val form get() = viewModel.state.value.route as BackupForm

    @Test
    fun `given a short password when the field loses focus then it is too short, live until 12`() {
        // Given
        viewModel.startBackup()
        viewModel.onPasswordChange("brass")

        // Then — no error while typing.
        assertFalse(form.passwordTooShort)

        // When
        viewModel.onPasswordFocusLost()

        // Then — from now on live.
        assertTrue(form.passwordTooShort)
        viewModel.onPasswordChange("brass lante")
        assertTrue(form.passwordTooShort)
        viewModel.onPasswordChange("brass lanter")
        assertFalse(form.passwordTooShort)
        viewModel.onPasswordChange("")
        assertFalse("an empty field shows no error", form.passwordTooShort)
    }

    @Test
    fun `given an empty or long enough password when the field loses focus then nothing is flagged`() {
        viewModel.startBackup()
        viewModel.onPasswordFocusLost()
        viewModel.onPasswordChange("brass lantern otter")
        viewModel.onPasswordFocusLost()
        viewModel.onPasswordChange("brass")

        assertFalse("the field was never left short", form.passwordTooShort)
    }

    @Test
    fun `given a second password when as long as the first and different then it doesn't match, clearing live`() {
        // Given
        viewModel.startBackup()
        viewModel.onPasswordChange(PASSWORD)

        // When — shorter and different: not yet.
        viewModel.onConfirmationChange("brass lantern")

        // Then
        assertFalse(form.confirmationMismatch)
        viewModel.onConfirmationChange("brass lantern otter quiex")
        assertTrue(form.confirmationMismatch)
        viewModel.onConfirmationChange(PASSWORD)
        assertFalse(form.confirmationMismatch)
        assertTrue(form.canContinue)
    }

    @Test
    fun `given a different second password when its field loses focus then it doesn't match`() {
        viewModel.startBackup()
        viewModel.onPasswordChange(PASSWORD)
        viewModel.onConfirmationChange("brass")
        viewModel.onConfirmationFocusLost()

        assertTrue(form.confirmationMismatch)
        assertFalse(form.canContinue)

        // Then — the first password edited later: the second is checked again.
        viewModel.onPasswordChange("brass")
        assertFalse(form.confirmationMismatch)
    }

    @Test
    fun `given an emoji password when counted then each emoji is one character`() {
        // Given — 12 code points, 24 UTF-16 units.
        val emoji = "😀".repeat(12)
        viewModel.startBackup()
        viewModel.onPasswordChange(emoji.substring(0, 22))
        viewModel.onPasswordFocusLost()

        // Then
        assertTrue(form.passwordTooShort)
        viewModel.onPasswordChange(emoji)
        viewModel.onConfirmationChange(emoji)
        assertTrue(form.canContinue)
    }

    @Test
    fun `given the form when choose where is tapped then the picker opens only once both fields pass`() =
        runTest(dispatcher) {
            val events = mutableListOf<BackupEvent>()
            // Unconfined: advanceUntilIdle never runs background work, so a queued collector would see nothing.
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.collect { events += it }
            }
            viewModel.startBackup()
            viewModel.onPasswordChange(PASSWORD)

            viewModel.onChooseWhere()
            advanceUntilIdle()
            assertTrue(events.isEmpty())

            viewModel.onConfirmationChange(PASSWORD)
            viewModel.onChooseWhere()
            advanceUntilIdle()
            assertEquals(listOf<BackupEvent>(BackupEvent.PickDocument), events)
        }

    @Test
    fun `given the picker backed out when it returns then the form is kept as it was`() {
        fill(secrets = true)

        viewModel.onDocumentPicked(null)

        assertEquals(PASSWORD, form.password)
        assertTrue(form.includeSecrets)
    }

    @Test
    fun `given a document when the backup runs then progress shows its steps and bytes`() = runTest(dispatcher) {
        // Given
        val release = CompletableDeferred<Unit>()
        coEvery { backUp(DOCUMENT, any(), false, any()) } coAnswers {
            arg<(BackupProgress) -> Unit>(3)(BackupProgress(BackupStep.FILES, bytesDone = 40, bytesTotal = 100))
            release.await()
            saved()
        }
        fill()

        // When
        viewModel.onDocumentPicked(DOCUMENT)
        advanceUntilIdle()

        // Then
        assertEquals(
            BackupWorking(FILE_NAME, BackupWorkStep.FILES, 40, 100),
            viewModel.state.value.route,
        )
        release.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.route is BackupSaved)
    }

    @Test
    fun `given a saved backup when it ends then the result shows what is in it and the password is dropped`() =
        runTest(dispatcher) {
            coEvery { backUp(DOCUMENT, any(), true, any()) } returns saved(skipped = 2)
            fill(secrets = true)

            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            assertEquals(
                BackupSaved(
                    fileName = FILE_NAME,
                    sizeBytes = 2_048,
                    chats = 12,
                    memories = 340,
                    pipelines = 9,
                    files = 6,
                    keysIncluded = true,
                    skippedFiles = 2,
                ),
                viewModel.state.value.route,
            )
            viewModel.closeRoute()
            viewModel.startBackup()
            assertEquals("", form.password)
        }

    @Test
    fun `given a failed backup when it ends then the reason shows and try again keeps the password`() =
        runTest(dispatcher) {
            coEvery { backUp(DOCUMENT, any(), false, any()) } returns
                BackupToDocumentOutcome.Failed(
                    BackupExportFailure.NO_SPACE,
                    neededBytes = 9_000,
                    documentDeleted = false,
                )
            fill()

            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            assertEquals(
                BackupFailed(BackupFailReason.NO_SPACE, 9_000, fileDeleted = false, fileName = FILE_NAME),
                viewModel.state.value.route,
            )
            viewModel.onTryAgain()
            assertEquals(PASSWORD, form.password)
        }

    @Test
    fun `given the backup running when cancelled then the screen returns and says the file was deleted`() =
        runTest(dispatcher) {
            // Given
            val events = mutableListOf<BackupEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.collect { events += it }
            }
            coEvery { backUp(DOCUMENT, any(), false, any()) } coAnswers { awaitCancellation() }
            coEvery { documents.discard(DOCUMENT) } returns false
            fill()
            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            // When — Back while working is Cancel.
            assertTrue(viewModel.onBack())
            advanceUntilIdle()

            // Then
            assertNull(viewModel.state.value.route)
            assertEquals(
                listOf<BackupEvent>(
                    BackupEvent.Cancelled(fileDeleted = false, fileName = FILE_NAME),
                ),
                events,
            )
            coVerify { documents.discard(DOCUMENT) }
        }

    @Test
    fun `given a route when Back is pressed then the route closes, and on the screen itself Back leaves`() {
        assertFalse(viewModel.onBack())
        fill()

        assertTrue(viewModel.onBack())
        assertNull(viewModel.state.value.route)
    }

    @Test
    fun `given the backup throws unexpectedly when it ends then it is a failure the user is told`() =
        runTest(dispatcher) {
            coEvery { backUp(DOCUMENT, any(), false, any()) } throws IllegalStateException("provider")
            fill()

            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            assertEquals(
                BackupFailed(BackupFailReason.READ_FAILED, fileDeleted = true, fileName = FILE_NAME),
                viewModel.state.value.route,
            )
        }

    private fun fill(secrets: Boolean = false) {
        viewModel.startBackup()
        viewModel.onPasswordChange(PASSWORD)
        viewModel.onConfirmationChange(PASSWORD)
        if (secrets) viewModel.onToggleSecrets()
    }

    private fun saved(skipped: Int = 0) = BackupToDocumentOutcome.Saved(
        BackupManifest(
            createdAtEpochMillis = 0,
            schemaVersion = 71,
            secretsIncluded = true,
            counts = BackupCounts(chats = 12, memories = 340, pipelines = 9, workspaceFiles = 4, attachments = 2),
            scheduledTasks = 0,
            models = emptyList(),
            skippedFiles = skipped,
        ),
        sizeBytes = 2_048,
    )

    private companion object {
        const val DOCUMENT = "content://downloads/document/7"
        const val FILE_NAME = "knotwork-backup-2026-10-13.knotwork"
        const val PASSWORD = "brass lantern otter quiet"
    }
}
