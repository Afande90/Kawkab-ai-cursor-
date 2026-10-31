package app.kawkab.android.hands

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the owner's contacts when READ_CONTACTS has been granted. Otherwise it returns nothing.
 *
 * Every phone row is read (a phone book is a few hundred rows, and Arabic spellings such as
 * أحمد and احمد differ in SQL), then [ContactMatcher] keeps only
 * whole-word or word-start matches, merges one number written twice, and drops the owner's own
 * numbers (task 23j).
 */
@Singleton
class AndroidContactBook @Inject constructor(@ApplicationContext private val context: Context) : ContactBook {
    override fun canRead(): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED

    override fun resolve(spoken: String): List<ContactMatch> {
        if (!canRead()) return emptyList()
        val rows = phoneRows(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null)
        return ContactMatcher.match(spoken, rows, ownNumbers())
    }

    /** The numbers on the owner's own profile card, which are never offered as a contact. */
    private fun ownNumbers(): List<String> = try {
        phoneRows(
            Uri.withAppendedPath(
                ContactsContract.Profile.CONTENT_URI,
                ContactsContract.Contacts.Data.CONTENT_DIRECTORY,
            ),
            "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
        ).map { it.phone }
    } catch (e: SecurityException) {
        Timber.w(e, "The owner's profile could not be read")
        emptyList()
    }

    private fun phoneRows(uri: Uri, selection: String?, args: Array<String>?): List<ContactEntry> {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
            ),
            selection,
            args,
            null,
        ) ?: return emptyList()
        cursor.use { rows ->
            val found = mutableListOf<ContactEntry>()
            val nameIndex = rows.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = rows.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIndex = rows.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelIndex = rows.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
            while (rows.moveToNext()) {
                val name = if (nameIndex >= 0) rows.getString(nameIndex).orEmpty() else ""
                val phone = if (numberIndex >= 0) rows.getString(numberIndex).orEmpty() else ""
                val type = if (typeIndex >= 0) rows.getInt(typeIndex) else 0
                val custom = if (labelIndex >= 0) rows.getString(labelIndex) else null
                val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    context.resources,
                    type,
                    custom,
                ).toString()
                if (phone.isNotBlank()) found += ContactEntry(name, phone, label)
            }
            return found
        }
    }
}
