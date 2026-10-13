package app.knotwork.android.data.backup

import app.knotwork.android.data.backup.BackupTestArchives.SMALL_CHUNK
import app.knotwork.android.data.backup.BackupTestArchives.content
import app.knotwork.android.data.backup.BackupTestArchives.encrypt
import app.knotwork.android.data.backup.BackupTestArchives.frames
import app.knotwork.android.data.backup.BackupTestArchives.header
import app.knotwork.android.data.backup.BackupTestArchives.keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * [BackupEncryptingOutputStream]: the archive opens with the header it was given, every chunk but the
 * last holds exactly one chunk of content, only [BackupEncryptingOutputStream.finish] flags a chunk as
 * last, and nothing can be written after it.
 */
class BackupEncryptingOutputStreamTest {

    @Test
    fun `given an archive when written then it opens with the header and the key's authentication code`() {
        // Given
        val header = header()

        // When
        val archive = encrypt(content(10), header = header)
        val parsed = BackupHeaderCodec.read(ByteArrayInputStream(archive))
        parsed.verify(keys().headerKey)

        // Then
        assertEquals(header.chunkSize, parsed.header.chunkSize)
        assertTrue(parsed.header.salt.contentEquals(header.salt))
    }

    @Test
    fun `given several chunks when written then every chunk but the last is full and only the last is flagged`() {
        // When
        val archive = encrypt(content(3 * SMALL_CHUNK + 5))
        val frames = frames(archive)

        // Then
        assertEquals(4, frames.size)
        frames.dropLast(1).forEach { frame ->
            assertEquals(0, archive[frame.first].toInt())
            assertEquals(SMALL_CHUNK + BackupChunkFrame.SEAL_OVERHEAD, frame.last - frame.first + 1 - 5)
        }
        assertEquals(BackupChunkFrame.FLAG_FINAL, archive[frames.last().first].toInt())
        assertEquals(5 + BackupChunkFrame.SEAL_OVERHEAD, frames.last().last - frames.last().first + 1 - 5)
    }

    @Test
    fun `given an archive closed without finishing when written then no chunk is flagged last`() {
        // When
        val archive = encrypt(content(3 * SMALL_CHUNK + 5), finish = false)

        // Then
        frames(archive).forEach { assertEquals(0, archive[it.first].toInt()) }
    }

    @Test
    fun `given a finished archive when written to or finished again then it refuses`() {
        // Given
        val stream = BackupEncryptingOutputStream(ByteArrayOutputStream(), header(), keys())
        stream.finish()

        // Then
        assertThrows(IllegalStateException::class.java) { stream.write(1) }
        assertThrows(IllegalStateException::class.java) { stream.finish() }
    }

    @Test
    fun `given bounds outside the buffer when written then it refuses`() {
        // Given
        val stream = BackupEncryptingOutputStream(ByteArrayOutputStream(), header(), keys())

        // Then
        assertThrows(IndexOutOfBoundsException::class.java) { stream.write(ByteArray(4), 0, -1) }
        assertThrows(IndexOutOfBoundsException::class.java) { stream.write(ByteArray(4), 3, 2) }
    }
}
