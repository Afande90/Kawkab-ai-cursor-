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
 * too, whatever cancelled it — on the way into the document as well; a caller that needs to know whether that
 * worked asks [BackupDocuments.discard] again, which reports what the first time answered. Once the archive is
 * complete, recording that is not cancellable: a complete backup is never left marked unfinished.
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
     * @param onProgress Told the current step and the bytes written so far.
     * @return What happened.
     */
    suspend operator fun invoke(
        document: String,
        password: CharArray,
        includeSecrets: Boolean,
        onProgress: (BackupProgress) -> Unit,
    ): BackupToDocumentOutcome {
        var settled = false
        try {
            val counting = try {
                CountingOutputStream(documents.openForWrite(document))
            } catch (e: IOException) {
                settled = true
                return BackupToDocumentOutcome.Failed(BackupExportFailure.WRITE_FAILED, 0, documents.discard(document))
            }
            val manifest = exportBackup(counting, password, includeSecrets, onProgress)
            // From here the file is a complete backup: nothing below may delete it, and a cancel cannot stop the
            // record that it is complete.
            settled = true
            withContext(NonCancellable) { documents.finish(document) }
            return BackupToDocumentOutcome.Saved(manifest, counting.written)
        } catch (e: BackupExportException) {
            settled = true
            return BackupToDocumentOutcome.Failed(e.failure, e.neededBytes, documents.discard(document))
        } finally {
            // Cancelled, or anything else unexpected: the unfinished file goes.
            if (!settled) withContext(NonCancellable) { documents.discard(document) }
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
