package app.knotwork.android.domain.backup

import java.io.InputStream

/**
 * Restores the app's data from an archive [BackupExporter] wrote, in three steps.
 *
 * 1. [stage] reads and checks the whole archive into a staging area: nothing the app uses changes, and a
 *    failure leaves the phone as it was.
 * 2. [commit], once the user confirms, makes the staged restore the one the next start applies, and stops
 *    this phone's background work. The caller then restarts the process.
 * 3. [finishPending], at the next start, completes what applying needs a running app for. The database
 *    and the files are swapped before the app's dependencies exist, outside this interface.
 *
 * Only one restore is staged at a time.
 */
interface BackupRestorer {

    /**
     * Reads the open part of an archive's header — what may be shown before the password is asked for.
     *
     * @param source The archive; closed before this returns.
     * @return What the header says about the archive. Nothing in it is authenticated yet.
     * @throws BackupFormatException When the file is not an archive this build can read.
     */
    suspend fun inspect(source: InputStream): BackupArchiveInfo

    /**
     * Reads the archive with [password], checks everything in it and stages it, replacing any restore
     * staged before.
     *
     * @param source The archive; closed when this returns.
     * @param password The password the user entered. The caller clears its copy afterwards.
     * @param onProgress Told the current step and how far it is.
     * @return What the staged restore holds.
     * @throws BackupFormatException When the archive cannot be read: not an archive, a newer format, the
     *   wrong password, damaged or cut short.
     * @throws BackupRestoreException When the archive was read but cannot be restored here.
     * @throws IllegalStateException When a committed restore waits for the restart: nothing more can be staged.
     */
    suspend fun stage(source: InputStream, password: CharArray, onProgress: (RestoreProgress) -> Unit): StagedRestore

    /**
     * Makes the staged restore the one the next start applies, then stops this phone's queued background
     * runs and trigger watches. Past this point the restore always completes: the caller restarts the
     * process, and a start that finds the restore committed applies it.
     *
     * @param applySecrets Whether the secrets the archive carries replace this phone's. Ignored when the
     *   archive has none.
     * @throws IllegalStateException When no restore is staged.
     */
    suspend fun commit(applySecrets: Boolean)

    /**
     * Deletes a staged restore that was not committed.
     *
     * @return `true` when nothing staged is left.
     */
    suspend fun discard(): Boolean

    /**
     * Deletes everything a restore keeps — the staged restore, committed or not, and the report — for the
     * recovery wipe: staging holds copies of the user's files.
     *
     * @return `true` when nothing is left.
     */
    suspend fun erase(): Boolean

    /**
     * At start: completes a committed restore — the secret stores, the model registry's paths, the report
     * — and removes what was staged; or removes a staged restore that was never committed. Does nothing
     * when there is neither. Every part is attempted; a part that fails is logged and skipped.
     */
    suspend fun finishPending()

    /**
     * The report of the restore applied last, until it is dismissed.
     *
     * @return The report, or `null` when no restore has been applied since the last dismissal.
     */
    suspend fun report(): RestoreReport?

    /** Forgets the report once the user has seen it. */
    suspend fun dismissReport()
}

/**
 * What an archive's open header says, before the password.
 *
 * @property appVersionName The version name of the build that wrote it.
 * @property createdAtEpochMillis When it was written.
 */
data class BackupArchiveInfo(val appVersionName: String, val createdAtEpochMillis: Long)

/**
 * A restore that has been read, checked and staged, waiting for the user to confirm.
 *
 * @property info What the header says.
 * @property manifest What the archive holds.
 */
data class StagedRestore(val info: BackupArchiveInfo, val manifest: BackupManifest)

/** The steps of [BackupRestorer.stage], in order. */
enum class RestoreStep {
    /** Deriving the keys from the password. */
    KEY,

    /** Reading and unpacking the archive. */
    UNPACK,

    /** Checking the database and bringing it to this build's schema. */
    DATABASE,
}

/**
 * How far [BackupRestorer.stage] is.
 *
 * @property step The current step.
 * @property bytesDone Content bytes unpacked so far; 0 outside [RestoreStep.UNPACK].
 * @property bytesTotal The content bytes the header announces; 0 outside [RestoreStep.UNPACK].
 */
data class RestoreProgress(val step: RestoreStep, val bytesDone: Long, val bytesTotal: Long)

/** Why an archive that was read cannot be restored here. Each is something the user is told. */
enum class RestoreFailure {

    /** The archive's database is from a later version of the app than this one. */
    NEWER_VERSION,

    /** The phone has too little free space to unpack the archive. */
    NO_SPACE,

    /** The database inside the archive did not pass its checks or could not be brought up to date. */
    DATABASE_UNUSABLE,

    /** This phone's own database key cannot be read, so the restored database cannot be keyed for it. */
    PHONE_KEY_UNAVAILABLE,

    /** The picked file, or the phone's storage, failed while the archive was read or staged. */
    READ_FAILED,
}

/**
 * Thrown by [BackupRestorer.stage] when the archive was read but cannot be restored here.
 *
 * @property failure Why.
 * @property neededBytes With [RestoreFailure.NO_SPACE], about how many bytes staging needs; 0 when the phone ran
 *   out of space part-way, where the need is not known, and with every other failure.
 * @property writerVersionName With [RestoreFailure.NEWER_VERSION], the version that wrote the archive.
 * @property freeBytes With [RestoreFailure.NO_SPACE] and a known [neededBytes], the bytes free when it was
 *   measured; 0 otherwise.
 * @param message A diagnostic for logs; it carries no content of the archive.
 * @param cause The underlying failure.
 */
class BackupRestoreException(
    val failure: RestoreFailure,
    message: String,
    cause: Throwable? = null,
    val neededBytes: Long = 0,
    val writerVersionName: String? = null,
    val freeBytes: Long = 0,
) : Exception(message, cause)

/**
 * What the restore applied last left for the user to know.
 *
 * @property modelsToDownload The models the restored registry lists whose file is not on this phone.
 * @property scheduledTasksLeftBehind How many tasks the agent had scheduled on the phone the backup came
 *   from; they are not carried over.
 * @property secretsApplied Whether secrets from the archive replaced this phone's.
 * @property backupCreatedAtEpochMillis When the restored backup was made; `null` in a report an earlier build wrote.
 */
data class RestoreReport(
    val modelsToDownload: List<BackupModelEntry>,
    val scheduledTasksLeftBehind: Int,
    val secretsApplied: Boolean,
    val backupCreatedAtEpochMillis: Long? = null,
)
