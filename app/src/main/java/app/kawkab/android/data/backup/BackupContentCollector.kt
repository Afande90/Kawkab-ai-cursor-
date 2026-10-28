package app.kawkab.android.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.kawkab.android.domain.backup.BackupCounts
import app.kawkab.android.domain.backup.BackupFormat
import app.kawkab.android.domain.backup.BackupManifest
import app.kawkab.android.domain.backup.BackupModelEntry
import app.kawkab.android.domain.repositories.ChatRepository
import app.kawkab.android.domain.repositories.LocalModelRepository
import app.kawkab.android.domain.repositories.MemoryRepository
import app.kawkab.android.domain.repositories.PipelineRepository
import app.kawkab.android.domain.services.TaskScheduler
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gathers what goes into a backup, before any of it is written.
 *
 * @see BackupContentCollector
 */
internal interface BackupContentSource {

    /**
     * Takes the database snapshot into [snapshotFile] and reads everything else.
     *
     * @param includeSecrets Whether the secret entries are read too.
     * @param snapshotFile Where the database snapshot goes; the caller deletes it.
     * @param createdAtEpochMillis The time the manifest records.
     * @return The content, ready to write.
     */
    suspend fun collect(includeSecrets: Boolean, snapshotFile: File, createdAtEpochMillis: Long): BackupContent
}

/**
 * Everything a backup writes, gathered.
 *
 * @property snapshotFile The database snapshot, encrypted under [snapshotKey].
 * @property snapshotKey The 32 random bytes the snapshot is encrypted with.
 * @property preferences The settings entry ([PreferencesBackupJson]).
 * @property providers The provider-settings entry: the encrypted stores' entries that are not secrets.
 * @property secrets The secrets entry, or `null` when the user left them out.
 * @property workspace The workspace's files.
 * @property attachments The image attachments.
 * @property manifest The manifest.
 */
internal class BackupContent(
    val snapshotFile: File,
    val snapshotKey: ByteArray,
    val preferences: ByteArray,
    val providers: ByteArray,
    val secrets: ByteArray?,
    val workspace: BackupFileSource.Listing,
    val attachments: BackupFileSource.Listing,
    val manifest: BackupManifest,
)

/**
 * [BackupContentSource] over the app's database, DataStore, encrypted stores and files.
 *
 * Every owner of encrypted entries first settles: secrets an older release kept inside a setting move to
 * their store before the settings are read, and each travelling setting passes through its owner's
 * scrub on the way into the archive. The counts and settings are read just after the snapshot; a write
 * in between can make them differ from the copy by that write.
 *
 * The secret stores' entries are read through their owners ([BackedUpSecrets]); entries of one store
 * that two owners share are merged. The database passphrase's store has no owner here: it never goes
 * into a backup.
 */
@Singleton
internal class BackupContentCollector @Inject constructor(
    private val snapshotter: DatabaseSnapshotter,
    private val files: BackupFileSource,
    private val secretOwners: Set<@JvmSuppressWildcards BackedUpSecrets>,
    private val dataStore: DataStore<Preferences>,
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val pipelineRepository: PipelineRepository,
    private val localModelRepository: LocalModelRepository,
    private val taskScheduler: TaskScheduler,
) : BackupContentSource {

    override suspend fun collect(
        includeSecrets: Boolean,
        snapshotFile: File,
        createdAtEpochMillis: Long,
    ): BackupContent {
        val key = ByteArray(BackupFormat.KEY_LENGTH).also { SecureRandom().nextBytes(it) }
        var collected = false
        try {
            return collect(includeSecrets, snapshotFile, createdAtEpochMillis, key).also { collected = true }
        } finally {
            // On success the key travels on in the content; on any failure it goes no further.
            if (!collected) key.fill(0)
        }
    }

    private suspend fun collect(
        includeSecrets: Boolean,
        snapshotFile: File,
        createdAtEpochMillis: Long,
        key: ByteArray,
    ): BackupContent {
        // Secrets an older release kept inside a setting leave it before the settings are read.
        secretOwners.forEach { it.settle() }
        val schemaVersion = snapshotter.snapshot(snapshotFile, key)
        val workspace = files.workspace()
        val attachments = files.attachments()
        val manifest = BackupManifest(
            createdAtEpochMillis = createdAtEpochMillis,
            schemaVersion = schemaVersion,
            secretsIncluded = includeSecrets,
            counts = BackupCounts(
                chats = chatRepository.getAllSessions().size,
                memories = memoryRepository.countMemories(),
                pipelines = pipelineRepository.getAllPipelines().first().size,
                workspaceFiles = workspace.entries.size,
                attachments = attachments.entries.size,
            ),
            scheduledTasks = taskScheduler.countScheduled(),
            models = localModelRepository.getAllModels().first().map { model ->
                BackupModelEntry(
                    name = model.name,
                    sizeBytes = model.size,
                    sha256 = localModelRepository.currentFileHash(model.path),
                    sourceUrl = model.sourceUrl,
                )
            },
            skippedFiles = workspace.skipped + attachments.skipped,
        )
        return BackupContent(
            snapshotFile = snapshotFile,
            snapshotKey = key,
            preferences = PreferencesBackupJson.encode(dataStore.data.first(), ::scrubbed).toByteArray(Charsets.UTF_8),
            providers = storesJson(secretOwners.map { it.storeName to it.alwaysEntries() }),
            secrets = if (includeSecrets) storesJson(secretOwners.map { it.storeName to it.secretEntries() }) else null,
            workspace = workspace,
            attachments = attachments,
            manifest = manifest,
        )
    }

    private fun scrubbed(key: String, value: Any): Any? =
        secretOwners.fold<BackedUpSecrets, Any?>(value) { current, owner -> current?.let { owner.scrubbed(key, it) } }

    /**
     * The JSON of the provider-settings and secrets entries:
     * `{"entriesVersion":1,"stores":{"<store>":{"<entry>":"<value>"}}}`.
     */
    private fun storesJson(entries: List<Pair<String, Map<String, String>>>): ByteArray {
        val stores = JSONObject()
        entries.groupBy({ it.first }, { it.second }).toSortedMap().forEach { (store, maps) ->
            val merged = maps.fold(emptyMap<String, String>()) { all, map -> all + map }
            stores.put(store, JSONObject(merged.toSortedMap() as Map<*, *>))
        }
        return JSONObject().put(ENTRIES_VERSION_KEY, ENTRIES_VERSION).put(STORES_KEY, stores).toString()
            .toByteArray(Charsets.UTF_8)
    }

    private companion object {
        const val ENTRIES_VERSION_KEY = "entriesVersion"
        const val ENTRIES_VERSION = 1
        const val STORES_KEY = "stores"
    }
}
