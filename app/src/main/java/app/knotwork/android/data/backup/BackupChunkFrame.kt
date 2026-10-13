package app.knotwork.android.data.backup

import java.nio.ByteBuffer
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * The frame every encrypted chunk of an archive travels in (`docs/backup-format.md` § Chunks).
 *
 * A frame is `u8` flags, `i32` sealed length, then the sealed chunk in [app.knotwork.android.data.local.crypto.AesGcmCodec]'s
 * layout: a 12-byte IV, the ciphertext and the 16-byte tag. Bit [FLAG_FINAL] marks the last chunk;
 * every other bit is zero. The associated data of chunk `i` is the SHA-256 of the authenticated
 * header bytes, `i` as `i64` and the flags byte — so a chunk moved to another position, copied from
 * another archive or relabelled as the last one fails authentication.
 */
internal object BackupChunkFrame {

    /** The flags bit that marks the last chunk. */
    const val FLAG_FINAL: Int = 0x01

    /** The bytes AES-GCM adds to a chunk: the 12-byte IV and the 16-byte tag. */
    const val SEAL_OVERHEAD: Int = 28

    private const val HEADER_DIGEST_LENGTH = 32
    private const val INDEX_LENGTH = Long.SIZE_BYTES
    private const val FLAGS_LENGTH = 1
    private const val AES = "AES"

    /**
     * The associated data of one chunk.
     *
     * @param headerDigest SHA-256 of the archive's authenticated header bytes.
     * @param index The chunk's position, from 0.
     * @param flags The chunk's flags byte.
     * @return `headerDigest ‖ index ‖ flags`.
     */
    fun associatedData(headerDigest: ByteArray, index: Long, flags: Int): ByteArray =
        ByteBuffer.allocate(HEADER_DIGEST_LENGTH + INDEX_LENGTH + FLAGS_LENGTH)
            .put(headerDigest)
            .putLong(index)
            .put(flags.toByte())
            .array()

    /**
     * Wraps the raw content key for the JCA cipher.
     *
     * @param contentKey The 32-byte content key.
     * @return An AES key over the same bytes.
     */
    fun aesKey(contentKey: ByteArray): SecretKey = SecretKeySpec(contentKey, AES)
}
