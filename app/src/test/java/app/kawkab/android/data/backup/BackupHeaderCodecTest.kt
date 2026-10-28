package app.kawkab.android.data.backup

import app.kawkab.android.domain.backup.BackupFormat
import app.kawkab.android.domain.backup.BackupFormatException
import app.kawkab.android.domain.backup.BackupHeader
import app.kawkab.android.domain.backup.BackupKdfParams
import app.kawkab.android.domain.backup.BackupReadFailure
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

/**
 * [BackupHeaderCodec]: the header survives a round trip, its MAC tells a wrong key, and every field a
 * reader cannot trust before the password is bounded — each failure with its own reason.
 */
class BackupHeaderCodecTest {

    private val headerKey = ByteArray(BackupFormat.KEY_LENGTH) { 7 }

    @Test
    fun `given a header when encoded and read back then every field and the stream position survive`() {
        // Given
        val header = sampleHeader()
        val bytes = BackupHeaderCodec.encode(header, headerKey) + byteArrayOf(42)
        val input = ByteArrayInputStream(bytes)

        // When
        val parsed = BackupHeaderCodec.read(input)
        parsed.verify(headerKey)

        // Then
        with(parsed.header) {
            assertEquals(BackupFormat.FORMAT_VERSION, formatVersion)
            assertEquals("0.12.0", appVersionName)
            assertEquals(17, appVersionCode)
            assertEquals("foss", flavor)
            assertEquals(71, schemaVersion)
            assertEquals(1_791_849_600_000L, createdAtEpochMillis)
            assertEquals(123_456_789_012L, contentBytes)
            assertEquals(BackupKdfParams.DEFAULT, kdfParams)
            assertArrayEquals(header.salt, salt)
            assertEquals(BackupFormat.CHUNK_SIZE, chunkSize)
        }
        assertEquals("the reader stops at the first chunk", 42, input.read())
    }

    @Test
    fun `given another key when the header is verified then the password is wrong`() {
        // Given
        val parsed = BackupHeaderCodec.read(ByteArrayInputStream(BackupHeaderCodec.encode(sampleHeader(), headerKey)))

        // When
        val failure = assertThrows(BackupFormatException::class.java) { parsed.verify(ByteArray(32) { 8 }) }

        // Then
        assertEquals(BackupReadFailure.WRONG_PASSWORD, failure.failure)
    }

    @Test
    fun `given a header altered after it was written when verified then it reads as a wrong password`() {
        // Given — the schema version's last byte, inside the authenticated body.
        val bytes = BackupHeaderCodec.encode(sampleHeader(), headerKey)
        val schemaOffset = bytes.size - BackupHeaderCodec.MAC_LENGTH - 2 - 4 - 4 - 8 - 1
        bytes[schemaOffset] = (bytes[schemaOffset] + 1).toByte()
        val parsed = BackupHeaderCodec.read(ByteArrayInputStream(bytes))

        // When
        val failure = assertThrows(BackupFormatException::class.java) { parsed.verify(headerKey) }

        // Then
        assertEquals(BackupReadFailure.WRONG_PASSWORD, failure.failure)
        assertEquals(72, parsed.header.schemaVersion)
    }

    @Test
    fun `given files that do not start with the magic when read then they are not backups`() {
        // Given
        val files = listOf(ByteArray(0), "KNOT".toByteArray(), "PK\u0003\u0004 a zip file".toByteArray())

        // Then
        files.forEach { assertFailure(BackupReadFailure.NOT_A_BACKUP, it) }
    }

    @Test
    fun `given a newer format version when read then it is unsupported and names the writer`() {
        // Given
        val bytes = prefix(formatVersion = BackupFormat.FORMAT_VERSION + 1, versionName = "0.14.0") + ByteArray(64)

        // When
        val failure = assertThrows(BackupFormatException::class.java) {
            BackupHeaderCodec.read(ByteArrayInputStream(bytes))
        }

        // Then
        assertEquals(BackupReadFailure.UNSUPPORTED_FORMAT_VERSION, failure.failure)
        assertEquals("0.14.0", failure.writerVersionName)
    }

    @Test
    fun `given key-derivation costs out of bounds when read then the header is too demanding`() {
        // Given — written past the writer's own check.
        val greedy = body().also { ByteBuffer.wrap(it).putInt(1, BackupKdfParams.MAX_MEMORY_KIB * 2) }
        val bytes = prefix(BackupFormat.FORMAT_VERSION, "0.12.0", bodyLength = greedy.size) + greedy + ByteArray(32)

        // Then
        assertFailure(BackupReadFailure.TOO_DEMANDING, bytes)
    }

    @Test
    fun `given a file that ends inside the header when read then it is truncated`() {
        // Given
        val bytes = BackupHeaderCodec.encode(sampleHeader(), headerKey)

        // Then — cut after the magic, inside the body, inside the MAC.
        listOf(BackupFormat.MAGIC.length + 1, bytes.size / 2, bytes.size - 1).forEach { length ->
            assertFailure(BackupReadFailure.TRUNCATED, bytes.copyOf(length))
        }
    }

    @Test
    fun `given fields out of range when read then the header is damaged`() {
        // Given
        val bodies = listOf(
            body(kdfId = 2),
            body(saltLength = 8),
            body(chunkSize = BackupFormat.MIN_CHUNK_SIZE - 1),
            body(chunkSize = BackupFormat.MAX_CHUNK_SIZE + 1),
            body(contentBytes = -1),
            body().copyOf(10),
        )

        // Then
        bodies.forEach { body ->
            val bytes = prefix(BackupFormat.FORMAT_VERSION, "0.12.0", bodyLength = body.size) + body +
                ByteArray(BackupHeaderCodec.MAC_LENGTH)
            assertFailure(BackupReadFailure.DAMAGED, bytes)
        }
        assertFailure(BackupReadFailure.DAMAGED, prefix(BackupFormat.FORMAT_VERSION, "0.12.0", bodyLength = 0))
        assertFailure(BackupReadFailure.DAMAGED, prefix(BackupFormat.FORMAT_VERSION, "0.12.0", bodyLength = 1 shl 20))
        assertFailure(BackupReadFailure.DAMAGED, prefix(0, "0.12.0", bodyLength = 4))
    }

    @Test
    fun `given a body with fields a later writer appended when read then they are ignored and covered by the MAC`() {
        // Given
        val body = body() + byteArrayOf(1, 2, 3)
        val authenticated = prefix(BackupFormat.FORMAT_VERSION, "0.12.0", bodyLength = body.size) + body
        val bytes = authenticated + BackupHeaderCodec.mac(headerKey, authenticated)

        // When
        val parsed = BackupHeaderCodec.read(ByteArrayInputStream(bytes))
        parsed.verify(headerKey)

        // Then
        assertEquals(70, parsed.header.schemaVersion)
    }

    @Test
    fun `given a newer format whose writer name is not a version when read then no name is offered for display`() {
        // Given — the name is read before anything is authenticated, and would be shown to the user.
        val names = listOf("0.14.0\nInstall the update from example.com", "\u202e0.41.0", "", "1".repeat(33))

        // Then
        names.forEach { name ->
            val failure = assertThrows(BackupFormatException::class.java) {
                BackupHeaderCodec.read(
                    ByteArrayInputStream(prefix(BackupFormat.FORMAT_VERSION + 1, name) + ByteArray(8)),
                )
            }
            assertEquals(BackupReadFailure.UNSUPPORTED_FORMAT_VERSION, failure.failure)
            assertNull(name, failure.writerVersionName)
        }
    }

    @Test
    fun `given a writer name or flavour longer than its bound when read then the header is damaged`() {
        // Given
        val longName = prefix(BackupFormat.FORMAT_VERSION, "0".repeat(257), bodyLength = 4)
        val flavor = "f".repeat(65).toByteArray()
        val body = body().let { it.copyOf(it.size - 6) } + byteArrayOf(0, 65) + flavor

        // Then
        assertFailure(BackupReadFailure.DAMAGED, longName)
        assertFailure(
            BackupReadFailure.DAMAGED,
            prefix(BackupFormat.FORMAT_VERSION, "0.12.0", bodyLength = body.size) + body + ByteArray(32),
        )
    }

    @Test
    fun `given a header a reader would refuse when encoded then the writer refuses it`() {
        // Given
        val refused = listOf(
            sampleHeader(kdf = BackupKdfParams(BackupKdfParams.MAX_MEMORY_KIB + 1, 3, 1)),
            sampleHeader(chunkSize = BackupFormat.MAX_CHUNK_SIZE + 1),
            sampleHeader(contentBytes = -1),
        )

        // Then
        refused.forEach { header ->
            assertThrows(IllegalArgumentException::class.java) { BackupHeaderCodec.encode(header, headerKey) }
        }
    }

    private fun assertFailure(expected: BackupReadFailure, bytes: ByteArray) {
        val failure = assertThrows(BackupFormatException::class.java) {
            BackupHeaderCodec.read(ByteArrayInputStream(bytes))
        }
        assertEquals("${bytes.size} bytes", expected, failure.failure)
    }

    private fun sampleHeader(
        kdf: BackupKdfParams = BackupKdfParams.DEFAULT,
        chunkSize: Int = BackupFormat.CHUNK_SIZE,
        contentBytes: Long = 123_456_789_012L,
    ) = BackupHeader(
        formatVersion = BackupFormat.FORMAT_VERSION,
        appVersionName = "0.12.0",
        appVersionCode = 17,
        flavor = "foss",
        schemaVersion = 71,
        createdAtEpochMillis = 1_791_849_600_000L,
        contentBytes = contentBytes,
        kdfParams = kdf,
        salt = ByteArray(BackupFormat.SALT_LENGTH) { (it * 3).toByte() },
        chunkSize = chunkSize,
    )

    private fun prefix(formatVersion: Int, versionName: String, bodyLength: Int? = null): ByteArray {
        val name = versionName.toByteArray(Charsets.UTF_8)
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).run {
                write(BackupFormat.MAGIC.toByteArray(Charsets.US_ASCII))
                writeShort(formatVersion)
                writeShort(name.size)
                write(name)
                bodyLength?.let { writeInt(it) }
            }
        }.toByteArray()
    }

    private fun body(
        kdfId: Int = BackupFormat.KDF_ARGON2ID,
        saltLength: Int = BackupFormat.SALT_LENGTH,
        chunkSize: Int = BackupFormat.CHUNK_SIZE,
        contentBytes: Long = 1_000L,
    ): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).run {
            writeByte(kdfId)
            writeInt(BackupKdfParams.DEFAULT.memoryKiB)
            writeInt(BackupKdfParams.DEFAULT.iterations)
            writeByte(BackupKdfParams.DEFAULT.parallelism)
            writeByte(saltLength)
            write(ByteArray(saltLength))
            writeInt(chunkSize)
            writeLong(contentBytes)
            writeInt(70)
            writeLong(0L)
            writeInt(17)
            writeShort(4)
            write("full".toByteArray())
        }
    }.toByteArray()
}
