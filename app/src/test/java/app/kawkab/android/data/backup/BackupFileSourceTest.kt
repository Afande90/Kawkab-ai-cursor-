package app.kawkab.android.data.backup

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * [BackupFileSource]: regular files of both directories become entries under their prefixes, sorted;
 * unfinished writes and symbolic links stay out; a path that cannot be an entry name is counted, not
 * written; a missing directory is simply empty.
 */
class BackupFileSourceTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `given files of both directories when listed then each is an entry under its prefix`() {
        // Given
        val workspace = temp.newFolder("agent_workspace")
        val attachments = temp.newFolder("attachments")
        File(workspace, "notes").mkdirs()
        File(workspace, "notes/b.md").writeText("bb")
        File(workspace, "a.txt").writeText("a")
        File(workspace, "a.txt.knotwork-tmp").writeText("unfinished")
        File(attachments, "1.jpg").writeBytes(ByteArray(3))
        Files.createSymbolicLink(File(workspace, "link").toPath(), File(workspace, "a.txt").toPath())
        val source = BackupFileSource({ workspace }, { attachments })

        // When
        val files = source.workspace()
        val images = source.attachments()

        // Then
        assertEquals(
            listOf("files/agent_workspace/a.txt" to 1L, "files/agent_workspace/notes/b.md" to 2L),
            files.entries.map { it.name to it.sizeBytes },
        )
        assertEquals(listOf("files/attachments/1.jpg"), images.entries.map { it.name })
        assertEquals(0, files.skipped)
    }

    @Test
    fun `given a file whose path cannot be an entry name when listed then it is counted, not listed`() {
        // Given — a backslash is legal in a Linux file name and illegal in an entry name.
        val workspace = temp.newFolder("agent_workspace")
        File(workspace, "a\\b.txt").writeText("x")
        File(workspace, "ok.txt").writeText("y")
        val source = BackupFileSource({ workspace }, { File(temp.root, "missing") })

        // When
        val listing = source.workspace()

        // Then
        assertEquals(listOf("files/agent_workspace/ok.txt"), listing.entries.map { it.name })
        assertEquals(1, listing.skipped)
        assertEquals(BackupFileSource.Listing(emptyList(), 0), source.attachments())
    }
}
