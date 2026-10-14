package app.knotwork.android.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.knotwork.android.domain.backup.BackupManifestJson
import app.knotwork.android.domain.backup.BackupModelEntry
import app.knotwork.android.domain.backup.RestoreReport
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.services.DownloadedModelFiles
import app.knotwork.android.domain.services.TaskScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Completes a committed restore once the app is up, after the start put its database and files in place
 * ([PendingRestoreApplier]).
 *
 * In order: the settings (opening the DataStore runs [RestoredPreferencesMigration]); this phone's queued
 * background runs, when the process ended before the commit stopped them; the provider settings, and the
 * secrets when the user chose them, written through their owners; the model registry's paths, pointed at
 * this phone's files; the report; and the staging area and what applying moved aside, removed. Each part —
 * and each owner — is attempted on its own: one that fails is logged and the rest go on, so the restore is
 * never left half-way by one stubborn part.
 *
 * @param layout Where the restore is staged.
 * @param stagedEntries The provider settings and secrets kept for the restore.
 * @param secretOwners The owners that write them.
 * @param dataStore The app's settings.
 * @param localModelRepository The model registry.
 * @param downloadedModelFiles The model files on this phone.
 * @param taskScheduler Cancels queued background runs.
 */
@Singleton
internal class RestoreFinisher(
    private val layout: RestoreLayout,
    private val stagedEntries: RestoreStagedEntries,
    private val secretOwners: Set<BackedUpSecrets>,
    private val dataStore: DataStore<Preferences>,
    private val localModelRepository: LocalModelRepository,
    private val downloadedModelFiles: DownloadedModelFiles,
    private val taskScheduler: TaskScheduler,
) {

    /** Completes restores into the app's own storage. */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        stagedEntries: RestoreStagedEntries,
        secretOwners: Set<@JvmSuppressWildcards BackedUpSecrets>,
        dataStore: DataStore<Preferences>,
        localModelRepository: LocalModelRepository,
        downloadedModelFiles: DownloadedModelFiles,
        taskScheduler: TaskScheduler,
    ) : this(
        RestoreLayout.of(context),
        stagedEntries,
        secretOwners,
        dataStore,
        localModelRepository,
        downloadedModelFiles,
        taskScheduler,
    )

    /**
     * Completes the committed restore. Call only when [RestoreLayout.isCommitted] and [RestoreLayout.isSwapped].
     *
     * @param marker What the user confirmed.
     */
    suspend fun finish(marker: RestoreLayout.CommitMarker) {
        attempt("settings") { dataStore.data.first() }
        if (!marker.backgroundStopped) attempt("background runs") { taskScheduler.cancelAllBackgroundRuns() }
        restoreEntries(RestoreStagedEntries.Kind.PROVIDERS)
        val secretsApplied = marker.applySecrets && restoreEntries(RestoreStagedEntries.Kind.SECRETS)
        val modelsToDownload = attemptOrNull("model paths") { repointModels() }.orEmpty()
        attempt("report") {
            val manifest = BackupManifestJson.decode(layout.stagedManifest.readText())
            RestoreReportJson.write(
                layout.report,
                RestoreReport(modelsToDownload, manifest.scheduledTasks, secretsApplied, manifest.createdAtEpochMillis),
            )
        }
        clear()
    }

    /**
     * Removes everything a restore keeps but its report: the commit marker first — a start that follows a
     * crash part-way through finds nothing committed and only clears up — then the kept entries, the staging
     * area and what applying moved aside.
     */
    fun clear() {
        if (!layout.clearCommit()) Timber.w("The restore's commit marker could not be deleted")
        attemptBlocking("staged entries") { stagedEntries.clear() }
        if (!layout.staging.deleteRecursively()) Timber.w("The restore's staging area could not all be deleted")
        if (!layout.replaced.deleteRecursively()) Timber.w("Files a restore replaced could not all be deleted")
    }

    /**
     * Writes the entries kept for [kind] through their owners, each owner on its own: one that fails does not
     * stop the others.
     *
     * @return `true` when there were entries and every owner wrote its own.
     */
    private suspend fun restoreEntries(kind: RestoreStagedEntries.Kind): Boolean {
        val stores = attemptOrNull("staged entries") { stagedEntries.entries(kind) }.orEmpty()
        val secrets = kind == RestoreStagedEntries.Kind.SECRETS
        val results = secretOwners.mapNotNull { owner ->
            stores[owner.storeName]?.let { entries ->
                attempt("an owner's entries") { owner.restore(entries, secrets) }
            }
        }
        return results.isNotEmpty() && results.all { it }
    }

    /**
     * Points every restored model at the file of the same name on this phone.
     *
     * @return The models with no such file, for the user to download again.
     */
    private suspend fun repointModels(): List<BackupModelEntry> {
        val files = downloadedModelFiles.list().associateBy { File(it.path).name }
        val missing = mutableListOf<BackupModelEntry>()
        localModelRepository.getAllModels().first().forEach { model ->
            val file = files[File(model.path).name]
            when {
                file == null -> missing += BackupModelEntry(model.name, model.size, null, model.sourceUrl)
                file.path != model.path -> localModelRepository.updateModel(model.copy(path = file.path))
            }
        }
        return missing
    }

    private suspend fun attempt(part: String, block: suspend () -> Unit): Boolean = attemptOrNull(part, block) != null

    private suspend fun <T> attemptOrNull(part: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "A restore could not complete one part: %s", part)
        null
    }

    private fun attemptBlocking(part: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Timber.e(e, "A restore could not complete one part: %s", part)
        }
    }
}

/** The report file of the restore applied last ([RestoreLayout.report]). */
internal object RestoreReportJson {

    /**
     * Writes [report] to [file].
     *
     * @param file The report file.
     * @param report What to write.
     */
    fun write(file: File, report: RestoreReport) {
        val models = JSONArray()
        report.modelsToDownload.forEach { model ->
            models.put(
                JSONObject().put(KEY_NAME, model.name).put(KEY_SIZE, model.sizeBytes)
                    .apply { model.sourceUrl?.let { put(KEY_SOURCE, it) } },
            )
        }
        file.parentFile?.mkdirs()
        file.writeText(
            JSONObject()
                .put(KEY_MODELS, models)
                .put(KEY_SCHEDULED, report.scheduledTasksLeftBehind)
                .put(KEY_SECRETS, report.secretsApplied)
                .apply { report.backupCreatedAtEpochMillis?.let { put(KEY_CREATED, it) } }
                .toString(),
        )
    }

    /**
     * Reads the report in [file].
     *
     * @param file The report file.
     * @return The report, or `null` when there is none or it does not parse.
     */
    fun read(file: File): RestoreReport? {
        if (!file.isFile) return null
        return try {
            val json = JSONObject(file.readText())
            val models = json.getJSONArray(KEY_MODELS)
            RestoreReport(
                modelsToDownload = (0 until models.length()).map { index ->
                    val model = models.getJSONObject(index)
                    BackupModelEntry(
                        name = model.getString(KEY_NAME),
                        sizeBytes = model.getLong(KEY_SIZE),
                        sha256 = null,
                        sourceUrl = model.optString(KEY_SOURCE).takeIf { model.has(KEY_SOURCE) },
                    )
                },
                scheduledTasksLeftBehind = json.getInt(KEY_SCHEDULED),
                secretsApplied = json.getBoolean(KEY_SECRETS),
                backupCreatedAtEpochMillis = json.optLong(KEY_CREATED).takeIf { json.has(KEY_CREATED) },
            )
        } catch (e: JSONException) {
            Timber.w(e, "The restore report does not parse")
            null
        }
    }

    private const val KEY_MODELS = "modelsToDownload"
    private const val KEY_NAME = "name"
    private const val KEY_SIZE = "sizeBytes"
    private const val KEY_SOURCE = "sourceUrl"
    private const val KEY_SCHEDULED = "scheduledTasksLeftBehind"
    private const val KEY_SECRETS = "secretsApplied"
    private const val KEY_CREATED = "backupCreatedAt"
}
