package app.kawkab.android.domain.usecases

import app.kawkab.android.domain.backup.BackupDocuments
import app.kawkab.android.domain.backup.BackupRestorer
import app.kawkab.android.domain.backup.RestoreProgress
import app.kawkab.android.domain.backup.StagedRestore
import javax.inject.Inject

/**
 * Checks a picked backup with its password and stages it, so the user can decide with the summary in front of
 * them. Nothing the app uses changes until [BackupRestorer.commit].
 *
 * @property documents Opens the picked document.
 * @property restorer Reads, checks and stages the archive.
 */
class StageRestoreFromDocumentUseCase @Inject constructor(
    private val documents: BackupDocuments,
    private val restorer: BackupRestorer,
) {

    /**
     * Checks and stages the archive in [document].
     *
     * @param document The document's URI.
     * @param password The password the user entered; the caller clears its copy afterwards.
     * @param onProgress Told the current step and how far it is.
     * @return What the staged restore holds.
     * @throws app.kawkab.android.domain.backup.BackupFormatException When the archive cannot be read: not an
     *   archive, a newer format, the wrong password, damaged or cut short.
     * @throws app.kawkab.android.domain.backup.BackupRestoreException When the archive was read but cannot be
     *   restored here.
     * @throws java.io.IOException When the document cannot be opened.
     */
    suspend operator fun invoke(
        document: String,
        password: CharArray,
        onProgress: (RestoreProgress) -> Unit,
    ): StagedRestore =
        // The restorer closes the stream; `use` closes it too when the restorer fails or is cancelled before it
        // takes it — waiting for its lock, or refusing because a committed restore waits for the restart.
        documents.openForRead(document).use { restorer.stage(it, password, onProgress) }
}
