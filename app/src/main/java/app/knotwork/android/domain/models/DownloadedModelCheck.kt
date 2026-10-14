package app.knotwork.android.domain.models

/**
 * What the check of a freshly downloaded model file concluded, before the file is installed.
 *
 * Hugging Face publishes the SHA-256 of every file it stores in LFS, and says it again in its
 * answer to the download request itself. A file whose hash differs from that is not the file the
 * source offers — a corrupt transfer, a resume that spliced two revisions of the file together,
 * or bytes changed on the way — and must never be registered as a model. A source that publishes
 * no hash (a link to another host, a file outside LFS) leaves the file unchecked, and the app says
 * so rather than implying a check that never happened.
 *
 * [of] is the whole rule; the download path acts on its answer.
 */
sealed interface DownloadedModelCheck {

    /**
     * The file's hash matches the one its source published.
     *
     * @property hash The file's SHA-256 with the stamp of the file that was read.
     */
    data class Verified(val hash: ModelFileHash) : DownloadedModelCheck

    /**
     * The source published no hash, so there was nothing to check the file against.
     *
     * @property hash The file's SHA-256 with its stamp, or `null` when the file could not be read
     *   (the background hashing pass tries it again later).
     */
    data class Unverified(val hash: ModelFileHash?) : DownloadedModelCheck

    /**
     * The file's hash differs from the one its source published. The file must not be installed.
     *
     * @property publishedSha256 The SHA-256 the source published, lowercase hex.
     * @property actualSha256 The SHA-256 of the bytes that arrived, lowercase hex.
     */
    data class Mismatch(val publishedSha256: String, val actualSha256: String) : DownloadedModelCheck

    /**
     * The source published a hash, but the file could not be read to compare with it — missing,
     * unreadable, or changed while it was read. Not installable until it is read: installing it
     * would skip the check the source made possible.
     */
    data object Unreadable : DownloadedModelCheck

    /** The rule. */
    companion object {

        /**
         * Compares a downloaded file's hash with the one its source published.
         *
         * @param publishedSha256 The SHA-256 the source published for the file, as hex in either
         *   case, or `null` when it published none.
         * @param actual The hash of the file as downloaded, or `null` when it could not be read.
         * @return [Verified] on a match, [Mismatch] on a difference, [Unverified] when the source
         *   published nothing, and [Unreadable] when there is a published hash but no file hash.
         */
        fun of(publishedSha256: String?, actual: ModelFileHash?): DownloadedModelCheck {
            val published = publishedSha256?.lowercase() ?: return Unverified(actual)
            if (actual == null) return Unreadable
            return if (actual.sha256.lowercase() == published) {
                Verified(actual)
            } else {
                Mismatch(publishedSha256 = published, actualSha256 = actual.sha256.lowercase())
            }
        }
    }
}
