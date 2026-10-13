package app.knotwork.android.data.backup

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.knotwork.android.domain.backup.BackupFormatException
import app.knotwork.android.domain.backup.BackupReadFailure
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [PreferencesBackupJson]: every type is written with its name, a float reads back as the same float, and
 * only settings the policy lets travel are written.
 */
class PreferencesBackupJsonTest {

    @Test
    fun `given values of every type when encoded then each carries its type and value`() {
        // Given — real key names, so the policy lets them travel; the types are what DataStore can hold.
        val preferences = mutablePreferencesOf(
            booleanPreferencesKey("auto_extract_enabled") to true,
            intPreferencesKey("top_k") to 40,
            longPreferencesKey("tool_call_timeout_ms") to 30_000L,
            floatPreferencesKey("temperature") to 0.7f,
            doublePreferencesKey("memory_search_threshold") to 0.25,
            stringPreferencesKey("system_prompt_prefix") to "Be brief.",
            stringSetPreferencesKey("approved_cleartext_origins") to setOf("http://b", "http://a"),
            byteArrayPreferencesKey("console_preferred_tab") to byteArrayOf(1, 2, 3),
        )

        // When
        val values = JSONObject(PreferencesBackupJson.encode(preferences)).getJSONObject("values")

        // Then
        fun typed(name: String) = values.getJSONObject(name).let { it.getString("type") to it.get("value").toString() }
        assertEquals("boolean" to "true", typed("auto_extract_enabled"))
        assertEquals("int" to "40", typed("top_k"))
        assertEquals("long" to "30000", typed("tool_call_timeout_ms"))
        assertEquals("float", typed("temperature").first)
        assertEquals(0.7f, values.getJSONObject("temperature").getDouble("value").toFloat())
        assertEquals("double" to "0.25", typed("memory_search_threshold"))
        assertEquals("string" to "Be brief.", typed("system_prompt_prefix"))
        assertEquals("stringSet" to """["http://a","http://b"]""", typed("approved_cleartext_origins"))
        assertEquals("byteArray" to "AQID", typed("console_preferred_tab"))
    }

    @Test
    fun `given a float or double that is not a number when encoded then it is left out, not fatal`() {
        // Given — JSON has no NaN; a broken value must not stop the whole backup.
        val preferences = mutablePreferencesOf(
            floatPreferencesKey("temperature") to Float.NaN,
            doublePreferencesKey("memory_search_threshold") to Double.POSITIVE_INFINITY,
            intPreferencesKey("top_k") to 40,
        )

        // When
        val values = JSONObject(PreferencesBackupJson.encode(preferences)).getJSONObject("values")

        // Then
        assertEquals(setOf("top_k"), values.keys().asSequence().toSet())
    }

    @Test
    fun `given settings that stay, reset or are unknown when encoded then they are left out`() {
        // Given
        val preferences = mutablePreferencesOf(
            intPreferencesKey("top_k") to 40,
            booleanPreferencesKey("crash_reporting_enabled") to true,
            booleanPreferencesKey("external_automation_enabled") to true,
            stringPreferencesKey("hugging_face_token") to "hf_plain_legacy",
            stringPreferencesKey("left_by_an_old_release") to "x",
        )

        // When
        val json = PreferencesBackupJson.encode(preferences)
        val values = JSONObject(json).getJSONObject("values")

        // Then
        assertEquals(setOf("top_k"), values.keys().asSequence().toSet())
        assertFalse("a legacy secret never reaches the archive: $json", json.contains("hf_plain_legacy"))
        assertEquals(PreferencesBackupJson.VERSION, JSONObject(json).getInt("preferencesVersion"))
    }

    @Test
    fun `given an encoded entry when decoded then every travelling setting comes back with its own type`() {
        // Given — each with the type the app declares it with.
        val preferences = mutablePreferencesOf(
            booleanPreferencesKey("auto_extract_enabled") to true,
            intPreferencesKey("top_k") to 40,
            longPreferencesKey("tool_call_timeout_ms") to 30_000L,
            floatPreferencesKey("temperature") to 0.7f,
            stringPreferencesKey("system_prompt_prefix") to "Be brief.",
            stringSetPreferencesKey("approved_cleartext_origins") to setOf("http://b", "http://a"),
        )

        // When
        val decoded = PreferencesBackupJson.decode(PreferencesBackupJson.encode(preferences))

        // Then
        assertEquals(preferences.asMap(), decoded)
    }

    @Test
    fun `given values that do not fit their setting when decoded then each is left out and the rest come back`() {
        // Given — written by hand: a type the setting is not declared with, a fraction for a whole number, a
        // number too large for an int, a setting that stays with the phone, and a name no setting has.
        val values = JSONObject()
            .put("temperature", typed("double", 0.4))
            .put("top_k", typed("int", 2.5))
            .put("max_context_length", typed("int", Long.MAX_VALUE))
            .put("crash_reporting_enabled", typed("boolean", true))
            .put("left_by_an_old_release", typed("string", "x"))
            .put("system_prompt_prefix", typed("string", "kept"))
        val json = JSONObject().put("preferencesVersion", 1).put("values", values).toString()

        // When
        val decoded = PreferencesBackupJson.decode(json)

        // Then
        assertEquals(mapOf(Pair(stringPreferencesKey("system_prompt_prefix"), "kept")), decoded)
    }

    @Test
    fun `given text that is not a settings entry this build reads when decoded then the archive is damaged`() {
        listOf("{not json", """{"preferencesVersion":2,"values":{}}""", """{"preferencesVersion":1}""").forEach {
            val failure = assertThrows(BackupFormatException::class.java) { PreferencesBackupJson.decode(it) }
            assertEquals(it, BackupReadFailure.DAMAGED, failure.failure)
        }
    }

    private fun typed(type: String, value: Any): JSONObject = JSONObject().put("type", type).put("value", value)
}
