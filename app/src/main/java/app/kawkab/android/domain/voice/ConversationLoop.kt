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

        /** The owner answered a card aloud with [text]; the driver applies [VoiceAnswers.decide]. */
        data class Answer(val text: String) : Step

        /** Do nothing. */
        data object None : Step
    }

    /** The current phase. */
    var phase: Phase = Phase.IDLE
        private set

    private var silentRetries = 0

    /**
     * `true` while listening for a follow-up after a spoken reply (task 28). Silence then ends the
     * conversation instead of asking again.
     */
    var followingUp: Boolean = false
        private set

    /** A card is being read aloud or answered (task 29). */
    private var card: CardMode? = null

    private enum class CardMode {
        /** Read it, then wait for the tap. */
        TAP_ONLY,

        /** Read it, then listen once for yes or no. */
        VOICE_ANSWER,

        /** Keep hearing cancellation until the action finishes; no answer approves it. */
        CANCEL_ONLY,
    }

    /** @return [Step.Listen], or [Step.Stop] when the loop was already running (the tap toggles). */
    fun tap(): Step = if (phase == Phase.IDLE) {
        silentRetries = 0
        followingUp = false
        card = null
        phase = Phase.LISTENING
        Step.Listen
    } else {
        phase = Phase.IDLE
        Step.Stop
    }

    /** The recognizer heard [text]. */
    fun heard(text: String): Step {
        // Cancellation must work during the spoken announcement, before TTS completes.
        if (phase == Phase.SPEAKING && card != null && VoiceAnswers.answer(text) == VoiceAnswers.Answer.NO) {
            card = null
            phase = Phase.WAITING
            return Step.Answer(text.trim())
        }
        if (phase != Phase.LISTENING) return Step.None
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return silence()
        silentRetries = 0
        followingUp = false
        if (card == CardMode.CANCEL_ONLY && VoiceAnswers.answer(trimmed) != VoiceAnswers.Answer.NO) {
            return Step.Listen
        }
        if (card != null) {
            // The answer to a card: the card decides, then its result is spoken like a reply.
            card = null
            phase = Phase.WAITING
            return Step.Answer(trimmed)
        }
        phase = Phase.WAITING
        return Step.Send(trimmed)
    }

    /** The recognizer ended with nothing (silence or no match). */
    fun silence(): Step {
        if (phase != Phase.LISTENING) return Step.None
        if (card == CardMode.CANCEL_ONLY) return Step.Listen
        if (card != null) {
            // No spoken answer: the card stays for a tap, and its result is still spoken.
            card = null
            phase = Phase.WAITING
            return Step.Stop
        }
        if (followingUp) {
            // No follow-up within the window: the conversation is over (task 28).
            followingUp = false
            phase = Phase.IDLE
            return Step.Stop
        }
        silentRetries += 1
        if (silentRetries > maxSilentRetries) {
            phase = Phase.IDLE
            return Step.Stop
        }
        return Step.Listen
    }

    /** Kawkab answered [text]. */
    fun replied(text: String): Step {
        if (card == CardMode.CANCEL_ONLY) {
            card = null
            phase = Phase.WAITING
        }
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
        when (card) {
            CardMode.TAP_ONLY -> {
                // Read aloud; now only a tap confirms. Wait for the result to speak it.
                card = null
                phase = Phase.WAITING
                return Step.Stop
            }
            CardMode.VOICE_ANSWER, CardMode.CANCEL_ONLY -> {
                phase = Phase.LISTENING
                return Step.Listen
            }
            null -> Unit
        }
        phase = Phase.LISTENING
        followingUp = true
        return Step.Listen
    }

    /** The owner started speaking while Kawkab was speaking. */
    fun bargeIn(): Step {
        if (phase != Phase.SPEAKING) return Step.None
        phase = Phase.LISTENING
        followingUp = false
        return Step.StopSpeakingAndListen
    }

    /**
     * A card waits (task 29). With [sentence], Kawkab reads it aloud ("Call Abdalla Mubadala?"),
     * then either waits for the tap or, when [voiceMayAnswer], listens once for yes or no. Without
     * a sentence it stops listening until the tap, as before.
     *
     * @param sentence The card in plain words, or `null`.
     * @param cancelOnly Keep listening only for cancellation during an automatic action window.
     * @param voiceMayAnswer Whether a spoken answer is listened for at all; the driver still
     *   applies [VoiceAnswers.decide], so a yes with the phone locked never sends.
     */
    fun waitsForTap(sentence: String? = null, voiceMayAnswer: Boolean = false, cancelOnly: Boolean = false): Step {
        if (phase == Phase.IDLE) return Step.None
        followingUp = false
        if (sentence.isNullOrBlank()) {
            phase = Phase.IDLE
            return Step.Stop
        }
        card = when {
            cancelOnly -> CardMode.CANCEL_ONLY
            voiceMayAnswer -> CardMode.VOICE_ANSWER
            else -> CardMode.TAP_ONLY
        }
        phase = Phase.SPEAKING
        return Step.Speak(sentence, ReplyLanguage.of(sentence))
    }

    /** Defaults. */
    companion object {
        /** Silent rounds in a row before the loop stops by itself. */
        const val DEFAULT_SILENT_RETRIES: Int = 2

        /** How long Kawkab waits for a follow-up after speaking, without the wake word (task 28). */
        const val FOLLOW_UP_MS: Long = 8_000L

        /** Silence that ends the owner's turn (task 28; smart-turn is not used, see DECISIONS.md). */
        const val END_OF_TURN_SILENCE_MS: Long = 700L
    }
}
