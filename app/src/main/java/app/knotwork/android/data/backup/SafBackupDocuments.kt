package app.knotwork.android.data.backup

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import app.knotwork.android.di.IoDispatcher
import app.knotwork.android.domain.backup.BackupDocuments
import app.knotwork.android.domain.repositories.AppStateSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BackupDocuments] over the Storage Access Framework.
 *
 * The picked document's grant is taken as persistable, so a start after the process died can still delete it;
 * the grant is released once the document is finished or deleted. A provider that refuses — a grant it never
 * made persistable, a `deleteDocument` it does not support, a failure of its own — is answered with `false`,
 * never a crash.
 *
 * The document is opened truncating: the user may have picked an existing file to overwrite, and a plain `w` may
 * or may not truncate depending on the provider — a longer old file would leave bytes after the archive's end,
 * which every reader rejects.
 *
 * [discardLeftBehind] never deletes the document a backup in this process is writing or has just finished: the
 * start-up maintenance that calls it also runs when the activity is only recreated. One lock orders its reading
 * of the record against every change to it.
 *
 * @param resolver Opens, queries and deletes documents.
 * @param appState Remembers the unfinished document between starts.
 * @param ioDispatcher Runs the provider calls.
 */
@Singleton
internal class SafBackupDocuments(
    private val resolver: ContentResolver,
    private val appState: AppStateSettings,
    private val ioDispatcher: CoroutineDispatcher,
) : BackupDocuments {

    /** The document a backup in this process is writing, if any. */
    @Volatile
    private var writing: String? = null

    /** Orders the record of the unfinished document, and [writing], against the start-up clean-up. */
    private val recordLock = Mutex()

    /** The streams opened here and not yet handed over for good, by document. */
    private val streams = ConcurrentHashMap<String, OutputStream>()

    /** Documents of the app's own content resolver. */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        appState: AppStateSettings,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(context.contentResolver, appState, ioDispatcher)

    override suspend fun openForWrite(document: String): OutputStream = withContext(ioDispatcher) {
        val uri = document.toUri()
        try {
            resolver.takePersistableUriPermission(uri, GRANT)
        } catch (e: SecurityException) {
            // Not persistable here: the document is deleted while the process lives, not after a restart.
            Timber.i(e, "A backup document's grant could not be kept")
        }
        recordLock.withLock {
            writing = document
            appState.setUnfinishedBackupDocument(document)
        }
        // The stream is the caller's to close: the export closes it whatever way it ends, and [discard] closes it
        // when the caller was cancelled before it got the stream.
        openTruncating(uri).also { streams[document] = it }
    }

    override suspend fun displayName(document: String): String? = withContext(ioDispatcher) {
        try {
            resolver.query(document.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: RuntimeException) {
            // A provider's failure crosses the binder as any runtime exception; the name is only shown.
            Timber.w(e, "A backup document's name could not be read")
            null
        }
    }

    override suspend fun finish(document: String) = withContext(ioDispatcher) {
        recordLock.withLock {
            streams.remove(document)
            forget(document)
        }
    }

    override suspend fun discard(document: String): Boolean = withContext(ioDispatcher) {
        recordLock.withLock { deleteAndForget(document) }
    }

    override suspend fun discardIfEmpty(document: String): Boolean = withContext(ioDispatcher) {
        // The document a backup is writing is empty for its first second, while the key is derived.
        if (document == writing || streams.containsKey(document)) return@withContext false
        // Unknown counts as not empty: a file that may hold someone's data is never deleted on a guess.
        if (sizeOf(document) != 0L) return@withContext false
        recordLock.withLock { deleteAndForget(document) }
    }

    override suspend fun discardLeftBehind(): Boolean = withContext(ioDispatcher) {
        recordLock.withLock {
            val document = appState.unfinishedBackupDocument.first() ?: return@withLock true
            if (document == writing) return@withLock true
            deleteAndForget(document)
        }
    }

    @SuppressLint("Recycle") // Returned to the caller, who closes it.
    private fun openTruncating(uri: Uri): OutputStream {
        val stream = try {
            resolver.openOutputStream(uri, WRITE_TRUNCATE)
        } catch (e: FileNotFoundException) {
            // What `openOutputStream` documents for a mode the provider rejects; a document that is really
            // missing fails again on the plain mode.
            Timber.i(e, "A backup document's provider refused to truncate; writing without")
            openPlain(uri)
        } catch (e: IllegalArgumentException) {
            Timber.i(e, "A backup document's provider does not truncate; writing without")
            openPlain(uri)
        } catch (e: UnsupportedOperationException) {
            Timber.i(e, "A backup document's provider does not truncate; writing without")
            openPlain(uri)
        } catch (e: SecurityException) {
            throw IOException("the provider refused the document", e)
        }
        return stream ?: throw IOException("the provider opened no stream")
    }

    @SuppressLint("Recycle") // Returned to the caller, who closes it.
    private fun openPlain(uri: Uri): OutputStream? = try {
        resolver.openOutputStream(uri, WRITE)
    } catch (e: SecurityException) {
        throw IOException("the provider refused the document", e)
    }

    /** The document's size, or `null` when the provider does not say. */
    private fun sizeOf(document: String): Long? = try {
        resolver.query(document.toUri(), arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
    } catch (e: RuntimeException) {
        Timber.i(e, "A backup document's size could not be read")
        null
    }

    /** Deletes [document] and forgets it; the caller holds [recordLock]. */
    private suspend fun deleteAndForget(document: String): Boolean {
        closeStream(document)
        val deleted = try {
            DocumentsContract.deleteDocument(resolver, document.toUri())
        } catch (e: FileNotFoundException) {
            Timber.i(e, "A backup document to delete was already gone")
            true
        } catch (e: SecurityException) {
            Timber.i(e, "A backup document's provider refused to delete it")
            false
        } catch (e: UnsupportedOperationException) {
            Timber.i(e, "A backup document's provider cannot delete documents")
            false
        } catch (e: IllegalArgumentException) {
            Timber.i(e, "A backup document's provider refused to delete it")
            false
        } catch (e: RuntimeException) {
            // A provider that died mid-call surfaces as a bare runtime exception.
            Timber.w(e, "A backup document's provider failed while deleting it")
            false
        }
        try {
            forget(document)
        } catch (e: IOException) {
            // The document is dealt with; a record left behind only makes the next start ask about it again.
            Timber.w(e, "A deleted backup document's record could not be cleared")
        }
        return deleted
    }

    /** Closes the stream this process opened to [document], if it was never handed over. */
    private fun closeStream(document: String) {
        val stream = streams.remove(document) ?: return
        try {
            stream.close()
        } catch (e: IOException) {
            Timber.i(e, "An unfinished backup's stream failed to close")
        }
    }

    /**
     * Forgets [document] as unfinished and releases its grant; the caller holds [recordLock].
     *
     * @throws IOException When the record cannot be cleared — for [finish], a complete backup still marked
     *   unfinished, which the next start would delete: the caller must not report it as saved.
     */
    private suspend fun forget(document: String) {
        // Only this document's record: deleting a stray document must not clear a running backup's.
        if (appState.unfinishedBackupDocument.first() == document) appState.setUnfinishedBackupDocument(null)
        if (writing == document) writing = null
        try {
            resolver.releasePersistableUriPermission(document.toUri(), GRANT)
        } catch (e: SecurityException) {
            Timber.i(e, "A backup document's grant was not held")
        }
    }

    private companion object {
        const val GRANT = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        /** Write, truncating whatever the document held before. */
        const val WRITE_TRUNCATE = "wt"

        /** Write, for a provider that knows no truncating mode. */
        const val WRITE = "w"
    }
}
