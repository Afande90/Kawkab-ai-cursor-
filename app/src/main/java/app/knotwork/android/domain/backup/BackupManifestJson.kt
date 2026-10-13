package app.knotwork.android.domain.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The JSON of [BackupManifest] — part of the public format (`docs/backup-format.md` § Manifest).
 *
 * ```
 * {
 *   "manifestVersion": 1,
 *   "createdAt": 1791849600000,
 *   "schemaVersion": 71,
 *   "secretsIncluded": false,
 *   "counts": { "chats": 12, "memories": 340, "pipelines": 9, "workspaceFiles": 4, "attachments": 2 },
 *   "scheduledTasks": 1,
 *   "skippedFiles": 0,
 *   "models": [
 *     { "name": "gemma-4-E2B-it.litertlm", "sizeBytes": 2588147712, "sha256": "…", "sourceUrl": "https://…" }
 *   ]
 * }
 * ```
 *
 * `sha256` and `sourceUrl` are omitted when unknown. A reader ignores keys it does not know, so a later
 * writer may add fields without a new manifest version.
 */
object BackupManifestJson {

    /** The manifest version this build writes and reads. */
    const val MANIFEST_VERSION: Int = 1

    /**
     * Encodes [manifest].
     *
     * @param manifest The manifest to write.
     * @return Its JSON text.
     */
    fun encode(manifest: BackupManifest): String = JSONObject()
        .put(KEY_VERSION, MANIFEST_VERSION)
        .put(KEY_CREATED_AT, manifest.createdAtEpochMillis)
        .put(KEY_SCHEMA, manifest.schemaVersion)
        .put(KEY_SECRETS, manifest.secretsIncluded)
        .put(
            KEY_COUNTS,
            JSONObject()
                .put(KEY_CHATS, manifest.counts.chats)
                .put(KEY_MEMORIES, manifest.counts.memories)
                .put(KEY_PIPELINES, manifest.counts.pipelines)
                .put(KEY_WORKSPACE_FILES, manifest.counts.workspaceFiles)
                .put(KEY_ATTACHMENTS, manifest.counts.attachments),
        )
        .put(KEY_SCHEDULED, manifest.scheduledTasks)
        .put(KEY_SKIPPED, manifest.skippedFiles)
        .put(
            KEY_MODELS,
            JSONArray().apply {
                manifest.models.forEach { model ->
                    put(
                        JSONObject()
                            .put(KEY_NAME, model.name)
                            .put(KEY_SIZE, model.sizeBytes)
                            .apply { model.sha256?.let { put(KEY_SHA256, it) } }
                            .apply { model.sourceUrl?.let { put(KEY_SOURCE, it) } },
                    )
                }
            },
        )
        .toString()

    /**
     * Decodes a manifest.
     *
     * @param text The manifest's JSON text.
     * @return The manifest.
     * @throws BackupFormatException With [BackupReadFailure.DAMAGED] when the text is not a manifest this
     *   build reads, and with [BackupReadFailure.UNSUPPORTED_FORMAT_VERSION] for a newer manifest version.
     */
    fun decode(text: String): BackupManifest = try {
        val root = JSONObject(text)
        val version = root.getInt(KEY_VERSION)
        if (version > MANIFEST_VERSION) {
            throw BackupFormatException(BackupReadFailure.UNSUPPORTED_FORMAT_VERSION, "manifest version $version")
        }
        val counts = root.getJSONObject(KEY_COUNTS)
        val models = root.getJSONArray(KEY_MODELS)
        BackupManifest(
            createdAtEpochMillis = root.getLong(KEY_CREATED_AT),
            schemaVersion = root.getInt(KEY_SCHEMA),
            secretsIncluded = root.getBoolean(KEY_SECRETS),
            counts = BackupCounts(
                chats = counts.getInt(KEY_CHATS),
                memories = counts.getInt(KEY_MEMORIES),
                pipelines = counts.getInt(KEY_PIPELINES),
                workspaceFiles = counts.getInt(KEY_WORKSPACE_FILES),
                attachments = counts.getInt(KEY_ATTACHMENTS),
            ),
            scheduledTasks = root.getInt(KEY_SCHEDULED),
            skippedFiles = root.optInt(KEY_SKIPPED, 0),
            models = (0 until models.length()).map { index ->
                val model = models.getJSONObject(index)
                BackupModelEntry(
                    name = model.getString(KEY_NAME),
                    sizeBytes = model.getLong(KEY_SIZE),
                    sha256 = model.optString(KEY_SHA256).takeIf { model.has(KEY_SHA256) },
                    sourceUrl = model.optString(KEY_SOURCE).takeIf { model.has(KEY_SOURCE) },
                )
            },
        )
    } catch (e: JSONException) {
        throw BackupFormatException(BackupReadFailure.DAMAGED, "not a manifest: ${e.javaClass.simpleName}")
    }

    private const val KEY_VERSION = "manifestVersion"
    private const val KEY_CREATED_AT = "createdAt"
    private const val KEY_SCHEMA = "schemaVersion"
    private const val KEY_SECRETS = "secretsIncluded"
    private const val KEY_COUNTS = "counts"
    private const val KEY_CHATS = "chats"
    private const val KEY_MEMORIES = "memories"
    private const val KEY_PIPELINES = "pipelines"
    private const val KEY_WORKSPACE_FILES = "workspaceFiles"
    private const val KEY_ATTACHMENTS = "attachments"
    private const val KEY_SCHEDULED = "scheduledTasks"
    private const val KEY_SKIPPED = "skippedFiles"
    private const val KEY_MODELS = "models"
    private const val KEY_NAME = "name"
    private const val KEY_SIZE = "sizeBytes"
    private const val KEY_SHA256 = "sha256"
    private const val KEY_SOURCE = "sourceUrl"
}
