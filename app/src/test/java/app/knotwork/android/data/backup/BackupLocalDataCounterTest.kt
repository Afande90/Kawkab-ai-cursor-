package app.knotwork.android.data.backup

import app.knotwork.android.domain.backup.BackupCounts
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.MemoryRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [BackupLocalDataCounter]: counts what a backup taken now would hold, files from the same listing it uses. */
class BackupLocalDataCounterTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `given this phone's data when counted then each kind is counted as a backup counts it`() = runTest {
        // Given — two workspace files (one in a folder), one image, and a half-written file the backup skips too.
        val workspace = folder.newFolder("workspace")
        workspace.resolve("notes.md").writeText("a")
        workspace.resolve("reports").mkdirs()
        workspace.resolve("reports/q3.md").writeText("b")
        workspace.resolve("draft.md.knotwork-tmp").writeText("c")
        val attachments = folder.newFolder("attachments")
        attachments.resolve("photo.jpg").writeBytes(ByteArray(3))
        val counter = BackupLocalDataCounter(
            chatRepository = mockk<ChatRepository> { coEvery { getAllSessions() } returns listOf("a", "b", "c") },
            memoryRepository = mockk<MemoryRepository> { coEvery { countMemories() } returns 10 },
            pipelineRepository = mockk<PipelineRepository> {
                every { getAllPipelines() } returns
                    flowOf(listOf(mockk(), mockk()))
            },
            files = BackupFileSource(workspaceRoot = { workspace }, attachmentsRoot = { attachments }),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        // When
        val counts = counter.count()

        // Then
        assertEquals(BackupCounts(chats = 3, memories = 10, pipelines = 2, workspaceFiles = 2, attachments = 1), counts)
    }
}
