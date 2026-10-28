package app.kawkab.android.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [PendingRestoreApplier] moves a committed restore's database and files into place, does nothing without
 * the commit, and finishes a start that stopped half-way — a second run changes nothing.
 */
class PendingRestoreApplierTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var layout: RestoreLayout

    @Before
    fun setUp() {
        val app = folder.newFolder("app")
        layout = RestoreLayout(
            root = File(app, "files/restore"),
            liveDatabase = File(app, "databases/agent_database.db"),
            liveWorkspace = File(app, "files/agent_workspace"),
            liveAttachments = File(app, "files/attachments"),
        )
        // This phone's data.
        layout.liveDatabase.write("phone database")
        File(layout.liveDatabase.path + "-wal").write("phone log")
        File(layout.liveWorkspace, "notes/old.md").write("phone note")
        File(layout.liveAttachments, "old.jpg").write("phone photo")
        // The staged restore.
        layout.stagedDatabase.write("restored database")
        File(layout.stagedWorkspace, "plan.md").write("restored note")
        layout.stagedAttachments.mkdirs()
    }

    @Test
    fun `given a restore that is staged but not committed when the app starts then nothing moves`() {
        // When
        val applied = PendingRestoreApplier.applyIfCommitted(layout)

        // Then
        assertFalse(applied)
        assertEquals("phone database", layout.liveDatabase.readText())
        assertTrue(layout.stagedDatabase.exists())
    }

    @Test
    fun `given a committed restore when the app starts then its database and files replace this phone's`() {
        // Given
        layout.writeCommit(RestoreLayout.CommitMarker(applySecrets = false, backgroundStopped = true))

        // When
        val applied = PendingRestoreApplier.applyIfCommitted(layout)

        // Then
        assertTrue(applied)
        assertEquals("restored database", layout.liveDatabase.readText())
        assertFalse("the replaced database's log goes with it", File(layout.liveDatabase.path + "-wal").exists())
        assertEquals(listOf("plan.md"), layout.liveWorkspace.list()!!.toList())
        assertEquals("an archive without attachments leaves none", 0, layout.liveAttachments.list()!!.size)
        assertTrue(layout.isSwapped)
        assertEquals(
            "what was replaced is moved aside, to be deleted once the app is up",
            setOf("agent_workspace", "attachments"),
            layout.replaced.list()!!.toSet(),
        )
        assertTrue("the marker stays for the rest of the restore", layout.isCommitted)
    }

    @Test
    fun `given a start that stopped after the database when the app starts again then it moves the rest`() {
        // Given — the database is already in place; the workspace was parked but not replaced.
        layout.writeCommit(RestoreLayout.CommitMarker(applySecrets = false, backgroundStopped = true))
        layout.liveDatabase.delete()
        layout.stagedDatabase.renameTo(layout.liveDatabase)
        layout.replaced.mkdirs()
        layout.liveWorkspace.renameTo(File(layout.replaced, layout.liveWorkspace.name))

        // When
        PendingRestoreApplier.applyIfCommitted(layout)

        // Then
        assertEquals("restored database", layout.liveDatabase.readText())
        assertEquals(listOf("plan.md"), layout.liveWorkspace.list()!!.toList())
        assertTrue(layout.isSwapped)
    }

    @Test
    fun `given an applied restore when the app starts again then nothing changes`() {
        // Given
        layout.writeCommit(RestoreLayout.CommitMarker(applySecrets = false, backgroundStopped = true))
        PendingRestoreApplier.applyIfCommitted(layout)
        File(layout.liveWorkspace, "written-since.md").write("new")

        // When
        PendingRestoreApplier.applyIfCommitted(layout)

        // Then
        assertEquals("restored database", layout.liveDatabase.readText())
        assertEquals(setOf("plan.md", "written-since.md"), layout.liveWorkspace.list()!!.toSet())
    }

    @Test
    fun `given a commit marker when read back then it says what was written, and a broken one is read safely`() {
        // When
        layout.writeCommit(RestoreLayout.CommitMarker(applySecrets = true, backgroundStopped = false))

        // Then
        assertEquals(RestoreLayout.CommitMarker(applySecrets = true, backgroundStopped = false), layout.readCommit())

        // Given — a marker that does not parse.
        File(layout.staging, "commit.json").writeText("{broken")

        // Then — secrets are left alone and background work is not taken as stopped.
        assertEquals(RestoreLayout.CommitMarker(applySecrets = false, backgroundStopped = false), layout.readCommit())
    }

    private fun File.write(text: String) {
        parentFile?.mkdirs()
        writeText(text)
    }
}
