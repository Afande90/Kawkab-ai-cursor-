package app.kawkab.android.data.backup

import app.kawkab.android.domain.backup.BackupFormat
import app.kawkab.android.domain.backup.BackupFormatException
import app.kawkab.android.domain.backup.BackupKdfParams
import app.kawkab.android.domain.backup.BackupKeyDerivation
import app.kawkab.android.domain.backup.BackupKeys
import app.kawkab.android.domain.backup.BackupReadFailure
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.CharBuffer
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BackupKeyDerivation] on Bouncy Castle's Argon2id (RFC 9106, version 0x13).
 *
 * The password is normalised to Unicode NFC and encoded as UTF-8; one derivation of
 * `2 · KEY_LENGTH` bytes is split into the content key (first half) and the header key (second half).
 * Before allocating, the derivation checks that the heap can spare the memory the parameters ask
 * for and a quarter more, and refuses with [BackupReadFailure.TOO_DEMANDING] instead of running out of
 * memory on a header written for a larger device.
 *
 * @param availableHeapBytes Reports how many bytes the heap can still grow by; injectable for tests.
 */
@Singleton
class Argon2idBackupKeyDerivation internal constructor(private val availableHeapBytes: () -> Long) :
    BackupKeyDerivation {

    /** Creates the derivation over this process's heap. */
    @Inject
    constructor() : this({
        val runtime = Runtime.getRuntime()
        runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
    })

    override fun deriveKeys(password: CharArray, salt: ByteArray, params: BackupKdfParams): BackupKeys {
        val blocks = params.memoryKiB.toLong() * BYTES_PER_KIB
        val needed = blocks + blocks / HEAP_HEADROOM_DIVISOR
        if (needed > availableHeapBytes()) {
            throw BackupFormatException(
                BackupReadFailure.TOO_DEMANDING,
                "Argon2id needs ${params.memoryKiB} KiB; the heap cannot spare it",
            )
        }
        val encoded = Normalizer.normalize(CharBuffer.wrap(password), Normalizer.Form.NFC)
            .toByteArray(Charsets.UTF_8)
        val output = try {
            argon2id(encoded, salt, params, outputLength = 2 * BackupFormat.KEY_LENGTH)
        } finally {
            encoded.fill(0)
        }
        val keys = BackupKeys(
            contentKey = output.copyOfRange(0, BackupFormat.KEY_LENGTH),
            headerKey = output.copyOfRange(BackupFormat.KEY_LENGTH, 2 * BackupFormat.KEY_LENGTH),
        )
        output.fill(0)
        return keys
    }

    private companion object {
        const val BYTES_PER_KIB = 1024L

        /**
         * A quarter on top of the block memory, an estimate: the generator holds one 1 KiB `long[]` per
         * block, each with its object headers, and the app's own objects share the heap.
         */
        const val HEAP_HEADROOM_DIVISOR = 4L
    }
}

/**
 * Raw Argon2id over Bouncy Castle, with RFC 9106's optional secret and associated data — the shape
 * the RFC's test vectors are stated in.
 *
 * @param password The password bytes.
 * @param salt The salt.
 * @param params Memory, passes and lanes.
 * @param outputLength The number of bytes to derive.
 * @param secret RFC 9106's secret value `K`, or `null` for none (the archive uses none).
 * @param additional RFC 9106's associated data `X`, or `null` for none (the archive uses none).
 * @return [outputLength] derived bytes.
 */
internal fun argon2id(
    password: ByteArray,
    salt: ByteArray,
    params: BackupKdfParams,
    outputLength: Int,
    secret: ByteArray? = null,
    additional: ByteArray? = null,
): ByteArray {
    val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withMemoryAsKB(params.memoryKiB)
        .withIterations(params.iterations)
        .withParallelism(params.parallelism)
        .withSalt(salt)
    secret?.let { builder.withSecret(it) }
    additional?.let { builder.withAdditional(it) }
    val generator = Argon2BytesGenerator()
    generator.init(builder.build())
    val output = ByteArray(outputLength)
    generator.generateBytes(password, output)
    return output
}
