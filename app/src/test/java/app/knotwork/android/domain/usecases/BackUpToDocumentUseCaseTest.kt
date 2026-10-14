package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.backup.BackupCounts
import app.knotwork.android.domain.backup.BackupDocuments
import app.knotwork.android.domain.backup.BackupExportException
import app.knotwork.android.domain.backup.BackupExportFailure
import app.knotwork.android.domain.backup.BackupExporter
import app.knotwork.android.domain.backup.BackupManifest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * [BackUpToDocumentUseCase]: a saved backup is finished and measured; a failed, refused, cancelled or broken one
 * deletes its unfinished document — and only a complete one is ever kept.
 */
class BackUpToDocumentUseCaseTest {

    private val written = ByteArrayOutputStream()
    private val documents = mockk<BackupDocuments>(relaxed = true) {
        coEvery { openForWrite(DOCUMENT) } returns written
        coEvery { discard(DOCUMENT) } returns true
    }
    private val exporter = mockk<BackupExporter>()
    private val useCase = BackUpToDocumentUseCase(ExportBackupUseCase(exporter), documents)

    @Test
    fun `given the export writes the archive when backed up then it is finished, measured and kept`() = runTest {
        // Given
        coEvery { exporter.export(any(), any(), true, any()) } coAnswers {
            firstArg<OutputStream>().use { it.write(ByteArray(1_500)) }
            MANIFEST
        }

        // When
        val outcome = useCase(DOCUMENT, PASSWORD.toCharArray(), includeSecrets = true) { }

        // Then
        assertEquals(BackupToDocumentOutcome.Saved(MANIFEST, sizeBytes = 1_500), outcome)
        coVerify { documents.finish(DOCUMENT) }
        coVerify(exactly = 0) { documents.discard(any()) }
    }

    @Test
    fun `given the export fails when backed up then the unfinished document is deleted and the failure told`() =
        runTest {
            coEvery { exporter.export(any(), any(), false, any()) } throws
                BackupExportException(BackupExportFailure.NO_SPACE, 9_000, "full")
            coEvery { documents.discard(DOCUMENT) } returns false

            val outcome = useCase(DOCUMENT, PASSWORD.toCharArray(), includeSecrets = false) { }

            assertEquals(BackupToDocumentOutcome.Failed(BackupExportFailure.NO_SPACE, 9_000, false), outcome)
            coVerify(exactly = 0) { documents.finish(any()) }
            // Once: a second delete would ask a provider that refused the first, and report its answer instead.
            coVerify(exactly = 1) { documents.discard(DOCUMENT) }
        }

    @Test
    fun `given the document cannot be opened when backed up then the place stopped accepting the file`() = runTest {
        coEvery { documents.openForWrite(DOCUMENT) } throws IOException("revoked")

        val outcome = useCase(DOCUMENT, PASSWORD.toCharArray(), includeSecrets = false) { }

        assertEquals(BackupToDocumentOutcome.Failed(BackupExportFailure.WRITE_FAILED, 0, true), outcome)
        coVerify { documents.discard(DOCUMENT) }
    }

    @Test
    fun `given the backup is cancelled when it stops then the unfinished document is deleted`() = runTest {
        // Given
        coEvery { exporter.export(any(), any(), false, any()) } coAnswers { awaitCancellation() }

        // When
        val job = launch { useCase(DOCUMENT, PASSWORD.toCharArray(), includeSecrets = false) { } }
        testScheduler.advanceUntilIdle()
        job.cancel(CancellationException("user"))
        job.join()

        // Then
        coVerify(exactly = 1) { documents.discard(DOCUMENT) }
        coVerify(exactly = 0) { documents.finish(any()) }
    }

    @Test
    fun `given the export throws unexpectedly when backed up then the document is deleted and the error passes`() =
        runTest {
            coEvery { exporter.export(any(), any(), false, any()) } throws IllegalStateException("bug")

            val failure = try {
                useCase(DOCUMENT, PASSWORD.toCharArray(), includeSecrets = false) { }
                null
            } catch (e: IllegalStateException) {
                e
            }

            assertTrue(failure != null)
            coVerify(exactly = 1) { documents.discard(DOCUMENT) }
        }

    @Test
    fun `given recording that the backup is finished fails when backed up then the complete file is not deleted`() =
        runTest {
            coEvery { exporter.export(any(), any(), false, any()) } returns MANIFEST
            coEvery { documents.finish(DOCUMENT) } throws IOException("datastore")

            try {
                useCase(DOCUMENT, PASSWORD.toCharArray(), includeSecrets = false) { }
            } catch (e: IOException) {
                // The caller hears of it; the backup itself is complete.
            }

            coVerify(exactly = 0) { documents.discard(any()) }
        }

    private companion object {
        const val DOCUMENT = "content://downloads/document/7"
        const val PASSWORD = "brass lantern otter quiet"
        val MANIFEST = BackupManifest(
            createdAtEpochMillis = 0,
            schemaVersion = 71,
            secretsIncluded = false,
            counts = BackupCounts(chats = 1, memories = 2, pipelines = 3, workspaceFiles = 4, attachments = 5),
            scheduledTasks = 0,
            models = emptyList(),
        )
    }
}
