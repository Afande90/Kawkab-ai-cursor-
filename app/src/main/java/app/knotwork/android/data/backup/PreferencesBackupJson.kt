package app.knotwork.android.data.backup

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.knotwork.android.data.backup.PreferenceBackupPolicy.ValueType
import app.knotwork.android.domain.backup.BackupFormatException
import app.knotwork.android.domain.backup.BackupReadFailure
import org.json.JSONArray
import org.json.JSONException
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
 * (Base64). Only keys [PreferenceBackupPolicy] lets travel are written, and only those are read back.
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

    /**
     * Reads a settings entry back.
     *
     * A value is kept only when its key travels and it has the type the app declares the key with
     * ([PreferenceBackupPolicy.typeOf]); anything else is left out and logged by key, so a setting of this
     * phone can never be overwritten with a value its readers cannot read, and an entry cannot set a
     * setting that stays with the phone.
     *
     * @param json The entry's text.
     * @return The settings to write, keyed as the app declares them.
     * @throws BackupFormatException With [BackupReadFailure.DAMAGED] when the entry is not this shape.
     */
    fun decode(json: String): Map<Preferences.Key<*>, Any> {
        val values = try {
            val root = JSONObject(json)
            if (root.optInt(KEY_VERSION, -1) != VERSION) damaged("unknown settings entry version")
            root.getJSONObject(KEY_VALUES)
        } catch (e: JSONException) {
            damaged("settings entry does not parse: ${e.javaClass.simpleName}")
        }
        return buildMap {
            values.keys().asSequence().sorted().forEach { name ->
                val restored = values.optJSONObject(name)?.let { restoredValue(name, it) }
                if (restored == null) {
                    Timber.w("Setting left out of a restore: %s", name)
                } else {
                    put(restored.first, restored.second)
                }
            }
        }
    }

    /**
     * Writes [settings] into [target].
     *
     * @param target The preferences being edited.
     * @param settings Settings as [decode] returns them: each value has its key's type.
     */
    @Suppress("UNCHECKED_CAST") // reason: decode pairs every key with a value of the key's own type.
    fun writeInto(target: MutablePreferences, settings: Map<Preferences.Key<*>, Any>) {
        settings.forEach { (key, value) -> target[key as Preferences.Key<Any>] = value }
    }

    private fun restoredValue(name: String, entry: JSONObject): Pair<Preferences.Key<*>, Any>? {
        val declared = PreferenceBackupPolicy.typeOf(name) ?: return null
        if (ValueType.ofWire(entry.optString(KEY_TYPE)) != declared || !entry.has(KEY_VALUE)) return null
        val value = entry.get(KEY_VALUE)
        return when (declared) {
            ValueType.BOOLEAN -> (value as? Boolean)?.let { Pair(booleanPreferencesKey(name), it) }
            ValueType.INT -> integral(value)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }
                ?.let { Pair(intPreferencesKey(name), it.toInt()) }
            ValueType.LONG -> integral(value)?.let { Pair(longPreferencesKey(name), it) }
            ValueType.FLOAT -> (value as? Number)?.toFloat()?.takeIf { it.isFinite() }
                ?.let { Pair(floatPreferencesKey(name), it) }
            ValueType.DOUBLE -> (value as? Number)?.toDouble()?.takeIf { it.isFinite() }
                ?.let { Pair(doublePreferencesKey(name), it) }
            ValueType.STRING -> (value as? String)?.let { Pair(stringPreferencesKey(name), it) }
            ValueType.STRING_SET -> stringSet(value)?.let { Pair(stringSetPreferencesKey(name), it) }
            ValueType.BYTE_ARRAY -> bytes(value)?.let { Pair(byteArrayPreferencesKey(name), it) }
        }
    }

    /** A whole number, as org.json parses one: an `Int` or a `Long`, never a fraction. */
    private fun integral(value: Any): Long? = when (value) {
        is Int -> value.toLong()
        is Long -> value
        else -> null
    }

    private fun stringSet(value: Any): Set<String>? {
        val array = value as? JSONArray ?: return null
        return (0 until array.length()).map { array.opt(it) as? String ?: return null }.toSet()
    }

    private fun bytes(value: Any): ByteArray? = try {
        (value as? String)?.let { Base64.getDecoder().decode(it) }
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "A byte-array setting is not Base64")
        null
    }

    private fun typed(value: Any): JSONObject? {
        val (type, json) = when (value) {
            is Boolean -> ValueType.BOOLEAN to value
            is Int -> ValueType.INT to value
            is Long -> ValueType.LONG to value
            is Float -> if (value.isFinite()) ValueType.FLOAT to value.toDouble() else return null
            is Double -> if (value.isFinite()) ValueType.DOUBLE to value else return null
            is String -> ValueType.STRING to value
            is Set<*> -> ValueType.STRING_SET to JSONArray(value.map { it.toString() }.sorted())
            is ByteArray -> ValueType.BYTE_ARRAY to Base64.getEncoder().encodeToString(value)
            else -> return null
        }
        return JSONObject().put(KEY_TYPE, type.wire).put(KEY_VALUE, json)
    }

    private fun damaged(reason: String): Nothing = throw BackupFormatException(BackupReadFailure.DAMAGED, reason)

    private const val KEY_VERSION = "preferencesVersion"
    private const val KEY_VALUES = "values"
    private const val KEY_TYPE = "type"
    private const val KEY_VALUE = "value"
}
