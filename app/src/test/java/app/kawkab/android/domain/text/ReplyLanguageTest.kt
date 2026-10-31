package app.kawkab.android.domain.text

import app.kawkab.android.domain.constants.DefaultPrompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Task 23g: one reply, in the user's language and dialect, with no label and no translation. */
class ReplyLanguageTest {

    private val evalFile = File("../evals/reply-language/v1/prompts.jsonl")

    @Test
    fun `the eval set is the frozen one`() {
        val digest = MessageDigest.getInstance("SHA-256").digest(evalFile.readBytes()).joinToString("") {
            "%02x".format(it)
        }
        assertEquals("82660d85c96bbd21680d7b1fd8977f3d400aacf26cd4e3b54b88209d5a8848af", digest)
    }

    @Test
    fun `every frozen prompt is detected in its own language`() {
        val rows = evalFile.readLines().filter { it.isNotBlank() }
        assertEquals(20, rows.size)
        val misses = rows.mapNotNull { row ->
            val expect = Regex("\"expect\": \"(\\w+)\"").find(row)!!.groupValues[1]
            val text = Regex("\"text\": \"([^\"]+)\"").find(row)!!.groupValues[1]
            val wanted = mapOf(
                "sd" to ReplyLanguage.SUDANESE,
                "msa" to ReplyLanguage.MSA,
                "en" to ReplyLanguage.ENGLISH,
            )[expect]
            if (ReplyLanguage.of(text) == wanted) null else "$text -> ${ReplyLanguage.of(text)}, expected $wanted"
        }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

    @Test
    fun `labelled translations become one reply in the user's script`() {
        val reply = "Modern Standard Arabic: الساعة التاسعة والنصف الآن.\nEnglish: It is half past nine now."
        assertEquals("الساعة التاسعة والنصف الآن.", ReplyLanguage.keepOne(reply, "يا زول الساعة كم هسي؟"))
        assertEquals("It is half past nine now.", ReplyLanguage.keepOne(reply, "what time is it?"))
        val bold = "**English:** Hello.\n**العربية:** مرحباً."
        assertEquals("مرحباً.", ReplyLanguage.keepOne(bold, "مرحبا"))
    }

    @Test
    fun `a reply with no labels is untouched`() {
        val reply = "Alarm set for 6:00 AM.\nTime: tomorrow"
        assertEquals(reply, ReplyLanguage.keepOne(reply, "set an alarm"))
    }

    @Test
    fun `the prompts say reply once, never label, never translate, with a Sudanese example`() {
        val preset = File("src/main/assets/presets/pipelines/kawkab_assistant.json").readText()
        listOf(DefaultPrompts.SYSTEM_PROMPT_PREFIX, preset).forEach { text ->
            assertTrue(text.contains("Reply once, in the same language and dialect the user wrote in"))
            assertTrue(text.contains("Never label the language. Never translate unless asked."))
        }
        assertTrue(preset.contains("يا زول"))
        assertFalse("the assistant lists its phone tools, not every tool", preset.contains("\$TOOLS"))
        assertTrue(preset.contains("set_alarm(hour, minute)"))
    }
}
