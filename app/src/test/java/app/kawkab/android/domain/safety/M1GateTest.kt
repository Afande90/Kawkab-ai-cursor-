package app.kawkab.android.domain.safety

import app.kawkab.android.domain.models.ToolApprovalPolicy
import app.kawkab.android.domain.models.ToolRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Milestone gates for outbound confirmation, the three denylist layers, and the activity log.
 */
class M1GateTest {
    @Test
    fun `every approval policy still asks for an outbound tool`() {
        val outbound = File("../safety/tool_classification.csv").readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split(",") }
            .filter { it[1] == ToolRisk.OUTBOUND.name }
            .map { it[0] }
        listOf("call_phone", "send_sms", "send_message", "macro_send", "submit_tap", "delete_file").forEach { name ->
            assertTrue(name, name in outbound)
        }
        ToolApprovalPolicy.entries.forEach { policy ->
            assertTrue(policy.name, policy.requiresApproval(ToolRisk.OUTBOUND))
        }
    }

    @Test
    fun `tool skill macro routine and tap all stop without a confirmation`() {
        val file = File.createTempFile("m1-gate", ".jsonl")
        file.deleteOnExit()
        val log = FileActivityLog(file)
        val runner = ActionRunner(ActionDenylist.fromText(""), log, ScreenWatch(), clock = { 5L })
        listOf("tool", "skill", "macro", "routine", "tap").forEach { entry ->
            var calls = 0
            val result = runner.run("text Ahmad hi", "send_sms", ToolRisk.OUTBOUND, entry, confirmed = false) {
                calls += 1
                "sent"
            }
            assertEquals(entry, ActionRunner.NEEDS_TAP, result)
            assertEquals(entry, 0, calls)
        }
        assertTrue(log.entries().isEmpty())
        val guard = OutboundTapGuard.fromText(File("../safety/outbound_patterns.txt").readText())
        val decision = GuardedTap.decision(
            guard,
            TapTarget("Send", null, "app.kawkab.stubmessenger:id/send_button"),
            confirmed = false,
        )
        assertEquals(TapDecision.NEEDS_TAP, decision)
    }

    @Test
    fun `the denylist blocks each layer and the log records the action`() {
        val denylist = ActionDenylist.fromText(File("../safety/package_denylist.txt").readText())
        assertEquals(
            BlockLayer.PACKAGE,
            denylist.inspectScreen(ScreenSnapshot("com.google.android.apps.walletnfcrel", false, false, ""))?.layer,
        )
        assertEquals(
            BlockLayer.HEURISTIC,
            denylist.inspectScreen(ScreenSnapshot("com.example.shop", false, false, "Pay now"))?.layer,
        )
        assertEquals(BlockLayer.INTENT, denylist.inspectIntent(null, "upi://pay?pa=shop")?.layer)

        val file = File.createTempFile("m1-log", ".jsonl")
        file.deleteOnExit()
        val log = FileActivityLog(file)
        val runner = ActionRunner(denylist, log, ScreenWatch(), clock = { 6L })
        val opened = runner.run("open Clock", "open_app", ToolRisk.SENSITIVE, "tool", confirmed = false) { "opened" }
        assertEquals("opened", opened)
        val entry = log.entries().single()
        assertEquals("open Clock", entry.asked)
        assertEquals("open_app", entry.ran)
        assertEquals("tool", entry.entryPoint)
        assertEquals("opened", entry.result)
        assertEquals("none", entry.confirmation)
        assertTrue(log.export().isNotBlank())
    }
}
