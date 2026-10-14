package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.backup.BackupArchiveInfo
import app.knotwork.android.domain.backup.BackupCounts
import app.knotwork.android.domain.backup.BackupDocuments
import app.knotwork.android.domain.backup.BackupManifest
import app.knotwork.android.domain.backup.BackupRestorer
import app.knotwork.android.domain.backup.StagedRestore
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * [StageRestoreFromDocumentUseCase]: the picked document's stream and the password go to the restorer as given, and
 * the stream is closed even when the restorer refuses before taking it.
 */
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

    @Test
    fun `given a restorer that refuses before it takes the stream when staged then the stream is closed`() = runTest {
        // Given — a committed restore waits for the restart, so the restorer refuses at once.
        val stream = mockk<InputStream>(relaxed = true)
        val documents = mockk<BackupDocuments> { coEvery { openForRead(DOCUMENT) } returns stream }
        val restorer = mockk<BackupRestorer> {
            coEvery { stage(any(), any(), any()) } throws IllegalStateException("a committed restore waits")
        }

        // When
        try {
            StageRestoreFromDocumentUseCase(documents, restorer)(DOCUMENT, "pw".toCharArray()) { }
        } catch (e: IllegalStateException) {
            // Expected: the caller hears of it.
        }

        // Then
        verify { stream.close() }
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
