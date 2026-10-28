package app.kawkab.android.data.backup

import app.kawkab.android.domain.backup.BackupFormat
import app.kawkab.android.domain.backup.BackupHeader
import app.kawkab.android.domain.backup.BackupKdfParams
import app.kawkab.android.domain.backup.BackupKeys
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.random.Random

/**
 * Builders shared by the archive tests: fixed keys and headers, an archive of given content, and the
 * byte ranges of its chunk frames — so a test can cut, swap or alter one frame exactly.
 */
internal object BackupTestArchives {

    /** The smallest chunk size a reader accepts; tests use it to get many chunks from little content. */
    const val SMALL_CHUNK: Int = BackupFormat.MIN_CHUNK_SIZE

    /**
     * Keys for tests that do not exercise the derivation.
     *
     * @param seed Varies the keys.
     * @return Distinct content and header keys.
     */
    fun keys(seed: Int = 1): BackupKeys = Random(seed).let {
        BackupKeys(
            contentKey = it.nextBytes(BackupFormat.KEY_LENGTH),
            headerKey = it.nextBytes(BackupFormat.KEY_LENGTH),
        )
    }

    /**
     * A header for tests.
     *
     * @param chunkSize The chunk size.
     * @param saltSeed Varies the salt, and so the header digest every chunk is bound to.
     * @return A format-1 header.
     */
    fun header(chunkSize: Int = SMALL_CHUNK, saltSeed: Int = 1): BackupHeader = BackupHeader(
        formatVersion = BackupFormat.FORMAT_VERSION,
        appVersionName = "0.12.0",
        appVersionCode = 17,
        flavor = "full",
        schemaVersion = 70,
        createdAtEpochMillis = 0L,
        contentBytes = 0L,
        kdfParams = BackupKdfParams.DEFAULT,
        salt = Random(saltSeed).nextBytes(BackupFormat.SALT_LENGTH),
        chunkSize = chunkSize,
    )

    /**
     * Encrypts [content] into a complete archive (or one closed without [BackupEncryptingOutputStream.finish]).
     *
     * @param content The plaintext.
     * @param keys The keys.
     * @param header The header.
     * @param finish `false` to close without writing the chunk flagged as last.
     * @return The archive's bytes.
     */
    fun encrypt(
        content: ByteArray,
        keys: BackupKeys = keys(),
        header: BackupHeader = header(),
        finish: Boolean = true,
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        BackupEncryptingOutputStream(bytes, header, keys).use { stream ->
            stream.write(content)
            if (finish) stream.finish()
        }
        return bytes.toByteArray()
    }

    /**
     * Reads a whole archive back.
     *
     * @param archive The archive's bytes.
     * @param keys The keys to read it with.
     * @return The plaintext.
     */
    fun decrypt(archive: ByteArray, keys: BackupKeys = keys()): ByteArray {
        val input = ByteArrayInputStream(archive)
        val parsed = BackupHeaderCodec.read(input)
        return BackupDecryptingInputStream(input, parsed, keys).use { it.readBytes() }
    }

    /**
     * The byte ranges of an archive's chunk frames, in order.
     *
     * @param archive A well-formed archive.
     * @return One range per frame: flags byte, length and sealed chunk.
     */
    fun frames(archive: ByteArray): List<IntRange> {
        val input = ByteArrayInputStream(archive)
        BackupHeaderCodec.read(input)
        var offset = archive.size - input.available()
        val frames = mutableListOf<IntRange>()
        while (offset < archive.size) {
            val length = ByteBuffer.wrap(archive, offset + 1, Int.SIZE_BYTES).int
            val end = offset + 1 + Int.SIZE_BYTES + length
            frames += offset until end
            offset = end
        }
        return frames
    }

    /**
     * Deterministic pseudo-random content.
     *
     * @param size Its length.
     * @param seed Varies it.
     * @return [size] bytes.
     */
    fun content(size: Int, seed: Int = 7): ByteArray = Random(seed).nextBytes(size)
}
