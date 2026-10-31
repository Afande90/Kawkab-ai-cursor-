package app.kawkab.android.domain.fastpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Task 23h: quotes, trailing punctuation and emoji no longer hide a command from the fast path. */
class CommandTextTest {

    @Test
    fun `decoration is stripped`() {
        assertEquals("Set an alarm for 6", CommandText.stripDecoration("Set an alarm for 6\""))
        assertEquals("set an alarm for 6", CommandText.stripDecoration("“set an alarm for 6”"))
        assertEquals("set an alarm for 6", CommandText.stripDecoration("«set an alarm for 6»!"))
        assertEquals("set an alarm for 6", CommandText.stripDecoration("set an alarm for 6 ⏰"))
        assertEquals("صحيني الساعة 6", CommandText.stripDecoration("صحيني الساعة 6؟"))
        assertEquals("open WhatsApp", CommandText.stripDecoration("'open WhatsApp'."))
    }

    @Test
    fun `v2-style lines with quotes, punctuation and emoji parse as commands`() {
        listOf(
            "Set an alarm for 6\"",
            "\"set an alarm for 6\"",
            "set an alarm for 6.",
            "set an alarm for 6!",
            "set an alarm for 6?",
            "set an alarm for 6 🙏",
            "“open WhatsApp”",
            "open WhatsApp 👍",
            "«open WhatsApp»",
            "call Ahmad،",
        ).forEach { line ->
            assertNotNull("not matched: $line", CommandFastPath.parse(line))
        }
        assertEquals("set_alarm", CommandFastPath.parse("Set an alarm for 6\"")?.tool)
        assertEquals("6", CommandFastPath.parse("Set an alarm for 6\"")?.args?.get("hour"))
    }
}
