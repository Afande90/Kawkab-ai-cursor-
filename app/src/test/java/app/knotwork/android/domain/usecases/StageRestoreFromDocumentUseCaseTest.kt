package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.backup.BackupArchiveInfo
import app.knotwork.android.domain.backup.BackupCounts
import app.knotwork.android.domain.backup.BackupDocuments
import app.knotwork.android.domain.backup.BackupManifest
import app.knotwork.android.domain.backup.BackupRestorer
import app.knotwork.android.domain.backup.StagedRestore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.ByteArrayInputStream

/** [StageRestoreFromDocumentUseCase]: the picked document's stream and the password go to the restorer as given. */
class StageRestoreFromDocumentUseCaseTest {

    @Test
    fun `given a picked backup when staged then the restorer reads that document with that password`() = runTest {
        // Given
        val stream = ByteArrayInputStream(ByteArray(0))
        val documents = mockk<BackupDocuments> { coEvery { openForRead(DOCUMENT) } returns stream }
        val password = "brass lantern otter quiet".toCharArray()
        val restorer = mockk<BackupRestorer> { coEvery { stage(stream, password, any()) } returns STAGED }

        // When
        val staged = StageRestoreFromDocumentUseCase(documents, restorer)(DOCUMENT, password) { }

        // Then
        assertSame(STAGED, staged)
        assertEquals("brass lantern otter quiet", String(password))
    }

    private companion object {
        const val DOCUMENT = "content://downloads/document/9"
        val STAGED = StagedRestore(
            BackupArchiveInfo("0.12.0", 1_000),
            BackupManifest(
                createdAtEpochMillis = 1_000,
                schemaVersion = 71,
                secretsIncluded = false,
                counts = BackupCounts(0, 0, 0, 0, 0),
                scheduledTasks = 0,
                models = emptyList(),
            ),
        )
    }
}
