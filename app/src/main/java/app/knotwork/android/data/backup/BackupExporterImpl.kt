package app.knotwork.android.data.backup

import android.content.Context
import android.database.sqlite.SQLiteFullException
import android.system.ErrnoException
import android.system.OsConstants
import app.knotwork.android.BuildConfig
import app.knotwork.android.di.IoDispatcher
import app.knotwork.android.domain.backup.BackupExportException
import app.knotwork.android.domain.backup.BackupExportFailure
import app.knotwork.android.domain.backup.BackupExporter
import app.knotwork.android.domain.backup.BackupFormat
import app.knotwork.android.domain.backup.BackupHeader
import app.knotwork.android.domain.backup.BackupKdfParams
import app.knotwork.android.domain.backup.BackupKeyDerivation
import app.knotwork.android.domain.backup.BackupKeys
import app.knotwork.android.domain.backup.BackupManifest
import app.knotwork.android.domain.backup.BackupManifestJson
import app.knotwork.android.domain.backup.BackupProgress
import app.knotwork.android.domain.backup.BackupStep
import app.knotwork.android.domain.constants.TransientCacheDirectory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BackupExporter]: derives the keys, gathers the content, writes the archive.
 *
 * Steps, as the user sees them: the password (Argon2id, about a second), then the database snapshot,
 * the settings and the manifest, then the files. The archive's chunk flagged as last is written only
 * after the last entry, so a cancelled or failed export leaves a file every reader reports as
 * incomplete; the database snapshot is deleted and the keys wiped however the export ends.
 *
 * Failures are told apart by where they happen: the document the user picked refusing bytes is
 * [BackupExportFailure.WRITE_FAILED] — or [BackupExportFailure.NO_SPACE] when it ran out of space — and
 * anything failing while the app's own data is read is [BackupExportFailure.READ_FAILED].
 *
 * @param source Gathers the content.
 * @param keyDerivation Derives the keys from the password.
 * @param snapshotDirectory Where the database snapshot is written for the length of the export.
 * @param kdfParams The key costs the archive is written with.
 * @param chunkSize The archive's chunk size.
 * @param now The clock the archive is stamped with.
 * @param ioDispatcher Runs the export.
 */
@Singleton
internal class BackupExporterImpl(
    private val source: BackupContentSource,
    private val keyDerivation: BackupKeyDerivation,
    private val snapshotDirectory: () -> File,
    private val kdfParams: BackupKdfParams,
    private val chunkSize: Int,
    private val now: () -> Long,
    private val ioDispatcher: CoroutineDispatcher,
) : BackupExporter {

    /** Exports with the writer's own key costs and chunk size, into the app's cache. */
    @Inject
    constructor(
        source: BackupContentSource,
        keyDerivation: BackupKeyDerivation,
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        source = source,
        keyDerivation = keyDerivation,
        snapshotDirectory = { File(context.cacheDir, TransientCacheDirectory.BACKUP_SNAPSHOT.dirName) },
        kdfParams = BackupKdfParams.DEFAULT,
        chunkSize = BackupFormat.CHUNK_SIZE,
        now = System::currentTimeMillis,
        ioDispatcher = ioDispatcher,
    )

    override suspend fun export(
        target: OutputStream,
        password: CharArray,
        includeSecrets: Boolean,
        onProgress: (BackupProgress) -> Unit,
    ): BackupManifest {
        val sink = GuardedSink(target)
        try {
            return withContext(ioDispatcher) { sink.use { exportInto(sink, password, includeSecrets, onProgress) } }
        } finally {
            // Closes the document when the export never started — a caller cancelled before the dispatcher
            // ran it. After a started export this is a no-op: the sink closes once.
            sink.closeQuietly()
        }
    }

    private suspend fun exportInto(
        sink: GuardedSink,
        password: CharArray,
        includeSecrets: Boolean,
        onProgress: (BackupProgress) -> Unit,
    ): BackupManifest {
        onProgress(BackupProgress(BackupStep.KEY, bytesDone = 0, bytesTotal = 0))
        val salt = ByteArray(BackupFormat.SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val snapshot = File(snapshotDirectory().apply { mkdirs() }, "snapshot-${UUID.randomUUID()}.db")
        var keys: BackupKeys? = null
        var neededBytes = 0L
        try {
            val archiveKeys = keyDerivation.deriveKeys(password, salt, kdfParams).also { keys = it }
            currentCoroutineContext().ensureActive()
            onProgress(BackupProgress(BackupStep.DATABASE, bytesDone = 0, bytesTotal = 0))
            val content = source.collect(includeSecrets, snapshot, now())
            try {
                val manifest = BackupManifestJson.encode(content.manifest).toByteArray(Charsets.UTF_8)
                neededBytes = totalBytes(content, manifest)
                val progress = Progress(neededBytes, onProgress, currentCoroutineContext().job)
                BackupArchiveWriter(sink, header(content.manifest, salt, neededBytes), archiveKeys).use { writer ->
                    writeContent(writer, content, manifest, progress)
                    writer.finish()
                }
                return content.manifest
            } finally {
                content.snapshotKey.fill(0)
                content.providers.fill(0)
                content.secrets?.fill(0)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw failureOf(e, neededBytes)
        } finally {
            deleteSnapshot(snapshot)
            keys?.wipe()
        }
    }

    private fun header(manifest: BackupManifest, salt: ByteArray, contentBytes: Long) = BackupHeader(
        formatVersion = BackupFormat.FORMAT_VERSION,
        appVersionName = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE,
        flavor = BuildConfig.FLAVOR,
        schemaVersion = manifest.schemaVersion,
        createdAtEpochMillis = manifest.createdAtEpochMillis,
        contentBytes = contentBytes,
        kdfParams = kdfParams,
        salt = salt,
        chunkSize = chunkSize,
    )

    /**
     * What the user is told about [e]: the document they picked failing is theirs to fix — no space, or
     * it stopped accepting bytes; the phone's own storage too full for the snapshot is the phone's; any
     * other failure is the app's own data failing to be read or sealed.
     */
    private fun failureOf(e: Exception, neededBytes: Long): BackupExportException {
        val failure = when {
            e is SinkFailure && e.isOutOfSpace() -> BackupExportFailure.NO_SPACE
            e is SinkFailure -> BackupExportFailure.WRITE_FAILED
            e is SQLiteFullException -> BackupExportFailure.PHONE_FULL
            else -> BackupExportFailure.READ_FAILED
        }
        val cause = if (e is SinkFailure) e.cause else e
        Timber.w("Backup export failed (%s): %s", failure, cause?.javaClass?.simpleName)
        return BackupExportException(failure, neededBytes, "backup export failed: $failure", cause)
    }

    /** Deletes the snapshot and whatever journal SQLite left beside it. */
    private fun deleteSnapshot(snapshot: File) {
        SNAPSHOT_SIDECARS.forEach { suffix -> File(snapshot.path + suffix).delete() }
    }

    private fun writeContent(
        writer: BackupArchiveWriter,
        content: BackupContent,
        manifest: ByteArray,
        progress: Progress,
    ) {
        writer.addEntry(BackupFormat.MANIFEST_ENTRY, compress = true) { progress.write(it, manifest) }
        writer.addEntry(BackupFormat.DATABASE_KEY_ENTRY, compress = false) { progress.write(it, content.snapshotKey) }
        writer.addEntry(BackupFormat.PREFERENCES_ENTRY, compress = true) { progress.write(it, content.preferences) }
        writer.addEntry(BackupFormat.PROVIDERS_ENTRY, compress = true) { progress.write(it, content.providers) }
        content.secrets?.let { secrets ->
            writer.addEntry(BackupFormat.SECRETS_ENTRY, compress = true) { progress.write(it, secrets) }
        }
        writer.addEntry(BackupFormat.DATABASE_ENTRY, compress = false) { progress.copy(it, content.snapshotFile) }
        progress.step = BackupStep.FILES
        content.workspace.entries.forEach { entry ->
            writer.addEntry(entry.name, compress = true) { progress.copy(it, entry.file) }
        }
        content.attachments.entries.forEach { entry ->
            writer.addEntry(entry.name, compress = false) { progress.copy(it, entry.file) }
        }
    }

    private fun totalBytes(content: BackupContent, manifest: ByteArray): Long =
        manifest.size.toLong() + content.snapshotKey.size + content.preferences.size + content.providers.size +
            (content.secrets?.size ?: 0) + content.snapshotFile.length() +
            content.workspace.entries.sumOf { it.sizeBytes } + content.attachments.entries.sumOf { it.sizeBytes }

    /**
     * Counts written content bytes for [onProgress], and stops the export between buffers once [job] is
     * cancelled — the entry writers run outside a suspend function.
     */
    private class Progress(
        private val total: Long,
        private val onProgress: (BackupProgress) -> Unit,
        private val job: Job,
    ) {
        var step: BackupStep = BackupStep.DATABASE
            set(value) {
                field = value
                report()
            }
        private var done = 0L

        fun write(out: OutputStream, bytes: ByteArray) {
            job.ensureActive()
            out.write(bytes)
            done += bytes.size
            report()
        }

        fun copy(out: OutputStream, file: File) {
            file.inputStream().use { input ->
                val buffer = ByteArray(COPY_BUFFER)
                while (true) {
                    job.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    done += read
                    report()
                }
            }
        }

        private fun report() = onProgress(BackupProgress(step, done, total))
    }

    /**
     * The document the user picked. Its failures are marked as [SinkFailure], so they are told apart
     * from failures reading the app's own data. It closes once; a second close does nothing.
     */
    private class GuardedSink(out: OutputStream) : FilterOutputStream(out) {
        private var closed = false

        override fun write(b: Int) = guard { out.write(b) }

        override fun write(b: ByteArray, off: Int, len: Int) = guard { out.write(b, off, len) }

        override fun flush() = guard { out.flush() }

        override fun close() {
            if (closed) return
            closed = true
            guard { out.close() }
        }

        /** Closes if not yet closed, keeping a failure to close to the log: nothing was written to lose. */
        fun closeQuietly() {
            try {
                close()
            } catch (e: SinkFailure) {
                Timber.w("Backup document could not be closed: %s", e.cause?.javaClass?.simpleName)
            }
        }

        private inline fun guard(block: () -> Unit) {
            try {
                block()
            } catch (e: IOException) {
                throw SinkFailure(e)
            }
        }
    }

    /** A failure of the document the user picked. */
    private class SinkFailure(cause: IOException) : IOException(cause) {
        fun isOutOfSpace(): Boolean = generateSequence<Throwable>(cause) { it.cause }
            .any { it is ErrnoException && it.errno == OsConstants.ENOSPC }
    }

    private companion object {
        const val COPY_BUFFER = 64 * 1024

        /** The snapshot file, and the journal files SQLite may leave beside it. */
        val SNAPSHOT_SIDECARS = listOf("", "-journal", "-wal", "-shm")
    }
}
