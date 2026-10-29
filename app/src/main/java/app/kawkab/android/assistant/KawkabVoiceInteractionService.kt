package app.kawkab.android.assistant

import android.service.voice.VoiceInteractionService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Registers Kawkab as a digital assistant. The session service draws the panel.
 */
@AndroidEntryPoint
class KawkabVoiceInteractionService : VoiceInteractionService()
