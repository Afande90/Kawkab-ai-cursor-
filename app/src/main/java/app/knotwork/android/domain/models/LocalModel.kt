package app.knotwork.android.domain.models

/**
 * Domain model representing a downloaded LLM model's metadata.
 * This class is independent of any persistence mechanism.
 *
 * @property id Unique identifier for the model record.
 * @property name Human-readable name of the model.
 * @property path Absolute path to the model file on the device.
 * @property size Size of the model file in bytes.
 * @property isActive Flag indicating if this model is currently active/selected.
 * @property supportsVision Whether this model can accept an image alongside the
 *   text prompt (a vision-capable LiteRT-LM model such as Gemma 4). Defaults to
 *   `false`: the LiteRT-LM 0.13.1 runtime exposes no capability probe (its
 *   `Capabilities` surface reports only speculative-decoding support), so vision
 *   support cannot be auto-detected from the `.litertlm` file and is instead a
 *   user-set flag on the model screen. The pre-flight send guard reads it to
 *   block an image message against a text-only active model before a run starts.
 * @property supportsAudio Whether this model can transcribe an audio clip (a
 *   multimodal LiteRT-LM model exposing an audio backend, such as Gemma-class
 *   speech models). Defaults to `false` for the same reason as [supportsVision]:
 *   the runtime offers no capability probe, so this is a user-set flag on the
 *   model screen. Voice input transcribes the clip with the active model *before*
 *   the pipeline runs; this flag gates that preprocessing step so a text-only
 *   model yields a calm "switch model" message instead of failing in native code.
 * @property fileHash The SHA-256 of the model file with the stamp of the file it
 *   was computed from, or `null` until the background pass has hashed it. Read
 *   through `LocalModelRepository.currentFileHash`, which also checks the stamp
 *   against the file on disk; the raw value may describe a file that has since
 *   been replaced.
 * @property sourceUrl The address the file was downloaded from, or `null` when unknown — a model
 *   registered before the address was recorded, or one found on disk. A restored phone offers a model
 *   for download again from here.
 * @property sourceSha256 The SHA-256 the source published for the file, recorded when a download
 *   matched it; `null` when no download of this model was checked (a source that publishes no
 *   hash, a model found on disk, one installed before checks existed). It records what was checked
 *   once, not what is on disk now — [matchesPublishedHash] compares it with the current file.
 */
data class LocalModel(
    val id: Long = 0,
    val name: String,
    val path: String,
    val size: Long,
    val isActive: Boolean,
    val supportsVision: Boolean = false,
    val supportsAudio: Boolean = false,
    val fileHash: ModelFileHash? = null,
    val sourceUrl: String? = null,
    val sourceSha256: String? = null,
) {

    /**
     * Whether the file on disk now is the file its source published: a download of this model was
     * checked against the source's hash, and the file's current hash is still that hash.
     *
     * The current hash comes from the caller because it is only known with the file at hand —
     * `LocalModelRepository.currentFileHash` answers it only while the stored hash still describes
     * the file on disk. A file replaced since the check, or one whose hash is not known yet, is
     * not vouched for.
     *
     * @param currentFileSha256 The SHA-256 of the file as it is on disk now, or `null` when unknown.
     * @return `true` only when both hashes are known and equal.
     */
    fun matchesPublishedHash(currentFileSha256: String?): Boolean =
        sourceSha256 != null && currentFileSha256 != null && sourceSha256.equals(currentFileSha256, ignoreCase = true)
}
