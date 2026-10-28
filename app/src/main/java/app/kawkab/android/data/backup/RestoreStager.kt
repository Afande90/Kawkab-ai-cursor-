package app.kawkab.android.data.backup

import android.content.Context
import app.kawkab.android.data.local.AppDatabase
import app.kawkab.android.data.local.EncryptedDbPassphraseProvider
import app.kawkab.android.data.local.PathContainment
import app.kawkab.android.domain.backup.BackupArchiveInfo
import app.kawkab.android.domain.backup.BackupFormat
import app.kawkab.android.domain.backup.BackupFormatException
import app.kawkab.android.domain.backup.BackupHeader
import app.kawkab.android.domain.backup.BackupKeyDerivation
import app.kawkab.android.domain.backup.BackupManifest
import app.kawkab.android.domain.backup.BackupManifestJson
import app.kawkab.android.domain.backup.BackupReadFailure
import app.kawkab.android.domain.backup.BackupRestoreException
import app.kawkab.android.domain.backup.RestoreFailure
import app.kawkab.android.domain.backup.RestoreProgress
import app.kawkab.android.domain.backup.RestoreStep
import app.kawkab.android.domain.backup.StagedRestore
import app.kawkab.android.domain.models.DbPassphraseUnavailableException
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads an archive with the user's password into the staging area ([RestoreLayout.staging]).
 *
 * In order: the header and its authentication code (a wrong password stops here), the schema (a later one
 * than this build's is refused), free space for the announced content, then every entry, unpacked as it is
 * authenticated. Only after the last chunk has authenticated is the database checked, keyed for this phone
 * and migrated ([StagedDatabasePreparer]). File entries land inside their staged directory or the archive
 * is damaged; the provider settings and secrets go to [RestoreStagedEntries], never to a file. An entry this
 * build does not know is skipped.
 *
 * Nothing outside the staging area is touched. The caller clears the staging area before and, on failure,
 * after.
 *
 * @param layout Where the restore is staged.
 * @param keyDerivation Derives the archive's keys from the password.
 * @param preparer Checks, keys and migrates the staged database.
 * @param stagedEntries Keeps the provider settings and secrets.
 * @param phoneKey Gives this phone's database passphrase, a fresh copy each call.
 * @param usableBytes How many bytes can still be written in a directory.
 */
@Singleton
internal class RestoreStager(
    private val layout: RestoreLayout,
    private val keyDerivation: BackupKeyDerivation,
    private val preparer: StagedDatabasePreparer,
    private val stagedEntries: RestoreStagedEntries,
    private val phoneKey: () -> ByteArray,
    private val usableBytes: (File) -> Long = File::getUsableSpace,
) {

    /** Stages into the app's own restore area, keyed with this phone's database passphrase. */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        keyDerivation: BackupKeyDerivation,
        preparer: StagedDatabasePreparer,
        stagedEntries: RestoreStagedEntries,
        passphraseProvider: EncryptedDbPassphraseProvider,
    ) : this(
        layout = RestoreLayout.of(context),
        keyDerivation = keyDerivation,
        preparer = preparer,
        stagedEntries = stagedEntries,
        phoneKey = { passphraseProvider.getOrCreatePassphrase() },
    )

    /**
     * Reads the open header.
     *
     * @param source The archive, at its first byte.
     * @return What the header says.
     * @throws BackupFormatException When the file is not an archive this build reads.
     */
    fun inspect(source: InputStream): BackupArchiveInfo = BackupHeaderCodec.read(source.buffered()).header.let {
        BackupArchiveInfo(it.appVersionName, it.createdAtEpochMillis)
    }

    /**
     * Stages the archive in [source].
     *
     * @param source The archive, at its first byte.
     * @param password The password the user entered.
     * @param onProgress Told the current step and how far it is.
     * @param ensureActive Throws when the caller has gone away; asked between every block unpacked.
     * @return What was staged.
     * @throws BackupFormatException When the archive cannot be read.
     * @throws BackupRestoreException When it was read but cannot be restored here.
     */
    fun stage(
        source: InputStream,
        password: CharArray,
        onProgress: (RestoreProgress) -> Unit,
        ensureActive: () -> Unit = {},
    ): StagedRestore {
        val input = source.buffered()
        onProgress(RestoreProgress(RestoreStep.KEY, bytesDone = 0, bytesTotal = 0))
        val parsed = BackupHeaderCodec.read(input)
        val header = parsed.header
        val keys = keyDerivation.deriveKeys(password, header.salt, header.kdfParams)
        try {
            parsed.verify(keys.headerKey)
            if (header.schemaVersion > AppDatabase.SCHEMA_VERSION) throw newerVersion(header)
            layout.staging.mkdirs()
            val needed = header.contentBytes + header.contentBytes / SPACE_MARGIN_DIVISOR + SPACE_MARGIN_BYTES
            val free = usableBytes(layout.staging)
            if (free < needed) {
                throw BackupRestoreException(
                    RestoreFailure.NO_SPACE,
                    "not enough room to stage",
                    neededBytes = needed,
                    freeBytes = free,
                )
            }
            val unpacked = BackupArchiveReader(input, parsed, keys).use { reader ->
                unpack(reader, Progress(header.contentBytes, onProgress, ensureActive))
            }
            onProgress(RestoreProgress(RestoreStep.DATABASE, bytesDone = 0, bytesTotal = 0))
            prepareDatabase(unpacked.databaseKey)
            writeSynced(layout.stagedManifest, BackupManifestJson.encode(unpacked.manifest).toByteArray(Charsets.UTF_8))
            return StagedRestore(
                BackupArchiveInfo(header.appVersionName, header.createdAtEpochMillis),
                unpacked.manifest,
            )
        } finally {
            keys.wipe()
        }
    }

    private fun unpack(reader: BackupArchiveReader, progress: Progress): Unpacked {
        layout.stagedWorkspace.mkdirs()
        layout.stagedAttachments.mkdirs()
        var manifest: BackupManifest? = null
        var databaseKey: ByteArray? = null
        val seen = mutableSetOf<String>()
        try {
            unpackEntries(reader, progress, seen, onManifest = { manifest = it }, onDatabaseKey = { databaseKey = it })
            REQUIRED.filterNot { it in seen }.firstOrNull()?.let { damaged("the archive has no $it") }
            return Unpacked(manifest ?: damaged("no manifest"), databaseKey ?: damaged("no database key"))
        } catch (e: Exception) {
            // The archive's database key goes no further than a failed unpack.
            databaseKey?.fill(0)
            throw e
        }
    }

    private fun unpackEntries(
        reader: BackupArchiveReader,
        progress: Progress,
        seen: MutableSet<String>,
        onManifest: (BackupManifest) -> Unit,
        onDatabaseKey: (ByteArray) -> Unit,
    ) {
        while (true) {
            val entry = reader.nextEntry() ?: break
            val name = entry.name
            val content = entry.content
            when {
                name == BackupFormat.MANIFEST_ENTRY -> onManifest(BackupManifestJson.decode(text(content, progress)))
                name == BackupFormat.DATABASE_KEY_ENTRY -> onDatabaseKey(databaseKey(content, progress))
                name == BackupFormat.DATABASE_ENTRY -> copy(content, layout.stagedDatabase, progress)
                name == BackupFormat.PREFERENCES_ENTRY -> {
                    val text = text(content, progress)
                    PreferencesBackupJson.decode(text)
                    writeSynced(layout.stagedPreferences, text.toByteArray(Charsets.UTF_8))
                }
                name == BackupFormat.PROVIDERS_ENTRY ->
                    stagedEntries.keep(RestoreStagedEntries.Kind.PROVIDERS, text(content, progress))
                name == BackupFormat.SECRETS_ENTRY ->
                    stagedEntries.keep(RestoreStagedEntries.Kind.SECRETS, text(content, progress))
                name.startsWith(BackupFormat.WORKSPACE_PREFIX) ->
                    copy(
                        content,
                        inside(layout.stagedWorkspace, name.removePrefix(BackupFormat.WORKSPACE_PREFIX)),
                        progress,
                    )
                name.startsWith(BackupFormat.ATTACHMENTS_PREFIX) ->
                    copy(
                        content,
                        inside(layout.stagedAttachments, name.removePrefix(BackupFormat.ATTACHMENTS_PREFIX)),
                        progress,
                    )
                else -> {
                    Timber.w("An archive entry this build does not know was skipped")
                    copy(content, OutputStream.nullOutputStream(), progress)
                }
            }
            seen += name
        }
    }

    private fun databaseKey(content: InputStream, progress: Progress): ByteArray {
        val key = bytes(content, progress)
        if (key.size != BackupFormat.KEY_LENGTH) {
            key.fill(0)
            damaged("the database key is not ${BackupFormat.KEY_LENGTH} bytes")
        }
        return key
    }

    /**
     * Checks room for the database's re-key and migration — about one more copy of it, in SQLite's rollback
     * journal and in the tables a migration rebuilds — then has it prepared. A full disk inside SQLite would
     * otherwise read as a damaged archive. Both keys are wiped however it ends.
     */
    private fun prepareDatabase(databaseKey: ByteArray) {
        try {
            val needed = layout.stagedDatabase.length() * DATABASE_COPIES + SPACE_MARGIN_BYTES
            val free = usableBytes(layout.staging)
            if (free < needed) {
                throw BackupRestoreException(
                    RestoreFailure.NO_SPACE,
                    "not enough room to key the database",
                    neededBytes = needed,
                    freeBytes = free,
                )
            }
            val key = try {
                phoneKey()
            } catch (e: DbPassphraseUnavailableException) {
                throw BackupRestoreException(
                    RestoreFailure.PHONE_KEY_UNAVAILABLE,
                    "this phone's database key is unreadable",
                    e,
                )
            }
            try {
                preparer.prepare(layout.stagedDatabase, databaseKey, key)
            } finally {
                key.fill(0)
            }
        } finally {
            databaseKey.fill(0)
        }
    }

    /** [relative] under [root], or a damaged archive when it would land anywhere else. */
    private fun inside(root: File, relative: String): File {
        val target =
            PathContainment.childOrNull(File(root, relative), root) ?: damaged("an entry escapes its directory")
        target.parentFile?.mkdirs()
        return target
    }

    /** Copies [content] into [target] and forces it to the disk: the next start deletes what it replaces. */
    private fun copy(content: InputStream, target: File, progress: Progress) {
        FileOutputStream(target).use {
            copy(content, it, progress)
            it.fd.sync()
        }
    }

    private fun writeSynced(target: File, bytes: ByteArray) {
        FileOutputStream(target).use {
            it.write(bytes)
            it.fd.sync()
        }
    }

    private fun copy(content: InputStream, target: OutputStream, progress: Progress) {
        val buffer = ByteArray(COPY_BUFFER)
        while (true) {
            val read = content.read(buffer)
            if (read < 0) break
            target.write(buffer, 0, read)
            progress.advance(read)
        }
    }

    private fun bytes(content: InputStream, progress: Progress): ByteArray {
        val bytes = content.readNBytes(MAX_TEXT_ENTRY_BYTES + 1)
        if (bytes.size > MAX_TEXT_ENTRY_BYTES) damaged("a settings entry is larger than any this app writes")
        progress.advance(bytes.size)
        return bytes
    }

    private fun text(content: InputStream, progress: Progress): String =
        String(bytes(content, progress), Charsets.UTF_8)

    private fun newerVersion(header: BackupHeader) = BackupRestoreException(
        RestoreFailure.NEWER_VERSION,
        "the archive's schema ${header.schemaVersion} is newer than this build's",
        writerVersionName = header.appVersionName,
    )

    private fun damaged(reason: String): Nothing = throw BackupFormatException(BackupReadFailure.DAMAGED, reason)

    /** What unpacking found that the next steps need. */
    private class Unpacked(val manifest: BackupManifest, val databaseKey: ByteArray)

    /** Reports unpacked bytes, at most once per [REPORT_EVERY_BYTES], and stops when the caller has gone. */
    private class Progress(
        private val total: Long,
        private val onProgress: (RestoreProgress) -> Unit,
        private val ensureActive: () -> Unit,
    ) {
        private var done = 0L
        private var reported = -REPORT_EVERY_BYTES

        fun advance(bytes: Int) {
            ensureActive()
            done += bytes
            if (done - reported >= REPORT_EVERY_BYTES) {
                reported = done
                onProgress(RestoreProgress(RestoreStep.UNPACK, bytesDone = done, bytesTotal = total))
            }
        }
    }

    private companion object {
        const val COPY_BUFFER = 64 * 1024
        const val REPORT_EVERY_BYTES = 1L shl 20

        /** No settings, provider or secrets entry the app writes comes near this; a larger one is damaged. */
        const val MAX_TEXT_ENTRY_BYTES = 16 shl 20

        /** Staging needs the content and a margin: a tenth more, and 16 MiB for the database's migration. */
        const val SPACE_MARGIN_DIVISOR = 10
        const val SPACE_MARGIN_BYTES = 16L shl 20

        /** Keying and migrating the staged database can need as much room again as the database. */
        const val DATABASE_COPIES = 2

        /** The entries every archive has. */
        val REQUIRED = listOf(
            BackupFormat.MANIFEST_ENTRY,
            BackupFormat.DATABASE_KEY_ENTRY,
            BackupFormat.DATABASE_ENTRY,
            BackupFormat.PREFERENCES_ENTRY,
            BackupFormat.PROVIDERS_ENTRY,
        )
    }
}
