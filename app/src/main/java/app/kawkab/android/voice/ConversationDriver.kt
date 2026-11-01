package app.kawkab.android.voice

import android.os.Handler
import android.os.Looper
import app.kawkab.android.domain.voice.ConversationLoop
import java.util.Locale

/**
 * Runs [ConversationLoop] with Android's offline recognizer and voice (task 23l).
 *
 * @property speech The recognizer.
 * @property voice The text-to-speech voice.
 * @property send Sends a transcript as the owner's message.
 * @property onPhase Reports the loop's phase for the button.
 */
class ConversationDriver(
    private val speech: OfflineSpeech,
    private val voice: OfflineVoice,
    private val send: (String) -> Unit,
    private val onPhase: (ConversationLoop.Phase) -> Unit,
) {
    private val loop = ConversationLoop()
    private val main = Handler(Looper.getMainLooper())

    /** The loop's phase. */
    val phase: ConversationLoop.Phase get() = loop.phase

    /** The talk button: starts the loop, or stops it. */
    fun tap() = run(loop.tap())

    /** Kawkab's reply, to be spoken. */
    fun replied(text: String) = run(loop.replied(text))

    /** A card waits for a tap; voice never confirms it. */
    fun waitsForTap() = run(loop.waitsForTap())

    /** Stops and releases everything. */
    fun release() {
        speech.stop()
        voice.shutdown()
    }

    private fun run(step: ConversationLoop.Step) {
        when (step) {
            ConversationLoop.Step.Listen -> listen()
            is ConversationLoop.Step.Send -> send(step.text)
            is ConversationLoop.Step.Speak -> {
                voice.speak(step.text, step.language) { main.post { run(loop.spoken()) } }
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

    private fun listen() {
        speech.listen(
            OfflineSpeech.languageFor(Locale.getDefault()),
            object : OfflineSpeech.Listener {
                override fun onSpeechStart() {
                    if (loop.phase == ConversationLoop.Phase.SPEAKING) run(loop.bargeIn())
                }

                override fun onText(text: String) = run(loop.heard(text))

                override fun onNothing(code: Int) {
                    if (loop.phase == ConversationLoop.Phase.LISTENING) run(loop.silence())
                }
            },
        )
    }
}
