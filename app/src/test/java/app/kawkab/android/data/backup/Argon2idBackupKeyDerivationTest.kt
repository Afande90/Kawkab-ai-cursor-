package app.kawkab.android.data.backup

import app.kawkab.android.domain.backup.BackupFormat
import app.kawkab.android.domain.backup.BackupFormatException
import app.kawkab.android.domain.backup.BackupKdfParams
import app.kawkab.android.domain.backup.BackupReadFailure
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Argon2idBackupKeyDerivation] and the raw [argon2id] it runs on: the RFC 9106 test vector, the
 * split into two keys, Unicode normalisation of the password, and the refusal of parameters the heap
 * cannot spare.
 */
class Argon2idBackupKeyDerivationTest {

    private val fast = BackupKdfParams(memoryKiB = 64, iterations = 1, parallelism = 1)
    private val salt = ByteArray(BackupFormat.SALT_LENGTH) { it.toByte() }
    private val derivation = Argon2idBackupKeyDerivation { Long.MAX_VALUE }

    @Test
    fun `given the RFC 9106 Argon2id inputs when derived then the tag is the RFC's`() {
        // Given — RFC 9106 §5.3.
        val params = BackupKdfParams(memoryKiB = 32, iterations = 3, parallelism = 4)

        // When
        val tag = argon2id(
            password = ByteArray(32) { 0x01 },
            salt = ByteArray(16) { 0x02 },
            params = params,
            outputLength = 32,
            secret = ByteArray(8) { 0x03 },
            additional = ByteArray(12) { 0x04 },
        )

        // Then
        assertEquals(
            "0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659",
            tag.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun `given a password when derived then the keys are the two halves of one derivation`() {
        // Given
        val password = "correct horse battery staple"

        // When
        val keys = derivation.deriveKeys(password.toCharArray(), salt, fast)
        val raw = argon2id(password.toByteArray(Charsets.UTF_8), salt, fast, outputLength = 2 * BackupFormat.KEY_LENGTH)

        // Then
        assertArrayEquals(raw.copyOfRange(0, BackupFormat.KEY_LENGTH), keys.contentKey)
        assertArrayEquals(raw.copyOfRange(BackupFormat.KEY_LENGTH, raw.size), keys.headerKey)
        assertFalse(keys.contentKey.contentEquals(keys.headerKey))
    }

    @Test
    fun `given the same password composed and decomposed when derived then both give the keys of its NFC bytes`() {
        // Given — "é" as one code point, then as "e" plus a combining acute accent.
        val composed = "caf\u00e9 au lait, s'il vous pla\u00eet"
        val decomposed = "cafe\u0301 au lait, s'il vous plai\u0302t"

        // When
        val a = derivation.deriveKeys(composed.toCharArray(), salt, fast)
        val b = derivation.deriveKeys(decomposed.toCharArray(), salt, fast)

        // Then — the format names NFC: another reader must arrive at the same bytes.
        val nfc = argon2id(composed.toByteArray(Charsets.UTF_8), salt, fast, outputLength = 2 * BackupFormat.KEY_LENGTH)
        assertArrayEquals(nfc.copyOfRange(0, BackupFormat.KEY_LENGTH), a.contentKey)
        assertArrayEquals(nfc.copyOfRange(0, BackupFormat.KEY_LENGTH), b.contentKey)
        assertArrayEquals(a.headerKey, b.headerKey)
    }

    @Test
    fun `given another salt or another password when derived then the keys differ`() {
        // Given
        val password = "correct horse battery staple".toCharArray()
        val base = derivation.deriveKeys(password, salt, fast)

        // When
        val otherSalt = derivation.deriveKeys(password, salt.reversedArray(), fast)
        val otherPassword = derivation.deriveKeys("correct horse battery stable".toCharArray(), salt, fast)

        // Then
        assertFalse(base.contentKey.contentEquals(otherSalt.contentKey))
        assertFalse(base.contentKey.contentEquals(otherPassword.contentKey))
    }

    @Test
    fun `given parameters the heap cannot spare when derived then it refuses as too demanding`() {
        // Given — the block memory and a quarter more is needed.
        val params = BackupKdfParams(memoryKiB = 1024, iterations = 1, parallelism = 1)
        val needed = 1024L * 1024 * 5 / 4
        val tight = Argon2idBackupKeyDerivation { needed - 1 }
        val enough = Argon2idBackupKeyDerivation { needed }

        // When
        val failure = assertThrows(BackupFormatException::class.java) {
            tight.deriveKeys("correct horse battery staple".toCharArray(), salt, params)
        }
        val keys = enough.deriveKeys("correct horse battery staple".toCharArray(), salt, params)

        // Then
        assertEquals(BackupReadFailure.TOO_DEMANDING, failure.failure)
        assertEquals(BackupFormat.KEY_LENGTH, keys.contentKey.size)
    }

    @Test
    fun `given keys when wiped then both are zeros`() {
        // Given
        val keys = derivation.deriveKeys("correct horse battery staple".toCharArray(), salt, fast)

        // When
        keys.wipe()

        // Then
        assertTrue(keys.contentKey.all { it == 0.toByte() } && keys.headerKey.all { it == 0.toByte() })
    }
}
