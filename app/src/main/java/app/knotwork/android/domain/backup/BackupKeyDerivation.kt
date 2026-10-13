package app.knotwork.android.domain.backup

/**
 * Turns the password the user chose into the two keys of an archive.
 *
 * One derivation yields both keys, so a wrong password is caught by the header's authentication code
 * before any content is decrypted. Implementations normalise the password to Unicode NFC before
 * encoding it as UTF-8, so the same password typed on another keyboard opens the same archive.
 */
interface BackupKeyDerivation {

    /**
     * Derives the archive keys from [password].
     *
     * @param password The password as typed. The caller clears its own copy afterwards.
     * @param salt The archive's random salt, [BackupFormat.SALT_LENGTH] bytes.
     * @param params The Argon2id costs; a reader passes those of the header, already bounded by
     *   [BackupKdfParams.isAcceptable].
     * @return The content key and the header key. The caller wipes them when done.
     * @throws BackupFormatException With [BackupReadFailure.TOO_DEMANDING] when the device cannot spare
     *   the memory [params] ask for.
     */
    fun deriveKeys(password: CharArray, salt: ByteArray, params: BackupKdfParams): BackupKeys
}

/**
 * The two keys of an archive, [BackupFormat.KEY_LENGTH] bytes each.
 *
 * @property contentKey The AES-256 key the chunks are encrypted with.
 * @property headerKey The HMAC-SHA256 key the header is authenticated with.
 */
class BackupKeys(val contentKey: ByteArray, val headerKey: ByteArray) {

    /** Overwrites both keys with zeros. Best effort: copies the JVM made elsewhere are out of reach. */
    fun wipe() {
        contentKey.fill(0)
        headerKey.fill(0)
    }
}
