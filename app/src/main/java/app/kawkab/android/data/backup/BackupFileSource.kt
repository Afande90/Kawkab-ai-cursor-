package app.kawkab.android.data.backup

import android.content.Context
import app.kawkab.android.data.local.AgentWorkspaceImpl
import app.kawkab.android.data.local.AttachmentStoreImpl
import app.kawkab.android.domain.backup.BackupEntryName
import app.kawkab.android.domain.backup.BackupFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The files a backup carries: the agent's workspace and the image attachments, each as an archive entry.
 *
 * Only regular files go in — no symbolic link, and no unfinished write (`.knotwork-tmp`). A file whose
 * path cannot be an entry name ([BackupEntryName]: a backslash, a control or bidirectional formatting
 * character) is left out and counted, so the export can say so instead of failing whole.
 *
 * @param workspaceRoot The workspace directory.
 * @param attachmentsRoot The attachments directory.
 */
@Singleton
internal class BackupFileSource(private val workspaceRoot: () -> File, private val attachmentsRoot: () -> File) {

    /** Reads the app's own directories. */
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        workspaceRoot = { File(context.filesDir, AgentWorkspaceImpl.WORKSPACE_DIR_NAME) },
        attachmentsRoot = { File(context.filesDir, AttachmentStoreImpl.ATTACHMENTS_DIR) },
    )

    /**
     * One file to put into the archive.
     *
     * @property name Its entry name.
     * @property file The file.
     * @property sizeBytes Its size when listed; it may change before it is read.
     */
    data class Entry(val name: String, val file: File, val sizeBytes: Long)

    /**
     * The files of one directory as entries.
     *
     * @property entries The files that go in, sorted by entry name.
     * @property skipped How many files were left out because their path cannot be an entry name.
     */
    data class Listing(val entries: List<Entry>, val skipped: Int)

    /** The workspace's files, under [BackupFormat.WORKSPACE_PREFIX]. */
    fun workspace(): Listing = list(workspaceRoot(), BackupFormat.WORKSPACE_PREFIX)

    /** The image attachments, under [BackupFormat.ATTACHMENTS_PREFIX]. */
    fun attachments(): Listing = list(attachmentsRoot(), BackupFormat.ATTACHMENTS_PREFIX)

    private fun list(root: File, prefix: String): Listing {
        if (!root.isDirectory) return Listing(emptyList(), skipped = 0)
        var skipped = 0
        val entries = root.walkTopDown()
            .filter { Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS) }
            .filterNot { it.name.endsWith(AgentWorkspaceImpl.RESERVED_TMP_SUFFIX) }
            .mapNotNull { file ->
                val name = prefix + file.relativeTo(root).invariantSeparatorsPath
                if (BackupEntryName.isValid(name)) {
                    Entry(name, file, file.length())
                } else {
                    skipped++
                    null
                }
            }
            .sortedBy { it.name }
            .toList()
        return Listing(entries, skipped)
    }
}
