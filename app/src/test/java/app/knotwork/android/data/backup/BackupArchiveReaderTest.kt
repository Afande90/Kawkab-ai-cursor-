package app.knotwork.android.data.backup

import app.knotwork.android.data.backup.BackupTestArchives.SMALL_CHUNK
import app.knotwork.android.data.backup.BackupTestArchives.content
import app.knotwork.android.data.backup.BackupTestArchives.header
import app.knotwork.android.data.backup.BackupTestArchives.keys
import app.knotwork.android.domain.backup.BackupFormat
import app.knotwork.android.domain.backup.BackupFormatException
import app.knotwork.android.domain.backup.BackupKeys
import app.knotwork.android.domain.backup.BackupReadFailure
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * [BackupArchiveWriter] and [BackupArchiveReader] together: named entries survive, compressed or stored;
 * the writer refuses a name the reader would refuse; the reader refuses a crafted entry list; and an
 * archive is authenticated to its end before the reader reports the last entry.
 */
class BackupArchiveReaderTest {

    @Test
    fun `given entries when written and read then names, order and bytes survive`() {
        // Given
        val entries = listOf(
            Triple(BackupFormat.MANIFEST_ENTRY, true, "{\"note\":\"text compresses\"}".toByteArray()),
            Triple("database/agent_database.db", false, content(3 * SMALL_CHUNK + 7)),
            Triple("files/agent_workspace/notes/día.md", true, "café ".repeat(500).toByteArray()),
            Triple("files/attachments/empty.jpg", false, ByteArray(0)),
        )

        // When
        val archive = write { writer ->
            entries.forEach { (name, compress, bytes) -> writer.addEntry(name, compress) { it.write(bytes) } }
        }
        val read = readAll(archive)

        // Then
        assertEquals(entries.map { it.first }, read.map { it.first })
        entries.zip(read).forEach { (expected, actual) ->
            assertArrayEquals(expected.first, expected.third, actual.second)
        }
    }

    @Test
    fun `given a bad name, a repeat or a first entry other than the manifest when added then the writer refuses it`() {
        // Given
        val empty = BackupArchiveWriter(ByteArrayOutputStream(), header(), keys())
        val writer = BackupArchiveWriter(ByteArrayOutputStream(), header(), keys())
        writer.addEntry(BackupFormat.MANIFEST_ENTRY, true) { it.write(1) }

        // Then
        assertThrows(IllegalArgumentException::class.java) { empty.addEntry("files/a.txt", true) { } }
        assertThrows(IllegalArgumentException::class.java) { writer.addEntry("../escape", true) { } }
        assertThrows(IllegalArgumentException::class.java) { writer.addEntry(BackupFormat.MANIFEST_ENTRY, true) { } }
    }

    @Test
    fun `given an entry whose content stream is closed when written then the archive goes on`() {
        // When
        val archive = write { writer ->
            writer.addEntry(BackupFormat.MANIFEST_ENTRY, true) { stream -> stream.use { it.write(1) } }
            writer.addEntry("b.txt", true) { it.write(2) }
        }

        // Then
        assertEquals(listOf(BackupFormat.MANIFEST_ENTRY, "b.txt"), readAll(archive).map { it.first })
    }

    @Test
    fun `given the wrong keys when the reader opens then the password is wrong`() {
        // Given
        val archive = write { writer -> writer.addEntry(BackupFormat.MANIFEST_ENTRY, true) { it.write(1) } }

        // Then
        assertFailure(BackupReadFailure.WRONG_PASSWORD) { readAll(archive, keys(seed = 9)) }
    }

    @Test
    fun `given an archive closed without finishing when its entries are read then the end reports it truncated`() {
        // Given
        val bytes = ByteArrayOutputStream()
        BackupArchiveWriter(bytes, header(), keys()).use { writer ->
            writer.addEntry(BackupFormat.MANIFEST_ENTRY, true) { it.write(content(2 * SMALL_CHUNK)) }
        }

        // Then
        assertFailure(BackupReadFailure.TRUNCATED) { readAll(bytes.toByteArray()) }
    }

    @Test
    fun `given crafted entry lists when read then each is damaged`() {
        // Given — written past the writer's checks, straight into the encrypted stream.
        val manifest = BackupFormat.MANIFEST_ENTRY
        val lists = listOf(
            emptyList(),
            listOf("files/a.txt", manifest),
            listOf(manifest, "../escape"),
            listOf(manifest, "files/a.txt", "files/a.txt"),
            listOf(manifest, manifest),
            listOf(manifest, "files/"),
            listOf(manifest, "/absolute"),
        )

        // Then
        // The hand-written list itself is well formed: the same writer, given valid names, reads back.
        assertEquals(
            listOf(manifest, "files/a.txt"),
            readAll(BackupTestArchives.encrypt(rawZip(listOf(manifest, "files/a.txt")))).map { it.first },
        )
        lists.forEach { names ->
            assertFailure(BackupReadFailure.DAMAGED) { readAll(BackupTestArchives.encrypt(rawZip(names))) }
        }
    }

    @Test
    fun `given authenticated content that is not an entry list when read then the archive is damaged`() {
        // Given
        val archive = BackupTestArchives.encrypt(content(100))

        // Then
        val failure = assertThrows(BackupFormatException::class.java) { readAll(archive) }
        assertEquals(BackupReadFailure.DAMAGED, failure.failure)
    }

    private fun write(block: (BackupArchiveWriter) -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        BackupArchiveWriter(bytes, header(), keys()).use { writer ->
            block(writer)
            writer.finish()
        }
        return bytes.toByteArray()
    }

    /**
     * A ZIP stream of empty stored entries written byte by byte: `java.util.zip` refuses to write a
     * repeated name, and a reader must still refuse one it is handed.
     */
    private fun rawZip(names: List<String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        names.forEach { name ->
            val encoded = name.toByteArray(Charsets.UTF_8)
            out.writeInt(Integer.reverseBytes(LOCAL_HEADER_SIGNATURE))
            out.write(ByteArray(LOCAL_HEADER_FIXED_FIELDS))
            out.writeShort(java.lang.Short.reverseBytes(encoded.size.toShort()).toInt())
            out.writeShort(0)
            out.write(encoded)
        }
        out.writeInt(Integer.reverseBytes(END_SIGNATURE))
        out.write(ByteArray(END_RECORD_FIXED_FIELDS))
        return bytes.toByteArray()
    }

    private fun readAll(archive: ByteArray, keys: BackupKeys = keys()): List<Pair<String, ByteArray>> {
        val input = ByteArrayInputStream(archive)
        return BackupArchiveReader(input, BackupHeaderCodec.read(input), keys).use { reader ->
            generateSequence { reader.nextEntry() }
                .map { it.name to it.content.readBytes() }
                .toList()
                .also { assertNull(reader.nextEntry()) }
        }
    }

    private fun assertFailure(expected: BackupReadFailure, block: () -> Unit) {
        val failure = assertThrows(BackupFormatException::class.java) { block() }
        assertEquals(failure.message, expected, failure.failure)
    }

    private companion object {
        const val LOCAL_HEADER_SIGNATURE = 0x04034b50
        const val END_SIGNATURE = 0x06054b50

        /** Version, flags, method, time, date, CRC and both sizes — all zero for an empty stored entry. */
        const val LOCAL_HEADER_FIXED_FIELDS = 22

        /** The end record's fields after its signature, all zero. */
        const val END_RECORD_FIXED_FIELDS = 18
    }
}
