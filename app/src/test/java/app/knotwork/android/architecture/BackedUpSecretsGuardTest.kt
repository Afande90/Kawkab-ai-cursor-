package app.knotwork.android.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every owner of entries in a Keystore-backed store tells the backup what it holds.
 *
 * A file that builds a `KeystoreBackedPrefsStore` or takes a `SecretStore` owns entries of an encrypted
 * store. Each must implement `BackedUpSecrets` and be bound into the backup's set in `BackupModule`, or
 * be named below with the reason it is not — otherwise a new store's entries would silently be left out
 * of every backup, or a new owner would be forgotten by restore.
 */
class BackedUpSecretsGuardTest {

    @Test
    fun `given every file that owns encrypted-store entries when read then it is backed up or excused`() {
        // Given
        val owners = ProductionSources.code.filterValues { code -> OWNS_ENTRIES.containsMatchIn(code) }.keys
            .map { it.substringAfterLast('/') }
            .toSet()
        val module = ProductionSources.code.entries.single { it.key.endsWith("/di/BackupModule.kt") }.value

        // Then
        assertEquals(
            "a file that owns encrypted-store entries is neither backed up nor excused",
            emptySet<String>(),
            owners - EXCUSED.keys - BACKED_UP,
        )
        assertEquals(
            "an excused or backed-up file no longer owns entries",
            emptySet<String>(),
            (EXCUSED.keys + BACKED_UP) - owners,
        )
        BACKED_UP.forEach { file ->
            val code = ProductionSources.code.entries.single { it.key.endsWith("/$file") }.value
            val type = file.removeSuffix(".kt")
            assertTrue(
                "$type implements BackedUpSecrets",
                Regex("""class $type\b[^{]*\bBackedUpSecrets\b""").containsMatchIn(code),
            )
            assertTrue(
                "$type is bound into the backup's set",
                Regex("""@IntoSet\s+abstract fun \w+\(impl: $type\)""").containsMatchIn(module),
            )
        }
    }

    private companion object {
        /** Building a Keystore-backed store, or taking one — as a store or as the interface — as a dependency. */
        val OWNS_ENTRIES = Regex("""KeystoreBackedPrefsStore\(|:\s*(?:SecretStore|KeystoreBackedPrefsStore)\b""")

        /** The owners a backup reads. */
        val BACKED_UP = setOf("ApiKeyManager.kt", "GenerationSettingsStore.kt", "ToolSettingsStore.kt")

        /** Files that touch a store without owning entries a backup should carry, and why. */
        val EXCUSED = mapOf(
            "EncryptedDbPassphraseProvider.kt" to "the database key never leaves the phone; a restore rekeys the copy",
            "AppModule.kt" to "provides the settings-secrets store; its entries belong to the stores that use it",
            "KeystoreBackedPrefsStore.kt" to "the store itself",
            "RestoreStagedEntries.kt" to "a staged restore's own copy of an archive's entries, destroyed once applied",
        )
    }
}
