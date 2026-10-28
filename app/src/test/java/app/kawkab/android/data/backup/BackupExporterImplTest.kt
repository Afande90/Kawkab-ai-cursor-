package app.kawkab.android.data.backup

import android.database.sqlite.SQLiteFullException
import android.system.ErrnoException
import android.system.OsConstants
import app.kawkab.android.domain.backup.BackupCounts
import app.kawkab.android.domain.backup.BackupExportException
import app.kawkab.android.domain.backup.BackupExportFailure
import app.kawkab.android.domain.backup.BackupFormat
import app.kawkab.android.domain.backup.BackupFormatException
import app.kawkab.android.domain.backup.BackupKdfParams
import app.kawkab.android.domain.backup.BackupManifest
import app.kawkab.android.domain.backup.BackupManifestJson
import app.kawkab.android.domain.backup.BackupProgress
import app.kawkab.android.domain.backup.BackupReadFailure
import app.kawkab.android.domain.backup.BackupStep
import app.kawkab.android.domain.models.DbPassphraseUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.GeneralSecurityException
import kotlin.random.Random

/**
 * [BackupExporterImpl] end to end, against a scripted content source and the real format: the archive
 * opens with the password and holds every entry in order, secrets only when asked; progress runs through
 * the three steps to the total; cancelled, the file reads as incomplete; a failing document and failing
 * data are told apart (no space among them); the database snapshot never outlives the export.
 */
@RunWith(RobolectricTestRunner::class)
class BackupExporterImplTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val password = "correct horse battery staple"
    private val fastKdf = BackupKdfParams(memoryKiB = 64, iterations = 1, parallelism = 1)
    private val snapshotDir by lazy { temp.newFolder("cache-backup") }
    private val files by lazy {
        val workspace = temp.newFolder("agent_workspace").also { File(it, "notes.md").writeText("x".repeat(9_000)) }
        val attachments = temp.newFolder("attachments").also {
            File(it, "1.jpg").writeBytes(Random(3).nextBytes(5_000))
        }
        BackupFileSource({ workspace }, { attachments })
    }
    private val snapshotBytes = Random(5).nextBytes(10_000)

    @Test
    fun `given a complete export when the archive is read with the password then every entry is there in order`() =
        runTest {
            // When
            val out = ByteArrayOutputStream()
            val manifest = exporter(this.testScheduler).export(out, password.toCharArray(), includeSecrets = false) { }

            // Then
            val entries = read(out.toByteArray())
            assertEquals(
                listOf(
                    BackupFormat.MANIFEST_ENTRY,
                    BackupFormat.DATABASE_KEY_ENTRY,
                    BackupFormat.PREFERENCES_ENTRY,
                    BackupFormat.PROVIDERS_ENTRY,
                    BackupFormat.DATABASE_ENTRY,
                    "files/agent_workspace/notes.md",
                    "files/attachments/1.jpg",
                ),
                entries.keys.toList(),
            )
            assertArrayEquals(snapshotBytes, entries.getValue(BackupFormat.DATABASE_ENTRY))
            assertEquals(manifest, BackupManifestJson.decode(String(entries.getValue(BackupFormat.MANIFEST_ENTRY))))
            assertEquals(BackupFormat.KEY_LENGTH, entries.getValue(BackupFormat.DATABASE_KEY_ENTRY).size)
            assertTrue("the snapshot does not outlive the export", snapshotDir.listFiles().isNullOrEmpty())
        }

    @Test
    fun `given secrets asked for when exported then the secrets entry is there, and only then`() = runTest {
        // When
        val with = ByteArrayOutputStream().also {
            exporter(testScheduler).export(it, password.toCharArray(), includeSecrets = true) { }
        }
        val without = ByteArrayOutputStream().also {
            exporter(testScheduler).export(it, password.toCharArray(), includeSecrets = false) { }
        }

        // Then
        assertEquals(SECRETS, String(read(with.toByteArray()).getValue(BackupFormat.SECRETS_ENTRY)))
        assertFalse(BackupFormat.SECRETS_ENTRY in read(without.toByteArray()))
    }

    @Test
    fun `given an export when it runs then progress goes through the three steps to the total`() = runTest {
        // Given
        val progress = mutableListOf<BackupProgress>()

        // When
        exporter(testScheduler).export(ByteArrayOutputStream(), password.toCharArray(), false) { progress += it }

        // Then
        assertEquals(listOf(BackupStep.KEY, BackupStep.DATABASE, BackupStep.FILES), progress.map { it.step }.distinct())
        val last = progress.last()
        assertEquals(last.bytesTotal, last.bytesDone)
        assertTrue(progress.zipWithNext().all { (a, b) -> b.bytesDone >= a.bytesDone })
    }

    @Test
    fun `given the export cancelled while files are written when the file is read then it is incomplete`() = runTest {
        // Given
        val out = ByteArrayOutputStream()
        var ended: Throwable? = null

        // When — the user cancels as soon as the files step starts.
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            val scope = this
            exporter(testScheduler).export(out, password.toCharArray(), false) { progress ->
                if (progress.step == BackupStep.FILES) scope.cancel(CancellationException("user cancelled"))
            }
        }
        job.invokeOnCompletion { ended = it }
        job.join()

        // Then
        assertTrue(ended is CancellationException)
        val failure = assertThrows(BackupFormatException::class.java) { read(out.toByteArray()) }
        assertEquals(BackupReadFailure.TRUNCATED, failure.failure)
        assertTrue("the snapshot does not outlive the export", snapshotDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `given the document refusing bytes, or out of space, when exported then the failure says which`() = runTest {
        // Given
        val refusing = FailingStream(IOException("provider gone"))
        val full = FailingStream(IOException("write", ErrnoException("write", OsConstants.ENOSPC)))

        // When
        val refused = exportFailure { exporter(testScheduler).export(refusing, password.toCharArray(), false) { } }
        val noSpace = exportFailure { exporter(testScheduler).export(full, password.toCharArray(), false) { } }

        // Then
        assertEquals(BackupExportFailure.WRITE_FAILED, refused.failure)
        assertEquals(BackupExportFailure.NO_SPACE, noSpace.failure)
        assertTrue("the space needed is known by then", noSpace.neededBytes > 0)
        assertTrue(refusing.closed && full.closed)
        assertTrue(snapshotDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `given the app's data failing to read when exported then the failure is a read failure`() = runTest {
        // Given
        val broken = object : BackupContentSource {
            override suspend fun collect(includeSecrets: Boolean, snapshotFile: File, createdAtEpochMillis: Long) =
                throw IOException("disk error")
        }
        val out = FailingStream(null)

        // When
        val failure = exportFailure { exporter(testScheduler, broken).export(out, password.toCharArray(), false) { } }

        // Then
        assertEquals(BackupExportFailure.READ_FAILED, failure.failure)
        assertTrue(out.closed)
    }

    @Test
    fun `given a caller already cancelled when exported then the document is still closed`() = runTest {
        // Given
        val out = FailingStream(null)
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            cancel()
            exporter(testScheduler).export(out, password.toCharArray(), false) { }
        }

        // When
        job.join()

        // Then
        assertTrue(out.closed)
    }

    @Test
    fun `given failures of the phone itself when exported then each is a failure the user is told, not a crash`() =
        runTest {
            // Given — the database key unavailable, the phone's storage full, the cipher refusing.
            val causes = mapOf<Throwable, BackupExportFailure>(
                DbPassphraseUnavailableException(DbPassphraseUnavailableException.Reason.DECRYPTION_FAILED) to
                    BackupExportFailure.READ_FAILED,
                SQLiteFullException("database or disk is full") to BackupExportFailure.PHONE_FULL,
                GeneralSecurityException("cipher") to BackupExportFailure.READ_FAILED,
            )

            // Then
            causes.forEach { (cause, expected) ->
                val source = object : BackupContentSource {
                    override suspend fun collect(
                        includeSecrets: Boolean,
                        snapshotFile: File,
                        createdAtEpochMillis: Long,
                    ) = throw cause
                }
                val failure = exportFailure {
                    exporter(testScheduler, source).export(FailingStream(null), password.toCharArray(), false) { }
                }
                assertEquals(cause.javaClass.simpleName, expected, failure.failure)
            }
        }

    @Test
    fun `given a snapshot that left journal files behind when the export ends then they go with it`() = runTest {
        // When
        exporter(testScheduler, ScriptedSource(journal = true))
            .export(ByteArrayOutputStream(), password.toCharArray(), false) { }

        // Then
        assertTrue(
            snapshotDir.listFiles().orEmpty().map {
                it.name
            }.toString(),
            snapshotDir.listFiles().isNullOrEmpty(),
        )
    }

    private suspend fun exportFailure(export: suspend () -> Unit): BackupExportException = try {
        export()
        throw AssertionError("the export did not fail")
    } catch (e: BackupExportException) {
        e
    }

    private fun exporter(scheduler: TestCoroutineScheduler, source: BackupContentSource = ScriptedSource()) =
        BackupExporterImpl(
            source = source,
            keyDerivation = Argon2idBackupKeyDerivation { Long.MAX_VALUE },
            snapshotDirectory = { snapshotDir },
            kdfParams = fastKdf,
            chunkSize = BackupFormat.MIN_CHUNK_SIZE,
            now = { 1_791_849_600_000L },
            ioDispatcher = UnconfinedTestDispatcher(scheduler),
        )

    private fun read(archive: ByteArray): Map<String, ByteArray> {
        val input = ByteArrayInputStream(archive)
        val parsed = BackupHeaderCodec.read(input)
        val keys = Argon2idBackupKeyDerivation { Long.MAX_VALUE }
            .deriveKeys(password.toCharArray(), parsed.header.salt, parsed.header.kdfParams)
        return BackupArchiveReader(input, parsed, keys).use { reader ->
            generateSequence { reader.nextEntry() }.associate { it.name to it.content.readBytes() }
        }
    }

    /** Content as the collector would gather it, with a snapshot of fixed bytes. */
    private inner class ScriptedSource(private val journal: Boolean = false) : BackupContentSource {
        override suspend fun collect(
            includeSecrets: Boolean,
            snapshotFile: File,
            createdAtEpochMillis: Long,
        ): BackupContent {
            snapshotFile.writeBytes(snapshotBytes)
            if (journal) File(snapshotFile.path + "-journal").writeBytes(ByteArray(4))
            val workspace = files.workspace()
            val attachments = files.attachments()
            return BackupContent(
                snapshotFile = snapshotFile,
                snapshotKey = Random(7).nextBytes(BackupFormat.KEY_LENGTH),
                preferences = "{\"preferencesVersion\":1,\"values\":{}}".toByteArray(),
                providers = "{\"entriesVersion\":1,\"stores\":{}}".toByteArray(),
                secrets = if (includeSecrets) SECRETS.toByteArray() else null,
                workspace = workspace,
                attachments = attachments,
                manifest = BackupManifest(
                    createdAtEpochMillis = createdAtEpochMillis,
                    schemaVersion = 71,
                    secretsIncluded = includeSecrets,
                    counts = BackupCounts(1, 2, 3, workspace.entries.size, attachments.entries.size),
                    scheduledTasks = 0,
                    models = emptyList(),
                ),
            )
        }
    }

    /** A document that refuses every byte with [failure], or accepts everything when it is `null`. */
    private class FailingStream(private val failure: IOException?) : OutputStream() {
        var closed = false

        override fun write(b: Int) {
            failure?.let { throw it }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            failure?.let { throw it }
        }

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val SECRETS = "{\"entriesVersion\":1,\"stores\":{\"secure_api_keys_v2\":{\"openai_api_key\":\"sk\"}}}"
    }
}
