package app.kawkab.android.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import app.kawkab.android.domain.assistant.AssistantCommandRunner
import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.repositories.PrivacySettings
import app.kawkab.android.presentation.state.ActiveSessionTracker
import app.kawkab.android.presentation.state.ChatEntryRequestRelay
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Opens one Kawkab assistant panel over the current app.
 */
@AndroidEntryPoint
class KawkabAssistantSessionService : VoiceInteractionSessionService() {
    @Inject lateinit var runner: AssistantCommandRunner

    @Inject lateinit var privacy: PrivacySettings

    @Inject lateinit var engine: LlmInferenceEngine

    @Inject lateinit var sessions: ActiveSessionTracker

    @Inject lateinit var relay: ChatEntryRequestRelay

    override fun onNewSession(args: Bundle?): VoiceInteractionSession = KawkabAssistantSession(
        appContext = this,
        runner = runner,
        privacy = privacy,
        engine = engine,
        sessions = sessions,
        relay = relay,
    )
}
