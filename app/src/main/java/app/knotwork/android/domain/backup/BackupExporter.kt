package app.knotwork.android.domain.backup

import java.io.IOException
import java.io.OutputStream

/**
 * Writes the whole app's data into one archive, encrypted with a password (`docs/backup-format.md`).
 *
 * The caller owns where the archive goes — the document the user picked — and opens the stream; the
 * exporter closes it. Only a complete export writes the chunk flagged as last: cancelled or failed
 * half-way, the file left behind reads as incomplete, never as a backup. Nothing the export makes on
 * the way (the database snapshot) outlives it.
 */
interface BackupExporter {

    /**
     * Exports everything into [target].
     *
     * @param target The stream the archive is written to. Closed when the export ends, however it ends.
     * @param password The password the user chose. The caller clears its own copy afterwards.
     * @param includeSecrets Whether API keys, the Hugging Face token and MCP sign-ins go in too.
     * @param onProgress Told the current step and how many bytes of content are written so far.
     * @return What the archive holds.
     * @throws BackupExportException When the export fails for a reason the user is told.
     * @throws kotlinx.coroutines.CancellationException When the coroutine is cancelled; the partial file
     *   reads as incomplete.
     */
    suspend fun export(
        target: OutputStream,
        password: CharArray,
        includeSecrets: Boolean,
        onProgress: (BackupProgress) -> Unit,
    ): BackupManifest
}

/**
 * Where an export is, for the progress the user watches.
 *
 * @property step The step running now.
 * @property bytesDone Content bytes written so far.
 * @property bytesTotal Content bytes the export expects to write — an estimate taken when it starts.
 */
data class BackupProgress(val step: BackupStep, val bytesDone: Long, val bytesTotal: Long)

/** The steps of an export, in order, as the user sees them named. */
enum class BackupStep {

    /** Deriving the keys from the password — about one second. */
    KEY,

    /** The database snapshot, settings and the manifest. */
    DATABASE,

    /** The agent's workspace and image attachments. */
    FILES,
}

/** Why an export failed, in terms the user is told. */
enum class BackupExportFailure {

    /** The place the user picked ran out of space. */
    NO_SPACE,

    /** The place the user picked stopped accepting the file. */
    WRITE_FAILED,

    /**
     * The phone's own storage is too full: for the database snapshot the export makes on the way, or for the record
     * that a written backup is complete.
     */
    PHONE_FULL,

    /** The app's own data could not be read or sealed — the database, a file, a key or the cipher failed. */
    READ_FAILED,
}

/**
 * Thrown when an export fails for [failure].
 *
 * @property failure Why the export failed.
 * @property neededBytes About how many bytes the archive needs, for the "not enough space" message.
 * @param message A diagnostic for logs; it carries no content of the archive.
 * @param cause The error underneath.
 */
class BackupExportException(
    val failure: BackupExportFailure,
    val neededBytes: Long,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
