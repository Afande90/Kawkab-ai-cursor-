package app.kawkab.android.domain.assistant

import app.kawkab.android.domain.fastpath.ChatTierRouter
import app.kawkab.android.domain.fastpath.PlannedCommand
import app.kawkab.android.domain.models.AgentOrchestratorState
import app.kawkab.android.domain.models.ChatSession
import app.kawkab.android.domain.models.ToolRisk
import app.kawkab.android.domain.repositories.ChatRepository
import app.kawkab.android.domain.usecases.PendingSubmissionOutcome
import app.kawkab.android.domain.usecases.SubmitApprovalDecisionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The assistant panel's decisions: screen text, model release, scores, and the tap gate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantPanelTest {
    @Test
    fun `screen text is read only when allowed and requested`() {
        assertFalse(ScreenTextPolicy.allow(ownerAllows = false, requested = true))
        assertFalse(ScreenTextPolicy.allow(ownerAllows = true, requested = false))
        assertTrue(ScreenTextPolicy.allow(ownerAllows = true, requested = true))
    }

    @Test
    fun `screen text keeps visible lines and drops blanks`() {
        val roots = listOf(
            ScreenNode(
                text = "Hello",
                children = listOf(ScreenNode("  "), ScreenNode("World", listOf(ScreenNode("There")))),
            ),
        )
        assertEquals("Hello\nWorld\nThere", ScreenTextCollector.collect(roots))
        assertEquals("", ScreenTextCollector.collect(emptyList()))
    }

    @Test
    fun `hiding the panel unloads after the delay when no chat is open`() = runTest {
        var unloaded = 0
        val waits = mutableListOf<Long>()
        val release = AssistantModelRelease(
            scope = this,
            chatOpen = { false },
            unload = { unloaded += 1 },
            wait = { waits += it },
        )
        release.hidden()
        advanceUntilIdle()
        assertEquals(listOf(AssistantModelRelease.RELEASE_AFTER_HIDE_MS), waits)
        assertEquals(1, unloaded)
    }

    @Test
    fun `hiding the panel keeps the model when a chat is open`() = runTest {
        var unloaded = 0
        val release = AssistantModelRelease(
            scope = this,
            chatOpen = { true },
            unload = { unloaded += 1 },
            wait = { },
        )
        release.hidden()
        advanceUntilIdle()
        assertEquals(0, unloaded)
    }

    @Test
    fun `showing the panel again cancels the pending unload`() = runTest {
        var unloaded = 0
        val hold = CompletableDeferred<Unit>()
        val release = AssistantModelRelease(
            scope = this,
            chatOpen = { false },
            unload = { unloaded += 1 },
            wait = { hold.await() },
        )
        release.hidden()
        release.shown()
        hold.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, unloaded)
    }

    @Test
    fun `tool-call scores are the reference runtime and E4B is the default`() {
        val label = ToolCallReport.REFERENCE_RUNTIME
        assertEquals("87/100, $label", ToolCallReport.score(ToolCallReport.Brain.GEMMA_4_E2B, ToolCallReport.EVAL_V1))
        assertEquals("55/78, $label", ToolCallReport.score(ToolCallReport.Brain.GEMMA_4_E2B, ToolCallReport.EVAL_V2))
        assertEquals("91/100, $label", ToolCallReport.score(ToolCallReport.Brain.GEMMA_4_E4B, ToolCallReport.EVAL_V1))
        assertEquals("66/78, $label", ToolCallReport.score(ToolCallReport.Brain.GEMMA_4_E4B, ToolCallReport.EVAL_V2))
        assertEquals("61/100, $label", ToolCallReport.score(ToolCallReport.Brain.QWEN3_1_7B, ToolCallReport.EVAL_V1))
        assertEquals("41/78, $label", ToolCallReport.score(ToolCallReport.Brain.QWEN3_1_7B, ToolCallReport.EVAL_V2))
        assertEquals(ToolCallReport.Brain.GEMMA_4_E4B, ToolCallReport.toolDefault())
        assertTrue(ToolCallReport.keepsGemmaForImages())
    }

    @Test
    fun `a known command stays on the panel and anything else opens chat`() {
        val alarm = AssistantPlanner.plan("set an alarm for 6")
        assertTrue(alarm is AssistantPlan.Tool)
        assertEquals("set_alarm", (alarm as AssistantPlan.Tool).command.tool)
        assertTrue(AssistantPlanner.plan("what time is it") is AssistantPlan.OpenChat)
        assertNull(AssistantPlanner.plan("   "))
    }

    @Test
    fun `a panel command is stored once and still goes through the gate`() = runTest {
        val router = mockk<ChatTierRouter>()
        val chats = mockk<ChatRepository>(relaxed = true)
        val approvals = mockk<SubmitApprovalDecisionUseCase>()
        val runner = AssistantCommandRunner(router, chats, approvals)
        coEvery { chats.getSessionById(AssistantCommandRunner.SESSION_ID) } returns null
        val command = PlannedCommand("set_alarm", mapOf("hour" to "6", "minute" to "0"))
        coEvery { router.runCommand(any(), any(), any(), any()) } coAnswers {
            arg<(AgentOrchestratorState) -> Unit>(3).invoke(
                AgentOrchestratorState.WaitingForApproval(
                    toolName = "set_alarm",
                    arguments = "{}",
                    risk = ToolRisk.SENSITIVE,
                    requestId = "req-1",
                ),
            )
        }
        val states = mutableListOf<AgentOrchestratorState>()
        runner.run("set an alarm for 6", command, states::add)
        val saved = slot<ChatSession>()
        coVerify { chats.saveSession(capture(saved)) }
        assertEquals(AssistantCommandRunner.SESSION_ID, saved.captured.id)
        assertEquals(AssistantCommandRunner.SESSION_NAME, saved.captured.name)
        coVerify {
            router.runCommand(AssistantCommandRunner.SESSION_ID, "set an alarm for 6", command, any())
        }
        assertTrue(states.single() is AgentOrchestratorState.WaitingForApproval)
        coEvery {
            approvals(AssistantCommandRunner.SESSION_ID, "req-1", true)
        } returns PendingSubmissionOutcome.LiveResumed
        assertTrue(runner.answer("req-1", approved = true))
    }

    @Test
    fun `an existing assistant chat is not rewritten`() = runTest {
        val router = mockk<ChatTierRouter>()
        val chats = mockk<ChatRepository>(relaxed = true)
        val runner = AssistantCommandRunner(router, chats, mockk())
        coEvery { chats.getSessionById(any()) } returns ChatSession("kawkab-assistant", "Kawkab", 1L)
        coEvery { router.runCommand(any(), any(), any(), any()) } returns Unit
        runner.run("open Clock", PlannedCommand("open_app", mapOf("app" to "Clock")), {})
        coVerify(exactly = 0) { chats.saveSession(any()) }
    }

    @Test
    fun `assistant steps exist in English and Arabic and the service is registered`() {
        val english = File("src/main/res/values/strings.xml").readText()
        val arabic = File("src/main/res/values-ar/strings.xml").readText()
        DefaultAssistantGuide.steps.forEach { name ->
            assertTrue(english.contains("name=\"$name\""))
            assertTrue(arabic.contains("name=\"$name\""))
        }
        assertTrue(english.contains("Choose Kawkab."))
        assertTrue(arabic.contains("كوكب"))
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.service.voice.VoiceInteractionService"))
        assertTrue(manifest.contains(".assistant.KawkabVoiceInteractionService"))
        assertTrue(manifest.contains(".assistant.KawkabRecognitionService"))
        val service = File("src/main/res/xml/voice_interaction_service.xml").readText()
        assertTrue(service.contains("supportsAssist=\"true\""))
    }
}
