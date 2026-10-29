package app.kawkab.android.presentation.ui.chat.home

import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.fastpath.ChatTierRouter
import app.kawkab.android.domain.fastpath.CommandFastPath
import app.kawkab.android.domain.fastpath.PlannedCommand
import app.kawkab.android.domain.fastpath.TierTwoRecipe
import app.kawkab.android.domain.models.AgentOrchestratorState
import app.kawkab.android.domain.models.ChatMessage
import app.kawkab.android.domain.models.LocalBackend
import app.kawkab.android.domain.models.MessageAttachment
import app.kawkab.android.domain.models.PipelineGraph
import app.kawkab.android.domain.models.PipelineRun
import app.kawkab.android.domain.repositories.ChatRepository
import app.kawkab.android.domain.repositories.LocalModelRepository
import app.kawkab.android.domain.repositories.PipelineRepository
import app.kawkab.android.domain.repositories.PipelineRunRepository
import app.kawkab.android.domain.repositories.SettingsRepository
import app.kawkab.android.domain.usecases.AgentOrchestratorUseCase
import app.kawkab.android.domain.usecases.GetContextWindowUseCase
import app.kawkab.android.domain.usecases.LoadModelUseCase
import app.kawkab.android.domain.usecases.ResolveApprovalRequestContextUseCase
import app.kawkab.android.presentation.state.ActiveSessionTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import app.kawkab.design.R as KnotworkR

/**
 * Types a hands command on the chat screen and checks the alarm intent, not the model.
 *
 * The emulator runs this. The unit tests cover the same router without a screen.
 */
class TierOneAlarmChatTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun typingSetAnAlarmFiresTheAlarmIntentAndSkipsTheModel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val engine = mockk<LlmInferenceEngine>(relaxed = true)
        every { engine.isInitialized } returns false
        val orchestrator = mockk<AgentOrchestratorUseCase>(relaxed = true)
        val viewModel = chatViewModel(engine, orchestrator, AlarmIntentRouter(instrumentation.targetContext))
        val monitor = instrumentation.addMonitor(IntentFilter(AlarmClock.ACTION_SET_ALARM), null, true)
        try {
            composeTestRule.setContent {
                MaterialTheme { ChatHomeScreen(viewModel = viewModel) }
            }
            composeTestRule.waitUntil(SESSION_WAIT_MS) {
                viewModel.state.value.thread.currentSessionId.isNotBlank()
            }
            composeTestRule.onNode(hasSetTextAction()).performTextInput("set an alarm for 6")
            val sendCd = instrumentation.targetContext.getString(KnotworkR.string.knotwork_composer_send)
            composeTestRule.onNodeWithContentDescription(sendCd).performClick()
            composeTestRule.waitUntil(SESSION_WAIT_MS) { monitor.hits >= 1 }
            assertEquals(1, monitor.hits)
            coVerify(exactly = 0) { engine.generateResponseStream(any(), any(), any()) }
            coVerify(exactly = 0) { orchestrator(any(), any(), any()) }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun chatViewModel(
        engine: LlmInferenceEngine,
        orchestrator: AgentOrchestratorUseCase,
        router: ChatTierRouter,
    ): ChatHomeViewModel {
        val settings = mockk<SettingsRepository>(relaxed = true)
        val chatRepository = mockk<ChatRepository>(relaxed = true)
        val pipelineRepository = mockk<PipelineRepository>(relaxed = true)
        val pipelineRunRepository = mockk<PipelineRunRepository>(relaxed = true)
        val localModelRepository = mockk<LocalModelRepository>(relaxed = true)
        every { settings.currentChatSessionId } returns MutableStateFlow(null)
        every { settings.defaultPipelineId } returns MutableStateFlow(null)
        every { settings.maxContextLength } returns MutableStateFlow(1_500)
        every { settings.consolePreferredConsoleTabName } returns MutableStateFlow("Logs")
        every { settings.localModelBackend } returns MutableStateFlow(LocalBackend.CPU.key)
        every { settings.audioMaxDurationSec } returns MutableStateFlow(30)
        every { pipelineRepository.getAllPipelines() } returns MutableStateFlow(emptyList<PipelineGraph>())
        every { pipelineRunRepository.observeActiveRunSessionIds() } returns MutableStateFlow(emptySet())
        every { pipelineRunRepository.observeRunsForSession(any()) } returns flowOf(emptyList<PipelineRun>())
        coEvery { pipelineRunRepository.getActiveRunForSession(any()) } returns null
        coEvery { pipelineRunRepository.getLatestRunForSession(any()) } returns null
        every { chatRepository.getSessionsFlow(any()) } returns MutableStateFlow(emptyList())
        every { chatRepository.getDisplayMessagesForSession(any()) } returns MutableStateFlow(emptyList<ChatMessage>())
        every { localModelRepository.getAllModels() } returns MutableStateFlow(emptyList())
        val contextWindow = mockk<GetContextWindowUseCase>(relaxed = true)
        coEvery { contextWindow(any()) } returns ""
        return ChatHomeViewModel(
            orchestrator,
            chatRepository,
            pipelineRepository,
            appStateSettings = settings,
            generationSettings = settings,
            entryPointSettings = settings,
            runSettings = settings,
            contextWindow,
            engine,
            mockk(relaxed = true),
            localModelRepository,
            mockk<LoadModelUseCase>(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            pipelineRunRepository,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            ActiveSessionTracker(),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk<ResolveApprovalRequestContextUseCase>(relaxed = true),
            router,
        )
    }

    /**
     * Fires the clock intent the hands path would fire, and does not call a model.
     */
    private class AlarmIntentRouter(private val context: android.content.Context) : ChatTierRouter {
        override fun command(text: String): PlannedCommand? = CommandFastPath.parse(text)

        override fun recipe(text: String, hasImage: Boolean): TierTwoRecipe? = null

        override suspend fun runCommand(
            sessionId: String,
            text: String,
            command: PlannedCommand,
            onState: (AgentOrchestratorState) -> Unit,
        ) {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, command.args["hour"]?.toIntOrNull() ?: 0)
                putExtra(AlarmClock.EXTRA_MINUTES, command.args["minute"]?.toIntOrNull() ?: 0)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            onState(AgentOrchestratorState.Completed("Alarm set."))
        }

        override suspend fun runRecipe(
            sessionId: String,
            text: String,
            recipe: TierTwoRecipe,
            imagePath: String?,
            attachment: MessageAttachment?,
            onState: (AgentOrchestratorState) -> Unit,
        ) = Unit
    }

    private companion object {
        const val SESSION_WAIT_MS: Long = 5_000
    }
}
