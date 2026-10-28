package app.kawkab.android.data.backup

/**
 * The entries one owner keeps in a Keystore-backed store, as a backup sees them.
 *
 * Every class that owns entries of an encrypted store implements this, so the entry names stay with the
 * code that writes them; `BackedUpSecretsGuardTest` fails for an owner that does not — except the
 * database passphrase, which never leaves the phone. The values are read decrypted, so they pass into
 * the archive only inside its encryption.
 */
interface BackedUpSecrets {

    /** The name of the encrypted store the entries live in. */
    val storeName: String

    /**
     * Entries that are not secrets and always go into a backup — a provider's chosen model and server
     * address, without which a restored pipeline would not know where to send its requests.
     *
     * @return Entry name to value; an unreadable entry is left out.
     */
    suspend fun alwaysEntries(): Map<String, String>

    /**
     * Secrets — API keys, tokens, sign-ins — that go into a backup only when the user asks.
     *
     * @return Entry name to value; an unreadable entry is left out.
     */
    suspend fun secretEntries(): Map<String, String>

    /**
     * Finishes moving this owner's secrets out of the plain settings, where an older release kept some,
     * before a backup reads the settings. Does nothing for an owner that never kept a secret there.
     */
    suspend fun settle() {
        // Nothing to move by default.
    }

    /**
     * The value a travelling setting this owner keeps goes into a backup with — with any secret an older
     * release left inside it removed. The settle step moves such secrets out; this is the second line,
     * for when that move could not be written.
     *
     * @param key The setting's name.
     * @param value Its stored value.
     * @return The value to back up, or `null` to leave the setting out.
     */
    fun scrubbed(key: String, value: Any): Any? = value

    /**
     * Writes entries a backup carried into this owner's store, through the owner's own writes, so every
     * reader sees them. Only names this owner writes are taken — an archive cannot place anything else in
     * the store — and an entry this phone has that the archive lacks is left alone.
     *
     * @param entries The archive's entries of [storeName], by entry name; owners sharing a store each get
     *   all of them and take their own.
     * @param secrets Whether these are the archive's secrets — taken only when the user chose them — rather
     *   than its provider settings. An owner takes a secret only in the first case, whichever entry it came in.
     * @return How many entries were written.
     */
    suspend fun restore(entries: Map<String, String>, secrets: Boolean): Int

    /** The names of the stores whose owners implement this. */
    companion object {
        /** The store of cloud API keys and each provider's model and server address. */
        const val API_KEYS_STORE: String = "secure_api_keys_v2"

        /** The store of the Hugging Face token and MCP sign-ins and headers. */
        const val SETTINGS_SECRETS_STORE: String = "secure_settings_secrets"
    }
}
