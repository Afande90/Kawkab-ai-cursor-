package app.knotwork.android.data.local

import android.content.Context
import app.knotwork.android.data.local.crypto.FakeAeadCipher
import app.knotwork.android.data.local.crypto.InMemorySharedPreferences
import app.knotwork.android.data.local.crypto.KeystoreBackedPrefsStore
import app.knotwork.android.domain.constants.SettingsDefaults
import app.knotwork.android.domain.models.CloudProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.ProviderException

/**
 * Verifies [ApiKeyManager]'s storage round-trip and its re-enterable-secret
 * recovery policy: values that can no longer be decrypted are reported as
 * absent and dropped, never propagated as errors.
 */
class ApiKeyManagerTest {

    private companion object {
        const val PREFS_NAME = "secure_api_keys_v2"
        const val KEY_ALIAS = "knotwork.api_keys"
        const val OPENAI_KEY = "openai_api_key"
        const val OLLAMA_CONTEXT = "ollama_context"

        /**
         * Every entry name an installed release may already hold, with the write that must
         * land on it. The names are user data: a rename reads every saved key as "not
         * configured", silently. Written out as literals on purpose — deriving them here
         * the way production does would let both drift together.
         */
        val SHIPPED_ENTRIES: Map<String, suspend (ApiKeyManager) -> Unit> = mapOf(
            "openai_api_key" to { m -> m.setApiKey(CloudProvider.OPENAI, "v") },
            "openai_model" to { m -> m.setModel(CloudProvider.OPENAI, "v") },
            "anthropic_api_key" to { m -> m.setApiKey(CloudProvider.ANTHROPIC, "v") },
            "anthropic_model" to { m -> m.setModel(CloudProvider.ANTHROPIC, "v") },
            "google_api_key" to { m -> m.setApiKey(CloudProvider.GOOGLE, "v") },
            "google_model" to { m -> m.setModel(CloudProvider.GOOGLE, "v") },
            "deepseek_api_key" to { m -> m.setApiKey(CloudProvider.DEEPSEEK, "v") },
            "deepseek_model" to { m -> m.setModel(CloudProvider.DEEPSEEK, "v") },
            "ollama_base_url" to { m -> m.setBaseUrl(CloudProvider.OLLAMA, "v") },
            "ollama_model" to { m -> m.setModel(CloudProvider.OLLAMA, "v") },
            "ollama_context" to { m -> m.setOllamaContextWindowSize(1) },
        )
    }

    private lateinit var context: Context
    private lateinit var prefs: InMemorySharedPreferences
    private lateinit var cipher: FakeAeadCipher

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        prefs = InMemorySharedPreferences()
        cipher = FakeAeadCipher()
        every { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) } returns prefs
    }

    private fun manager() = ApiKeyManager(context, cipher)

    @Test
    fun `given key saved when read through a fresh instance then round-trips`() = runTest {
        manager().setApiKey(CloudProvider.OPENAI, "sk-test-123")

        assertEquals("sk-test-123", manager().getApiKey(CloudProvider.OPENAI).first())
    }

    @Test
    fun `given key saved when raw prefs inspected then value is not plaintext`() = runTest {
        manager().setApiKey(CloudProvider.OPENAI, "sk-test-123")

        val raw = prefs.values[OPENAI_KEY] as String
        assertFalse(raw.contains("sk-test-123"))
    }

    @Test
    fun `given key set to null when read then returns null and entry is removed`() = runTest {
        val subject = manager()
        subject.setApiKey(CloudProvider.OPENAI, "sk-test-123")

        subject.setApiKey(CloudProvider.OPENAI, null)

        assertNull(subject.getApiKey(CloudProvider.OPENAI).first())
        assertFalse(prefs.values.containsKey(OPENAI_KEY))
    }

    @Test
    fun `given the Keystore key behind a stored key is gone when read then treated as unset and dropped`() = runTest {
        manager().setApiKey(CloudProvider.OPENAI, "sk-test-123")
        cipher.failDecrypt = true

        // A lost Keystore key must surface as "no key configured", not crash:
        // API keys are user re-enterable, unlike the database passphrase.
        assertNull(manager().getApiKey(CloudProvider.OPENAI).first())
        assertFalse(prefs.values.containsKey(OPENAI_KEY))
    }

    @Test
    fun `given a stored key that does not authenticate when read then treated as unset and dropped`() = runTest {
        manager().setApiKey(CloudProvider.OPENAI, "sk-test-123")
        manager().setApiKey(CloudProvider.ANTHROPIC, "sk-ant")
        // A blob sealed for another slot fails the tag check, as a tampered one would.
        prefs.values[OPENAI_KEY] = prefs.values.getValue("anthropic_api_key")

        assertNull(manager().getApiKey(CloudProvider.OPENAI).first())
        assertFalse(prefs.values.containsKey(OPENAI_KEY))
    }

    @Test
    fun `given the Keystore fails for now when a key is read then it is kept and a later process reads it`() = runTest {
        manager().setApiKey(CloudProvider.OPENAI, "sk-test-123")
        cipher.decryptFailure = ProviderException("Keystore operation failed")

        assertNull("unreadable for now reads as unset", manager().getApiKey(CloudProvider.OPENAI).first())
        assertTrue("a key stored intact is not dropped", prefs.values.containsKey(OPENAI_KEY))

        cipher.decryptFailure = null
        assertEquals("sk-test-123", manager().getApiKey(CloudProvider.OPENAI).first())
    }

    @Test
    fun `given the Keystore fails for now when the same instance asks again then the key reappears`() = runTest {
        manager().setApiKey(CloudProvider.OPENAI, "sk-test-123")
        cipher.decryptFailure = java.security.KeyStoreException("Keystore not initialized")
        val subject = manager()
        val observed = subject.getApiKey(CloudProvider.OPENAI)
        assertNull(observed.first())

        cipher.decryptFailure = null

        // The next access reads the entry again and publishes it to every observer of it.
        assertEquals("sk-test-123", subject.getApiKey(CloudProvider.OPENAI).first())
        assertEquals("sk-test-123", observed.first())
    }

    @Test
    fun `given a key unreadable for now when the user saves a new one then the new one wins`() = runTest {
        manager().setApiKey(CloudProvider.OPENAI, "sk-old")
        cipher.decryptFailure = ProviderException("Keystore operation failed")
        val subject = manager()
        assertNull(subject.getApiKey(CloudProvider.OPENAI).first())
        cipher.decryptFailure = null

        subject.setApiKey(CloudProvider.OPENAI, "sk-new")

        assertEquals("sk-new", subject.getApiKey(CloudProvider.OPENAI).first())
        assertEquals("sk-new", manager().getApiKey(CloudProvider.OPENAI).first())
    }

    @Test
    fun `given no stored ollama context when read then returns default`() = runTest {
        assertEquals(
            SettingsDefaults.OLLAMA_CONTEXT_WINDOW_DEFAULT,
            manager().getOllamaContextWindowSize().first(),
        )
    }

    @Test
    fun `given ollama context saved when read through a fresh instance then round-trips`() = runTest {
        manager().setOllamaContextWindowSize(8192)

        assertEquals(8192, manager().getOllamaContextWindowSize().first())
        assertTrue(prefs.values.containsKey(OLLAMA_CONTEXT))
    }

    @Test
    fun `given updated key when flow observed then emits the new value`() = runTest {
        val subject = manager()
        subject.setApiKey(CloudProvider.ANTHROPIC, "first")

        subject.setApiKey(CloudProvider.ANTHROPIC, "second")

        assertEquals("second", subject.getApiKey(CloudProvider.ANTHROPIC).first())
    }

    @Test
    fun `given each slot an installed release may hold when written then it lands under its shipped name`() = runTest {
        SHIPPED_ENTRIES.forEach { (name, write) ->
            prefs.values.clear()

            write(manager())

            assertEquals("write for '$name' landed elsewhere", setOf(name), prefs.values.keys)
        }
    }

    @Test
    fun `given values an earlier release wrote under the shipped names when read then each is returned`() = runTest {
        // Written the way the per-provider store did: straight through the encrypted
        // store, under the literal name, the file and the key alias.
        val earlier = KeystoreBackedPrefsStore(context, PREFS_NAME, KEY_ALIAS, cipher)
        earlier.putString("deepseek_api_key", "sk-ds", synchronous = true)
        earlier.putString("google_model", "gemini-x", synchronous = true)
        earlier.putString("ollama_base_url", "http://10.0.0.2:11434", synchronous = true)

        val subject = manager()

        assertEquals("sk-ds", subject.getApiKey(CloudProvider.DEEPSEEK).first())
        assertEquals("gemini-x", subject.getModel(CloudProvider.GOOGLE).first())
        assertEquals("http://10.0.0.2:11434", subject.getBaseUrl(CloudProvider.OLLAMA).first())
    }

    @Test
    fun `given a key saved for one provider when another is read then it stays unset`() = runTest {
        val subject = manager()
        subject.setApiKey(CloudProvider.OPENAI, "sk-openai")

        assertNull(subject.getApiKey(CloudProvider.ANTHROPIC).first())
        assertNull(subject.getModel(CloudProvider.OPENAI).first())
    }

    @Test
    fun `given a slot the provider does not use when read then it is null`() = runTest {
        assertNull(manager().getApiKey(CloudProvider.OLLAMA).first())
        assertNull(manager().getBaseUrl(CloudProvider.OPENAI).first())
    }

    @Test
    fun `given keys, models and an address when backed up then only the keys are secrets`() = runTest {
        // Given
        val subject = manager()
        subject.setApiKey(CloudProvider.OPENAI, "sk-test-123")
        subject.setModel(CloudProvider.OPENAI, "gpt-5.4")
        subject.setBaseUrl(CloudProvider.OLLAMA, "http://192.168.1.5:11434")
        subject.setModel(CloudProvider.OLLAMA, "llama3")
        subject.setOllamaContextWindowSize(8192)

        // When
        val always = subject.alwaysEntries()
        val secrets = subject.secretEntries()

        // Then — the store's own entry names, so a restore writes back exactly what was there.
        assertEquals(PREFS_NAME, subject.storeName)
        assertEquals(
            mapOf(
                "openai_model" to "gpt-5.4",
                "ollama_base_url" to "http://192.168.1.5:11434",
                "ollama_model" to "llama3",
                OLLAMA_CONTEXT to "8192",
            ),
            always,
        )
        assertEquals(mapOf(OPENAI_KEY to "sk-test-123"), secrets)
    }

    @Test
    fun `given entries the Keystore cannot decrypt for now when backed up then they are left out and kept`() = runTest {
        // Given — a transient Keystore failure must not cost the user a key because a backup ran.
        manager().setApiKey(CloudProvider.OPENAI, "sk-test-123")
        manager().setModel(CloudProvider.OPENAI, "gpt-5.4")
        cipher.failDecrypt = true

        // When
        val subject = manager()
        val always = subject.alwaysEntries()
        val secrets = subject.secretEntries()

        // Then
        assertEquals(emptyMap<String, String>(), always + secrets)
        assertTrue(prefs.values.containsKey(OPENAI_KEY))
        assertTrue(prefs.values.containsKey("openai_model"))
    }

    @Test
    fun `given nothing stored when backed up then both sets are empty`() = runTest {
        assertEquals(emptyMap<String, String>(), manager().alwaysEntries())
        assertEquals(emptyMap<String, String>(), manager().secretEntries())
    }

    @Test
    fun `given entries a backup carried when restored then each is written and readers see it`() = runTest {
        // Given — this phone already has a key for another provider, and an observer of the restored one.
        val subject = manager()
        subject.setApiKey(CloudProvider.ANTHROPIC, "sk-ant-kept")
        val observed = subject.getApiKey(CloudProvider.OPENAI)

        // When
        val written = subject.restore(
            mapOf(
                OPENAI_KEY to "sk-restored",
                "openai_model" to "gpt-5.4",
                "ollama_context" to "8192",
                "not_an_entry_of_this_store" to "x",
            ),
            secrets = true,
        )

        // Then
        assertEquals(3, written)
        assertEquals("sk-restored", observed.first())
        assertEquals("gpt-5.4", manager().getModel(CloudProvider.OPENAI).first())
        assertEquals(8192, manager().getOllamaContextWindowSize().first())
        assertEquals("sk-ant-kept", manager().getApiKey(CloudProvider.ANTHROPIC).first())
        assertFalse(prefs.values.containsKey("not_an_entry_of_this_store"))
    }

    @Test
    fun `given provider settings that carry a key when restored without secrets then the key is not taken`() = runTest {
        // Given — the user declined the secrets; this phone keeps its own key.
        val subject = manager()
        subject.setApiKey(CloudProvider.OPENAI, "sk-phone")

        // When
        val written = subject.restore(
            mapOf(OPENAI_KEY to "sk-archive", "openai_model" to "gpt-5.4"),
            secrets = false,
        )

        // Then
        assertEquals(1, written)
        assertEquals("sk-phone", manager().getApiKey(CloudProvider.OPENAI).first())
        assertEquals("gpt-5.4", manager().getModel(CloudProvider.OPENAI).first())
    }
}
