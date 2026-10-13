package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.backup.BackupExporter
import app.knotwork.android.domain.backup.BackupManifest
import app.knotwork.android.domain.backup.BackupProgress
import java.io.OutputStream
import javax.inject.Inject

/**
 * Backs up the whole app into one password-locked file the user picked (Settings → Privacy → Backup &
 * restore → Back up).
 *
 * The screen owns the system file picker and hands over the stream it opened. The password must have
 * at least [MIN_PASSWORD_LENGTH] characters, counted as Unicode code points (an emoji counts once): the
 * file can be attacked offline with no limit on guesses, so length is its defence. The rule is checked
 * here as well as on the screen, so no caller can write a file with a shorter one.
 *
 * @property exporter Writes the archive.
 */
class ExportBackupUseCase @Inject constructor(private val exporter: BackupExporter) {

    /**
     * Writes the backup.
     *
     * @param target The stream of the document the user picked; closed when the export ends.
     * @param password The password the user chose, at least [MIN_PASSWORD_LENGTH] characters. Spaces
     *   count; nothing is trimmed. The caller clears its copy afterwards.
     * @param includeSecrets Whether API keys, the Hugging Face token and MCP sign-ins go in too.
     * @param onProgress Told the current step and the bytes written so far.
     * @return What the archive holds.
     * @throws IllegalArgumentException When [password] is shorter than [MIN_PASSWORD_LENGTH].
     * @throws app.knotwork.android.domain.backup.BackupExportException When the export fails.
     */
    suspend operator fun invoke(
        target: OutputStream,
        password: CharArray,
        includeSecrets: Boolean,
        onProgress: (BackupProgress) -> Unit,
    ): BackupManifest {
        if (Character.codePointCount(password, 0, password.size) < MIN_PASSWORD_LENGTH) {
            target.close()
            throw IllegalArgumentException("the password needs at least $MIN_PASSWORD_LENGTH characters")
        }
        return exporter.export(target, password, includeSecrets, onProgress)
    }

    /** The password rule. */
    companion object {
        /** The fewest characters a backup password may have. */
        const val MIN_PASSWORD_LENGTH: Int = 12
    }
}
