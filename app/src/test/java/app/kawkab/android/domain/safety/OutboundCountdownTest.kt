package app.kawkab.android.domain.safety

import app.kawkab.android.domain.models.RegisteredTools
import app.kawkab.android.domain.models.ToolRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Owner's vision rules 1 and 3: which outbound actions announce and run after 3 s. */
class OutboundCountdownTest {
    @Test
    fun `calls and messages in an open chat run after the window`() {
        listOf("call_phone", "send_sms", "send_message", "macro_send").forEach { tool ->
            assertTrue(
                tool,
                OutboundCountdown.applies(tool, ToolRisk.OUTBOUND, interactive = true, askedToConfirm = false),
            )
        }
        assertEquals(3_000L, OutboundCountdown.WINDOW_MS)
    }

    @Test
    fun `deleting, network, background runs and forced confirmations still ask`() {
        assertFalse(
            OutboundCountdown.applies("delete_file", ToolRisk.OUTBOUND, interactive = true, askedToConfirm = false),
        )
        assertFalse(
            OutboundCountdown.applies("http_request", ToolRisk.OUTBOUND, interactive = true, askedToConfirm = false),
        )
        assertFalse(
            OutboundCountdown.applies("call_phone", ToolRisk.OUTBOUND, interactive = false, askedToConfirm = false),
        )
        assertFalse(
            OutboundCountdown.applies("call_phone", ToolRisk.OUTBOUND, interactive = true, askedToConfirm = true),
        )
        assertFalse(InteractiveSessions.NONE.isInteractive("any"))
    }

    @Test
    fun `the confirmation table lists every outbound tool and matches the rule`() {
        val rows = File("../safety/outbound_confirmation.csv").readLines().drop(1).filter { it.isNotBlank() }
            .associate { line -> line.substringBefore(',') to line.split(',')[1] }
        val outbound = RegisteredTools.all.filter { it.risk == ToolRisk.OUTBOUND }.map { it.name }.toSet()
        assertEquals(outbound, rows.keys)
        assertEquals(OutboundCountdown.TOOLS, rows.filterValues { it == "announce_3s" }.keys)
        assertEquals("explicit_yes", rows["delete_file"])
    }
}
