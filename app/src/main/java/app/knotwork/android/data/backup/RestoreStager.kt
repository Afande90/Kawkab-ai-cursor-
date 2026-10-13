package app.knotwork.android.data.backup

import android.content.Context
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.EncryptedDbPassphraseProvider
import app.knotwork.android.data.local.PathContainment
import app.knotwork.android.domain.backup.BackupArchiveInfo
import app.knotwork.android.domain.backup.BackupFormat
import app.knotwork.android.domain.backup.BackupFormatException
import app.knotwork.android.domain.backup.BackupHeader
import app.knotwork.android.domain.backup.BackupKeyDerivation
import app.knotwork.android.domain.backup.BackupManifest
import app.knotwork.android.domain.backup.BackupManifestJson
import app.knotwork.android.domain.backup.BackupReadFailure
import app.knotwork.android.domain.backup.BackupRestoreException
import app.knotwork.android.domain.backup.RestoreFailure
import app.knotwork.android.domain.backup.RestoreProgress
import app.knotwork.android.domain.backup.RestoreStep
import app.knotwork.android.domain.backup.StagedRestore
import app.knotwork.android.domain.models.DbPassphraseUnavailableException
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
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
     * @return What was staged.
     * @throws BackupFormatException When the archive cannot be read.
     * @throws BackupRestoreException When it was read but cannot be restored here.
     */
    fun stage(source: InputStream, password: CharArray, onProgress: (RestoreProgress) -> Unit): StagedRestore {
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
            if (usableBytes(layout.staging) < needed) {
                throw BackupRestoreException(RestoreFailure.NO_SPACE, "not enough room to stage", neededBytes = needed)
            }
            val unpacked = BackupArchiveReader(input, parsed, keys).use { reader ->
                unpack(reader, Progress(header.contentBytes, onProgress))
            }
            onProgress(RestoreProgress(RestoreStep.DATABASE, bytesDone = 0, bytesTotal = 0))
            prepareDatabase(unpacked.databaseKey)
            layout.stagedManifest.writeText(BackupManifestJson.encode(unpacked.manifest))
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
        while (true) {
            val entry = reader.nextEntry() ?: break
            val name = entry.name
            val content = entry.content
            when {
                name == BackupFormat.MANIFEST_ENTRY -> manifest = BackupManifestJson.decode(text(content, progress))
                name == BackupFormat.DATABASE_KEY_ENTRY -> databaseKey = bytes(content, progress).also {
                    if (it.size !=
                        BackupFormat.KEY_LENGTH
                    ) {
                        damaged("the database key is not ${BackupFormat.KEY_LENGTH} bytes")
                    }
                }
                name == BackupFormat.DATABASE_ENTRY -> copy(content, layout.stagedDatabase, progress)
                name == BackupFormat.PREFERENCES_ENTRY -> {
                    val text = text(content, progress)
                    PreferencesBackupJson.decode(text)
                    layout.stagedPreferences.writeText(text)
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
        REQUIRED.filterNot { it in seen }.firstOrNull()?.let { damaged("the archive has no $it") }
        return Unpacked(manifest ?: damaged("no manifest"), databaseKey ?: damaged("no database key"))
    }

    private fun prepareDatabase(databaseKey: ByteArray) {
        val key = try {
            phoneKey()
        } catch (e: DbPassphraseUnavailableException) {
            databaseKey.fill(0)
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

    private fun copy(content: InputStream, target: File, progress: Progress) {
        target.outputStream().use { copy(content, it, progress) }
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

    /** Reports unpacked bytes, at most once per [REPORT_EVERY_BYTES]. */
    private class Progress(private val total: Long, private val onProgress: (RestoreProgress) -> Unit) {
        private var done = 0L
        private var reported = -REPORT_EVERY_BYTES

        fun advance(bytes: Int) {
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
