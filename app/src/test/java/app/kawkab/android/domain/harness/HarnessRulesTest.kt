package app.kawkab.android.domain.harness

import app.kawkab.android.domain.models.ChatMessage
import app.kawkab.android.domain.models.MessageAttachment
import app.kawkab.android.domain.models.Role
import app.kawkab.android.domain.models.ToolRisk
import app.kawkab.android.domain.safety.ActionDenylist
import app.kawkab.android.domain.safety.ActionRunner
import app.kawkab.android.domain.safety.FileActivityLog
import app.kawkab.android.domain.safety.ScreenWatch
import app.kawkab.android.presentation.ui.growth.GrowthViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Planner decisions, history, breakers, the skill cache, and the extra biometric check.
 */
class HarnessRulesTest {
    @Test
    fun `one typed decision is accepted and plain text is not`() {
        val act = HarnessParser.parse("ACT open_app name=Phone")
        assertTrue(act is HarnessDecision.Act)
        assertEquals("open_app", (act as HarnessDecision.Act).tool)
        assertEquals("Phone", act.args["name"])
        assertTrue(HarnessParser.parse("REPLY On my way") is HarnessDecision.Reply)
        assertTrue(HarnessParser.parse("FINISH Done") is HarnessDecision.Finish)
        assertTrue(HarnessParser.parse("please open the phone") is HarnessDecision.Invalid)
        assertTrue(HarnessParser.parse("ACT open_app\nACT call_phone") is HarnessDecision.Invalid)
    }

    @Test
    fun `history keeps the goal and the last nine steps`() {
        val history = SlidingHistory("call Mom")
        repeat(10) { index -> history.add(HarnessStep("step $index", transientError = false)) }
        val rendered = history.render()
        assertTrue(rendered.startsWith("Goal: call Mom"))
        assertFalse(rendered.contains("step 0"))
        assertTrue(rendered.contains("step 9"))
    }

    @Test
    fun `a success drops transient errors`() {
        val history = SlidingHistory("open maps")
        history.add(HarnessStep("timeout", transientError = true))
        history.add(HarnessStep("opened", transientError = false))
        history.success()
        val rendered = history.render()
        assertFalse(rendered.contains("timeout"))
        assertTrue(rendered.contains("opened"))
    }

    @Test
    fun `each error tier names its own recovery`() {
        assertEquals(ErrorTier.TRANSIENT, ErrorRecovery.classify("timeout"))
        assertEquals(ErrorTier.UNKNOWN_TOOL, ErrorRecovery.classify("unknown tool flashlight"))
        assertEquals(ErrorTier.ARGUMENTS, ErrorRecovery.classify("missing argument"))
        assertEquals(ErrorTier.STOP, ErrorRecovery.classify("denied by the denylist"))
        assertTrue(ErrorRecovery.recovery(ErrorTier.TRANSIENT).contains("Retry"))
        assertTrue(ErrorRecovery.recovery(ErrorTier.STOP).contains("owner"))
    }

    @Test
    fun `five failures or the same unknown tool three times opens the breaker`() {
        val failures = CircuitBreaker()
        repeat(4) { assertFalse(failures.recordFailure()) }
        assertTrue(failures.recordFailure())
        val reset = CircuitBreaker()
        reset.recordFailure()
        reset.recordSuccess()
        assertFalse(reset.recordFailure())
        val unknown = CircuitBreaker()
        assertFalse(unknown.recordUnknown("flashlight"))
        assertFalse(unknown.recordUnknown("flashlight"))
        assertTrue(unknown.recordUnknown("flashlight"))
    }

    @Test
    fun `an invalid planner line counts toward the breaker`() {
        val breaker = CircuitBreaker()
        val known = setOf("open_app")
        repeat(4) { assertTrue(HarnessLoop.accept("just talk", known, breaker) is HarnessDecision.Invalid) }
        assertTrue(HarnessLoop.accept("just talk", known, breaker) is HarnessDecision.Finish)
    }

    @Test
    fun `a saved plan replays with no extra success and can be deleted`() {
        val cache = SkillCache()
        cache.remember("water the plants", "open_app", mapOf("name" to "Plants"))
        cache.remember("water the plants", "open_app", mapOf("name" to "Plants"))
        cache.remember("call Mom", "call_phone", mapOf("contact" to "Mom"))
        assertEquals("water the plants", cache.ranked.value.first().key)
        assertEquals(2, cache.replay("water the plants")?.successes)
        val view = GrowthViewModel(cache)
        view.delete("water the plants")
        assertNull(cache.replay("water the plants"))
        assertEquals(1, cache.ranked.value.size)
    }

    @Test
    fun `the owner line and stated facts sit above the prompt`() {
        val facts = HarnessFacts()
        facts.state("Lives in Khartoum")
        facts.state("  ")
        val prompt = HarnessPrompt.top("Sara", facts.snapshot(), "You are Kawkab.")
        assertTrue(prompt.startsWith("Owner: Sara."))
        assertTrue(prompt.contains("Fact: Lives in Khartoum"))
        assertTrue(prompt.endsWith("You are Kawkab."))
        assertTrue(HarnessPrompt.top("", emptyList(), "Body").startsWith("Owner: the person who uses this phone."))
    }

    @Test
    fun `biometric confirmation is extra and does not replace the tap`() {
        assertFalse(BiometricRisk.ask(toolRisky = true, wordsRisky = true, tapApproved = false))
        assertTrue(BiometricRisk.ask(toolRisky = true, wordsRisky = true, tapApproved = true))
        assertFalse(BiometricRisk.ask(toolRisky = true, wordsRisky = false, tapApproved = true))
        assertTrue(BiometricRisk.wordsLookOutbound("call Ahmad"))
        assertTrue(BiometricRisk.wordsLookOutbound("ابعت رسالة"))
        val file = File.createTempFile("activity", ".jsonl")
        file.deleteOnExit()
        val runner = ActionRunner(ActionDenylist.fromText(""), FileActivityLog(file), ScreenWatch(), clock = { 1L })
        var calls = 0
        val needsTap = runner.run(
            asked = "call Ahmad",
            ran = "call_phone",
            risk = ToolRisk.OUTBOUND,
            entryPoint = "tool",
            confirmed = false,
            deviceConfirmed = false,
        ) {
            calls += 1
            "called"
        }
        assertEquals(ActionRunner.NEEDS_TAP, needsTap)
        val needsBiometric = runner.run(
            asked = "call Ahmad",
            ran = "call_phone",
            risk = ToolRisk.OUTBOUND,
            entryPoint = "tool",
            confirmed = true,
            deviceConfirmed = false,
        ) {
            calls += 1
            "called"
        }
        assertEquals(ActionRunner.NEEDS_BIOMETRIC, needsBiometric)
        assertEquals(0, calls)
    }

    @Test
    fun `older images become a short note and the latest image stays`() {
        val older = message("a very long caption ".repeat(40), attachment = image("old.jpg"))
        val latest = message("now", attachment = image("now.jpg"))
        val rewritten = ImageMemory.olderAsText(listOf(older, latest))
        assertNull(rewritten[0].attachment)
        assertTrue(rewritten[0].content.length <= 400)
        assertEquals("now.jpg", rewritten[1].attachment?.path)
        assertEquals("not measured", EmbeddingChoice.geckoScore())
        assertTrue(EmbeddingChoice.keepEmbeddingGemma())
    }

    private fun message(content: String, attachment: MessageAttachment?): ChatMessage = ChatMessage(
        sessionId = "s",
        role = Role.USER,
        content = content,
        timestamp = 1L,
        attachment = attachment,
    )

    private fun image(path: String): MessageAttachment = MessageAttachment(path, "image/jpeg", 10, 10)
}
