package app.kawkab.android.data.backup

import app.kawkab.android.domain.backup.BackupFormat
import app.kawkab.android.domain.backup.BackupHeader
import app.kawkab.android.domain.backup.BackupKdfParams
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.random.Random

/**
 * Reads the committed format-1 archive `src/test/resources/backup/format-1.knotwork` — the regression
 * anchor of the public format (`docs/backup-format.md`). Runs in both flavours' suites: the format does
 * not depend on the flavour that wrote it.
 *
 * The archive was written by this file's generator with the writer's own Argon2id costs, the password
 * [PASSWORD] and the smallest chunk size, so its few kilobytes span many chunks. Its content is
 * synthetic: what an export puts in an archive is described in the format document. The test checks
 * every header field and every entry's name, order, size and SHA-256 — so a reader change that would
 * stop opening a file written by an earlier build fails here.
 *
 * `-PrecordBackupFixture` writes the file anew. That is allowed only before a release ships format 1;
 * after it, a new format version adds its own fixture beside this one and this one never changes.
 */
class BackupFormatV1FixtureTest {

    @Test
    fun `given the committed format-1 archive when read with its password then every field and entry is as written`() {
        // Given
        if (System.getProperty(RECORD_PROPERTY) == "true") record()
        val archive = requireNotNull(javaClass.getResourceAsStream(RESOURCE)) {
            "fixture missing"
        }.use { it.readBytes() }
        val input = ByteArrayInputStream(archive)

        // When
        val parsed = BackupHeaderCodec.read(input)
        val keys = Argon2idBackupKeyDerivation().deriveKeys(
            PASSWORD.toCharArray(),
            parsed.header.salt,
            parsed.header.kdfParams,
        )
        val entries = BackupArchiveReader(input, parsed, keys).use { reader ->
            generateSequence { reader.nextEntry() }
                .map { entry -> entry.content.readBytes().let { Triple(entry.name, it.size, sha256(it)) } }
                .toList()
        }

        // Then
        with(parsed.header) {
            assertEquals(1, formatVersion)
            assertEquals("0.12.0", appVersionName)
            assertEquals(17, appVersionCode)
            assertEquals("full", flavor)
            assertEquals(70, schemaVersion)
            assertEquals(CREATED_AT, createdAtEpochMillis)
            assertEquals(EXPECTED.sumOf { it.second.toLong() }, contentBytes)
            assertEquals(BackupKdfParams.DEFAULT, kdfParams)
            assertEquals(BackupFormat.MIN_CHUNK_SIZE, chunkSize)
        }
        assertEquals(EXPECTED, entries)
    }

    private fun record() {
        val content = fixtureContent()
        val header = BackupHeader(
            formatVersion = BackupFormat.FORMAT_VERSION,
            appVersionName = "0.12.0",
            appVersionCode = 17,
            flavor = "full",
            schemaVersion = 70,
            createdAtEpochMillis = CREATED_AT,
            contentBytes = content.sumOf { it.third.size.toLong() },
            kdfParams = BackupKdfParams.DEFAULT,
            salt = ByteArray(BackupFormat.SALT_LENGTH).also { SecureRandom().nextBytes(it) },
            chunkSize = BackupFormat.MIN_CHUNK_SIZE,
        )
        val keys = Argon2idBackupKeyDerivation().deriveKeys(PASSWORD.toCharArray(), header.salt, header.kdfParams)
        val target = File(RECORD_PATH).also { it.parentFile?.mkdirs() }
        target.outputStream().use { sink ->
            BackupArchiveWriter(sink, header, keys).use { writer ->
                content.forEach { (name, compress, bytes) -> writer.addEntry(name, compress) { it.write(bytes) } }
                writer.finish()
            }
        }
        content.forEach { (name, _, bytes) -> println("FIXTURE $name ${bytes.size} ${sha256(bytes)}") }
    }

    private companion object {
        const val PASSWORD = "correct horse battery staple"
        const val RESOURCE = "/backup/format-1.knotwork"
        const val RECORD_PATH = "src/test/resources/backup/format-1.knotwork"
        const val RECORD_PROPERTY = "knotwork.backupFixture.record"

        /** 2026-10-13T00:00:00Z. */
        const val CREATED_AT = 1_791_849_600_000L

        /** Name, size and SHA-256 of every entry, in order. */
        val EXPECTED: List<Triple<String, Int, String>> = listOf(
            Triple(
                BackupFormat.MANIFEST_ENTRY,
                111,
                "6ec46186efa907ad18d439dfac9748298554670918157ab28e0b60d2f1fa6494",
            ),
            Triple(
                "database/agent_database.db",
                10_000,
                "94d1c4dee5e7d0a4aee88c94488183c2bc77e4ff7464399f95a0b2a65f819658",
            ),
            Triple(
                "files/agent_workspace/notes/d\u00eda de campo.md",
                1_480,
                "98ca56ce70eeb7decea674700df929a0fbdf2452cc8b92ea64d580a96b013227",
            ),
            Triple(
                "files/attachments/3f2a9c.jpg",
                3_000,
                "d30182d720a93259000d4afdebb992b2f3707a6230f6a63b9c674b50573e96c0",
            ),
        )

        fun fixtureContent(): List<Triple<String, Boolean, ByteArray>> = listOf(
            Triple(
                BackupFormat.MANIFEST_ENTRY,
                true,
                (
                    "{\"fixture\":\"format 1\",\"note\":\"Synthetic content. What an export writes is described " +
                        "in docs/backup-format.md.\"}"
                    ).toByteArray(Charsets.UTF_8),
            ),
            Triple("database/agent_database.db", false, Random(46).nextBytes(10_000)),
            Triple(
                "files/agent_workspace/notes/d\u00eda de campo.md",
                true,
                "# D\u00eda de campo\n\nCaf\u00e9, \u0447\u0430\u0439, tea.\n".repeat(40).toByteArray(Charsets.UTF_8),
            ),
            Triple("files/attachments/3f2a9c.jpg", false, Random(47).nextBytes(3_000)),
        )

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
