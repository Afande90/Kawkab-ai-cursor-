package app.kawkab.android.hands

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the owner's contacts when READ_CONTACTS has been granted. Otherwise it returns nothing.
 */
@Singleton
class AndroidContactBook @Inject constructor(@ApplicationContext private val context: Context) : ContactBook {
    override fun resolve(spoken: String): List<ContactMatch> {
        val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
        if (granted != PackageManager.PERMISSION_GRANTED) return emptyList()
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            selection,
            arrayOf("%$spoken%"),
            null,
        ) ?: return emptyList()
        cursor.use { rows ->
            val found = mutableListOf<ContactMatch>()
            val nameIndex = rows.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = rows.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (rows.moveToNext()) {
                val name = if (nameIndex >= 0) rows.getString(nameIndex).orEmpty() else ""
                val phone = if (numberIndex >= 0) rows.getString(numberIndex).orEmpty() else ""
                if (phone.isNotBlank()) found += ContactMatch(name, phone)
            }
            return found.distinctBy { it.phone }
        }
    }
}
