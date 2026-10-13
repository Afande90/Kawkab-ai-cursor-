package app.knotwork.android.domain.backup

/**
 * The fixed numbers of the backup archive format — the public contract described in
 * `docs/backup-format.md`.
 *
 * An archive is one file: an open header naming the key-derivation parameters, then the content
 * encrypted with AES-256-GCM in numbered chunks. From release 0.12.0 on, every format version that
 * was ever written stays readable: a reader that meets a newer [FORMAT_VERSION] refuses the file
 * with [BackupReadFailure.UNSUPPORTED_FORMAT_VERSION] instead of guessing.
 */
object BackupFormat {

    /** The eight ASCII bytes every archive starts with. */
    const val MAGIC: String = "KNOTWORK"

    /** The format version this build writes, and the newest one it reads. */
    const val FORMAT_VERSION: Int = 1

    /** Identifier of Argon2id, version 0x13 (RFC 9106), in the header's KDF field. */
    const val KDF_ARGON2ID: Int = 1

    /** Length of the random salt the key is derived with, in bytes. */
    const val SALT_LENGTH: Int = 16

    /** Length of each derived key — the content key and the header key — in bytes. */
    const val KEY_LENGTH: Int = 32

    /** Plaintext bytes in every chunk but the last, as this build writes them. */
    const val CHUNK_SIZE: Int = 1 shl 20

    /** The smallest chunk size a reader accepts from a header. */
    const val MIN_CHUNK_SIZE: Int = 1 shl 12

    /** The largest chunk size a reader accepts from a header: one chunk is held in memory. */
    const val MAX_CHUNK_SIZE: Int = 1 shl 24

    /** The name of the first entry of every archive: the manifest that says what the archive holds. */
    const val MANIFEST_ENTRY: String = "manifest.json"

    /** The file name extension the app suggests when saving an archive. */
    const val FILE_EXTENSION: String = "knotwork"

    /** The media type an archive is saved with: the extension is not a registered type. */
    const val MIME_TYPE: String = "application/octet-stream"
}
