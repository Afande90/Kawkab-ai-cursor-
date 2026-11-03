package app.kawkab.android.voice

import android.content.Context
import android.media.AudioManager
import app.kawkab.android.domain.voice.WakeAssessment
import app.kawkab.android.domain.voice.WakeWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** A bounded fresh wake test using the production VAD, keyword and speaker path; it can never run an action. */
class WakeTestCapture(private val context: Context) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val capture = WakeMicSession({ WakeAudioInput.open(context) }, {})

    /** Signals readiness on the main thread only after native warm-up and actual microphone acquisition. */
    suspend fun attempt(onReady: () -> Unit = {}): WakeAssessment = withContext(Dispatchers.IO) {
        check(!closed.get())
        WakeWordService.pauseAndAwait(context)
        currentCoroutineContext().ensureActive()
        check(!closed.get())
        val audio = requireNotNull(context.getSystemService(AudioManager::class.java))
        check(audio.mode == AudioManager.MODE_NORMAL) { "Finish the phone call before testing." }
        val frames = WakeSpeechFrames(context.assets, context, onResult = {})
        try {
            currentCoroutineContext().ensureActive()
            check(!closed.get())
            capture.setAllowed(true)
            val mic = capture.acquireOrWait(1) ?: error("Allow microphone access and try again.")
            withContext(Dispatchers.Main.immediate) {
                currentCoroutineContext().ensureActive()
                check(!closed.get())
                onReady()
            }
            repeat(TEST_FRAMES) {
                currentCoroutineContext().ensureActive()
                if (closed.get()) error("Wake test stopped.")
                check(audio.mode == AudioManager.MODE_NORMAL) { "Wake test paused for the call." }
                frames.read(mic) { capture.isCurrent(mic) }
                if (frames.best?.accepted == true) {
                    return@withContext frames.best!!.also {
                        WakeScoreLog(context).record(WakeScoreLog.Source.TEST, it)
                    }
                }
            }
            (frames.best ?: WakeAssessment(speech = false)).also {
                WakeScoreLog(context).record(WakeScoreLog.Source.TEST, it)
            }
        } finally {
            capture.close()
            frames.close()
        }
    }

    /** Also called from lifecycle pause/disposal so Android's microphone indicator goes off immediately. */
    override fun close() {
        closed.set(true)
        capture.close()
    }

    private companion object {
        const val TEST_FRAMES = WakeWord.SAMPLE_RATE * 5 / WakeWord.FRAME_SAMPLES
    }
}
