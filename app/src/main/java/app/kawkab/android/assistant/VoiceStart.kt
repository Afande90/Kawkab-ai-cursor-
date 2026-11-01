package app.kawkab.android.assistant

import android.service.voice.VoiceInteractionSession

/**
 * When the assistant panel starts listening by itself (task 23l).
 */
object VoiceStart {
    /**
     * @param showFlags The flags `VoiceInteractionSession.onShow` received.
     * @return `true` for a long-press of power or the assist gesture; a tile or button tap opens
     *   the panel without listening.
     */
    fun fromGesture(showFlags: Int): Boolean = showFlags and GESTURE != 0

    private const val GESTURE = VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE
}
