package app.knotwork.android.data.backup

import app.knotwork.android.domain.backup.BackupFormat
import app.knotwork.android.domain.backup.BackupFormatException
import app.knotwork.android.domain.backup.BackupHeader
import app.knotwork.android.domain.backup.BackupKdfParams
import app.knotwork.android.domain.backup.BackupReadFailure
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Writes and reads the open header of a backup archive (`docs/backup-format.md` § Header).
 *
 * Layout, big-endian:
 * - **Prefix**, the same in every format version: the [BackupFormat.MAGIC] bytes, `u16` format version,
 *   `u16` length + version name of the writing build, `i32` body length. A reader of an older format
 *   still learns which build wrote a newer file. The name is shown before anything is authenticated, so
 *   it must look like a version ([isVersionName]); a newer file's name that does not is withheld.
 * - **Body** of format 1: `u8` KDF id, `i32` memory KiB, `i32` passes, `u8` lanes, `u8` salt length +
 *   salt, `i32` chunk size, `i64` content bytes, `i32` schema version, `i64` creation time,
 *   `i32` version code, `u16` length + UTF-8 flavour. Bytes after these are ignored: a later writer of
 *   format 1 may append fields, and the MAC covers them.
 * - **MAC**: HMAC-SHA256 under the header key over prefix and body, [MAC_LENGTH] bytes.
 */
internal object BackupHeaderCodec {

    /** Length of the header's authentication code, in bytes. */
    const val MAC_LENGTH: Int = 32

    private const val MAX_VERSION_NAME_BYTES = 256
    private const val MAX_VERSION_NAME_LENGTH = 32
    private const val MAX_FLAVOR_BYTES = 64
    private const val MAX_BODY_LENGTH = 4096
    private const val HMAC = "HmacSHA256"
    private const val DIGEST = "SHA-256"

    /**
     * Encodes [header] and appends its authentication code under [headerKey].
     *
     * @param header The header to write.
     * @param headerKey The header key derived from the password.
     * @return Prefix, body and MAC — the first bytes of the archive.
     */
    fun encode(header: BackupHeader, headerKey: ByteArray): ByteArray {
        val versionName = header.appVersionName.toByteArray(Charsets.UTF_8)
        val flavor = header.flavor.toByteArray(Charsets.UTF_8)
        require(isVersionName(header.appVersionName)) { "version name must look like a version" }
        require(flavor.size <= MAX_FLAVOR_BYTES) { "flavor too long" }
        require(header.salt.size == BackupFormat.SALT_LENGTH) { "salt must be ${BackupFormat.SALT_LENGTH} bytes" }
        require(header.kdfParams.isAcceptable()) { "key costs a reader would refuse" }
        require(header.chunkSize in BackupFormat.MIN_CHUNK_SIZE..BackupFormat.MAX_CHUNK_SIZE) { "chunk size" }
        require(header.contentBytes >= 0) { "content bytes" }
        val body = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).run {
                writeByte(BackupFormat.KDF_ARGON2ID)
                writeInt(header.kdfParams.memoryKiB)
                writeInt(header.kdfParams.iterations)
                writeByte(header.kdfParams.parallelism)
                writeByte(header.salt.size)
                write(header.salt)
                writeInt(header.chunkSize)
                writeLong(header.contentBytes)
                writeInt(header.schemaVersion)
                writeLong(header.createdAtEpochMillis)
                writeInt(header.appVersionCode)
                writeShort(flavor.size)
                write(flavor)
            }
        }.toByteArray()
        val authenticated = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).run {
                write(BackupFormat.MAGIC.toByteArray(Charsets.US_ASCII))
                writeShort(header.formatVersion)
                writeShort(versionName.size)
                write(versionName)
                writeInt(body.size)
                write(body)
            }
        }.toByteArray()
        return authenticated + mac(headerKey, authenticated)
    }

    /**
     * Reads the header from the start of [source], leaving [source] at the first chunk.
     *
     * @param source The archive, positioned at its first byte.
     * @return The parsed header and the bytes its MAC is checked over.
     * @throws BackupFormatException [BackupReadFailure.NOT_A_BACKUP] when the magic is missing,
     *   [BackupReadFailure.UNSUPPORTED_FORMAT_VERSION] for a newer format (with the writer's version
     *   name), [BackupReadFailure.TOO_DEMANDING] for KDF costs out of bounds,
     *   [BackupReadFailure.TRUNCATED] when the file ends inside the header, and
     *   [BackupReadFailure.DAMAGED] for any other field out of range.
     */
    fun read(source: InputStream): ParsedBackupHeader {
        val magic = BackupFormat.MAGIC.toByteArray(Charsets.US_ASCII)
        val found = source.readNBytes(magic.size)
        if (!found.contentEquals(magic)) {
            throw BackupFormatException(BackupReadFailure.NOT_A_BACKUP, "no archive magic")
        }
        val input = DataInputStream(source)
        return try {
            readAfterMagic(input, magic)
        } catch (e: EOFException) {
            throw BackupFormatException(
                BackupReadFailure.TRUNCATED,
                "file ends inside the header: ${e.javaClass.simpleName}",
            )
        }
    }

    private fun readAfterMagic(input: DataInputStream, magic: ByteArray): ParsedBackupHeader {
        val formatVersion = input.readUnsignedShort()
        val versionName = input.readBytes(input.readUnsignedShort(), MAX_VERSION_NAME_BYTES)
        val writerVersionName = String(versionName, Charsets.UTF_8)
        if (formatVersion > BackupFormat.FORMAT_VERSION) {
            throw BackupFormatException(
                BackupReadFailure.UNSUPPORTED_FORMAT_VERSION,
                "format $formatVersion is newer than ${BackupFormat.FORMAT_VERSION}",
                writerVersionName = writerVersionName.takeIf(::isVersionName),
            )
        }
        if (formatVersion < 1) damaged("format version $formatVersion")
        if (!isVersionName(writerVersionName)) damaged("writer version name is not a version")
        val bodyLength = input.readInt()
        if (bodyLength !in 1..MAX_BODY_LENGTH) damaged("body length $bodyLength")
        val body = ByteArray(bodyLength).also { input.readFully(it) }
        val mac = ByteArray(MAC_LENGTH).also { input.readFully(it) }
        val authenticated = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).run {
                write(magic)
                writeShort(formatVersion)
                writeShort(versionName.size)
                write(versionName)
                writeInt(bodyLength)
                write(body)
            }
        }.toByteArray()
        val header = try {
            parseBody(DataInputStream(ByteArrayInputStream(body)), formatVersion, writerVersionName)
        } catch (e: EOFException) {
            damaged("body ends early: ${e.javaClass.simpleName}")
        }
        return ParsedBackupHeader(header, authenticated, mac)
    }

    private fun parseBody(body: DataInputStream, formatVersion: Int, versionName: String): BackupHeader {
        val kdf = body.readUnsignedByte()
        if (kdf != BackupFormat.KDF_ARGON2ID) damaged("unknown KDF $kdf")
        val params = BackupKdfParams(
            memoryKiB = body.readInt(),
            iterations = body.readInt(),
            parallelism = body.readUnsignedByte(),
        )
        if (!params.isAcceptable()) {
            throw BackupFormatException(BackupReadFailure.TOO_DEMANDING, "KDF parameters out of bounds: $params")
        }
        val saltLength = body.readUnsignedByte()
        if (saltLength != BackupFormat.SALT_LENGTH) damaged("salt length $saltLength")
        val salt = ByteArray(saltLength).also { body.readFully(it) }
        val chunkSize = body.readInt()
        if (chunkSize !in BackupFormat.MIN_CHUNK_SIZE..BackupFormat.MAX_CHUNK_SIZE) damaged("chunk size $chunkSize")
        val contentBytes = body.readLong()
        if (contentBytes < 0) damaged("content bytes $contentBytes")
        val schemaVersion = body.readInt()
        val createdAt = body.readLong()
        val versionCode = body.readInt()
        val flavor = String(body.readBytes(body.readUnsignedShort(), MAX_FLAVOR_BYTES), Charsets.UTF_8)
        return BackupHeader(
            formatVersion = formatVersion,
            appVersionName = versionName,
            appVersionCode = versionCode,
            flavor = flavor,
            schemaVersion = schemaVersion,
            createdAtEpochMillis = createdAt,
            contentBytes = contentBytes,
            kdfParams = params,
            salt = salt,
            chunkSize = chunkSize,
        )
    }

    /**
     * Whether [name] looks like a version name and may be shown before the header is authenticated:
     * 1 to [MAX_VERSION_NAME_LENGTH] characters of ASCII letters, digits, `.`, `+` and `-`.
     *
     * @param name The candidate.
     * @return `true` when [name] may be shown.
     */
    fun isVersionName(name: String): Boolean = name.length in 1..MAX_VERSION_NAME_LENGTH &&
        name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '+' || it == '-' }

    private fun DataInputStream.readBytes(length: Int, max: Int): ByteArray {
        if (length > max) damaged("field of $length bytes, at most $max")
        return ByteArray(length).also { readFully(it) }
    }

    private fun damaged(reason: String): Nothing = throw BackupFormatException(BackupReadFailure.DAMAGED, reason)

    /**
     * HMAC-SHA256 of [data] under [key].
     *
     * @param key The header key.
     * @param data The bytes to authenticate.
     * @return The [MAC_LENGTH]-byte code.
     */
    fun mac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance(HMAC).run {
        init(SecretKeySpec(key, HMAC))
        doFinal(data)
    }

    /**
     * SHA-256 of [data] — the header digest every chunk's associated data starts with.
     *
     * @param data The authenticated header bytes.
     * @return The 32-byte digest.
     */
    fun digest(data: ByteArray): ByteArray = MessageDigest.getInstance(DIGEST).digest(data)
}

/**
 * A header as read from an archive, before and after its authentication code is checked.
 *
 * @property header The parsed fields. Only [BackupHeader.appVersionName] and
 *   [BackupHeader.createdAtEpochMillis] may be shown before [verify] succeeds.
 * @property authenticatedBytes The prefix and body exactly as they appear in the file.
 * @property mac The authentication code the file carries.
 */
internal class ParsedBackupHeader(val header: BackupHeader, val authenticatedBytes: ByteArray, val mac: ByteArray) {

    /**
     * Checks the header's authentication code under [headerKey] in constant time.
     *
     * @param headerKey The header key derived from the password the user entered.
     * @throws BackupFormatException With [BackupReadFailure.WRONG_PASSWORD] when the code does not match:
     *   the password is wrong, or the header was altered.
     */
    @Throws(IOException::class)
    fun verify(headerKey: ByteArray) {
        val expected = BackupHeaderCodec.mac(headerKey, authenticatedBytes)
        if (!MessageDigest.isEqual(expected, mac)) {
            throw BackupFormatException(BackupReadFailure.WRONG_PASSWORD, "header MAC does not match")
        }
    }
}
