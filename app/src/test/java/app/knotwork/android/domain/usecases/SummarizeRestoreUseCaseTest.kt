package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.backup.BackupCounts
import app.knotwork.android.domain.backup.BackupManifest
import app.knotwork.android.domain.backup.BackupModelEntry
import app.knotwork.android.domain.backup.LocalDataCounter
import app.knotwork.android.domain.models.LocalModel
import app.knotwork.android.domain.models.ModelFileStatus
import app.knotwork.android.domain.repositories.LocalModelRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SummarizeRestoreUseCase]: this phone's counts as they are, and of the backup's models only those whose file is
 * not here — a registered model with its file missing still has to be downloaded.
 */
class SummarizeRestoreUseCaseTest {

    @Test
    fun `given a backup when summarised then this phone's counts and the models it lacks come back`() = runTest {
        // Given — one model here with its file, one registered without it, one unknown here.
        val counter = mockk<LocalDataCounter> { coEvery { count() } returns PHONE }
        val models = mockk<LocalModelRepository> {
            coEvery { findByFileName("here.litertlm") } returns local("here.litertlm")
            coEvery { findByFileName("gone.litertlm") } returns local("gone.litertlm")
            coEvery { findByFileName("new.litertlm") } returns null
            coEvery { fileStatus("/models/here.litertlm") } returns ModelFileStatus.HashPending
            coEvery { fileStatus("/models/gone.litertlm") } returns ModelFileStatus.Missing
        }
        val manifest = BackupManifest(
            createdAtEpochMillis = 0,
            schemaVersion = 71,
            secretsIncluded = false,
            counts = BackupCounts(1, 1, 1, 1, 1),
            scheduledTasks = 0,
            models = listOf(entry("here.litertlm"), entry("gone.litertlm"), entry("new.litertlm")),
        )

        // When
        val summary = SummarizeRestoreUseCase(counter, models)(manifest)

        // Then
        assertEquals(RestoreSummaryData(PHONE, listOf(entry("gone.litertlm"), entry("new.litertlm"))), summary)
    }

    private fun entry(name: String) = BackupModelEntry(name, 100, null, "https://example/$name")

    private fun local(name: String) = mockk<LocalModel> { coEvery { path } returns "/models/$name" }

    private companion object {
        val PHONE = BackupCounts(chats = 3, memories = 10, pipelines = 2, workspaceFiles = 1, attachments = 0)
    }
}
