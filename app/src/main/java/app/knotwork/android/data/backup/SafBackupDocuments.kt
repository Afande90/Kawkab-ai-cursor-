package app.knotwork.android.data.backup

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import app.knotwork.android.di.IoDispatcher
import app.knotwork.android.domain.backup.BackupDocuments
import app.knotwork.android.domain.repositories.AppStateSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
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
 * made persistable, a `deleteDocument` it does not support — is answered with `false`, never a crash. A document
 * deleted once in this process is not asked about again: a second [discard] reports what the first did, whatever
 * the provider would say about a document it no longer has.
 *
 * [discardLeftBehind] never deletes the document a backup in this process is still writing: the start-up
 * maintenance that calls it also runs when the activity is only recreated. So the document is marked as being
 * written before it is recorded as unfinished, and its record is cleared before the mark.
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

    /** The streams opened here and not yet handed over for good, by document. */
    private val streams = ConcurrentHashMap<String, OutputStream>()

    /** What deleting each document answered, so asking again reports the same. */
    private val discarded = ConcurrentHashMap<String, Boolean>()

    /** Documents of the app's own content resolver. */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        appState: AppStateSettings,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(context.contentResolver, appState, ioDispatcher)

    override suspend fun openForWrite(document: String): OutputStream = withContext(ioDispatcher) {
        val uri = document.toUri()
        discarded.remove(document)
        try {
            resolver.takePersistableUriPermission(uri, GRANT)
        } catch (e: SecurityException) {
            // Not persistable here: the document is deleted while the process lives, not after a restart.
            Timber.w(e, "A backup document's grant could not be kept")
        }
        writing = document
        appState.setUnfinishedBackupDocument(document)
        // The stream is the caller's to close: the export closes it whatever way it ends, and [discard] closes it
        // when the caller was cancelled before it got the stream.
        @SuppressLint("Recycle")
        val stream = try {
            resolver.openOutputStream(uri, WRITE)
        } catch (e: SecurityException) {
            throw IOException("the provider refused the document", e)
        }
        (stream ?: throw IOException("the provider opened no stream")).also { streams[document] = it }
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
        streams.remove(document)
        forget(document)
    }

    override suspend fun discard(document: String): Boolean = withContext(ioDispatcher) {
        discarded[document]?.let { return@withContext it }
        closeStream(document)
        val deleted = try {
            DocumentsContract.deleteDocument(resolver, document.toUri())
        } catch (e: FileNotFoundException) {
            Timber.i(e, "A backup document to delete was already gone")
            true
        } catch (e: SecurityException) {
            Timber.w(e, "A backup document's provider refused to delete it")
            false
        } catch (e: UnsupportedOperationException) {
            Timber.w(e, "A backup document's provider cannot delete documents")
            false
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "A backup document's provider refused to delete it")
            false
        }
        discarded[document] = deleted
        forget(document)
        deleted
    }

    override suspend fun discardLeftBehind(): Boolean {
        val document = appState.unfinishedBackupDocument.first() ?: return true
        if (document == writing) return true
        return discard(document)
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

        /** Write: the document was just created, so there is nothing to truncate — and not every provider can. */
        const val WRITE = "w"
    }
}
