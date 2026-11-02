package app.kawkab.android.voice

import android.os.Handler
import android.os.Looper
import app.kawkab.android.domain.voice.ConversationLoop
import app.kawkab.android.domain.voice.VoiceAnswers
import java.util.Locale

/**
 * Runs [ConversationLoop] with Android's offline recognizer and voice (task 23l).
 *
 * @property speech The recognizer.
 * @property voice The text-to-speech voice.
 * @property send Sends a transcript as the owner's message.
 * @property onPhase Reports the loop's phase for the button.
 * @property answer Applies a spoken answer to the waiting card (task 29).
 */
class ConversationDriver(
    private val speech: OfflineSpeech,
    private val voice: OfflineVoice,
    private val send: (String) -> Unit,
    private val onPhase: (ConversationLoop.Phase) -> Unit,
    private val answer: (String) -> Unit = {},
) {
    private var announcementDone: (() -> Unit)? = null
    private val loop = ConversationLoop()
    private val main = Handler(Looper.getMainLooper())

    /** The loop's phase. */
    val phase: ConversationLoop.Phase get() = loop.phase

    /** The talk button: starts the loop, or stops it. */
    fun tap() = run(loop.tap())

    /** Kawkab's reply, to be spoken. */
    fun replied(text: String) = run(loop.replied(text))

    /**
     * A card waits. It is read aloud when [sentence] is given; a spoken answer is listened for
     * only when [voiceMayAnswer] (task 29).
     */
    fun waitsForTap(sentence: String? = null, voiceMayAnswer: Boolean = false) =
        run(loop.waitsForTap(sentence, voiceMayAnswer))

    /** Reads a contact question and listens for its spoken answer, including after a typed command. */
    fun ask(sentence: String) {
        if (loop.phase == ConversationLoop.Phase.IDLE) loop.tap()
        run(loop.waitsForTap(sentence, voiceMayAnswer = true))
    }

    /** Announces an action even when the command was typed; only cancellation is listened for. */
    fun announce(sentence: String, done: () -> Unit = {}) {
        announcementDone = done
        if (loop.phase == ConversationLoop.Phase.IDLE) loop.tap()
        run(loop.waitsForTap(sentence, voiceMayAnswer = true, cancelOnly = true))
    }

    /** Stops and releases everything. */
    fun release() {
        main.removeCallbacks(followUpTimeout)
        speech.stop()
        voice.shutdown()
    }

    private fun run(step: ConversationLoop.Step) {
        when (step) {
            ConversationLoop.Step.Listen -> listen()
            is ConversationLoop.Step.Send -> send(step.text)
            is ConversationLoop.Step.Answer -> {
                voice.stop()
                speech.stop()
                answer(step.text)
            }
            is ConversationLoop.Step.Speak -> {
                val announced = announcementDone.also { announcementDone = null }
                voice.speak(step.text, step.language) {
                    main.post {
                        announced?.invoke()
                        run(loop.spoken())
                    }
                }
                // Listening while speaking lets the owner talk over the reply (barge-in).
                listen()
            }
            // The recognizer that heard the owner is already running; only the voice stops.
            ConversationLoop.Step.StopSpeakingAndListen -> voice.stop()
            ConversationLoop.Step.Stop -> {
                speech.stop()
                voice.stop()
            }
            ConversationLoop.Step.None -> Unit
        }
        onPhase(loop.phase)
    }

    private val followUpTimeout = Runnable {
        // Nobody spoke within the follow-up window: end the conversation (task 28).
        if (loop.phase == ConversationLoop.Phase.LISTENING && loop.followingUp) {
            speech.stop()
            run(loop.silence())
        }
    }

    private fun listen() {
        main.removeCallbacks(followUpTimeout)
        if (loop.followingUp) main.postDelayed(followUpTimeout, ConversationLoop.FOLLOW_UP_MS)
        speech.listen(
            OfflineSpeech.languageFor(Locale.getDefault()),
            object : OfflineSpeech.Listener {
                override fun onSpeechStart() {
                    main.removeCallbacks(followUpTimeout)
                    if (loop.phase == ConversationLoop.Phase.SPEAKING) run(loop.bargeIn())
                }

                override fun onText(text: String) {
                    main.removeCallbacks(followUpTimeout)
                    run(loop.heard(text))
                }

                override fun onPartialText(text: String) {
                    if (VoiceAnswers.answer(text) == VoiceAnswers.Answer.NO) run(loop.heard(text))
                }

                override fun onNothing(code: Int) {
                    main.removeCallbacks(followUpTimeout)
                    if (loop.phase == ConversationLoop.Phase.LISTENING) run(loop.silence())
                }
            },
        )
    }
}
