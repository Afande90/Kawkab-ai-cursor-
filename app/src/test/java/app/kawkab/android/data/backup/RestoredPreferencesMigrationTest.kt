package app.kawkab.android.data.backup

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [RestoredPreferencesMigration] writes a committed restore's settings over this phone's: travelling settings
 * are replaced, those that stay are kept, those that reset are cleared — and it never fails the DataStore.
 */
class RestoredPreferencesMigrationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var layout: RestoreLayout
    private lateinit var migration: RestoredPreferencesMigration

    private val phone = mutablePreferencesOf(
        floatPreferencesKey("temperature") to 0.9f,
        intPreferencesKey("top_k") to 64,
        stringPreferencesKey("local_model_backend") to "GPU",
        booleanPreferencesKey("external_automation_enabled") to true,
        stringPreferencesKey("left_by_an_old_release") to "kept",
    ).toPreferences()

    @Before
    fun setUp() {
        val app = folder.newFolder("app")
        layout = RestoreLayout(
            root = File(app, "files/restore"),
            liveDatabase = File(app, "databases/agent_database.db"),
            liveWorkspace = File(app, "files/agent_workspace"),
            liveAttachments = File(app, "files/attachments"),
        )
        layout.staging.mkdirs()
        val archive = mutablePreferencesOf(
            floatPreferencesKey("temperature") to 0.2f,
            stringPreferencesKey("system_prompt_prefix") to "Be brief.",
        )
        layout.stagedPreferences.writeText(PreferencesBackupJson.encode(archive))
        migration = RestoredPreferencesMigration(layout) { true }
    }

    @Test
    fun `given a staged restore that is not committed when the DataStore opens then it does not migrate`() = runTest {
        assertFalse(migration.shouldMigrate(phone))
    }

    @Test
    fun `given a committed restore whose database is not in place yet when the DataStore opens then it waits`() =
        runTest {
            // Given — the start could not move the database the restored settings point into.
            commit()
            layout.stagedDatabase.writeText("restored database")

            // Then
            assertFalse(migration.shouldMigrate(phone))
        }

    @Test
    fun `given a committed restore when the DataStore opens in the restart's own process then it does not migrate`() =
        runTest {
            // Given
            commit()

            // Then
            assertFalse(RestoredPreferencesMigration(layout) { false }.shouldMigrate(phone))
        }

    @Test
    fun `given a committed restore when migrated then each setting follows its policy`() = runTest {
        // Given
        commit()

        // When
        assertTrue(migration.shouldMigrate(phone))
        val migrated = migration.migrate(phone)

        // Then
        assertEquals(0.2f, migrated[floatPreferencesKey("temperature")])
        assertEquals("Be brief.", migrated[stringPreferencesKey("system_prompt_prefix")])
        assertNull(
            "a travelling setting the archive lacks goes back to its default",
            migrated[intPreferencesKey("top_k")],
        )
        assertEquals("GPU", migrated[stringPreferencesKey("local_model_backend")])
        assertNull(migrated[booleanPreferencesKey("external_automation_enabled")])
        assertEquals("kept", migrated[stringPreferencesKey("left_by_an_old_release")])
    }

    @Test
    fun `given a staged entry that does not parse when migrated then this phone's settings are kept`() = runTest {
        // Given
        commit()
        layout.stagedPreferences.writeText("{not json")

        // When
        val migrated = migration.migrate(phone)

        // Then
        assertEquals(phone, migrated)
    }

    @Test
    fun `given an entry that sets a setting of this phone when migrated then that setting is not touched`() = runTest {
        // Given — an archive written by hand to set settings that stay, and one of the wrong type.
        commit()
        val values = JSONObject()
            .put("local_model_backend", JSONObject().put("type", "string").put("value", "NPU"))
            .put("temperature", JSONObject().put("type", "double").put("value", 0.4))
        layout.stagedPreferences.writeText(
            JSONObject().put("preferencesVersion", 1).put("values", values).toString(),
        )

        // When
        val migrated = migration.migrate(phone)

        // Then
        assertEquals("GPU", migrated[stringPreferencesKey("local_model_backend")])
        assertNull(migrated[floatPreferencesKey("temperature")])
    }

    @Test
    fun `given the migration ran when it cleans up then the staged settings are gone`() = runTest {
        // When
        migration.cleanUp()

        // Then
        assertFalse(layout.stagedPreferences.exists())
    }

    private fun commit() {
        layout.writeCommit(RestoreLayout.CommitMarker(applySecrets = false, backgroundStopped = true))
    }
}
