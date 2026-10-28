package app.kawkab.android.data.backup

import app.kawkab.android.data.local.crypto.AesGcmCodec
import app.kawkab.android.domain.backup.BackupFormatException
import app.kawkab.android.domain.backup.BackupKeys
import app.kawkab.android.domain.backup.BackupReadFailure
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.util.Objects

/**
 * Reads the content of an archive whose header has been read, checking the header's authentication
 * code first and every chunk as it arrives.
 *
 * Content comes out only after its chunk has authenticated. The stream ends after the chunk flagged
 * as last, and only once the file is confirmed to end there too; [readToEnd] forces that check for a
 * reader that stops early (a ZIP reader does not read past its central directory). The first failure
 * is final: every later read throws it again, so a caller that retries is never handed what follows a
 * bad chunk.
 *
 * @param source The archive, positioned at its first chunk (just after the header).
 * @param parsed The header read from [source].
 * @param keys The keys derived from the password the user entered.
 * @throws BackupFormatException On construction, with [BackupReadFailure.WRONG_PASSWORD] when the keys
 *   do not authenticate the header; while reading, with [BackupReadFailure.DAMAGED] for a chunk that
 *   does not authenticate, has an impossible length or flags, or is followed by more bytes after the
 *   last one, and [BackupReadFailure.TRUNCATED] when the file ends before the last chunk.
 */
internal class BackupDecryptingInputStream(source: InputStream, parsed: ParsedBackupHeader, keys: BackupKeys) :
    InputStream() {

    private val input = DataInputStream(source)
    private val key = BackupChunkFrame.aesKey(keys.contentKey)
    private val headerDigest = BackupHeaderCodec.digest(parsed.authenticatedBytes)
    private val chunkSize = parsed.header.chunkSize
    private var current = ByteArray(0)
    private var position = 0
    private var index = 0L
    private var ended = false
    private var failure: BackupFormatException? = null

    init {
        parsed.verify(keys.headerKey)
    }

    override fun read(): Int {
        if (!ensureAvailable()) return END
        return current[position++].toInt() and BYTE_MASK
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        Objects.checkFromIndexSize(off, len, b.size)
        if (len == 0) return 0
        if (!ensureAvailable()) return END
        val count = minOf(len, current.size - position)
        System.arraycopy(current, position, b, off, count)
        position += count
        return count
    }

    override fun available(): Int = current.size - position

    /**
     * Reads and discards the rest of the content, so that every remaining chunk is authenticated and
     * the end of the file is checked.
     *
     * @throws BackupFormatException As for reading.
     */
    fun readToEnd() {
        val sink = ByteArray(DRAIN_BUFFER)
        while (read(sink, 0, sink.size) != END) {
            // Reading is the check.
        }
    }

    override fun close() {
        input.close()
    }

    private fun ensureAvailable(): Boolean {
        failure?.let { throw it }
        while (position == current.size) {
            if (ended) return false
            try {
                readChunk()
            } catch (e: BackupFormatException) {
                failure = e
                throw e
            }
        }
        return true
    }

    private fun readChunk() {
        val flags = input.read()
        if (flags == END) truncated("file ends before the last chunk")
        if (flags and BackupChunkFrame.FLAG_FINAL.inv() != 0) damaged("chunk $index has unknown flags")
        val final = flags and BackupChunkFrame.FLAG_FINAL != 0
        val sealed = try {
            val length = input.readInt()
            if (flags == 0 && length == 0) {
                // Zeros where a chunk should start: a copy that reserved the file's length and stopped.
                truncated("file is zero-filled from chunk $index")
            }
            if (length !in BackupChunkFrame.SEAL_OVERHEAD..chunkSize + BackupChunkFrame.SEAL_OVERHEAD) {
                damaged("chunk $index has length $length")
            }
            ByteArray(length).also { input.readFully(it) }
        } catch (e: EOFException) {
            truncated("file ends inside chunk $index: ${e.javaClass.simpleName}")
        }
        val plain = try {
            AesGcmCodec.decrypt(key, BackupChunkFrame.associatedData(headerDigest, index, flags), sealed)
        } catch (e: GeneralSecurityException) {
            damaged("chunk $index does not authenticate: ${e.javaClass.simpleName}")
        }
        if (!final && plain.size != chunkSize) damaged("chunk $index is short but not the last")
        if (final && input.read() != END) damaged("bytes follow the last chunk")
        current = plain
        position = 0
        index++
        ended = final
    }

    private fun damaged(reason: String): Nothing = throw BackupFormatException(BackupReadFailure.DAMAGED, reason)

    private fun truncated(reason: String): Nothing = throw BackupFormatException(BackupReadFailure.TRUNCATED, reason)

    private companion object {
        const val END = -1
        const val BYTE_MASK = 0xFF
        const val DRAIN_BUFFER = 64 * 1024
    }
}
