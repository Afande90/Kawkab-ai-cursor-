package app.kawkab.stubmessenger

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * The messages the stub messenger has accepted.
 *
 * The instrumented test queries this provider. That query is the system state
 * for a sent message: the screen writes a row when Send is tapped, and the
 * test reads the row back.
 */
class InboxProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(arrayOf(COLUMN_CONTACT, COLUMN_BODY))
        synchronized(rows) {
            rows.forEach { message -> cursor.addRow(arrayOf(message.contact, message.body)) }
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val contact = values?.getAsString(COLUMN_CONTACT).orEmpty()
        val body = values?.getAsString(COLUMN_BODY).orEmpty()
        val index = synchronized(rows) {
            rows += StoredMessage(contact, body)
            rows.size
        }
        return Uri.withAppendedPath(CONTENT_URI, index.toString())
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        synchronized(rows) {
            val count = rows.size
            rows.clear()
            count
        }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.kawkab.stubmessage"

    private data class StoredMessage(val contact: String, val body: String)

    /** Column names and the content URI the agent test queries. */
    companion object {
        /** Contact column. */
        const val COLUMN_CONTACT: String = "contact"

        /** Body column. */
        const val COLUMN_BODY: String = "body"

        /** Authority of this provider. */
        const val AUTHORITY: String = "app.kawkab.stubmessenger.inbox"

        /** Every stored message. */
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/messages")

        private val rows = mutableListOf<StoredMessage>()
    }
}
