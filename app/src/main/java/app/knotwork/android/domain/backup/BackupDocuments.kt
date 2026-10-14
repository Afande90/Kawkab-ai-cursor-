package app.knotwork.android.domain.backup

import java.io.IOException
import java.io.OutputStream

/**
 * The documents the user picks for a backup, by URI: opened for writing, named, finished, deleted.
 *
 * A document opened here is remembered as unfinished until [finish] or [discard], so a start after the process
 * died mid-backup deletes what it left ([discardLeftBehind]).
 */
interface BackupDocuments {

    /**
     * Opens [document] for writing and remembers it as unfinished.
     *
     * @param document The document's URI, as the system picker returned it.
     * @return The stream; the caller closes it.
     * @throws IOException When the document cannot be opened for writing.
     */
    suspend fun openForWrite(document: String): OutputStream

    /**
     * The document's name, as its provider shows it.
     *
     * @param document The document's URI.
     * @return The name, or `null` when the provider gives none.
     */
    suspend fun displayName(document: String): String?

    /**
     * Forgets [document] as unfinished: the backup in it is complete, and the stream is closed.
     *
     * @param document The document's URI.
     * @throws IOException When the record cannot be cleared — the document is then still marked unfinished, and the
     *   next start would delete it.
     */
    suspend fun finish(document: String)

    /**
     * Deletes [document] and forgets it, closing first any stream [openForWrite] opened to it that its caller never
     * got — a caller cancelled on the way back.
     *
     * @param document The document's URI.
     * @return `true` when the document is gone; `false` when its provider refused to delete it, or failed.
     */
    suspend fun discard(document: String): Boolean

    /**
     * Deletes [document] only when it holds nothing — an untouched document the picker created for a backup that
     * never started. A document holding bytes is an existing file the user chose to overwrite, and stays; so does
     * one whose size the provider does not tell.
     *
     * @param document The document's URI.
     * @return `true` when it was empty and is now gone.
     */
    suspend fun discardIfEmpty(document: String): Boolean

    /**
     * Deletes the document an earlier backup left unfinished, if any.
     *
     * @return `true` when nothing unfinished is left.
     */
    suspend fun discardLeftBehind(): Boolean
}
