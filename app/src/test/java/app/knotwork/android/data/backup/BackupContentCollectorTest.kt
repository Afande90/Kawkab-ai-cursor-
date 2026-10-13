package app.knotwork.android.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.knotwork.android.data.local.crypto.FakeAeadCipher
import app.knotwork.android.data.local.crypto.InMemorySharedPreferences
import app.knotwork.android.data.local.crypto.KeystoreBackedPrefsStore
import app.knotwork.android.data.local.settings.ToolSettingsStore
import app.knotwork.android.domain.models.LocalModel
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.MemoryRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.services.TaskScheduler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * [BackupContentCollector]: the snapshot is taken under a fresh 32-byte key into the given file; the
 * manifest counts what the app holds and lists the models with their current hash and source; settings
 * come from DataStore; the encrypted stores' entries are merged per store, secrets only when asked.
 */
class BackupContentCollectorTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `given the app's data when collected then every part is there and the manifest counts it`() = runTest {
        // Given
        val snapshot = File(temp.root, "snapshot.db")
        val collector = collector()

        // When
        val content = collector.collect(includeSecrets = false, snapshotFile = snapshot, createdAtEpochMillis = 42L)

        // Then
        assertEquals(snapshot, content.snapshotFile)
        assertEquals(32, content.snapshotKey.size)
        assertEquals(snapshotKeys.single().toList(), content.snapshotKey.toList())
        with(content.manifest) {
            assertEquals(42L, createdAtEpochMillis)
            assertEquals(71, schemaVersion)
            assertEquals(false, secretsIncluded)
            assertEquals(
                listOf(3, 340, 2, 1, 0),
                counts.run {
                    listOf(chats, memories, pipelines, workspaceFiles, attachments)
                },
            )
            assertEquals(2, scheduledTasks)
            assertEquals("hash-of-a", models.single { it.name == "a.litertlm" }.sha256)
            assertEquals("https://h/a", models.single { it.name == "a.litertlm" }.sourceUrl)
            assertNull(models.single { it.name == "b.litertlm" }.sha256)
        }
        assertEquals(
            40,
            JSONObject(String(content.preferences)).getJSONObject("values").getJSONObject("top_k").getInt("value"),
        )
        assertNull(content.secrets)
    }

    @Test
    fun `given two owners of one store when collected then their entries are merged under it`() = runTest {
        // When
        val content = collector().collect(
            includeSecrets = true,
            snapshotFile = File(temp.root, "s.db"),
            createdAtEpochMillis = 0,
        )

        // Then
        val providers = JSONObject(String(content.providers)).getJSONObject("stores")
        val secrets = JSONObject(String(requireNotNull(content.secrets))).getJSONObject("stores")
        assertEquals("gpt-5.4", providers.getJSONObject("api").getString("openai_model"))
        assertEquals(
            setOf("hugging_face_token", "mcp_auth_x"),
            secrets.getJSONObject("settings").keys().asSequence().toSet(),
        )
        assertEquals("sk", secrets.getJSONObject("api").getString("openai_api_key"))
    }

    @Test
    fun `given MCP sign-ins an older release left inline when collected then no setting carries them`() = runTest {
        // Given — the JSON as 0.10 wrote it; nothing has read the server list in this process yet.
        val dataStore = dataStore()
        dataStore.edit {
            it[stringPreferencesKey("mcp_servers_json")] =
                """[{"url":"https://m.example","transport":"sse","auth":{"type":"bearer","token":"legacy_tok"},""" +
                """"headers":{"Authorization":"Bearer legacy_hdr"}}]"""
        }
        val context = mockk<Context>(relaxed = true) {
            every { getSharedPreferences(any(), any()) } returns InMemorySharedPreferences()
        }
        val secrets =
            KeystoreBackedPrefsStore(
                context,
                "secure_settings_secrets",
                "knotwork.settings_secrets",
                FakeAeadCipher(),
            )
        val tool = ToolSettingsStore(dataStore, secrets)

        // When
        val content = collector(dataStore, owners = setOf(tool))
            .collect(includeSecrets = false, snapshotFile = File(temp.root, "s.db"), createdAtEpochMillis = 0)

        // Then
        val settings = String(content.preferences)
        assertFalse(settings, settings.contains("legacy_tok") || settings.contains("legacy_hdr"))
        assertTrue("the server itself still travels: $settings", settings.contains("https://m.example"))
        // The owner settled before the settings were read: the sign-in already sits in the encrypted store.
        // Read straight from the store — asking the owner would list the servers and migrate after the fact.
        val authKey = "mcp_auth_" + MessageDigest.getInstance("SHA-256")
            .digest("https://m.example".toByteArray()).joinToString("") { "%02x".format(it) }
        assertTrue(secrets.getString(authKey).orEmpty().contains("legacy_tok"))
    }

    @Test
    fun `given the collection failing after the snapshot when it ends then the snapshot key is wiped`() = runTest {
        // Given
        val failing = mockk<TaskScheduler> { coEvery { countScheduled() } throws IllegalStateException("runtime gone") }

        // When
        runCatchingFailure { collector(scheduler = failing).collect(false, File(temp.root, "s.db"), 0) }

        // Then
        assertTrue(snapshotKeys.single().all { it == 0.toByte() })
    }

    private val snapshotKeys = mutableListOf<ByteArray>()

    private suspend fun runCatchingFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: IllegalStateException) {
            return
        }
        throw AssertionError("the collection did not fail")
    }

    private fun dataStore() = PreferenceDataStoreFactory.create(scope = scope) {
        File(temp.root, "prefs-${System.nanoTime()}.preferences_pb")
    }

    private suspend fun collector(
        dataStore: DataStore<Preferences>? = null,
        owners: Set<BackedUpSecrets>? = null,
        scheduler: TaskScheduler = mockk { coEvery { countScheduled() } returns 2 },
    ): BackupContentCollector {
        val dataStore = dataStore ?: dataStore().also { store -> store.edit { it[intPreferencesKey("top_k")] = 40 } }
        val workspace = temp.newFolder("agent_workspace").also { File(it, "a.txt").writeText("a") }
        val models = mockk<LocalModelRepository> {
            every { getAllModels() } returns flowOf(
                listOf(
                    LocalModel(
                        name = "a.litertlm",
                        path = "/m/a",
                        size = 10L,
                        isActive = true,
                        sourceUrl = "https://h/a",
                    ),
                    LocalModel(name = "b.litertlm", path = "/m/b", size = 20L, isActive = false),
                ),
            )
            coEvery { currentFileHash("/m/a") } returns "hash-of-a"
            coEvery { currentFileHash("/m/b") } returns null
        }
        return BackupContentCollector(
            snapshotter = object : DatabaseSnapshotter {
                override fun snapshot(target: File, key: ByteArray): Int {
                    snapshotKeys += key
                    target.writeBytes(ByteArray(8))
                    return 71
                }
            },
            files = BackupFileSource({ workspace }, { File(temp.root, "no-attachments") }),
            secretOwners = owners ?: setOf(
                Owner("api", always = mapOf("openai_model" to "gpt-5.4"), secret = mapOf("openai_api_key" to "sk")),
                Owner("settings", always = emptyMap(), secret = mapOf("hugging_face_token" to "hf")),
                Owner("settings", always = emptyMap(), secret = mapOf("mcp_auth_x" to "{}")),
            ),
            dataStore = dataStore,
            chatRepository = mockk<ChatRepository> { coEvery { getAllSessions() } returns listOf("1", "2", "3") },
            memoryRepository = mockk<MemoryRepository> { coEvery { countMemories() } returns 340 },
            pipelineRepository = mockk<PipelineRepository> {
                every { getAllPipelines() } returns
                    flowOf(listOf(mockk(), mockk()))
            },
            localModelRepository = models,
            taskScheduler = scheduler,
        )
    }

    private class Owner(
        override val storeName: String,
        private val always: Map<String, String>,
        private val secret: Map<String, String>,
    ) : BackedUpSecrets {
        override suspend fun alwaysEntries() = always

        override suspend fun secretEntries() = secret
    }
}
