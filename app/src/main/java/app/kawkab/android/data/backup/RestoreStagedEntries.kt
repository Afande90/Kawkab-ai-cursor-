package app.kawkab.android.data.backup

import android.content.Context
import app.kawkab.android.data.local.crypto.AeadCipher
import app.kawkab.android.data.local.crypto.KeystoreBackedPrefsStore
import app.kawkab.android.domain.backup.BackupFormatException
import app.kawkab.android.domain.backup.BackupReadFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds a staged restore's encrypted-store entries — the provider settings, and the secrets when the archive
 * carries them — between the check and the restart, in a Keystore-backed store of their own, so no secret
 * of the archive is ever written to disk as plain text. [clear] destroys the store and its key.
 *
 * An entry's text is the archive's own: `{"entriesVersion":1,"stores":{"<store>":{"<entry>":"<value>"}}}`.
 *
 * @param store The store the entries are kept in.
 */
@Singleton
internal class RestoreStagedEntries(private val store: KeystoreBackedPrefsStore) {

    /** Keeps the entries in the app's own store. */
    @Inject
    constructor(@ApplicationContext context: Context, cipher: AeadCipher) : this(
        KeystoreBackedPrefsStore(context = context, prefsName = PREFS_NAME, keyAlias = KEY_ALIAS, cipher = cipher),
    )

    /** The two entries of an archive that end up in the encrypted stores. */
    enum class Kind(val key: String) {
        /** Not secrets: always applied. */
        PROVIDERS("providers"),

        /** Applied only when the user chose to. */
        SECRETS("secrets"),
    }

    /**
     * Checks one entry's text and keeps it.
     *
     * @param kind Which entry it is.
     * @param text The entry's text.
     * @throws BackupFormatException With [BackupReadFailure.DAMAGED] when the text is not this shape.
     */
    fun keep(kind: Kind, text: String) {
        parse(text)
        store.putString(kind.key, text, synchronous = true)
    }

    /**
     * The entries kept for [kind], by store name and entry name.
     *
     * @param kind Which entry.
     * @return The entries, or an empty map when none were kept.
     */
    fun entries(kind: Kind): Map<String, Map<String, String>> = store.getString(kind.key)?.let(::parse).orEmpty()

    /** Whether entries were kept for [kind]. */
    fun has(kind: Kind): Boolean = store.getString(kind.key) != null

    /** Destroys the store: its file and its Keystore key. */
    fun clear() {
        store.destroy()
    }

    private fun parse(text: String): Map<String, Map<String, String>> = try {
        val root = JSONObject(text)
        if (root.optInt(KEY_VERSION, -1) != ENTRIES_VERSION) {
            throw BackupFormatException(BackupReadFailure.DAMAGED, "unknown entries version")
        }
        val stores = root.getJSONObject(KEY_STORES)
        stores.keys().asSequence().associateWith { name ->
            val entries = stores.getJSONObject(name)
            entries.keys().asSequence().associateWith { entries.getString(it) }
        }
    } catch (e: JSONException) {
        throw BackupFormatException(BackupReadFailure.DAMAGED, "entries do not parse: ${e.javaClass.simpleName}")
    }

    private companion object {
        const val PREFS_NAME = "restore_staging_entries"
        const val KEY_ALIAS = "kawkab.restore_staging"
        const val KEY_VERSION = "entriesVersion"
        const val ENTRIES_VERSION = 1
        const val KEY_STORES = "stores"
    }
}
