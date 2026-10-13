package app.knotwork.android.data.backup

import androidx.datastore.preferences.core.Preferences
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.Base64

/**
 * The settings entry of an archive ([app.knotwork.android.domain.backup.BackupFormat.PREFERENCES_ENTRY]):
 * every travelling DataStore value with its type, so a restore writes back exactly what was there.
 *
 * ```
 * { "preferencesVersion": 1,
 *   "values": { "temperature": { "type": "float", "value": 0.7 },
 *               "approved_cleartext_origins": { "type": "stringSet", "value": ["http://192.168.1.5:11434"] } } }
 * ```
 *
 * Types: `boolean`, `int`, `long`, `float`, `double`, `string`, `stringSet` (sorted array), `byteArray`
 * (Base64). Only keys [PreferenceBackupPolicy] lets travel are written.
 */
internal object PreferencesBackupJson {

    /** The version of this entry's shape. */
    const val VERSION: Int = 1

    /**
     * Encodes the travelling values of [preferences].
     *
     * @param preferences The app's DataStore contents.
     * @param scrub Given each travelling setting's name and value, returns the value to write, or `null` to
     *   leave the setting out.
     * @return The entry's JSON text. A float or double that is not a finite number is left out: JSON cannot
     *   hold it, and one broken value must not stop the backup.
     */
    fun encode(preferences: Preferences, scrub: (String, Any) -> Any? = { _, value -> value }): String {
        val values = JSONObject()
        preferences.asMap().entries.sortedBy { it.key.name }.forEach { (key, stored) ->
            if (PreferenceBackupPolicy.membershipOf(key.name) != PreferenceBackupPolicy.Membership.TRAVELS) {
                return@forEach
            }
            val value = scrub(key.name, stored) ?: return@forEach
            val typed = typed(value)
            if (typed == null) {
                Timber.w("Setting of an unknown type left out of the backup: %s", key.name)
            } else {
                values.put(key.name, typed)
            }
        }
        return JSONObject().put(KEY_VERSION, VERSION).put(KEY_VALUES, values).toString()
    }

    private fun typed(value: Any): JSONObject? {
        val (type, json) = when (value) {
            is Boolean -> "boolean" to value
            is Int -> "int" to value
            is Long -> "long" to value
            is Float -> if (value.isFinite()) "float" to value.toDouble() else return null
            is Double -> if (value.isFinite()) "double" to value else return null
            is String -> "string" to value
            is Set<*> -> "stringSet" to JSONArray(value.map { it.toString() }.sorted())
            is ByteArray -> "byteArray" to Base64.getEncoder().encodeToString(value)
            else -> return null
        }
        return JSONObject().put(KEY_TYPE, type).put(KEY_VALUE, json)
    }

    private const val KEY_VERSION = "preferencesVersion"
    private const val KEY_VALUES = "values"
    private const val KEY_TYPE = "type"
    private const val KEY_VALUE = "value"
}
