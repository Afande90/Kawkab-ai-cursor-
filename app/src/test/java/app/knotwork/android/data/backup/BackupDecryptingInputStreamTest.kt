package app.knotwork.android.data.backup

import app.knotwork.android.data.backup.BackupTestArchives.SMALL_CHUNK
import app.knotwork.android.data.backup.BackupTestArchives.content
import app.knotwork.android.data.backup.BackupTestArchives.decrypt
import app.knotwork.android.data.backup.BackupTestArchives.encrypt
import app.knotwork.android.data.backup.BackupTestArchives.frames
import app.knotwork.android.data.backup.BackupTestArchives.header
import app.knotwork.android.data.backup.BackupTestArchives.keys
import app.knotwork.android.domain.backup.BackupFormatException
import app.knotwork.android.domain.backup.BackupKeys
import app.knotwork.android.domain.backup.BackupReadFailure
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * [BackupDecryptingInputStream] against [BackupEncryptingOutputStream]: content of every size around the
 * chunk boundary survives the round trip, and every way of altering the file — a changed byte, chunks
 * swapped, a chunk from another archive, a relabelled last chunk, a cut at any point, bytes appended,
 * another key — is reported with its own reason and hands out no content it did not authenticate.
 */
class BackupDecryptingInputStreamTest {

    @Test
    fun `given content of every size around the chunk boundary when encrypted and read then it is unchanged`() {
        // Given
        val sizes = listOf(0, 1, SMALL_CHUNK - 1, SMALL_CHUNK, SMALL_CHUNK + 1, 3 * SMALL_CHUNK, 3 * SMALL_CHUNK + 5)

        // Then
        sizes.forEach { size ->
            val content = content(size)
            assertArrayEquals("size $size", content, decrypt(encrypt(content)))
        }
    }

    @Test
    fun `given content of whole chunks when encrypted then the last full chunk is the one flagged last`() {
        // When
        val archive = encrypt(content(2 * SMALL_CHUNK))

        // Then — two frames, no empty trailing one.
        assertEquals(2, frames(archive).size)
        assertEquals(BackupChunkFrame.FLAG_FINAL, archive[frames(archive).last().first].toInt())
    }

    @Test
    fun `given another key when read then the password is wrong`() {
        // Given
        val archive = encrypt(content(10))

        // Then
        assertFailure(BackupReadFailure.WRONG_PASSWORD, archive, keys(seed = 2))
    }

    @Test
    fun `given one byte changed in any chunk when read then the archive is damaged`() {
        // Given
        val archive = encrypt(content(3 * SMALL_CHUNK + 5))

        // Then — the IV, the middle of the ciphertext and the tag of every frame.
        frames(archive).forEach { frame ->
            listOf(frame.first + 5, (frame.first + frame.last) / 2, frame.last).forEach { offset ->
                val altered = archive.copyOf().also { it[offset] = (it[offset] + 1).toByte() }
                assertFailure(BackupReadFailure.DAMAGED, altered)
            }
        }
    }

    @Test
    fun `given two chunks swapped when read then the archive is damaged`() {
        // Given
        val archive = encrypt(content(3 * SMALL_CHUNK + 5))
        val (first, second) = frames(archive)
        val swapped = archive.copyOfRange(0, first.first) +
            archive.copyOfRange(second.first, second.last + 1) +
            archive.copyOfRange(first.first, first.last + 1) +
            archive.copyOfRange(second.last + 1, archive.size)

        // Then
        assertFailure(BackupReadFailure.DAMAGED, swapped)
    }

    @Test
    fun `given a chunk taken from another archive under the same keys when read then the archive is damaged`() {
        // Given — same keys and position, different salt: each chunk is bound to its own header.
        val archive = encrypt(content(2 * SMALL_CHUNK + 1))
        val other = encrypt(content(2 * SMALL_CHUNK + 1), header = header(saltSeed = 2))
        val frame = frames(archive)[1]
        val otherFrame = frames(other)[1]
        val spliced = archive.copyOfRange(0, frame.first) +
            other.copyOfRange(otherFrame.first, otherFrame.last + 1) +
            archive.copyOfRange(frame.last + 1, archive.size)

        // Then
        assertFailure(BackupReadFailure.DAMAGED, spliced)
    }

    @Test
    fun `given a chunk relabelled as the last one when read then the archive is damaged`() {
        // Given
        val archive = encrypt(content(2 * SMALL_CHUNK + 1))
        val relabelled = archive.copyOfRange(0, frames(archive)[1].last + 1)
            .also { it[frames(archive)[1].first] = BackupChunkFrame.FLAG_FINAL.toByte() }

        // Then
        assertFailure(BackupReadFailure.DAMAGED, relabelled)
    }

    @Test
    fun `given unknown flag bits when read then the archive is damaged`() {
        // Given
        val archive = encrypt(content(10))
        val flagged = archive.copyOf().also { it[frames(archive).single().first] = 0x03 }

        // Then
        assertFailure(BackupReadFailure.DAMAGED, flagged)
    }

    @Test
    fun `given the file cut at a chunk boundary or inside a chunk when read then it is truncated`() {
        // Given
        val archive = encrypt(content(3 * SMALL_CHUNK + 5))
        val frames = frames(archive)
        val cuts = listOf(
            frames.first().first,
            frames[1].first,
            frames.last().first,
            frames[1].first + 3,
            frames[1].first + 100,
            archive.size - 1,
        )

        // Then
        cuts.forEach { cut -> assertFailure(BackupReadFailure.TRUNCATED, archive.copyOf(cut)) }
    }

    @Test
    fun `given an archive closed without finishing when read then it is truncated`() {
        // Given
        val archive = encrypt(content(3 * SMALL_CHUNK + 5), finish = false)

        // Then
        assertFailure(BackupReadFailure.TRUNCATED, archive)
    }

    @Test
    fun `given bytes after the last chunk when read then the archive is damaged`() {
        // Given
        val archive = encrypt(content(10)) + byteArrayOf(0)

        // Then
        assertFailure(BackupReadFailure.DAMAGED, archive)
    }

    @Test
    fun `given an impossible chunk length when read then the archive is damaged`() {
        // Given — the length field of the only frame, raised past one chunk plus its overhead.
        val archive = encrypt(content(10))
        val frame = frames(archive).single()
        val altered = archive.copyOf().also {
            it[frame.first + 1] = 0x7F
        }

        // Then
        assertFailure(BackupReadFailure.DAMAGED, altered)
    }

    @Test
    fun `given a damaged second chunk when read then the first chunk's content is handed out and nothing after it`() {
        // Given
        val content = content(2 * SMALL_CHUNK + 1)
        val archive = encrypt(content)
        val offset = frames(archive)[1].last
        val altered = archive.copyOf().also { it[offset] = (it[offset] + 1).toByte() }
        val input = ByteArrayInputStream(altered)
        val stream = BackupDecryptingInputStream(input, BackupHeaderCodec.read(input), keys())

        // When
        val first = ByteArray(SMALL_CHUNK).also { buffer ->
            var read = 0
            while (read < buffer.size) read += stream.read(buffer, read, buffer.size - read)
        }
        val failure = assertThrows(BackupFormatException::class.java) { stream.read() }

        // Then
        assertArrayEquals(content.copyOf(SMALL_CHUNK), first)
        assertEquals(BackupReadFailure.DAMAGED, failure.failure)
    }

    @Test
    fun `given a reader that stops early when read to the end then the cut at the end is still found`() {
        // Given
        val archive = encrypt(content(2 * SMALL_CHUNK + 1))
        val cut = archive.copyOf(frames(archive).last().first)
        val input = ByteArrayInputStream(cut)
        val stream = BackupDecryptingInputStream(input, BackupHeaderCodec.read(input), keys())
        stream.read()

        // When
        val failure = assertThrows(BackupFormatException::class.java) { stream.readToEnd() }

        // Then
        assertEquals(BackupReadFailure.TRUNCATED, failure.failure)
    }

    private fun assertFailure(expected: BackupReadFailure, archive: ByteArray, keys: BackupKeys = keys()) {
        val failure = assertThrows(BackupFormatException::class.java) { decrypt(archive, keys) }
        assertEquals(failure.message, expected, failure.failure)
    }
}
