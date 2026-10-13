package app.knotwork.android.data.backup

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

    /** The names of the stores whose owners implement this. */
    companion object {
        /** The store of cloud API keys and each provider's model and server address. */
        const val API_KEYS_STORE: String = "secure_api_keys_v2"

        /** The store of the Hugging Face token and MCP sign-ins and headers. */
        const val SETTINGS_SECRETS_STORE: String = "secure_settings_secrets"
    }
}
