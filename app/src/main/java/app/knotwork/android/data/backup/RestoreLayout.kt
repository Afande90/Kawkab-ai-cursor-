package app.knotwork.android.data.backup

import android.content.Context
import app.knotwork.android.data.local.AgentWorkspaceImpl
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.AttachmentStoreImpl
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Where a restore keeps what it has staged, and where what it replaces lives.
 *
 * ```
 * files/restore/
 *   staging/                 the archive, checked and unpacked
 *     agent_database.db      the database, keyed for this phone and at this build's schema
 *     preferences.json       the settings entry, read by the DataStore migration
 *     manifest.json          the manifest
 *     agent_workspace/       the workspace's files
 *     attachments/           the image attachments
 *     commit.json            present once the user confirmed: the next start applies the restore
 *   replaced/                what applying moved out of the way, deleted once it is done
 *   report.json              what the restore applied last left for the user to know
 * ```
 *
 * Secrets are not staged here: [RestoreStagedEntries] keeps them in a Keystore-backed store. Built
 * without the app's dependencies, so the start can apply a committed restore before they exist.
 *
 * @property root The restore directory.
 * @property liveDatabase The app's database file.
 * @property liveWorkspace The agent's workspace directory.
 * @property liveAttachments The attachments directory.
 */
internal class RestoreLayout(
    val root: File,
    val liveDatabase: File,
    val liveWorkspace: File,
    val liveAttachments: File,
) {

    /** The staging directory. */
    val staging: File get() = File(root, STAGING_DIR)

    /** The staged database. */
    val stagedDatabase: File get() = File(staging, AppDatabase.DATABASE_NAME)

    /** The staged settings entry. */
    val stagedPreferences: File get() = File(staging, PREFERENCES_FILE)

    /** The staged manifest. */
    val stagedManifest: File get() = File(staging, MANIFEST_FILE)

    /** The staged workspace. */
    val stagedWorkspace: File get() = File(staging, liveWorkspace.name)

    /** The staged attachments. */
    val stagedAttachments: File get() = File(staging, liveAttachments.name)

    /** Where applying moves what it replaces. */
    val replaced: File get() = File(root, REPLACED_DIR)

    /** The report of the restore applied last. */
    val report: File get() = File(root, REPORT_FILE)

    private val commitMarker: File get() = File(staging, COMMIT_FILE)

    /** Whether a restore is committed: the user confirmed it and the next start applies it. */
    val isCommitted: Boolean get() = commitMarker.isFile

    /** Whether the files of a committed restore are all in place: nothing staged is left to move. */
    val isSwapped: Boolean get() = !stagedDatabase.exists() && !stagedWorkspace.exists() && !stagedAttachments.exists()

    /**
     * Records the user's confirmation. Written to a temporary file and renamed, so the marker is either
     * whole or absent.
     *
     * @param marker What the confirmation says.
     * @throws IOException When the marker cannot be written.
     */
    fun writeCommit(marker: CommitMarker) {
        val temporary = File(staging, "$COMMIT_FILE.tmp")
        val json = JSONObject()
            .put(KEY_APPLY_SECRETS, marker.applySecrets)
            .put(KEY_BACKGROUND_STOPPED, marker.backgroundStopped)
            .toString()
        FileOutputStream(temporary).use {
            it.write(json.toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        if (!temporary.renameTo(commitMarker)) throw IOException("the restore's commit marker was not written")
    }

    /**
     * Withdraws the confirmation once the restore is complete — before what it staged is removed, so a start
     * that follows a crash half-way through the removal finds nothing committed and only clears up.
     *
     * @return `true` when no marker is left.
     */
    fun clearCommit(): Boolean = commitMarker.delete() || !commitMarker.exists()

    /**
     * Reads the confirmation.
     *
     * @return What it says, or `null` when the restore is not committed. A marker that does not parse reads
     *   as committed with secrets left alone and background work not known to be stopped — the safe reading.
     */
    fun readCommit(): CommitMarker? {
        if (!isCommitted) return null
        return try {
            val json = JSONObject(commitMarker.readText())
            CommitMarker(
                applySecrets = json.optBoolean(KEY_APPLY_SECRETS, false),
                backgroundStopped = json.optBoolean(KEY_BACKGROUND_STOPPED, false),
            )
        } catch (e: JSONException) {
            Timber.w(e, "The restore's commit marker does not parse; secrets are left alone")
            CommitMarker(applySecrets = false, backgroundStopped = false)
        }
    }

    /**
     * What the user confirmed.
     *
     * @property applySecrets Whether the archive's secrets replace this phone's.
     * @property backgroundStopped Whether this phone's background work was stopped before the restart.
     */
    data class CommitMarker(val applySecrets: Boolean, val backgroundStopped: Boolean)

    /** Builds the layout. */
    companion object {
        /** The restore directory's name under the app's files directory. */
        const val RESTORE_DIR: String = "restore"

        private const val STAGING_DIR = "staging"
        private const val REPLACED_DIR = "replaced"
        private const val PREFERENCES_FILE = "preferences.json"
        private const val MANIFEST_FILE = "manifest.json"
        private const val COMMIT_FILE = "commit.json"
        private const val REPORT_FILE = "report.json"
        private const val KEY_APPLY_SECRETS = "applySecrets"
        private const val KEY_BACKGROUND_STOPPED = "backgroundStopped"

        /**
         * The app's own layout.
         *
         * @param context Any context of the app.
         * @return The layout over the app's directories.
         */
        fun of(context: Context): RestoreLayout = RestoreLayout(
            root = File(context.filesDir, RESTORE_DIR),
            liveDatabase = context.getDatabasePath(AppDatabase.DATABASE_NAME),
            liveWorkspace = File(context.filesDir, AgentWorkspaceImpl.WORKSPACE_DIR_NAME),
            liveAttachments = File(context.filesDir, AttachmentStoreImpl.ATTACHMENTS_DIR),
        )
    }
}
