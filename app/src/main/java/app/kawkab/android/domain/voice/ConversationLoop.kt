package app.kawkab.android.domain.voice

import app.kawkab.android.domain.text.ReplyLanguage

/**
 * Conversation mode (task 23l): listen, send what was said, speak the reply, listen again,
 * until the owner taps stop. Speaking over the reply stops it (barge-in).
 *
 * Pure: the Android driver turns each [Step] into speech recognition, text-to-speech or a send.
 * Voice never confirms an outbound action; the tap card stays (spec §0.4.3).
 */
class ConversationLoop(private val maxSilentRetries: Int = DEFAULT_SILENT_RETRIES) {
    /** Where the loop is. */
    enum class Phase {
        /** Off. */
        IDLE,

        /** The recognizer is listening. */
        LISTENING,

        /** A message was sent; waiting for Kawkab's reply. */
        WAITING,

        /** Kawkab is speaking its reply. */
        SPEAKING,
    }

    /** What the driver does next. */
    sealed interface Step {
        /** Start listening. */
        data object Listen : Step

        /** Send [text] as the owner's message. */
        data class Send(val text: String) : Step

        /** Speak [text] in [language]. */
        data class Speak(val text: String, val language: ReplyLanguage) : Step

        /** Stop speaking at once, then listen (the owner spoke over the reply). */
        data object StopSpeakingAndListen : Step

        /** Stop everything. */
        data object Stop : Step

        /** Do nothing. */
        data object None : Step
    }

    /** The current phase. */
    var phase: Phase = Phase.IDLE
        private set

    private var silentRetries = 0

    /** @return [Step.Listen], or [Step.Stop] when the loop was already running (the tap toggles). */
    fun tap(): Step = if (phase == Phase.IDLE) {
        silentRetries = 0
        phase = Phase.LISTENING
        Step.Listen
    } else {
        phase = Phase.IDLE
        Step.Stop
    }

    /** The recognizer heard [text]. */
    fun heard(text: String): Step {
        if (phase != Phase.LISTENING) return Step.None
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return silence()
        silentRetries = 0
        phase = Phase.WAITING
        return Step.Send(trimmed)
    }

    /** The recognizer ended with nothing (silence or no match). */
    fun silence(): Step {
        if (phase != Phase.LISTENING) return Step.None
        silentRetries += 1
        if (silentRetries > maxSilentRetries) {
            phase = Phase.IDLE
            return Step.Stop
        }
        return Step.Listen
    }

    /** Kawkab answered [text]. */
    fun replied(text: String): Step {
        if (phase != Phase.WAITING) return Step.None
        if (text.isBlank()) {
            phase = Phase.LISTENING
            return Step.Listen
        }
        phase = Phase.SPEAKING
        return Step.Speak(text, ReplyLanguage.of(text))
    }

    /** The reply finished speaking. */
    fun spoken(): Step {
        if (phase != Phase.SPEAKING) return Step.None
        phase = Phase.LISTENING
        return Step.Listen
    }

    /** The owner started speaking while Kawkab was speaking. */
    fun bargeIn(): Step {
        if (phase != Phase.SPEAKING) return Step.None
        phase = Phase.LISTENING
        return Step.StopSpeakingAndListen
    }

    /** A card waits for a tap: stop listening until the owner taps it. */
    fun waitsForTap(): Step {
        if (phase == Phase.IDLE) return Step.None
        phase = Phase.IDLE
        return Step.Stop
    }

    /** Defaults. */
    companion object {
        /** Silent rounds in a row before the loop stops by itself. */
        const val DEFAULT_SILENT_RETRIES: Int = 2
    }
}
