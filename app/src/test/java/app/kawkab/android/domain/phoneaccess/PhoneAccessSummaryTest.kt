package app.kawkab.android.domain.phoneaccess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Task 23: eight items, a "5 of 8 on" summary, and the banner count. */
class PhoneAccessSummaryTest {

    @Test
    fun `the eight items are in the owner's order`() {
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
            ),
            PhoneAccessItem.entries.map { it.name },
        )
        assertNull(PhoneAccessStatus.permission(PhoneAccessItem.ASSISTANT))
    }

    @Test
    fun `summary and off count`() {
        val on = PhoneAccessItem.entries.take(5).associateWith { true }
        val lines = PhoneAccessStatus.lines(on)
        assertEquals(5 to 8, PhoneAccessStatus.summary(lines))
        assertEquals(3, PhoneAccessStatus.offCount(lines))
    }

    @Test
    fun `the progress and banner text exist in both languages`() {
        listOf("values", "values-ar").forEach { dir ->
            val strings = File("src/main/res/$dir/strings_phone_access.xml").readText()
            listOf("phone_access_progress", "phone_access_banner", "phone_access_item_assistant").forEach { key ->
                assertEquals("$dir $key", true, strings.contains("name=\"$key\""))
            }
        }
    }
}
