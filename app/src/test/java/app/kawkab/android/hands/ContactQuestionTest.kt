package app.kawkab.android.hands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Task 29: contact choices are read aloud as a short numbered list of names. */
class ContactQuestionTest {
    @Test
    fun `the question numbers the names and never lists phone numbers`() {
        val question = ContactChoice.question("Abdalla", 2, listOf("Abdalla Mubadala", "Abdalla Osman"))
        assertEquals(
            "2 contacts match 'Abdalla'. Which one? 1, Abdalla Mubadala. 2, Abdalla Osman. " +
                "Say the number or the name, tap one, or say cancel.",
            question,
        )
        assertFalse(question.any { it.isDigit() && it !in "12" })
    }

    @Test
    fun `the numbers it reads are the ones an answer can use`() {
        val choice = ContactChoice()
        choice.offer(
            "call_phone",
            emptyMap(),
            listOf(ContactMatch("Abdalla Mubadala", "0911"), ContactMatch("Abdalla Osman", "0922")),
            nowMs = 0,
        )
        val answer = choice.answer("the second one", nowMs = 1_000) as ContactAnswer.Picked
        assertEquals("Abdalla Osman", answer.contact.name)
    }
}
