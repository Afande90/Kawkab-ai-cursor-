package app.kawkab.android.assistant

import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.kawkab.android.domain.assistant.AssistantCommandRunner
import app.kawkab.android.domain.assistant.AssistantModelRelease
import app.kawkab.android.domain.assistant.ScreenTextCollector
import app.kawkab.android.domain.assistant.ScreenTextPolicy
import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.models.AgentOrchestratorState
import app.kawkab.android.domain.repositories.PrivacySettings
import app.kawkab.android.domain.voice.ConversationLoop
import app.kawkab.android.domain.voice.VoiceAnswers
import app.kawkab.android.glyph.GlyphMirror
import app.kawkab.android.hands.ContactChoice
import app.kawkab.android.presentation.state.ActiveSessionTracker
import app.kawkab.android.presentation.state.ChatEntryRequestRelay
import app.kawkab.android.presentation.ui.MainActivity
import app.kawkab.android.presentation.ui.chat.home.ActionSentence
import app.kawkab.android.presentation.ui.common.resolve
import app.kawkab.android.voice.ConversationDriver
import app.kawkab.android.voice.OfflineSpeech
import app.kawkab.android.voice.OfflineVoice
import app.kawkab.android.voice.OwnerVoice
import app.kawkab.android.voice.WakeWordService
import app.kawkab.design.device.DevicePage
import app.kawkab.design.device.DeviceSignal
import app.kawkab.design.device.DeviceState
import app.kawkab.design.device.KawkabDevice
import app.kawkab.design.theme.KnotworkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Power-button and wake-word entry use the same Pocket LCD device as the main app. */
class KawkabAssistantSession(
    private val appContext: Context,
    private val runner: AssistantCommandRunner,
    private val privacy: PrivacySettings,
    engine: LlmInferenceEngine,
    sessions: ActiveSessionTracker,
    private val relay: ChatEntryRequestRelay,
    private val choice: ContactChoice = ContactChoice(),
) : VoiceInteractionSession(appContext) {
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val release = AssistantModelRelease(
        releaseScope,
        chatOpen = { sessions.activeSessionId.value != null },
        unload = { engine.unload() },
    )
    private val owner = PocketSessionOwner()
    private val device = MutableStateFlow(DeviceState())
    private var structure: AssistStructure? = null
    private var pending: AgentOrchestratorState.WaitingForApproval? = null
    private var countdown: Job? = null
    private var contactWatch: Job? = null
    private var busy = false
    private var conversation: ConversationDriver? = null

    override fun onCreateContentView(): View = ComposeView(appContext).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val state by device.collectAsState()
            KnotworkTheme {
                GlyphMirror(state)
                KawkabDevice(
                    state,
                    onHome = {
                        cancelAction()
                        openChat("")
                    },
                    onShots = {
                        cancelAction()
                        device.value = device.value.copy(page = DevicePage.SHOTS)
                    },
                    onGrowth = {
                        cancelAction()
                        device.value = device.value.copy(page = DevicePage.GROWTH)
                    },
                    onSettings = {
                        cancelAction()
                        openChat("")
                    },
                    onVoice = { openChat("") },
                    onTalk = { if (pending != null) cancelAction() else startTalking() },
                )
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        owner.show()
        release.shown()
        WakeWordService.pause(appContext)
        AssistantPanelVisibility.shown = true
        if (!OwnerVoice(appContext).enrolled()) {
            device.value = device.value.copy(page = DevicePage.ENROLL, reply = "ENROLL YOUR VOICE IN THE APP")
            return
        }
        contactWatch?.cancel()
        contactWatch = uiScope.launch {
            choice.state.collect { contact ->
                if (contact != null) {
                    device.value = device.value.copy(
                        page = DevicePage.WHICH,
                        signal = DeviceSignal.ASKING,
                        name = contact.args[ContactChoice.SPOKEN].orEmpty(),
                        choices = contact.choices.map { it.name },
                    )
                    conversation().ask(
                        ContactChoice.question(
                            contact.args[ContactChoice.SPOKEN].orEmpty(),
                            contact.choices.size,
                            contact.choices.map { it.name },
                        ),
                    )
                }
            }
        }
        val woke = args?.getBoolean(WakeWordService.EXTRA_WAKE) == true
        if (woke || VoiceStart.fromGesture(showFlags)) startTalking()
    }

    override fun onHide() {
        contactWatch?.cancel()
        cancelAction()
        conversation?.release()
        conversation = null
        owner.hide()
        WakeWordService.resume(appContext)
        AssistantPanelVisibility.shown = false
        release.hidden()
        super.onHide()
    }

    override fun onDestroy() {
        owner.destroy()
        uiScope.cancel()
        super.onDestroy()
    }

    override fun onHandleAssist(state: AssistState) {
        structure = state.assistStructure
    }

    private fun startTalking() {
        if (OwnerVoice(appContext).enrolled()) conversation().tap() else openChat("")
    }

    private fun conversation(): ConversationDriver = conversation ?: ConversationDriver(
        speech = OfflineSpeech(appContext),
        voice = OfflineVoice(appContext),
        send = ::sendLine,
        onPhase = { phase ->
            val signal = when (phase) {
                ConversationLoop.Phase.LISTENING -> DeviceSignal.LISTENING
                ConversationLoop.Phase.SPEAKING -> DeviceSignal.SPEAKING
                else -> device.value.signal
            }
            device.value = device.value.copy(
                signal = signal,
                page = if (phase == ConversationLoop.Phase.LISTENING && pending == null && choice.state.value == null) {
                    DevicePage.LISTENING
                } else {
                    device.value.page
                },
            )
        },
        answer = { text ->
            val request = pending
            if (request == null) {
                sendLine(text)
            } else {
                uiScope.launch {
                    when (VoiceAnswers.answer(text)) {
                        VoiceAnswers.Answer.NO -> runner.answerByVoice(request.requestId, false, text, request.toolName)
                        VoiceAnswers.Answer.YES -> runner.answerByVoice(request.requestId, true, text, request.toolName)
                        else -> Unit
                    }
                }
            }
        },
    ).also { conversation = it }

    private fun sendLine(text: String) {
        if (busy || text.isBlank()) return
        device.value = device.value.copy(heard = text, page = DevicePage.TALK)
        uiScope.launch {
            busy = true
            try {
                if (text.equals("read my screen", true)) {
                    readScreen()
                } else if (!runner.route(text, ::render)) {
                    openChat(text)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                device.value = device.value.copy(reply = "I could not finish that. Please try again.")
            } finally {
                busy = false
            }
        }
    }

    private fun cancelAction() {
        pending?.let { request -> uiScope.launch { runner.answer(request.requestId, false) } }
    }

    private fun openChat(text: String) {
        if (text.isNotBlank()) relay.spoken(text)
        startAssistantActivity(
            Intent(appContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
        hide()
    }

    private suspend fun readScreen() {
        val allowed = privacy.useScreenText.first()
        val reply = if (ScreenTextPolicy.allow(ownerAllows = allowed, requested = true)) {
            ScreenTextCollector.collect(AssistScreenNodes.of(structure)).ifBlank { "There is no readable screen text." }
        } else {
            "Allow screen text in Settings before asking me to read it."
        }
        device.value = device.value.copy(reply = reply)
        conversation().replied(reply)
    }

    private fun render(state: AgentOrchestratorState) {
        when (state) {
            is AgentOrchestratorState.WaitingForApproval -> showPending(state)
            is AgentOrchestratorState.Completed -> {
                pending = null
                countdown?.cancel()
                device.value =
                    device.value.copy(
                        page = if (choice.state.value == null) DevicePage.TALK else DevicePage.WHICH,
                        reply = state.finalResponse,
                        signal = DeviceSignal.STANDBY,
                    )
                if (choice.state.value == null) conversation().replied(state.finalResponse)
            }
            is AgentOrchestratorState.Error -> {
                pending = null
                countdown?.cancel()
                device.value = device.value.copy(
                    page = DevicePage.TALK,
                    reply = state.message,
                    signal = DeviceSignal.ASKING,
                )
            }
            else -> Unit
        }
    }

    private fun showPending(state: AgentOrchestratorState.WaitingForApproval) {
        val firstAnnouncement = pending?.requestId != state.requestId
        pending = state
        val args = ActionSentence.arguments(state.arguments)
        val sentence = appContext.resolve(ActionSentence.announcement(state.toolName, args))
        if (state.runsAtMs != null) {
            device.value = device.value.copy(
                page = DevicePage.CALLING,
                signal = DeviceSignal.ACTING,
                name = args["name"] ?: args["spoken"] ?: args["contact"].orEmpty(),
                number = args["number"] ?: args["contact"].orEmpty(),
                usual =
                args["spoken"]?.let { choice.remembered(it) } != null,
            )
            if (firstAnnouncement) conversation().announce(sentence) { runner.announced(state.requestId) }
            countdown?.cancel()
            countdown = uiScope.launch {
                while (pending?.requestId == state.requestId) {
                    device.value =
                        device.value.copy(
                            countdownProgress = (
                                1f -
                                    (state.runsAtMs - System.currentTimeMillis()).toFloat() /
                                    (COUNTDOWN_SECONDS * SECOND_MS)
                                ).coerceIn(0f, 1f),
                            seconds = ((state.runsAtMs - System.currentTimeMillis() + SECOND_MS - 1) / SECOND_MS)
                                .toInt().coerceIn(0, COUNTDOWN_SECONDS),
                        )
                    delay(COUNTDOWN_TICK_MS)
                }
            }
        } else {
            device.value = device.value.copy(page = DevicePage.TALK, signal = DeviceSignal.ASKING, reply = sentence)
            conversation().ask("$sentence. Say yes or cancel.")
        }
    }
}

private const val SECOND_MS = 1_000L
private const val COUNTDOWN_SECONDS = 3
private const val COUNTDOWN_TICK_MS = 100L
