package app.kawkab.android.data.backup

import app.kawkab.android.data.local.crypto.AesGcmCodec
import app.kawkab.android.domain.backup.BackupHeader
import app.kawkab.android.domain.backup.BackupKeys
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Objects

/**
 * Writes an archive: the authenticated header first, then everything written to this stream as
 * AES-256-GCM chunks of [BackupHeader.chunkSize] plaintext bytes in [BackupChunkFrame]s.
 *
 * A full chunk is held back until more content arrives, so the last chunk may be full. Only
 * [finish] writes the chunk flagged as last: closing without it leaves a file every reader reports as
 * truncated, which is what a cancelled or failed export must leave behind if anything is left at all.
 * Closing closes [sink].
 *
 * @param sink Where the archive goes — the document the user picked.
 * @param header The header to write; its [BackupHeader.chunkSize] sets the chunking.
 * @param keys The keys derived for this archive's salt. The stream does not wipe them.
 */
internal class BackupEncryptingOutputStream(sink: OutputStream, header: BackupHeader, keys: BackupKeys) :
    OutputStream() {

    private val out = DataOutputStream(sink)
    private val key = BackupChunkFrame.aesKey(keys.contentKey)
    private val headerDigest: ByteArray
    private val buffer = ByteArray(header.chunkSize)
    private var filled = 0
    private var index = 0L
    private var finished = false

    init {
        val headerBytes = BackupHeaderCodec.encode(header, keys.headerKey)
        out.write(headerBytes)
        headerDigest =
            BackupHeaderCodec.digest(headerBytes.copyOfRange(0, headerBytes.size - BackupHeaderCodec.MAC_LENGTH))
    }

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        Objects.checkFromIndexSize(off, len, b.size)
        check(!finished) { "archive already finished" }
        var offset = off
        var remaining = len
        while (remaining > 0) {
            if (filled == buffer.size) writeChunk(final = false)
            val count = minOf(remaining, buffer.size - filled)
            System.arraycopy(b, offset, buffer, filled, count)
            filled += count
            offset += count
            remaining -= count
        }
    }

    /**
     * Writes the held-back content as the last chunk and flushes. Nothing may be written after it.
     *
     * @throws IOException When the sink fails.
     */
    fun finish() {
        check(!finished) { "archive already finished" }
        writeChunk(final = true)
        finished = true
        out.flush()
    }

    override fun flush() {
        out.flush()
    }

    override fun close() {
        out.close()
    }

    private fun writeChunk(final: Boolean) {
        val flags = if (final) BackupChunkFrame.FLAG_FINAL else 0
        val sealed = AesGcmCodec.encrypt(
            key,
            BackupChunkFrame.associatedData(headerDigest, index, flags),
            buffer.copyOf(filled),
        )
        out.writeByte(flags)
        out.writeInt(sealed.size)
        out.write(sealed)
        filled = 0
        index++
    }
}
