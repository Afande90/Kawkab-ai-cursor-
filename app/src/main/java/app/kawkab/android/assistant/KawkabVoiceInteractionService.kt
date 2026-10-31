package app.kawkab.android.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import dagger.hilt.android.AndroidEntryPoint

/**
 * Registers Kawkab as a digital assistant. The session service draws the panel.
 *
 * While Android runs this service (Kawkab holds the assistant role), [showPanel] opens the same
 * panel as a long-press of power, so the accessibility button, the Quick Settings tile and the
 * "Ask Kawkab" shortcut reach it too (tasks 23b and 23k).
 */
@AndroidEntryPoint
class KawkabVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        active = this
    }

    override fun onShutdown() {
        if (active === this) active = null
        super.onShutdown()
    }

    /** The running service. */
    companion object {
        @Volatile
        private var active: KawkabVoiceInteractionService? = null

        /**
         * Opens the assistant panel over the current app.
         *
         * @param args Extras for the session, such as starting conversation mode.
         * @return `true` when the panel was asked for; `false` when Kawkab is not the assistant.
         */
        fun showPanel(args: Bundle = Bundle()): Boolean {
            val service = active ?: return false
            service.showSession(args, VoiceInteractionSession.SHOW_WITH_ASSIST)
            return true
        }
    }
}
