package app.kawkab.android.hands

import app.kawkab.android.domain.models.RegisteredTools
import app.kawkab.android.domain.models.ToolApprovalPolicy
import app.kawkab.android.domain.models.ToolRisk
import app.kawkab.android.domain.safety.ActivityLog
import app.kawkab.android.domain.safety.ActivityLogEntry
import app.kawkab.android.domain.text.ClockText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/** Task 23i: local actions do not ask, can be undone for 10 seconds, and read times properly. */
class LocalActionsTest {

    @Test
    fun `alarm, timer, open app and calendar are LOCAL and never ask by default`() {
        listOf("set_alarm", "set_timer", "open_app", "add_calendar_event").forEach { tool ->
            assertEquals(tool, ToolRisk.LOCAL, RegisteredTools.risk(tool))
            assertFalse(tool, ToolApprovalPolicy.DEFAULT.requiresApproval(RegisteredTools.risk(tool)))
            assertFalse(tool, ToolApprovalPolicy.NeverPrompt.requiresApproval(ToolRisk.LOCAL))
        }
        assertTrue(ToolApprovalPolicy.AllCalls.requiresApproval(ToolRisk.LOCAL))
    }

    @Test
    fun `outbound tools still always ask`() {
        listOf("call_phone", "send_sms", "send_message", "delete_file").forEach { tool ->
            assertEquals(tool, ToolRisk.OUTBOUND, RegisteredTools.risk(tool))
            ToolApprovalPolicy.entries.forEach { policy ->
                assertTrue("$tool $policy", policy.requiresApproval(ToolRisk.OUTBOUND))
            }
        }
    }

    @Test
    fun `the committed classification lists the LOCAL tools`() {
        val csv = File("../safety/tool_classification.csv").readLines()
        listOf("set_alarm", "set_timer", "open_app", "add_calendar_event").forEach { tool ->
            assertTrue(tool, csv.contains("$tool,LOCAL,false"))
        }
    }

    @Test
    fun `undo within 10 seconds reverses the action and is logged`() {
        val hands = RecordingHands()
        val log = RecordingLog()
        val undo = LocalUndo(hands, log)
        undo.offer("set_alarm", mapOf("hour" to "6", "minute" to "0"), nowMs = 1_000)

        assertEquals("Removed the alarm.", undo.undo(nowMs = 5_000))

        assertEquals(listOf("set_alarm"), hands.undone)
        assertEquals("undo", log.entries.single().asked)
        assertEquals("set_alarm", log.entries.single().ran)
        assertNull(undo.state.value)
    }

    @Test
    fun `undo after 10 seconds does nothing, and outbound tools get no Undo`() {
        val hands = RecordingHands()
        val undo = LocalUndo(hands, RecordingLog())
        undo.offer("set_timer", emptyMap(), nowMs = 0)
        assertNull(undo.undo(nowMs = LocalUndo.WINDOW_MS + 1))
        assertTrue(hands.undone.isEmpty())

        undo.offer("call_phone", emptyMap(), nowMs = 0)
        assertNull(undo.state.value)
    }

    @Test
    fun `an expired chip clears only its own offer`() {
        val undo = LocalUndo(RecordingHands(), RecordingLog())
        undo.offer("set_alarm", emptyMap(), nowMs = 0)
        val first = undo.state.value!!
        undo.offer("set_timer", emptyMap(), nowMs = 1)
        undo.expire(first)
        assertEquals("set_timer", undo.state.value?.tool)
    }

    @Test
    fun `times read with minutes, AM or PM, and the locale's digits`() {
        assertEquals("6:00 AM", ClockText.format(6, 0, Locale.US))
        assertEquals("6:30 PM", ClockText.format(18, 30, Locale.US))
        assertEquals("12:05 AM", ClockText.format(0, 5, Locale.US))
        val arabic = ClockText.format(6, 0, Locale.forLanguageTag("ar"))
        assertTrue(arabic, arabic.startsWith("٦:٠٠"))
        assertFalse(arabic, arabic.contains("6:0"))
    }

    private class RecordingHands : PhoneHands {
        val undone = mutableListOf<String>()
        override fun undo(tool: String, args: Map<String, String>): String {
            undone += tool
            return if (tool == "set_alarm") "Removed the alarm." else "Undone."
        }
        override fun placeCall(number: String) = ""
        override fun composeSms(number: String, body: String) = ""
        override fun sendInApp(packageName: String, body: String) = ""
        override fun setAlarm(hour: Int, minute: Int, label: String) = ""
        override fun setTimer(seconds: Int) = ""
        override fun openApp(name: String) = ""
        override fun addCalendarEvent(title: String, startEpochMs: Long) = ""
        override fun readScreen() = ""
        override fun tap(text: String, viewId: String, confirmed: Boolean) = ""
    }

    private class RecordingLog : ActivityLog {
        val entries = mutableListOf<ActivityLogEntry>()
        override fun record(entry: ActivityLogEntry) {
            entries += entry
        }
        override fun entries(): List<ActivityLogEntry> = entries
        override fun export(): String = ""
    }
}
