package app.kawkab.android.domain.safety

import app.kawkab.android.domain.models.ToolRisk
import app.kawkab.android.hands.AliasResolver
import app.kawkab.android.hands.HabitRoutineEngine
import app.kawkab.android.hands.HistoryRow
import app.kawkab.android.hands.MacroRecording
import app.kawkab.android.hands.MacroStep
import app.kawkab.android.hands.StepReplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tap guard, denylist, activity log, macro replay, and habit detection.
 */
class HandsSafetyTest {
    @Test
    fun `pattern file matches the asset copy and lists the required needles`() {
        val root = File("../safety/outbound_patterns.txt").readText()
        val asset = File("src/main/assets/safety/outbound_patterns.txt").readText()
        assertEquals(root, asset)
        val guard = OutboundTapGuard.fromText(root)
        listOf(
            "send", "post", "pay", "buy", "share", "submit", "transfer", "confirm order",
            "ارسال", "إرسال", "ابعت", "دفع", "شراء", "تحويل", "مشاركة", "تأكيد",
        ).forEach { needle ->
            assertTrue(needle, guard.matches(needle, null, null))
        }
    }

    @Test
    fun `a send label needs a tap and a plain label does not`() {
        val guard = OutboundTapGuard.fromText(File("../safety/outbound_patterns.txt").readText())
        val send = TapTarget("Send", null, "com.example:id/send_button")
        assertEquals(TapDecision.NEEDS_TAP, GuardedTap.decision(guard, send, confirmed = false))
        assertEquals(TapDecision.ALLOW, GuardedTap.decision(guard, send, confirmed = true))
        val plain = TapTarget("Photos", "gallery", "com.example:id/photos")
        assertEquals(TapDecision.ALLOW, GuardedTap.decision(guard, plain, confirmed = false))
        assertEquals(TapDecision.NEEDS_TAP, GuardedTap.decision(guard, TapTarget("إرسال", null, null), false))
    }

    @Test
    fun `denylist blocks a wallet a secure window a card an otp a checkout and a payment link`() {
        val denylist = ActionDenylist.fromText(File("../safety/package_denylist.txt").readText())
        val wallet = denylist.inspectScreen(ScreenSnapshot("com.google.android.apps.walletnfcrel", false, false, ""))
        assertEquals(BlockLayer.PACKAGE, wallet?.layer)
        val settings = denylist.inspectScreen(
            ScreenSnapshot("com.android.settings", false, false, "Choose screen lock"),
        )
        assertEquals(BlockLayer.PACKAGE, settings?.layer)
        val home = denylist.inspectScreen(ScreenSnapshot("com.android.settings", false, false, "Wi-Fi"))
        assertNull(home)
        val secure = denylist.inspectScreen(ScreenSnapshot("com.example.app", true, false, "hello"))
        assertEquals(BlockLayer.HEURISTIC, secure?.layer)
        val password = denylist.inspectScreen(ScreenSnapshot("com.example.app", false, true, "hello"))
        assertEquals(BlockLayer.HEURISTIC, password?.layer)
        val card = denylist.inspectScreen(ScreenSnapshot("com.example.shop", false, false, "4242 4242 4242 4242"))
        assertEquals(BlockLayer.HEURISTIC, card?.layer)
        val otp = denylist.inspectScreen(ScreenSnapshot("com.example.shop", false, false, "Your OTP is 123456"))
        assertEquals(BlockLayer.HEURISTIC, otp?.layer)
        val checkout = denylist.inspectScreen(ScreenSnapshot("com.example.shop", false, false, "Pay now"))
        assertEquals(BlockLayer.HEURISTIC, checkout?.layer)
        val iban = denylist.inspectScreen(ScreenSnapshot("com.example.bank", false, false, "AE070331234567890123456"))
        assertEquals(BlockLayer.HEURISTIC, iban?.layer)
        assertEquals(BlockLayer.INTENT, denylist.inspectIntent("com.paypal.android.p2pmobile", null)?.layer)
        assertEquals(BlockLayer.INTENT, denylist.inspectIntent(null, "upi://pay?pa=shop")?.layer)
        assertNull(denylist.inspectIntent(null, "tel:0501234567"))
        assertNull(denylist.inspectIntent(null, "smsto:0501234567"))
    }

    @Test
    fun `editing the denylist without a device confirmation is refused`() {
        val store = DenylistStore(setOf("com.example.bank"))
        try {
            store.replace(emptySet(), DeviceConfirmation { false })
            error("expected a refusal")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("biometric"))
        }
        assertEquals(setOf("com.example.bank"), store.snapshot())
        store.replace(setOf("com.example.other"), DeviceConfirmation { true })
        assertEquals(setOf("com.example.other"), store.snapshot())
    }

    @Test
    fun `the activity log round trips and an unconfirmed outbound action does not run`() {
        val file = File.createTempFile("activity", ".jsonl")
        file.deleteOnExit()
        val log = FileActivityLog(file)
        val denylist = ActionDenylist.fromText("com.example.bank\n")
        val runner = ActionRunner(denylist, log, ScreenWatch(), clock = { 10L })
        var calls = 0
        val needsTap = runner.run("call Ahmad", "call_phone", ToolRisk.OUTBOUND, "tool", confirmed = false) {
            calls += 1
            "called"
        }
        assertEquals(ActionRunner.NEEDS_TAP, needsTap)
        assertEquals(0, calls)
        assertTrue(log.entries().isEmpty())

        val done = runner.run("open photos", "open_app", ToolRisk.SENSITIVE, "tool", confirmed = false) {
            calls += 1
            "opened"
        }
        assertEquals("opened", done)
        assertEquals(1, calls)
        val entry = log.entries().single()
        assertEquals("open_app", entry.ran)
        assertEquals("none", entry.confirmation)
        assertFalse(log.export().contains("http"))

        val watch = ScreenWatch()
        watch.latest = ScreenSnapshot("com.example.bank", false, false, "")
        val blockedRunner = ActionRunner(denylist, log, watch, clock = { 11L })
        val blocked = blockedRunner.run("call", "call_phone", ToolRisk.OUTBOUND, "tool", confirmed = true) {
            calls += 1
            "called"
        }
        assertTrue(blocked.startsWith("Blocked"))
        assertEquals(1, calls)
        assertEquals(BlockLayer.PACKAGE.name, log.entries().last().confirmation)
    }

    @Test
    fun `alias resolver macro recording habit detection and replay keep their gates`() {
        assertEquals("TOGGLE_FLASHLIGHT", AliasResolver.resolve("turn on flashlight")?.action)
        assertNull(AliasResolver.resolve("open WhatsApp and send hello"))
        assertEquals("6", AliasResolver.extractAlarmTime("wake me at 6"))
        assertEquals(300, AliasResolver.extractTimerDuration("set a timer for 5 minutes"))

        assertNull(
            MacroRecording.stepsFromCompleted(listOf(HistoryRow("open_app", emptyMap(), success = false, order = 1))),
        )
        val steps = MacroRecording.stepsFromCompleted(
            listOf(
                HistoryRow("read_screen", emptyMap(), success = true, order = 2),
                HistoryRow("open_app", mapOf("name" to "Clock"), success = true, order = 1),
            ),
        )
        assertEquals(listOf("open_app", "read_screen"), steps?.map { it.tool })

        val habits = HabitRoutineEngine()
        val day = 86_400_000L
        repeat(3) { index ->
            val base = 1_700_000_000_000L + index * day
            habits.recordAction("open_app", base)
            habits.recordAction("read_screen", base + 60_000L)
        }
        assertEquals(1, habits.detect().size)

        val file = File.createTempFile("replay", ".jsonl")
        val runner = ActionRunner(ActionDenylist.fromText(""), FileActivityLog(file), ScreenWatch(), clock = { 1L })
        var calls = 0
        val replay = StepReplay({ ToolRisk.OUTBOUND }, runner)
        val refused = replay.play(listOf(MacroStep("send_sms", emptyMap())), confirmed = false, "macro") {
            calls += 1
            "sent"
        }
        assertEquals(ActionRunner.NEEDS_TAP, refused)
        assertEquals(0, calls)
        val played = replay.play(listOf(MacroStep("send_sms", emptyMap())), confirmed = true, "skill") {
            calls += 1
            "sent"
        }
        assertEquals("Replayed 1 steps.", played)
        assertEquals(1, calls)
        assertEquals("tap", FileActivityLog(file).entries().single().confirmation)
    }
}
