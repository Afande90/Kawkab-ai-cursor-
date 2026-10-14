package app.knotwork.android.presentation.ui.settings.backup

import android.content.Context
import app.knotwork.android.BuildConfig
import app.knotwork.android.domain.backup.BackupArchiveInfo
import app.knotwork.android.domain.backup.BackupCounts
import app.knotwork.android.domain.backup.BackupFormatException
import app.knotwork.android.domain.backup.BackupManifest
import app.knotwork.android.domain.backup.BackupModelEntry
import app.knotwork.android.domain.backup.BackupReadFailure
import app.knotwork.android.domain.backup.BackupRestoreException
import app.knotwork.android.domain.backup.BackupRestorer
import app.knotwork.android.domain.backup.RestoreFailure
import app.knotwork.android.domain.backup.RestoreProgress
import app.knotwork.android.domain.backup.RestoreStep
import app.knotwork.android.domain.backup.StagedRestore
import app.knotwork.android.domain.usecases.InspectBackupDocumentUseCase
import app.knotwork.android.domain.usecases.PickedBackup
import app.knotwork.android.domain.usecases.RestoreSummaryData
import app.knotwork.android.domain.usecases.StageRestoreFromDocumentUseCase
import app.knotwork.android.domain.usecases.SummarizeRestoreUseCase
import app.knotwork.design.R
import app.knotwork.design.screens.settings.backup.RestoreCheckStep
import app.knotwork.design.screens.settings.backup.RestoreChecking
import app.knotwork.design.screens.settings.backup.RestoreCounts
import app.knotwork.design.screens.settings.backup.RestoreFile
import app.knotwork.design.screens.settings.backup.RestorePassword
import app.knotwork.design.screens.settings.backup.RestoreProblem
import app.knotwork.design.screens.settings.backup.RestoreProblemKind
import app.knotwork.design.screens.settings.backup.RestoreSummary
import app.knotwork.design.screens.settings.backup.Restoring
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import java.io.IOException

/**
 * [RestoreViewModel]: the picked file and what is wrong with it, the password and its check, every failure in
 * the user's terms, the summary, the typed confirm and the commit — and that nothing staged outlives a cancel or a
 * close.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RestoreViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val inspect = mockk<InspectBackupDocumentUseCase>()
    private val stage = mockk<StageRestoreFromDocumentUseCase>()
    private val summarize = mockk<SummarizeRestoreUseCase>()
    private val restorer = mockk<BackupRestorer>(relaxed = true) {
        coEvery { discard() } returns true
    }
    private val context = mockk<Context> {
        every { getString(R.string.knotwork_backup_confirm_keyword) } returns "REPLACE"
    }
    private lateinit var viewModel: RestoreViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { inspect(DOCUMENT) } returns PickedBackup.Readable(FILE_NAME, 4_096, INFO)
        coEvery { summarize(MANIFEST) } returns RestoreSummaryData(PHONE_COUNTS, listOf(MODEL))
        viewModel = RestoreViewModel(inspect, stage, summarize, restorer, context) { "on $it" }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given a backup when picked then its password is asked for, with what wrote it and when`() =
        runTest(dispatcher) {
            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            assertEquals(RestorePassword(FILE), viewModel.route.value)
        }

    @Test
    fun `given a file that is not a backup when picked then it says so by name`() = runTest(dispatcher) {
        coEvery { inspect(DOCUMENT) } returns
            PickedBackup.Unreadable("notes.txt", BackupFormatException(BackupReadFailure.NOT_A_BACKUP, "magic"))

        viewModel.onDocumentPicked(DOCUMENT)
        advanceUntilIdle()

        assertEquals(
            RestoreProblem(RestoreProblemKind.NOT_A_BACKUP, RestoreFile("notes.txt")),
            viewModel.route.value,
        )
    }

    @Test
    fun `given a backup in a newer format when picked then it asks to update Knotwork, naming both versions`() =
        runTest(dispatcher) {
            coEvery { inspect(DOCUMENT) } returns PickedBackup.Unreadable(
                FILE_NAME,
                BackupFormatException(BackupReadFailure.UNSUPPORTED_FORMAT_VERSION, "v2", writerVersionName = "1.4.0"),
            )

            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            assertEquals(
                RestoreProblem(
                    kind = RestoreProblemKind.NEWER,
                    file = RestoreFile(FILE_NAME, writerVersion = "1.4.0"),
                    phoneVersion = BuildConfig.VERSION_NAME,
                ),
                viewModel.route.value,
            )
        }

    @Test
    fun `given a file that cannot be read when picked then the read failure is told`() = runTest(dispatcher) {
        coEvery { inspect(DOCUMENT) } throws IOException("gone")

        viewModel.onDocumentPicked(DOCUMENT)
        advanceUntilIdle()

        assertEquals(RestoreProblem(RestoreProblemKind.READ_FAILED), viewModel.route.value)
    }

    @Test
    fun `given the picker backed out when it returns then nothing opens`() = runTest(dispatcher) {
        viewModel.onDocumentPicked(null)
        advanceUntilIdle()

        assertNull(viewModel.route.value)
    }

    @Test
    fun `given a password when checked then the steps show and the summary weighs the backup against this phone`() =
        runTest(dispatcher) {
            // Given
            val release = CompletableDeferred<Unit>()
            coEvery { stage(DOCUMENT, any(), any()) } coAnswers {
                thirdArg<(RestoreProgress) -> Unit>()(RestoreProgress(RestoreStep.UNPACK, 40, 100))
                release.await()
                STAGED
            }
            typePassword()

            // When
            viewModel.onCheck()

            // Then — the password step at once, then the file in bytes.
            assertEquals(RestoreChecking(FILE, RestoreCheckStep.PASSWORD), viewModel.route.value)
            advanceUntilIdle()
            assertEquals(RestoreChecking(FILE, RestoreCheckStep.FILE, 40, 100), viewModel.route.value)
            release.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                RestoreSummary(
                    fromBackup = RestoreCounts(chats = 12, memories = 340, pipelines = 9, files = 6),
                    keysIncluded = true,
                    modelsToDownload = 1,
                    modelsBytes = 600,
                    onThisPhone = RestoreCounts(chats = 3, memories = 10, pipelines = 2, files = 1),
                ),
                viewModel.route.value,
            )
        }

    @Test
    fun `given the wrong password when checked then it says so in place, keeping the text until edited`() =
        runTest(dispatcher) {
            coEvery { stage(DOCUMENT, any(), any()) } throws
                BackupFormatException(BackupReadFailure.WRONG_PASSWORD, "hmac")
            typePassword()

            viewModel.onCheck()
            advanceUntilIdle()

            assertEquals(RestorePassword(FILE, PASSWORD, wrongPassword = true), viewModel.route.value)
            assertFalse((viewModel.route.value as RestorePassword).canCheck)
            viewModel.onPasswordChange("$PASSWORD!")
            assertEquals(RestorePassword(FILE, "$PASSWORD!"), viewModel.route.value)
        }

    @Test
    fun `given each failure of a check when it ends then the user is told it in their own terms`() =
        runTest(dispatcher) {
            val cases = listOf(
                BackupFormatException(BackupReadFailure.DAMAGED, "tag") to
                    RestoreProblem(RestoreProblemKind.DAMAGED, FILE),
                BackupFormatException(BackupReadFailure.TRUNCATED, "end") to
                    RestoreProblem(RestoreProblemKind.INCOMPLETE, FILE),
                BackupFormatException(BackupReadFailure.TOO_DEMANDING, "kdf") to
                    RestoreProblem(RestoreProblemKind.DAMAGED, FILE),
                BackupRestoreException(RestoreFailure.NO_SPACE, "full", neededBytes = 9_000, freeBytes = 1_000) to
                    RestoreProblem(RestoreProblemKind.NO_SPACE, FILE, neededBytes = 9_000, freeBytes = 1_000),
                BackupRestoreException(RestoreFailure.NEWER_VERSION, "schema", writerVersionName = "2.0.0") to
                    RestoreProblem(
                        RestoreProblemKind.NEWER,
                        FILE.copy(writerVersion = "2.0.0"),
                        phoneVersion = BuildConfig.VERSION_NAME,
                    ),
                BackupRestoreException(RestoreFailure.DATABASE_UNUSABLE, "db") to
                    RestoreProblem(RestoreProblemKind.DAMAGED, FILE),
                BackupRestoreException(RestoreFailure.PHONE_KEY_UNAVAILABLE, "key") to
                    RestoreProblem(RestoreProblemKind.PHONE_KEY_UNAVAILABLE, FILE),
                BackupRestoreException(RestoreFailure.READ_FAILED, "io") to
                    RestoreProblem(RestoreProblemKind.READ_FAILED, FILE),
                IOException("provider") to RestoreProblem(RestoreProblemKind.READ_FAILED, FILE),
                IllegalStateException("a committed restore waits") to
                    RestoreProblem(RestoreProblemKind.READ_FAILED, FILE),
            )
            cases.forEach { (failure, expected) ->
                coEvery { stage(DOCUMENT, any(), any()) } throws failure
                viewModel.closeRoute()
                typePassword()

                viewModel.onCheck()
                advanceUntilIdle()

                assertEquals(failure.javaClass.simpleName, expected, viewModel.route.value)
            }
        }

    @Test
    fun `given too little space when Try again is tapped then the same password is checked again`() =
        runTest(dispatcher) {
            val passwords = mutableListOf<String>()
            coEvery { stage(DOCUMENT, any(), any()) } coAnswers {
                passwords += String(secondArg<CharArray>())
                if (passwords.size == 1) throw BackupRestoreException(RestoreFailure.NO_SPACE, "full", neededBytes = 1)
                STAGED
            }
            typePassword()
            viewModel.onCheck()
            advanceUntilIdle()

            viewModel.onTryAgain()
            advanceUntilIdle()

            assertEquals(listOf(PASSWORD, PASSWORD), passwords)
            assertTrue(viewModel.route.value is RestoreSummary)
        }

    @Test
    fun `given a check running when cancelled then what it staged goes and the password comes back`() =
        runTest(dispatcher) {
            coEvery { stage(DOCUMENT, any(), any()) } coAnswers { awaitCancellation() }
            typePassword()
            viewModel.onCheck()
            advanceUntilIdle()

            // Back while checking is Cancel.
            assertTrue(viewModel.onBack())
            advanceUntilIdle()

            assertEquals(RestorePassword(FILE, PASSWORD), viewModel.route.value)
            coVerify(exactly = 1) { restorer.discard() }
        }

    @Test
    fun `given the summary when Replace is typed loosely and confirmed then it commits and asks for a restart`() =
        runTest(dispatcher) {
            // Given — nothing collects: a recreated screen must still find the restart.
            reachSummary()
            viewModel.onReplace()
            assertEquals("", (viewModel.route.value as RestoreSummary).confirmInput)

            // When — trimmed and in any case, by the dialog's rule.
            viewModel.onConfirmInputChange(" replace ")
            viewModel.onConfirmReplace()

            // Then
            assertEquals(Restoring, viewModel.route.value)
            advanceUntilIdle()
            coVerify(exactly = 1) { restorer.commit(applySecrets = true) }
            assertTrue(viewModel.restartRequested.value)
            assertTrue("Back does nothing while restoring", viewModel.onBack())
            assertEquals(Restoring, viewModel.route.value)
        }

    @Test
    fun `given the wrong word typed when Replace is confirmed then nothing is committed`() = runTest(dispatcher) {
        reachSummary()
        viewModel.onReplace()

        viewModel.onConfirmInputChange("yes")
        viewModel.onConfirmReplace()
        advanceUntilIdle()

        assertTrue(viewModel.route.value is RestoreSummary)
        coVerify(exactly = 0) { restorer.commit(any()) }
    }

    @Test
    fun `given a commit that fails when it ends then the staged copy goes and the failure is told`() =
        runTest(dispatcher) {
            coEvery { restorer.commit(any()) } throws IOException("marker")
            reachSummary()
            viewModel.onReplace()
            viewModel.onConfirmInputChange("REPLACE")

            viewModel.onConfirmReplace()
            advanceUntilIdle()

            assertEquals(RestoreProblem(RestoreProblemKind.NOT_STARTED, FILE), viewModel.route.value)
            coVerify(exactly = 1) { restorer.discard() }
            assertFalse(viewModel.restartRequested.value)
        }

    @Test
    fun `given this phone's data that cannot be counted when the check ends then the staged copy goes`() =
        runTest(dispatcher) {
            coEvery { stage(DOCUMENT, any(), any()) } returns STAGED
            coEvery { summarize(MANIFEST) } throws IllegalStateException("database")
            typePassword()

            viewModel.onCheck()
            advanceUntilIdle()

            assertEquals(RestoreProblem(RestoreProblemKind.READ_FAILED, FILE), viewModel.route.value)
            coVerify(exactly = 1) { restorer.discard() }
        }

    @Test
    fun `given a check running when another file comes back from a picker then it is ignored`() = runTest(dispatcher) {
        coEvery { stage(DOCUMENT, any(), any()) } coAnswers { awaitCancellation() }
        typePassword()
        viewModel.onCheck()
        advanceUntilIdle()

        viewModel.onDocumentPicked("content://downloads/document/10")
        advanceUntilIdle()

        assertTrue(viewModel.route.value is RestoreChecking)
        coVerify(exactly = 1) { inspect(any()) }
    }

    @Test
    fun `given a provider that fails in its own way when the file is opened then it cannot be read, no crash`() =
        runTest(dispatcher) {
            coEvery { inspect(DOCUMENT) } throws IllegalArgumentException("provider")

            viewModel.onDocumentPicked(DOCUMENT)
            advanceUntilIdle()

            assertEquals(RestoreProblem(RestoreProblemKind.READ_FAILED), viewModel.route.value)
        }

    @Test
    fun `given the summary when Back is pressed then the dialog closes first, then the route, discarding the copy`() =
        runTest(dispatcher) {
            reachSummary()
            viewModel.onReplace()

            assertTrue(viewModel.onBack())
            assertNull((viewModel.route.value as RestoreSummary).confirmInput)
            assertTrue(viewModel.onBack())
            advanceUntilIdle()

            assertNull(viewModel.route.value)
            coVerify(exactly = 1) { restorer.discard() }
            assertFalse("on the screen itself Back leaves", viewModel.onBack())
        }

    @Test
    fun `given the password step when closed then nothing staged is discarded`() = runTest(dispatcher) {
        typePassword()

        viewModel.closeRoute()
        advanceUntilIdle()

        assertNull(viewModel.route.value)
        coVerify(exactly = 0) { restorer.discard() }
    }

    @Test
    fun `given a check when it ends then the password handed to it is wiped`() = runTest(dispatcher) {
        val handed = slot<CharArray>()
        coEvery { stage(DOCUMENT, capture(handed), any()) } returns STAGED
        typePassword()

        viewModel.onCheck()
        advanceUntilIdle()

        assertTrue(handed.captured.all { it == ' ' })
    }

    @Test
    fun `given Restore from file when tapped then the picker opens`() = runTest(dispatcher) {
        val events = events()

        viewModel.startRestore()
        advanceUntilIdle()

        assertEquals(listOf<RestoreEvent>(RestoreEvent.PickDocument), events)
    }

    private fun TestScope.events(): MutableList<RestoreEvent> {
        val events = mutableListOf<RestoreEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        return events
    }

    private fun TestScope.typePassword() {
        viewModel.onDocumentPicked(DOCUMENT)
        advanceUntilIdle()
        viewModel.onPasswordChange(PASSWORD)
    }

    private fun TestScope.reachSummary() {
        coEvery { stage(DOCUMENT, any(), any()) } returns STAGED
        typePassword()
        viewModel.onCheck()
        advanceUntilIdle()
        assertTrue(viewModel.route.value is RestoreSummary)
    }

    private companion object {
        const val DOCUMENT = "content://downloads/document/9"
        const val FILE_NAME = "knotwork-backup-2026-10-12.knotwork"
        const val PASSWORD = "brass lantern otter quiet"
        val INFO = BackupArchiveInfo(appVersionName = "0.12.0", createdAtEpochMillis = 1_000)
        val FILE = RestoreFile(FILE_NAME, writerVersion = "0.12.0", createdLabel = "on 1000", sizeBytes = 4_096)
        val MODEL = BackupModelEntry("gemma.litertlm", 600, null, "https://example/gemma")
        val MANIFEST = BackupManifest(
            createdAtEpochMillis = 1_000,
            schemaVersion = 71,
            secretsIncluded = true,
            counts = BackupCounts(chats = 12, memories = 340, pipelines = 9, workspaceFiles = 4, attachments = 2),
            scheduledTasks = 0,
            models = listOf(MODEL),
        )
        val STAGED = StagedRestore(INFO, MANIFEST)
        val PHONE_COUNTS = BackupCounts(chats = 3, memories = 10, pipelines = 2, workspaceFiles = 1, attachments = 0)
    }
}
