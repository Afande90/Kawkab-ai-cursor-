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
import java.util.concurrent.atomic.AtomicReference

/** A bounded fresh wake test using the production VAD, keyword and speaker path; it can never run an action. */
class WakeTestCapture(private val context: Context) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val microphone = AtomicReference<WakeCapture?>(null)

    /** Call after pausing background listening; raw PCM exists only inside the bounded frame buffer. */
    suspend fun attempt(): WakeAssessment = withContext(Dispatchers.IO) {
        check(!closed.get())
        WakeWordService.pauseAndAwait(context)
        currentCoroutineContext().ensureActive()
        check(!closed.get())
        val audio = requireNotNull(context.getSystemService(AudioManager::class.java))
        check(audio.mode == AudioManager.MODE_NORMAL) { "Finish the phone call before testing." }
        val frames = WakeSpeechFrames(context.assets, context, onResult = {})
        val mic = WakeAudioInput.open(context)
        if (mic == null) {
            frames.close()
            error("Allow microphone access and try again.")
        }
        microphone.set(mic)
        try {
            check(!closed.get())
            repeat(TEST_FRAMES) {
                currentCoroutineContext().ensureActive()
                if (closed.get()) error("Wake test stopped.")
                check(audio.mode == AudioManager.MODE_NORMAL) { "Wake test paused for the call." }
                frames.read(mic) { !closed.get() }
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
            microphone.getAndSet(null)?.close()
            frames.close()
        }
    }

    /** Also called from lifecycle pause/disposal so Android's microphone indicator goes off immediately. */
    override fun close() {
        closed.set(true)
        microphone.getAndSet(null)?.close()
    }

    private companion object {
        const val TEST_FRAMES = WakeWord.SAMPLE_RATE * 5 / WakeWord.FRAME_SAMPLES
    }
}
