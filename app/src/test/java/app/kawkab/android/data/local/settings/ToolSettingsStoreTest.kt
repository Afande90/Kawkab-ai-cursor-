package app.kawkab.android.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.kawkab.android.data.local.crypto.FakeAeadCipher
import app.kawkab.android.data.local.crypto.InMemorySharedPreferences
import app.kawkab.android.data.local.crypto.KeystoreBackedPrefsStore
import app.kawkab.android.domain.models.McpAuth
import app.kawkab.android.domain.models.McpServerConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.Executors

/**
 * [ToolSettingsStore]'s MCP server list as an observer sees it while a server changes, on a real
 * file-backed DataStore.
 *
 * The Tools screen observes the list for as long as it lives and connects each server with the
 * config it last received. These tests hold the store to what that needs: the list a change
 * produces already carries the server's new auth and headers, a change of auth alone still
 * reaches the observer, and a list that cannot be written leaves the secrets as they were.
 *
 * The observer and the change run on one thread, as both do on the main thread in the app. That is
 * the ordering in which a list written before its secrets reached the observer without them, and an
 * edited server with its old auth.
 */
class ToolSettingsStoreTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private val securePrefs = InMemorySharedPreferences()
    private val context = mockk<Context>(relaxed = true) {
        every { getSharedPreferences(SECRETS_PREFS, Context.MODE_PRIVATE) } returns securePrefs
    }
    private val secretStore = KeystoreBackedPrefsStore(
        context = context,
        prefsName = SECRETS_PREFS,
        keyAlias = "kawkab.settings_secrets",
        cipher = FakeAeadCipher(),
    )

    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore = PreferenceDataStoreFactory.create(
        scope = dataStoreScope,
        produceFile = { tempFolder.root.resolve("tool-settings.preferences_pb") },
    )

    /** A DataStore whose every write fails, as a full disk would make it. */
    private val unwritableDataStore = object : DataStore<Preferences> by dataStore {
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException(DISK_FULL)
    }

    /** Stands for the main thread: the observer and the change both run here. */
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val mainScope = CoroutineScope(SupervisorJob() + main)

    @After
    fun tearDown() {
        mainScope.cancel()
        main.close()
        dataStoreScope.cancel()
    }

    @Test
    fun `given an observer of the list when a server is added then its first list carries the auth and headers`() =
        runTest {
            // Given
            val store = ToolSettingsStore(dataStore, secretStore)
            val received = observe(store)

            // When
            withContext(main) {
                store.addMcpServer(
                    McpServerConfig(url = URL, auth = McpAuth.Bearer(token = "tok"), headers = mapOf("X-Org" to "a")),
                )
            }

            // Then
            val added = received.receiveWithin().single()
            assertEquals(McpAuth.Bearer(token = "tok"), added.auth)
            assertEquals(mapOf("X-Org" to "a"), added.headers)
        }

    @Test
    fun `given an observer of the list when a server's name and auth change then its first list carries both`() =
        runTest {
            // Given
            val store = ToolSettingsStore(dataStore, secretStore)
            store.addMcpServer(McpServerConfig(url = URL, auth = McpAuth.Bearer(token = "old")))
            val received = observe(store)

            // When
            withContext(main) {
                store.updateMcpServer(
                    originalUrl = URL,
                    updated = McpServerConfig(url = URL, name = "Renamed", auth = McpAuth.Bearer(token = "new")),
                )
            }

            // Then
            val edited = received.receiveWithin().single()
            assertEquals("Renamed", edited.name)
            assertEquals(McpAuth.Bearer(token = "new"), edited.auth)
        }

    @Test
    fun `given an observer of the list when only a server's auth changes then the observer receives the new auth`() =
        runTest {
            // Given
            val store = ToolSettingsStore(dataStore, secretStore)
            store.addMcpServer(McpServerConfig(url = URL, auth = McpAuth.Bearer(token = "old")))
            val received = observe(store)

            // When — the stored JSON stays byte-identical, so DataStore itself emits nothing
            withContext(main) {
                store.updateMcpServer(
                    originalUrl = URL,
                    updated = McpServerConfig(url = URL, auth = McpAuth.Bearer(token = "new")),
                )
            }

            // Then
            assertEquals(McpAuth.Bearer(token = "new"), received.receiveWithin().single().auth)
        }

    @Test
    fun `given the list cannot be written when a server is added then none of its secrets stay`() = runTest {
        // Given
        val store = ToolSettingsStore(unwritableDataStore, secretStore)

        // When
        val failure = try {
            store.addMcpServer(
                McpServerConfig(url = URL, auth = McpAuth.Bearer(token = "tok"), headers = mapOf("X-Org" to "a")),
            )
            null
        } catch (e: IOException) {
            e
        }

        // Then
        assertEquals(DISK_FULL, failure?.message)
        assertTrue(
            "no MCP secret may outlive a list that was not written: ${securePrefs.values.keys}",
            securePrefs.values.keys.none { it.startsWith("mcp_") },
        )
    }

    @Test
    fun `given the list cannot be written when a server's auth is edited then the old auth stays`() = runTest {
        // Given
        ToolSettingsStore(dataStore, secretStore)
            .addMcpServer(McpServerConfig(url = URL, name = "Server", auth = McpAuth.Bearer(token = "old")))
        val store = ToolSettingsStore(unwritableDataStore, secretStore)

        // When
        val failure = try {
            store.updateMcpServer(
                originalUrl = URL,
                updated = McpServerConfig(url = URL, name = "Renamed", auth = McpAuth.Bearer(token = "new")),
            )
            null
        } catch (e: IOException) {
            e
        }

        // Then — both what this store answers and what a fresh read of the secrets finds
        assertEquals(DISK_FULL, failure?.message)
        assertEquals(McpAuth.Bearer(token = "old"), store.mcpServers.first().single().auth)
        assertEquals(
            McpAuth.Bearer(token = "old"),
            ToolSettingsStore(dataStore, secretStore).mcpServers.first().single().auth,
        )
    }

    /**
     * Starts observing [store]'s server list on [main], as the Tools screen does, and returns what the
     * observer receives after the list as it stands.
     */
    private suspend fun observe(store: ToolSettingsStore): ReceiveChannel<List<McpServerConfig>> {
        val received = Channel<List<McpServerConfig>>(Channel.UNLIMITED)
        mainScope.launch { store.mcpServers.collect { received.send(it) } }
        received.receiveWithin()
        return received
    }

    /** The next list the observer receives, waiting in real time: the store's writes run on real threads. */
    private suspend fun <T> ReceiveChannel<T>.receiveWithin(): T =
        withContext(Dispatchers.Default) { withTimeout(RECEIVE_TIMEOUT_MS) { receive() } }

    private companion object {
        const val SECRETS_PREFS = "secure_settings_secrets"
        const val URL = "https://mcp.example"
        const val DISK_FULL = "disk full"
        const val RECEIVE_TIMEOUT_MS = 5_000L
    }
}
