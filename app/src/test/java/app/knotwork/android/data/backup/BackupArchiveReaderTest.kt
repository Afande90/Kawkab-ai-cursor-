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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
            readAll(BackupTestArchives.encrypt(rawZip(listOf(manifest, "files/a.txt").raw()))).map { it.first },
        )
        lists.forEach { names ->
            assertFailure(BackupReadFailure.DAMAGED) { readAll(BackupTestArchives.encrypt(rawZip(names.raw()))) }
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

    @Test
    fun `given the last chunk missing after a long central directory when every entry is read then it is truncated`() {
        // Given — 150 one-byte entries: their data sits in early chunks, the central directory runs on past a
        // chunk boundary, so ZIP reading stops in a chunk that is not the last.
        val names = listOf(BackupFormat.MANIFEST_ENTRY) + (1..150).map { "files/entry-%03d.txt".format(it) }
        val complete = write { writer -> names.forEach { name -> writer.addEntry(name, true) { it.write(1) } } }
        val frames = BackupTestArchives.frames(complete)
        val cut = complete.copyOf(frames.last().first)
        val input = ByteArrayInputStream(cut)
        val read = mutableListOf<String>()

        // When
        val failure = assertThrows(BackupFormatException::class.java) {
            BackupArchiveReader(input, BackupHeaderCodec.read(input), keys()).use { reader ->
                while (true) {
                    val entry = reader.nextEntry() ?: break
                    entry.content.readBytes()
                    read += entry.name
                }
            }
        }

        // Then — every entry came out; only the check at the end could fail.
        assertEquals(names, read)
        assertEquals(BackupReadFailure.TRUNCATED, failure.failure)
    }

    @Test
    fun `given authenticated content that ends inside an entry when read then the archive is damaged`() {
        // Given
        val zip = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry(BackupFormat.MANIFEST_ENTRY))
                zip.write(1)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("files/a.bin"))
                zip.write(content(5_000))
                zip.closeEntry()
            }
        }.toByteArray()

        // Then
        assertFailure(BackupReadFailure.DAMAGED) { readAll(BackupTestArchives.encrypt(zip.copyOf(zip.size / 2))) }
    }

    @Test
    fun `given an entry name that is not UTF-8 when read then the archive is damaged`() {
        // Given
        val zip = rawZip(
            listOf(
                RawEntry(BackupFormat.MANIFEST_ENTRY.toByteArray()),
                RawEntry(byteArrayOf('a'.code.toByte(), 0xC3.toByte(), 0x28)),
            ),
        )

        // Then
        assertFailure(BackupReadFailure.DAMAGED) { readAll(BackupTestArchives.encrypt(zip)) }
    }

    @Test
    fun `given an entry whose checksum does not match when read or skipped then the archive is damaged`() {
        // Given — a stored entry with three bytes and a CRC of zero.
        val zip = rawZip(
            listOf(
                RawEntry(BackupFormat.MANIFEST_ENTRY.toByteArray()),
                RawEntry("files/a.txt".toByteArray(), data = "abc".toByteArray()),
            ),
        )

        // Then
        assertFailure(BackupReadFailure.DAMAGED) { readAll(BackupTestArchives.encrypt(zip)) }
        assertFailure(BackupReadFailure.DAMAGED) {
            val input = ByteArrayInputStream(BackupTestArchives.encrypt(zip))
            BackupArchiveReader(input, BackupHeaderCodec.read(input), keys()).use { reader ->
                reader.nextEntry()
                requireNotNull(reader.nextEntry()).content.skip(10)
            }
        }
    }

    @Test
    fun `given a name that is both a file and a directory when written or read then it is refused`() {
        // Given
        val manifest = BackupFormat.MANIFEST_ENTRY
        val writer = BackupArchiveWriter(ByteArrayOutputStream(), header(), keys())
        writer.addEntry(manifest, true) { }
        writer.addEntry("files/a", true) { }

        // Then
        assertThrows(IllegalArgumentException::class.java) { writer.addEntry("files/a/b", true) { } }
        listOf(listOf(manifest, "files/a", "files/a/b"), listOf(manifest, "files/a/b", "files/a")).forEach { names ->
            val zip = rawZip(names.map { RawEntry(it.toByteArray()) })
            assertFailure(BackupReadFailure.DAMAGED) { readAll(BackupTestArchives.encrypt(zip)) }
        }
    }

    @Test
    fun `given an entry's content when read after the next entry was opened then it refuses`() {
        // Given
        val archive = write { writer ->
            writer.addEntry(BackupFormat.MANIFEST_ENTRY, true) { it.write(1) }
            writer.addEntry("files/b.txt", true) { it.write(2) }
        }
        val input = ByteArrayInputStream(archive)

        // Then
        BackupArchiveReader(input, BackupHeaderCodec.read(input), keys()).use { reader ->
            val manifest = requireNotNull(reader.nextEntry())
            reader.nextEntry()
            assertThrows(IllegalStateException::class.java) { manifest.content.read() }
        }
    }

    private fun write(block: (BackupArchiveWriter) -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        BackupArchiveWriter(bytes, header(), keys()).use { writer ->
            block(writer)
            writer.finish()
        }
        return bytes.toByteArray()
    }

    /** One entry of [rawZip]: its name's bytes as written, and stored data with a CRC of zero. */
    private class RawEntry(val name: ByteArray, val data: ByteArray = ByteArray(0))

    /**
     * A ZIP stream of stored entries written byte by byte, with every CRC zero: `java.util.zip` refuses to
     * write a repeated name, a name that is not UTF-8 or a wrong checksum, and a reader must still refuse
     * them when it is handed one.
     */
    private fun rawZip(entries: List<RawEntry>): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        entries.forEach { entry ->
            out.writeInt(Integer.reverseBytes(LOCAL_HEADER_SIGNATURE))
            out.write(ByteArray(LOCAL_HEADER_BEFORE_SIZES))
            out.writeInt(Integer.reverseBytes(entry.data.size))
            out.writeInt(Integer.reverseBytes(entry.data.size))
            out.writeShort(java.lang.Short.reverseBytes(entry.name.size.toShort()).toInt())
            out.writeShort(0)
            out.write(entry.name)
            out.write(entry.data)
        }
        out.writeInt(Integer.reverseBytes(END_SIGNATURE))
        out.write(ByteArray(END_RECORD_FIXED_FIELDS))
        return bytes.toByteArray()
    }

    private fun List<String>.raw(): List<RawEntry> = map { RawEntry(it.toByteArray(Charsets.UTF_8)) }

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

        /** Version, flags, method (0: stored), time, date and CRC — all zero. */
        const val LOCAL_HEADER_BEFORE_SIZES = 14

        /** The end record's fields after its signature, all zero. */
        const val END_RECORD_FIXED_FIELDS = 18
    }
}
