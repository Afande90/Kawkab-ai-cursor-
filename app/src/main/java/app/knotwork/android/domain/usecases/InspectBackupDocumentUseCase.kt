package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.backup.BackupArchiveInfo
import app.knotwork.android.domain.backup.BackupDocuments
import app.knotwork.android.domain.backup.BackupFormatException
import app.knotwork.android.domain.backup.BackupRestorer
import javax.inject.Inject

/**
 * Reads what may be shown about a picked backup before its password is asked for: the file's name and size as its
 * provider tells them, and its open header (Settings → Privacy → Backup & restore → Restore).
 *
 * A file whose header this build cannot read is an answer, not a failure: its name is still shown, with why.
 *
 * @property documents Opens, names and measures the picked document.
 * @property restorer Reads the header.
 */
class InspectBackupDocumentUseCase @Inject constructor(
    private val documents: BackupDocuments,
    private val restorer: BackupRestorer,
) {

    /**
     * Reads the picked document.
     *
     * @param document The document's URI, as the system picker returned it.
     * @return The file, readable or not; nothing in its header is authenticated yet.
     * @throws java.io.IOException When the document cannot be read at all.
     */
    suspend operator fun invoke(document: String): PickedBackup {
        val name = documents.displayName(document)
        val size = documents.sizeOf(document)
        return try {
            // The restorer closes the stream; `use` closes it too when the restorer fails before it takes it.
            PickedBackup.Readable(name, size, documents.openForRead(document).use { restorer.inspect(it) })
        } catch (e: BackupFormatException) {
            PickedBackup.Unreadable(name, e)
        }
    }
}

/** A picked backup, before its password. */
sealed interface PickedBackup {

    /** The file's name, or `null` when its provider gives none. */
    val name: String?

    /**
     * A file this build can read.
     *
     * @property name The file's name.
     * @property sizeBytes The file's size, or `null` when its provider does not say.
     * @property info What its open header says.
     */
    data class Readable(override val name: String?, val sizeBytes: Long?, val info: BackupArchiveInfo) : PickedBackup

    /**
     * A file this build cannot read: not a backup, or a newer format.
     *
     * @property name The file's name.
     * @property failure Why.
     */
    data class Unreadable(override val name: String?, val failure: BackupFormatException) : PickedBackup
}
