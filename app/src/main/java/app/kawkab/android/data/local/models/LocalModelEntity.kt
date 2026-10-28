package app.kawkab.android.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a downloaded LLM model's metadata.
 *
 * @property id Unique identifier for the model record.
 * @property name Human-readable name of the model.
 * @property path Absolute path to the model file on the device.
 * @property size Size of the model file in bytes.
 * @property isActive Flag indicating if this model is currently active/selected.
 * @property supportsVision Whether the user has marked this model as
 *   vision-capable (able to read an attached image). Added in schema v40
 *   (`MIGRATION_39_40`) with a `false` default so every pre-existing row stays
 *   text-only until the user opts in on the model screen.
 * @property supportsAudio Whether the user has marked this model as
 *   audio-capable (able to transcribe a recorded/picked audio clip). Added in
 *   schema v41 (`MIGRATION_40_41`) with a `false` default so every pre-existing
 *   row stays audio-incapable until the user opts in on the model screen.
 * @property sha256 Lowercase hex SHA-256 of the model file, or `null` until the
 *   background hashing pass has read it. Added in schema v66 (`MIGRATION_65_66`)
 *   as a nullable column, so every pre-existing row starts unhashed and the
 *   start-up re-arm schedules the pass for it.
 * @property sha256FileSize The file's length when [sha256] was computed — half
 *   of the stamp that tells a current hash from one of a file since replaced.
 *   `null` exactly when [sha256] is.
 * @property sha256FileModifiedAt The file's last-modified time (epoch ms) when
 *   [sha256] was computed — the other half of that stamp. `null` exactly when
 *   [sha256] is.
 * @property sourceUrl The address the file was downloaded from, or `null` when it was never
 *   recorded (registered before v71, or found on disk at start-up).
 * @property sourceSha256 The SHA-256 the source published for the file, recorded when a download
 *   matched it; `null` when no download was checked (registered before v72, a source that
 *   publishes no hash, or a file found on disk at start-up).
 */
@Entity(tableName = "local_models")
data class LocalModelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val path: String,
    val size: Long,
    val isActive: Boolean,
    val supportsVision: Boolean = false,
    val supportsAudio: Boolean = false,
    val sha256: String? = null,
    val sha256FileSize: Long? = null,
    val sha256FileModifiedAt: Long? = null,
    val sourceUrl: String? = null,
    val sourceSha256: String? = null,
)
