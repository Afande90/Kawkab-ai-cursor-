package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.backup.BackupDocuments
import app.knotwork.android.domain.backup.BackupExportException
import app.knotwork.android.domain.backup.BackupExportFailure
import app.knotwork.android.domain.backup.BackupManifest
import app.knotwork.android.domain.backup.BackupProgress
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

/**
 * Backs the app up into the document the user picked (Settings → Privacy → Backup & restore → Back up).
 *
 * The document is remembered as unfinished while it is written, so a start after the process died deletes it. A
 * backup that fails deletes its unfinished document and says whether that worked. A cancelled one deletes it
 * too, whatever cancelled it — on the way into the document as well — and tells `onUnfinishedDeleted` whether
 * that worked, since a cancelled call returns nothing. Once the outcome is settled, neither recording a complete
 * backup nor deleting a failed one can be cancelled: a complete backup is never left marked unfinished, and a
 * failed one never left behind half-deleted. A complete backup this phone cannot record as complete is deleted and
 * reported as [BackupExportFailure.PHONE_FULL], since the next start would delete it as unfinished.
 *
 * @property exportBackup Writes the archive.
 * @property documents The picked document.
 */
class BackUpToDocumentUseCase @Inject constructor(
    private val exportBackup: ExportBackupUseCase,
    private val documents: BackupDocuments,
) {

    /**
     * Writes the backup.
     *
     * @param document The document's URI.
     * @param password The password the user chose; the caller clears its copy afterwards.
     * @param includeSecrets Whether API keys, the Hugging Face token and MCP sign-ins go in.
     * @param onUnfinishedDeleted Told, when the backup is cancelled or breaks, whether its unfinished document was
     *   deleted; not called when the call returns an outcome.
     * @param onProgress Told the current step and the bytes written so far.
     * @return What happened.
     */
    suspend operator fun invoke(
        document: String,
        password: CharArray,
        includeSecrets: Boolean,
        onUnfinishedDeleted: (Boolean) -> Unit = {},
        onProgress: (BackupProgress) -> Unit,
    ): BackupToDocumentOutcome {
        var settled = false
        try {
            val counting = try {
                CountingOutputStream(documents.openForWrite(document))
            } catch (e: IOException) {
                settled = true
                val deleted = withContext(NonCancellable) { documents.discard(document) }
                return BackupToDocumentOutcome.Failed(BackupExportFailure.WRITE_FAILED, 0, deleted)
            }
            val manifest = exportBackup(counting, password, includeSecrets, onProgress)
            // From here the file is a complete backup: nothing below may delete it, and a cancel cannot stop the
            // record that it is complete.
            settled = true
            try {
                withContext(NonCancellable) { documents.finish(document) }
            } catch (e: IOException) {
                // Complete, but this phone could not record it: the next start would take it for unfinished and
                // delete it. It goes now, told as the phone's own failure — a write this small fails on a full disk.
                val deleted = withContext(NonCancellable) { documents.discard(document) }
                return BackupToDocumentOutcome.Failed(BackupExportFailure.PHONE_FULL, 0, deleted)
            }
            return BackupToDocumentOutcome.Saved(manifest, counting.written)
        } catch (e: BackupExportException) {
            settled = true
            val deleted = withContext(NonCancellable) { documents.discard(document) }
            return BackupToDocumentOutcome.Failed(e.failure, e.neededBytes, deleted)
        } finally {
            // Cancelled, or anything else unexpected: the unfinished file goes, and the caller hears how that went.
            if (!settled) onUnfinishedDeleted(withContext(NonCancellable) { documents.discard(document) })
        }
    }

    /** Counts the bytes that reach the document: the size of the file. */
    private class CountingOutputStream(target: OutputStream) : FilterOutputStream(target) {
        var written = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            written++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            written += len
        }
    }
}

/** How a backup into a picked document ended. */
sealed interface BackupToDocumentOutcome {

    /**
     * The backup is in the document.
     *
     * @property manifest What it holds.
     * @property sizeBytes The size of the file.
     */
    data class Saved(val manifest: BackupManifest, val sizeBytes: Long) : BackupToDocumentOutcome

    /**
     * The backup could not be written.
     *
     * @property failure Why.
     * @property neededBytes With [BackupExportFailure.NO_SPACE], about how much room the file needs.
     * @property documentDeleted Whether the unfinished document was deleted.
     */
    data class Failed(val failure: BackupExportFailure, val neededBytes: Long, val documentDeleted: Boolean) :
        BackupToDocumentOutcome
}
