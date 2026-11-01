package app.kawkab.android.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

/** Task 28: replies are spoken sentence by sentence. */
class SpokenSentencesTest {
    @Test
    fun `english and arabic sentence ends split`() {
        assertEquals(
            listOf("Alarm set for 6:00.", "Anything else?"),
            SpokenSentences.split("Alarm set for 6:00. Anything else?"),
        )
        assertEquals(
            listOf("تم ضبط المنبه.", "داير شي تاني؟", "تمام"),
            SpokenSentences.split("تم ضبط المنبه. داير شي تاني؟ تمام"),
        )
    }

    @Test
    fun `line breaks end sentences and markdown marks are not read`() {
        assertEquals(
            listOf("Two matches", "Abdalla Mubadala", "Abdalla Osman"),
            SpokenSentences.split("**Two matches**\n- Abdalla Mubadala\n- Abdalla Osman"),
        )
    }

    @Test
    fun `times and decimals stay whole`() {
        assertEquals(
            listOf("Timer for 2.5 minutes at 6:30 started."),
            SpokenSentences.split("Timer for 2.5 minutes at 6:30 started."),
        )
    }

    @Test
    fun `blank text gives nothing`() {
        assertEquals(emptyList<String>(), SpokenSentences.split("  \n "))
    }
}
