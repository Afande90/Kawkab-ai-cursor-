package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.backup.BackupArchiveInfo
import app.knotwork.android.domain.backup.BackupDocuments
import app.knotwork.android.domain.backup.BackupFormatException
import app.knotwork.android.domain.backup.BackupReadFailure
import app.knotwork.android.domain.backup.BackupRestorer
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * [InspectBackupDocumentUseCase]: a readable backup comes back with its name, size and header; one this build
 * cannot read comes back with its name and why, not as a failure; a file that cannot be read at all fails.
 */
class InspectBackupDocumentUseCaseTest {

    private val documents = mockk<BackupDocuments> {
        coEvery { displayName(DOCUMENT) } returns "backup.knotwork"
        coEvery { sizeOf(DOCUMENT) } returns 4_096
        coEvery { openForRead(DOCUMENT) } returns ByteArrayInputStream(ByteArray(0))
    }
    private val restorer = mockk<BackupRestorer>()
    private val useCase = InspectBackupDocumentUseCase(documents, restorer)

    @Test
    fun `given a backup when inspected then its name, size and header come back`() = runTest {
        coEvery { restorer.inspect(any()) } returns INFO

        assertEquals(PickedBackup.Readable("backup.knotwork", 4_096, INFO), useCase(DOCUMENT))
    }

    @Test
    fun `given a file this build cannot read when inspected then it comes back named, with why`() = runTest {
        val failure = BackupFormatException(BackupReadFailure.NOT_A_BACKUP, "magic")
        coEvery { restorer.inspect(any()) } throws failure

        assertEquals(PickedBackup.Unreadable("backup.knotwork", failure), useCase(DOCUMENT))
    }

    @Test
    fun `given a file that cannot be opened when inspected then the failure passes`() = runTest {
        coEvery { documents.openForRead(DOCUMENT) } throws IOException("revoked")

        val failure = try {
            useCase(DOCUMENT)
            null
        } catch (e: IOException) {
            e
        }

        assertTrue(failure != null)
    }

    private companion object {
        const val DOCUMENT = "content://downloads/document/9"
        val INFO = BackupArchiveInfo(appVersionName = "0.12.0", createdAtEpochMillis = 1_000)
    }
}
