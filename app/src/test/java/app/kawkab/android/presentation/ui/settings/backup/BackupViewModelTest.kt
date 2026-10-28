package app.kawkab.android.presentation.ui.settings.backup

import app.kawkab.android.domain.backup.BackupCounts
import app.kawkab.android.domain.backup.BackupDocuments
import app.kawkab.android.domain.backup.BackupExportFailure
import app.kawkab.android.domain.backup.BackupManifest
import app.kawkab.android.domain.backup.BackupProgress
import app.kawkab.android.domain.backup.BackupStep
import app.kawkab.android.domain.usecases.BackUpToDocumentUseCase
import app.kawkab.android.domain.usecases.BackupToDocumentOutcome
import app.kawkab.design.screens.settings.backup.BackupFailReason
import app.kawkab.design.screens.settings.backup.BackupFailed
import app.kawkab.design.screens.settings.backup.BackupForm
import app.kawkab.design.screens.settings.backup.BackupSaved
import app.kawkab.design.screens.settings.backup.BackupWorkStep
import app.kawkab.design.screens.settings.backup.BackupWorking
import io.mockk.MockKAnswerScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [BackupViewModel]: the password rule as the designer wrote it, the picker, the work and its three endings —
 * saved, failed, cancelled — and Back inside a route; and the edges where a cancel, a stale picker result or a
 * provider without names meets a backup in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val backUp = mockk<BackUpToDocumentUseCase>()
    private val documents = mockk<BackupDocuments> {
        coEvery { displayName(DOCUMENT) } returns FILE_NAME
        coEvery { discard(DOCUMENT) } returns true
        coEvery { discard(OTHER_DOCUMENT) } returns true
        coEvery { discardIfEmpty(any()) } returns true
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
    fun `given the form when choose where is tapped then the picker opens once both fields pass, with a dated name`() =
        runTest(dispatcher) {
            val events = events()
            viewModel.startBackup()
            viewModel.onPasswordChange(PASSWORD)

            viewModel.onChooseWhere()
            advanceUntilIdle()
            assertTrue(events.isEmpty())

            viewModel.onConfirmationChange(PASSWORD)
            viewModel.onChooseWhere()
            advanceUntilIdle()
            val pick = events.single() as BackupEvent.PickDocument
            val dated = Regex("knotwork-backup-\\d{4}-\\d{2}-\\d{2}\\.knotwork")
            assertTrue(pick.suggestedName, pick.suggestedName.matches(dated))
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
        coEvery { backUp(DOCUMENT, any(), false, any(), any()) } coAnswers {
            arg<(BackupProgress) -> Unit>(4)(BackupProgress(BackupStep.FILES, bytesDone = 40, bytesTotal = 100))
            release.await()
            saved()
        }
        fill()

        // When
        viewModel.onDocumentPicked(DOCUMENT)
        advanceUntilIdle()

        // Then
        assertEquals(BackupWorking(FILE_NAME, BackupWorkStep.FILES, 40, 100), viewModel.state.value.route)
        release.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.route is BackupSaved)
    }

    @Test
    fun `given a saved backup when it ends then the result shows what is in it and the password is dropped`() =
        runTest(dispatcher) {
            // Given
            coEvery { backUp(DOCUMENT, any(), true, any(), any()) } returns saved(skipped = 2)
            fill(secrets = true)

            // When
            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            // Then
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
            // A stale picker result finds no password to lock a second backup with: its empty file goes.
            viewModel.onDocumentPicked(OTHER_DOCUMENT)
            advanceUntilIdle()
            coVerify(exactly = 1) { backUp(any(), any(), any(), any(), any()) }
            coVerify { documents.discardIfEmpty(OTHER_DOCUMENT) }
        }

    @Test
    fun `given a failed backup when it ends then the reason shows and try again keeps the password`() =
        runTest(dispatcher) {
            val events = events()
            coEvery { backUp(DOCUMENT, any(), false, any(), any()) } returns
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
            advanceUntilIdle()
            assertEquals(PASSWORD, form.password)
            assertTrue(events.single() is BackupEvent.PickDocument)
        }

    @Test
    fun `given the backup running when cancelled then the screen returns and says whether the file was deleted`() =
        runTest(dispatcher) {
            // Given — the backup deletes its document on the way out and reports that the provider refused.
            coEvery { backUp(DOCUMENT, any(), false, any(), any()) } coAnswers { reportingOnTheWayOut(deleted = false) }
            fill()
            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            // When — Back while working is Cancel.
            assertTrue(viewModel.onBack())
            advanceUntilIdle()

            // Then
            assertNull(viewModel.state.value.route)
            assertEquals(BackupCancelNotice(fileDeleted = false, fileName = FILE_NAME), viewModel.cancelNotice.value)
            coVerify(exactly = 0) { documents.discard(any()) }
            viewModel.onCancelNoticeShown()
            assertNull(viewModel.cancelNotice.value)
        }

    @Test
    fun `given a cancel already on its way when cancel and Back come again then it is done once`() =
        runTest(dispatcher) {
            var reports = 0
            coEvery { backUp(DOCUMENT, any(), false, any(), any()) } coAnswers {
                reports++
                reportingOnTheWayOut(deleted = true)
            }
            fill()
            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            viewModel.onCancel()
            viewModel.onCancel()
            assertTrue("Back is still the route's while the cancel finishes", viewModel.onBack())
            advanceUntilIdle()

            assertEquals(1, reports)
            coVerify(exactly = 0) { documents.discard(any()) }
            assertEquals(BackupCancelNotice(fileDeleted = true, fileName = FILE_NAME), viewModel.cancelNotice.value)
        }

    @Test
    fun `given a backup that already finished when a late cancel arrives then the complete file is kept`() =
        runTest(dispatcher) {
            coEvery { backUp(DOCUMENT, any(), false, any(), any()) } returns saved()
            fill()
            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            viewModel.onCancel()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.route is BackupSaved)
            assertNull(viewModel.cancelNotice.value)
            coVerify(exactly = 0) { documents.discard(any()) }
        }

    @Test
    fun `given a cancel that lands while the complete backup is recorded when it ends then the result stands`() =
        runTest(dispatcher) {
            // Given — the use case records a complete backup where a cancel cannot stop it.
            val recording = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            coEvery { backUp(DOCUMENT, any(), false, any(), any()) } coAnswers {
                withContext(NonCancellable) {
                    recording.complete(Unit)
                    release.await()
                }
                saved()
            }
            fill()
            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()
            recording.await()

            // When
            viewModel.onCancel()
            advanceUntilIdle()
            release.complete(Unit)
            advanceUntilIdle()

            // Then — the saved result is on screen, so the cancel came too late to delete anything.
            assertTrue(viewModel.state.value.route is BackupSaved)
            assertNull(viewModel.cancelNotice.value)
            coVerify(exactly = 0) { documents.discard(any()) }
        }

    @Test
    fun `given the picked document still being named when Back is pressed then nothing is written and it goes`() =
        runTest(dispatcher) {
            // Given — the provider is slow to name the document.
            val events = events()
            coEvery { documents.displayName(DOCUMENT) } coAnswers { awaitCancellation() }
            fill()
            viewModel.onChooseWhere()
            advanceUntilIdle()
            val suggested = (events.single() as BackupEvent.PickDocument).suggestedName

            // When
            viewModel.onDocumentPicked(DOCUMENT)

            // Then — the form is gone at once, so Back is Cancel.
            assertEquals(BackupWorking(suggested, BackupWorkStep.PASSWORD), viewModel.state.value.route)
            advanceUntilIdle()
            assertTrue(viewModel.onBack())
            advanceUntilIdle()
            assertNull(viewModel.state.value.route)
            coVerify(exactly = 0) { backUp(any(), any(), any(), any(), any()) }
            coVerify(exactly = 1) { documents.discard(DOCUMENT) }
            assertEquals(BackupCancelNotice(fileDeleted = true, fileName = suggested), viewModel.cancelNotice.value)
        }

    @Test
    fun `given a picker result with no password behind it when it returns then it goes only if empty`() =
        runTest(dispatcher) {
            // Given — the process was recreated while the picker was open: a new view model, nothing typed.

            // When
            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            // Then
            assertNull(viewModel.state.value.route)
            coVerify(exactly = 0) { backUp(any(), any(), any(), any(), any()) }
            coVerify(exactly = 1) { documents.discardIfEmpty(DOCUMENT) }
            coVerify(exactly = 0) { documents.discard(any()) }
        }

    @Test
    fun `given a second document while a backup runs when it returns then it goes if empty, the backup goes on`() =
        runTest(dispatcher) {
            val release = CompletableDeferred<Unit>()
            coEvery { backUp(DOCUMENT, any(), false, any(), any()) } coAnswers {
                release.await()
                saved()
            }
            fill()
            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            viewModel.onDocumentPicked(OTHER_DOCUMENT)
            advanceUntilIdle()
            release.complete(Unit)
            advanceUntilIdle()

            coVerify(exactly = 1) { documents.discardIfEmpty(OTHER_DOCUMENT) }
            coVerify(exactly = 0) { documents.discard(any()) }
            assertEquals(FILE_NAME, (viewModel.state.value.route as BackupSaved).fileName)
        }

    @Test
    fun `given the running document picked again when it returns then it is left to the backup writing it`() =
        runTest(dispatcher) {
            val release = CompletableDeferred<Unit>()
            coEvery { backUp(DOCUMENT, any(), false, any(), any()) } coAnswers {
                release.await()
                saved()
            }
            fill()
            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            // When — a second picker, opened by a double tap, returns the very file being written.
            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()
            release.complete(Unit)
            advanceUntilIdle()

            // Then
            coVerify(exactly = 0) { documents.discardIfEmpty(any()) }
            coVerify(exactly = 0) { documents.discard(any()) }
            assertTrue(viewModel.state.value.route is BackupSaved)
        }

    @Test
    fun `given a cancel finishing when a document is picked then no second backup starts`() = runTest(dispatcher) {
        coEvery { backUp(DOCUMENT, any(), false, any(), any()) } coAnswers { reportingOnTheWayOut(deleted = true) }
        fill()
        viewModel.onDocumentPicked(DOCUMENT)
        advanceUntilIdle()

        // When — a pick lands after Cancel, before the cancel has closed the route.
        viewModel.onCancel()
        viewModel.onDocumentPicked(OTHER_DOCUMENT)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { backUp(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { documents.discardIfEmpty(OTHER_DOCUMENT) }
        assertNull(viewModel.state.value.route)
    }

    @Test
    fun `given a second picker backed out of while a backup runs when it returns then the work stays on screen`() =
        runTest(dispatcher) {
            val release = CompletableDeferred<Unit>()
            coEvery { backUp(DOCUMENT, any(), false, any(), any()) } coAnswers {
                arg<(BackupProgress) -> Unit>(4)(BackupProgress(BackupStep.FILES, bytesDone = 40, bytesTotal = 100))
                release.await()
                saved()
            }
            fill()
            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            // When — the user backs out of the other picker a double tap opened.
            viewModel.onDocumentPicked(null)

            // Then — still the work, and Back is still Cancel.
            assertEquals(BackupWorking(FILE_NAME, BackupWorkStep.FILES, 40, 100), viewModel.state.value.route)
            release.complete(Unit)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.route is BackupSaved)
        }

    @Test
    fun `given a backup that deleted its file and then broke when it ends then the screen says what it did`() =
        runTest(dispatcher) {
            // Given — the use case deleted the document on its way out, the provider refusing, then threw.
            coEvery { backUp(DOCUMENT, any(), false, any(), any()) } coAnswers {
                arg<(Boolean) -> Unit>(3)(false)
                throw IllegalStateException("provider")
            }
            fill()

            // When
            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            // Then — its answer stands; nothing is deleted twice.
            assertEquals(
                BackupFailed(BackupFailReason.READ_FAILED, fileDeleted = false, fileName = FILE_NAME),
                viewModel.state.value.route,
            )
            coVerify(exactly = 0) { documents.discard(any()) }
        }

    @Test
    fun `given a provider that gives no name when the backup is saved then the suggested name stands in`() =
        runTest(dispatcher) {
            val events = events()
            coEvery { documents.displayName(DOCUMENT) } returns null
            coEvery { backUp(DOCUMENT, any(), false, any(), any()) } returns saved()
            fill()
            viewModel.onChooseWhere()
            advanceUntilIdle()

            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            val suggested = (events.single() as BackupEvent.PickDocument).suggestedName
            assertEquals(suggested, (viewModel.state.value.route as BackupSaved).fileName)
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
            coEvery { backUp(DOCUMENT, any(), false, any(), any()) } throws IllegalStateException("provider")
            fill()

            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            assertEquals(
                BackupFailed(BackupFailReason.READ_FAILED, fileDeleted = true, fileName = FILE_NAME),
                viewModel.state.value.route,
            )
        }

    /** Collects the view model's events; unconfined, because `advanceUntilIdle` never runs background work. */
    private fun TestScope.events(): MutableList<BackupEvent> {
        val events = mutableListOf<BackupEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        return events
    }

    /** A use case cancelled while it writes, deleting its document on the way out and reporting how that went. */
    private suspend fun MockKAnswerScope<BackupToDocumentOutcome, BackupToDocumentOutcome>.reportingOnTheWayOut(
        deleted: Boolean,
    ): BackupToDocumentOutcome = try {
        awaitCancellation()
    } finally {
        arg<(Boolean) -> Unit>(3)(deleted)
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
        const val OTHER_DOCUMENT = "content://downloads/document/8"
        const val FILE_NAME = "knotwork-backup-2026-10-13.knotwork"
        const val PASSWORD = "brass lantern otter quiet"
    }
}
