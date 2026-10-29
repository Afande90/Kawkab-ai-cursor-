package app.kawkab.android.domain.fastpath

import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.engine.executors.ToolInvocationGate
import app.kawkab.android.domain.harness.SkillCache
import app.kawkab.android.domain.models.AgentOrchestratorState
import app.kawkab.android.domain.models.ChatMessage
import app.kawkab.android.domain.models.NodeExecutionResult
import app.kawkab.android.domain.models.NodeOutput
import app.kawkab.android.domain.models.Role
import app.kawkab.android.domain.repositories.ChatRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The router plans a hands command with no model, and a skill with one text call.
 */
class ChatTierRouterTest {
    private val gate = mockk<ToolInvocationGate>()
    private val chatRepository = mockk<ChatRepository>(relaxed = true)
    private val engine = mockk<LlmInferenceEngine>(relaxed = true)
    private val router = HandsChatTierRouter(gate, chatRepository, engine)

    @Test
    fun `an alarm is a hands command and does not call the model`() = runTest {
        val command = router.command("set an alarm for 6")
        assertEquals("set_alarm", command?.tool)
        assertEquals("6", command?.args?.get("hour"))
        assertEquals("0", command?.args?.get("minute"))
        stubGate("Alarm set for 6:0.")

        val states = mutableListOf<AgentOrchestratorState>()
        router.runCommand("session", "set an alarm for 6", command!!, states::add)

        coVerify(exactly = 0) { engine.generateResponseStream(any(), any(), any()) }
        coVerify {
            gate.dispatch(
                any(),
                "FAST_PATH",
                "FAST_PATH",
                "session",
                null,
                "set_alarm",
                any(),
                false,
            )
        }
        assertTrue(states.last() is AgentOrchestratorState.Completed)
    }

    @Test
    fun `an outbound text still asks through the gate`() = runTest {
        val command = router.command("text Ahmad running late")
        assertEquals("send_sms", command?.tool)
        stubGate("Sent.")
        router.runCommand("session", "text Ahmad running late", command!!, {})
        coVerify {
            gate.dispatch(any(), any(), any(), any(), null, "send_sms", any(), false)
        }
        coVerify(exactly = 0) { engine.generateResponseStream(any(), any(), any()) }
    }

    @Test
    fun `a reply skill writes once then sends through the gate`() = runTest {
        every { engine.generateResponseStream(any(), any(), any()) } returns flowOf("On my way")
        coEvery { chatRepository.getMessagesForSession(any()) } returns flowOf(emptyList())
        val recipe = router.recipe("reply to Ahmad", hasImage = false)
        assertTrue(recipe is TierTwoRecipe.Reply)
        stubGate("Sent.")

        router.runRecipe("session", "reply to Ahmad", recipe!!, imagePath = null, attachment = null) {}

        coVerify(exactly = 1) { engine.generateResponseStream(any(), isNull(), isNull()) }
        val args = slot<String>()
        coVerify {
            gate.dispatch(any(), "SKILL", "SKILL", "session", null, "send_sms", capture(args), false)
        }
        assertTrue(args.captured.contains("Ahmad"))
        assertTrue(args.captured.contains("On my way"))
    }

    @Test
    fun `a line the grammar does not know is not claimed`() {
        assertNull(router.command("what time is it"))
        assertNull(router.recipe("what time is it", hasImage = false))
        assertNull(router.recipe("set a reminder from this screenshot", hasImage = false))
        assertTrue(
            router.recipe("set a reminder from this screenshot", hasImage = true) is TierTwoRecipe.ScreenshotReminder,
        )
        assertTrue(router.recipe("read my last messages", hasImage = false) is TierTwoRecipe.ReadLast)
    }

    @Test
    fun `reading an empty chat does not call the model`() = runTest {
        coEvery { chatRepository.getMessagesForSession(any()) } returns flowOf(emptyList())
        val saved = mutableListOf<ChatMessage>()
        coEvery { chatRepository.saveMessage(any()) } coAnswers { saved += firstArg<ChatMessage>() }
        val recipe = router.recipe("read my last messages", hasImage = false)!!

        router.runRecipe("session", "read my last messages", recipe, null, null) {}

        coVerify(exactly = 0) { engine.generateResponseStream(any(), any(), any()) }
        assertEquals(Role.AGENT, saved.last().role)
        assertTrue(saved.last().content.contains("no messages"))
    }

    @Test
    fun `a saved plan replays when the grammar does not match`() = runTest {
        val cache = SkillCache()
        val withCache = HandsChatTierRouter(gate, chatRepository, engine, cache)
        cache.remember("water the plants", "open_app", mapOf("name" to "Plants"))
        assertNull(router.command("water the plants"))
        val saved = withCache.command("water the plants")
        assertEquals("open_app", saved?.tool)
        assertEquals("Plants", saved?.args?.get("name"))
        coVerify(exactly = 0) { engine.generateResponseStream(any(), any(), any()) }
    }

    private fun stubGate(output: String) {
        coEvery {
            gate.dispatch(any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            val collector = firstArg<FlowCollector<NodeOutput>>()
            collector.emit(
                NodeOutput.Result(NodeExecutionResult(outputText = output, resolvedToolName = "set_alarm")),
            )
        }
    }
}
