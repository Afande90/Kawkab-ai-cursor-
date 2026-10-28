package app.kawkab.android.data.backup

import java.io.File
import java.io.IOException

/**
 * Puts the database and the files of a committed restore in place, at the very start of the process —
 * before the app's dependencies exist, so nothing has the database or a file open.
 *
 * Only renames: the staged files live on the same file system as the live ones. Each part is done when
 * its staged source is gone, so a start interrupted half-way finishes the rest the next time, and a
 * second run does nothing. What it moves aside goes to [RestoreLayout.replaced]; that, the settings, the
 * secret stores and the model registry are dealt with later, once the app is up
 * ([BackupRestorer.finishPending][app.kawkab.android.domain.backup.BackupRestorer]).
 */
internal object PendingRestoreApplier {

    /**
     * Applies a committed restore's files.
     *
     * @param layout Where the restore and the live data are.
     * @return `true` when a committed restore's files are now in place; `false` when there was nothing
     *   committed to apply.
     * @throws IOException When a file could not be moved; what was moved stays moved, and the next start
     *   tries the rest again.
     */
    fun applyIfCommitted(layout: RestoreLayout): Boolean {
        if (!layout.isCommitted) return false
        replaceDatabase(layout)
        replaceDirectory(layout.stagedWorkspace, layout.liveWorkspace, layout.replaced)
        replaceDirectory(layout.stagedAttachments, layout.liveAttachments, layout.replaced)
        // What was moved aside is deleted later, off the main thread, once the app is up.
        return true
    }

    private fun replaceDatabase(layout: RestoreLayout) {
        val staged = layout.stagedDatabase
        if (!staged.exists()) return
        val live = layout.liveDatabase
        DATABASE_SIDECARS.forEach { suffix ->
            val file = File(live.path + suffix)
            if (file.exists() && !file.delete()) throw IOException("the database being replaced could not be deleted")
        }
        live.parentFile?.mkdirs()
        if (!staged.renameTo(live)) throw IOException("the restored database could not be moved into place")
    }

    private fun replaceDirectory(staged: File, live: File, replaced: File) {
        if (!staged.exists()) return
        if (live.exists()) {
            replaced.mkdirs()
            val parked = File(replaced, live.name)
            if (parked.exists() && !parked.deleteRecursively()) {
                throw IOException("an earlier attempt's replaced files could not be cleared")
            }
            if (!live.renameTo(parked)) throw IOException("the directory being replaced could not be moved")
        }
        if (!staged.renameTo(live)) throw IOException("the restored directory could not be moved into place")
    }

    /** The database file and the journal files SQLite may keep beside it. */
    private val DATABASE_SIDECARS = listOf("", "-wal", "-shm", "-journal")
}
