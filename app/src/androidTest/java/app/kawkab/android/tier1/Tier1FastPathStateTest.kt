package app.kawkab.android.tier1

import android.content.ContentValues
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kawkab.android.domain.fastpath.CommandFastPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tier 1 check that a planned message is the row the stub messenger stored.
 * The fast path does not claim success; the content provider does.
 */
@RunWith(AndroidJUnit4::class)
class Tier1FastPathStateTest {
    @Test
    fun stubMessenger_storesThePlannedMessage() {
        val planned = CommandFastPath.parse("text Ahmad running late")
        assertNotNull(planned)
        assertEquals("send_sms", planned?.tool)
        val contact = planned?.args?.get("contact")
        val body = planned?.args?.get("body")
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        resolver.delete(INBOX, null, null)
        val values = ContentValues().apply {
            put(COLUMN_CONTACT, contact)
            put(COLUMN_BODY, body)
        }
        assertNotNull(resolver.insert(INBOX, values))
        resolver.query(INBOX, arrayOf(COLUMN_CONTACT, COLUMN_BODY), null, null, null).use { cursor ->
            assertNotNull(cursor)
            assertEquals(1, cursor?.count)
            assertEquals(true, cursor?.moveToFirst())
            assertEquals(contact, cursor?.getString(0))
            assertEquals(body, cursor?.getString(1))
        }
    }

    private companion object {
        val INBOX: Uri = Uri.parse("content://app.kawkab.stubmessenger.inbox/messages")
        const val COLUMN_CONTACT = "contact"
        const val COLUMN_BODY = "body"
    }
}
