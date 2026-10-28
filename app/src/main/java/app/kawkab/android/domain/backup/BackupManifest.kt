package app.kawkab.android.domain.backup

/**
 * What an archive holds — the first entry of every archive ([BackupFormat.MANIFEST_ENTRY]), written by
 * the export and read before a restore replaces anything.
 *
 * Restore shows it as "From the backup" and uses it to decide what comes next: the models to offer for
 * download again, whether keys must be entered again, whether scheduled tasks were left behind.
 *
 * @property createdAtEpochMillis When the archive was written, milliseconds since the epoch.
 * @property schemaVersion The Room schema version of the database snapshot inside.
 * @property secretsIncluded Whether [BackupFormat.SECRETS_ENTRY] is in the archive.
 * @property counts How much of each kind the archive holds.
 * @property scheduledTasks How many tasks the agent had scheduled with `schedule_task` when the archive
 *   was written. They live in the background scheduler, not in the database, and are never carried; the
 *   count lets the restored phone say so only when some were left behind.
 * @property models The models that were installed. Their files are not in the archive.
 * @property skippedFiles Workspace files and attachments left out because their path cannot be an entry
 *   name ([BackupEntryName]); the export says so instead of failing whole.
 */
data class BackupManifest(
    val createdAtEpochMillis: Long,
    val schemaVersion: Int,
    val secretsIncluded: Boolean,
    val counts: BackupCounts,
    val scheduledTasks: Int,
    val models: List<BackupModelEntry>,
    val skippedFiles: Int = 0,
)

/**
 * How much of each kind an archive holds — the numbers the restore summary shows.
 *
 * @property chats Chat sessions, archived ones included.
 * @property memories Long-term memory entries.
 * @property pipelines Pipelines.
 * @property workspaceFiles Files in the agent's workspace.
 * @property attachments Image attachments.
 */
data class BackupCounts(
    val chats: Int,
    val memories: Int,
    val pipelines: Int,
    val workspaceFiles: Int,
    val attachments: Int,
)

/**
 * One model that was installed when the archive was written.
 *
 * @property name The model's file name, the identity a new download registers under.
 * @property sizeBytes The size of the file.
 * @property sha256 The file's SHA-256 when it had been computed for the file as it was, else `null`.
 * @property sourceUrl Where the file was downloaded from, or `null` when that was never recorded — then
 *   the restored phone can only send the user to look for it.
 */
data class BackupModelEntry(val name: String, val sizeBytes: Long, val sha256: String?, val sourceUrl: String?)
