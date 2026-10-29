package app.kawkab.android.domain.phoneaccess

import app.kawkab.android.hands.ContactBook
import app.kawkab.android.hands.ContactLookup
import app.kawkab.android.hands.ContactMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Status lines and the card a command shows when a grant is missing.
 */
class PhoneAccessStatusTest {
    @Test
    fun linesKeepEveryItemAndTreatAMissingAnswerAsOff() {
        val lines = PhoneAccessStatus.lines(mapOf(PhoneAccessItem.PHONE to true))
        assertEquals(PhoneAccessItem.entries.toList(), lines.map { it.item })
        assertTrue(lines.first { it.item == PhoneAccessItem.PHONE }.on)
        assertFalse(lines.first { it.item == PhoneAccessItem.CONTACTS }.on)
    }

    @Test
    fun serviceOnMatchesTheClassAfterTheSlash() {
        val enabled = "com.other/.Other:app.kawkab.android/.KawkabAccessibilityService"
        assertFalse(PhoneAccessStatus.serviceOn(enabled, PhoneAccessStatus.ACCESSIBILITY_SERVICE))
        val ours = "app.kawkab.android/app.kawkab.android.accessibility.KawkabAccessibilityService"
        assertTrue(PhoneAccessStatus.serviceOn(ours, PhoneAccessStatus.ACCESSIBILITY_SERVICE))
        assertFalse(PhoneAccessStatus.serviceOn(null, PhoneAccessStatus.ACCESSIBILITY_SERVICE))
    }

    @Test
    fun runtimeItemsHavePermissionsAndSettingsDoNot() {
        assertEquals(MissingPermission.CALL_PHONE, PhoneAccessStatus.permission(PhoneAccessItem.PHONE))
        assertNull(PhoneAccessStatus.permission(PhoneAccessItem.ACCESSIBILITY))
        assertNull(PhoneAccessStatus.permission(PhoneAccessItem.BATTERY))
    }

    @Test
    fun callCardNamesTheFirstMissingPermission() {
        val named = MissingPermission.card("call_phone", emptySet(), spoken = "Ahmad")
        assertEquals(MissingPermission.CALL_PHONE, named?.permission)
        assertEquals("call_phone", named?.tool)
        val contactsOnly = MissingPermission.card("call_phone", setOf(MissingPermission.CALL_PHONE), "Ahmad")
        assertEquals(MissingPermission.READ_CONTACTS, contactsOnly?.permission)
        val number = MissingPermission.card("call_phone", emptySet(), "+971501234567")
        assertEquals(MissingPermission.CALL_PHONE, number?.permission)
        assertNull(MissingPermission.card("call_phone", setOf(MissingPermission.CALL_PHONE), "+971501234567"))
    }

    @Test
    fun messageAndCalendarCardsStayQuietWhenTheGrantIsPresent() {
        assertEquals(
            MissingPermission.READ_CONTACTS,
            MissingPermission.card("send_sms", emptySet(), "Ahmad")?.permission,
        )
        assertNull(MissingPermission.card("send_sms", emptySet(), "0501234567"))
        assertEquals(
            MissingPermission.WRITE_CALENDAR,
            MissingPermission.card("add_calendar_event", emptySet())?.permission,
        )
        assertNull(MissingPermission.card("add_calendar_event", setOf(MissingPermission.WRITE_CALENDAR)))
        assertNull(MissingPermission.card("open_app", emptySet()))
    }

    @Test
    fun sentenceRoundTripsToThePermission() {
        val card = MissingPermissionCard(MissingPermission.WRITE_CALENDAR, "add_calendar_event")
        assertEquals(MissingPermission.WRITE_CALENDAR, MissingPermission.permissionIn(card.sentence()))
        assertNull(MissingPermission.permissionIn("Contact 'Ahmad' was not found."))
    }

    @Test
    fun aNameWithoutContactsReturnsTheCardInsteadOfNotFound() {
        var shown = ""
        val number = ContactLookup.numberOrNull("Ahmad", ClosedBook) { shown = it }
        assertNull(number)
        assertEquals(MissingPermission.READ_CONTACTS, MissingPermission.permissionIn(shown))
    }

    private object ClosedBook : ContactBook {
        override fun resolve(spoken: String): List<ContactMatch> = emptyList()

        override fun canRead(): Boolean = false
    }
}
