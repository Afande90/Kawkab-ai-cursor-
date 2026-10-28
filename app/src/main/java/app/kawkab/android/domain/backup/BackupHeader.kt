package app.kawkab.android.domain.backup

/**
 * The open header of a backup archive: everything a reader needs before the password, and what the
 * app shows about a file before asking for it.
 *
 * Nothing here is secret, and nothing is trusted until the header's authentication code has been
 * checked with the key derived from the password: a reader may show [appVersionName] and
 * [createdAtEpochMillis] early ("made with Knotwork 0.12.0 on …"), but decides on [schemaVersion] or
 * [contentBytes] only after that check.
 *
 * @property formatVersion The archive format version ([BackupFormat.FORMAT_VERSION] when written).
 * @property appVersionName The version name of the build that wrote the archive.
 * @property appVersionCode The version code of the build that wrote the archive.
 * @property flavor The distribution flavour that wrote it (`full` or `foss`). Informational only: the
 *   format does not depend on it, and an archive restores into either.
 * @property schemaVersion The Room schema version of the database inside the archive.
 * @property createdAtEpochMillis When the archive was written, milliseconds since the epoch.
 * @property contentBytes The writer's estimate of the uncompressed content, in bytes — what the reader
 *   checks free space against before it unpacks anything. Files that change while the archive is
 *   written can make the real size differ; unpacking checks the space it actually uses.
 * @property kdfParams The Argon2id costs the keys were derived with.
 * @property salt The random salt the keys were derived with, [BackupFormat.SALT_LENGTH] bytes.
 * @property chunkSize Plaintext bytes in every chunk but the last.
 */
class BackupHeader(
    val formatVersion: Int,
    val appVersionName: String,
    val appVersionCode: Int,
    val flavor: String,
    val schemaVersion: Int,
    val createdAtEpochMillis: Long,
    val contentBytes: Long,
    val kdfParams: BackupKdfParams,
    val salt: ByteArray,
    val chunkSize: Int,
)
