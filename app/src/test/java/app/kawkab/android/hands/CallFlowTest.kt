package app.kawkab.android.hands

import app.kawkab.android.domain.phoneaccess.GrantRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task 23j: contact matching, the pending choice, the grant route and the dialer fallback. */
class CallFlowTest {

    private val book = listOf(
        ContactEntry("Abdalla Mubadala", "+249 912 345 678", "Mobile"),
        ContactEntry("Abdalla Mubadala", "0912345678", "Mobile"),
        ContactEntry("Abdalla Osman", "+249 911 000 111", "Work"),
        ContactEntry("Abdallah Ali", "+249 922 111 222", "Mobile"),
        ContactEntry("Ibrahim Hassan", "+249 933 222 333", "Mobile"),
        ContactEntry("Hashim Musa", "+249 944 333 444", "Mobile"),
        ContactEntry("Me", "+249 955 444 555", "Mobile"),
    )

    @Test
    fun `one number written twice is one choice`() {
        val matches = ContactMatcher.match("abdalla mubadala", book)
        assertEquals(1, matches.size)
        assertEquals("Abdalla Mubadala", matches.single().name)
    }

    @Test
    fun `a word must be a whole name word or its start`() {
        assertEquals(
            listOf("Abdalla Mubadala", "Abdalla Osman", "Abdallah Ali"),
            ContactMatcher.match("abdalla", book).map {
                it.name
            }.sorted(),
        )
        assertTrue(ContactMatcher.match("him", book).isEmpty())
        assertTrue(ContactMatcher.match("ash", book).isEmpty())
        assertEquals("Ibrahim Hassan", ContactMatcher.match("ibra", book).single().name)
    }

    @Test
    fun `the owner's own number is never offered`() {
        assertTrue(ContactMatcher.match("me", book, ownNumbers = listOf("00249955444555")).isEmpty())
    }

    @Test
    fun `Arabic spellings with and without hamza match`() {
        val arabic = listOf(ContactEntry("أحمد عبدالله", "0911111111"))
        assertEquals(1, ContactMatcher.match("احمد", arabic).size)
        assertEquals(1, ContactMatcher.match("الأحمد", arabic).size)
    }

    @Test
    fun `pronouns mean the last resolved contact`() {
        assertTrue(ContactMatcher.isPronoun("him"))
        assertTrue(ContactMatcher.isPronoun("هو"))
        assertFalse(ContactMatcher.isPronoun("Ibrahim"))
        val last = ContactMatch("Abdalla Mubadala", "+249912345678")
        val number = ContactLookup.numberOrNull("him", FakeBook(book), lastResolved = last) { error(it) }
        assertEquals("+249912345678", number)
        var failure = ""
        assertNull(ContactLookup.numberOrNull("him", FakeBook(book)) { failure = it })
        assertTrue(failure.startsWith("Who do you mean"))
    }

    @Test
    fun `several matches become choices and the sentence lists no number`() {
        var offered = emptyList<ContactMatch>()
        var failure = ""
        val number = ContactLookup.numberOrNull("abdalla", FakeBook(book), onChoices = {
            offered = it
        }) { failure = it }
        assertNull(number)
        assertEquals(3, offered.size)
        assertFalse(failure.contains("912"))
    }

    @Test
    fun `a follow-up name, an ordinal, or a chip completes the pending call`() {
        val choices = ContactMatcher.match("abdalla", book)
        val choice = ContactChoice()

        choice.offer("call_phone", emptyMap(), choices, nowMs = 0)
        val byName = choice.answer("the mubadala", nowMs = 1_000) as ContactAnswer.Picked
        assertEquals("Abdalla Mubadala", byName.contact.name)
        assertEquals("call_phone", byName.tool)
        assertEquals(byName.contact.phone, byName.args["contact"])
        assertEquals(byName.contact, choice.lastResolved)
        assertNull(choice.state.value)

        choice.offer("call_phone", emptyMap(), choices, nowMs = 0)
        val second = choice.answer("the second one", nowMs = 1_000) as ContactAnswer.Picked
        assertEquals(choices[1], second.contact)

        choice.offer("send_sms", mapOf("body" to "I'm late"), choices, nowMs = 0)
        val chip = choice.answer(ContactChip.text(choices[2]), nowMs = 1_000) as ContactAnswer.Picked
        assertEquals(choices[2], chip.contact)
        assertEquals("I'm late", chip.args["body"])
    }

    @Test
    fun `cancel, an unclear line, and the two-minute limit`() {
        val choices = ContactMatcher.match("abdalla", book)
        val choice = ContactChoice()
        choice.offer("call_phone", emptyMap(), choices, nowMs = 0)
        assertEquals(ContactAnswer.Unclear, choice.answer("what is the weather", nowMs = 1))
        assertEquals(ContactAnswer.Cancelled, choice.answer("cancel", nowMs = 2))
        assertEquals(ContactAnswer.NotPending, choice.answer("abdalla osman", nowMs = 3))
        choice.offer("call_phone", emptyMap(), choices, nowMs = 0)
        assertEquals(ContactAnswer.NotPending, choice.answer("abdalla osman", nowMs = ContactChoice.TIMEOUT_MS + 1))
    }

    @Test
    fun `a chip shows the last four digits and the label, not the number`() {
        assertEquals(
            "Abdalla Mubadala ·5678 Mobile",
            ContactChip.text(ContactMatch("Abdalla Mubadala", "+249 912 345 678", "Mobile")),
        )
    }

    @Test
    fun `a permanently denied permission opens App info`() {
        assertEquals(GrantRoute.RESUME, GrantRoute.next(granted = true, canShowDialog = false, deniedBefore = true))
        assertEquals(GrantRoute.ASK, GrantRoute.next(granted = false, canShowDialog = false, deniedBefore = false))
        assertEquals(GrantRoute.ASK, GrantRoute.next(granted = false, canShowDialog = true, deniedBefore = true))
        assertEquals(
            GrantRoute.OPEN_APP_SETTINGS,
            GrantRoute.next(granted = false, canShowDialog = false, deniedBefore = true),
        )
    }

    @Test
    fun `without CALL_PHONE a call uses the dialer`() {
        assertTrue(CallFallback.usesDialer(callPhoneGranted = false))
        assertFalse(CallFallback.usesDialer(callPhoneGranted = true))
        assertFalse(CallFallback.DIALER_OPENED.contains("Missing permission"))
    }

    private class FakeBook(private val entries: List<ContactEntry>) : ContactBook {
        override fun resolve(spoken: String): List<ContactMatch> = ContactMatcher.match(spoken, entries)
    }
}
