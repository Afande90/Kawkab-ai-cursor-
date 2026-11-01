package app.kawkab.android.domain.phoneaccess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Tasks 23, 27 and 29: ten items, a "5 of 10 on" summary, and a banner count without the optional wake word and voice confirmation. */
class PhoneAccessSummaryTest {

    @Test
    fun `the ten items are in the owner's order`() {
        assertEquals(
            listOf(
                "CONTACTS",
                "PHONE",
                "CALENDAR",
                "NOTIFICATIONS",
                "MICROPHONE",
                "ACCESSIBILITY",
                "BATTERY",
                "ASSISTANT",
                "WAKE_WORD",
                "VOICE_CONFIRM",
            ),
            PhoneAccessItem.entries.map { it.name },
        )
        assertNull(PhoneAccessStatus.permission(PhoneAccessItem.ASSISTANT))
        assertNull(PhoneAccessStatus.permission(PhoneAccessItem.WAKE_WORD))
    }

    @Test
    fun `summary and off count`() {
        val on = PhoneAccessItem.entries.take(5).associateWith { true }
        val lines = PhoneAccessStatus.lines(on)
        assertEquals(5 to 10, PhoneAccessStatus.summary(lines))
        // The wake word and voice confirmation are optional: off, they do not count as missing.
        assertEquals(3, PhoneAccessStatus.offCount(lines))
    }

    @Test
    fun `the progress and banner text exist in both languages`() {
        listOf("values", "values-ar").forEach { dir ->
            val strings = File("src/main/res/$dir/strings_phone_access.xml").readText()
            listOf(
                "phone_access_progress",
                "phone_access_banner",
                "phone_access_item_assistant",
                "phone_access_item_wake",
            ).forEach { key ->
                assertEquals("$dir $key", true, strings.contains("name=\"$key\""))
            }
        }
    }
}
