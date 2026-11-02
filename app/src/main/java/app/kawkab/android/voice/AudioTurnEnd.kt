package app.kawkab.android.voice

import android.content.res.AssetManager
import android.os.SystemClock
import app.kawkab.android.domain.voice.SmartTurnBudget
import app.kawkab.android.domain.voice.TurnCompletion
import app.kawkab.android.domain.voice.TurnCompletionModel
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlin.math.sqrt

/** Silero observes capture on its own thread; semantic checks run separately so capture never waits 150 ms. */
internal class AudioTurnEnd(
    private val assets: AssetManager,
    model: TurnCompletionModel,
    private val samples: () -> FloatArray,
    private val onEnd: () -> Unit,
) : AutoCloseable {
    private val budget = SmartTurnBudget(model)
    private var vad: Vad? = null
    private var monitor: Thread? = null

    @Volatile private var alive = false

    @Volatile private var speechSeen = false

    @Volatile private var lastVoiceMs = 0L

    @Volatile private var fallback = false

    @Volatile private var recordedSamples = 0

    /** Called on the capture thread before AudioRecord starts. */
    fun prepare() {
        vad = runCatching {
            Vad(
                assets,
                VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = "wake/silero_vad.onnx",
                        threshold = VAD_THRESHOLD,
                        minSilenceDuration = VAD_SILENCE_SECONDS,
                        minSpeechDuration = VAD_SPEECH_SECONDS,
                        windowSize = VAD_WINDOW,
                    ),
                    sampleRate = RATE,
                    numThreads = 1,
                ),
            )
        }.getOrElse {
            fallback = true
            null
        }
    }

    /** Called only by the capture thread with the exact PCM sent to recognition. */
    fun accept(frame: FloatArray) {
        recordedSamples += frame.size
        val detector = vad
        val speech = if (detector != null) {
            runCatching {
                detector.acceptWaveform(frame)
                val detected = detector.isSpeechDetected()
                while (!detector.empty()) detector.pop()
                detected
            }.getOrElse {
                fallback = true
                energy(frame)
            }
        } else {
            energy(frame)
        }
        // An energy onset vetoes stale completion while Silero confirms resumed speech.
        if (speech || energy(frame)) {
            speechSeen = true
            lastVoiceMs = SystemClock.elapsedRealtime()
        }
    }

    /** One monitor per listening session, with a separate cancellable CPU worker. */
    fun start() {
        alive = true
        monitor = Thread({
            var checkedVoice = -1L
            var checkedSilence = 0L
            try {
                while (alive) {
                    Thread.sleep(POLL_MS)
                    // Preserve the existing minimum PCM length for speaker verification.
                    if (!speechSeen || recordedSamples < RATE) continue
                    val voice = lastVoiceMs
                    val silence = SystemClock.elapsedRealtime() - voice
                    if (silence < SmartTurnBudget.MIN_SILENCE_MS) continue
                    if (fallback) {
                        if (silence >= SmartTurnBudget.SILENCE_MS && lastVoiceMs == voice) {
                            onEnd()
                            break
                        }
                        continue
                    }
                    if (voice == checkedVoice && silence - checkedSilence < RETRY_MS) continue
                    checkedVoice = voice
                    checkedSilence = silence
                    val completion = budget.evaluate(samples())
                    if (completion == TurnCompletion.Fallback) fallback = true
                    if (alive &&
                        lastVoiceMs == voice &&
                        SmartTurnBudget.ends(completion, SystemClock.elapsedRealtime() - voice)
                    ) {
                        onEnd()
                        break
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }, "turn-end").apply {
            isDaemon = true
            start()
        }
    }

    /** Native VAD ownership stays on the capture thread, even during cancellation. */
    fun finishCapture() {
        runCatching { vad?.release() }
        vad = null
        close()
    }

    override fun close() {
        alive = false
        monitor?.interrupt()
        budget.close()
    }

    private fun energy(frame: FloatArray): Boolean =
        frame.isNotEmpty() && sqrt(frame.sumOf { (it * it).toDouble() } / frame.size) >= ENERGY_THRESHOLD

    private companion object {
        const val RATE = 16_000
        const val POLL_MS = 50L
        const val RETRY_MS = 300L
        const val VAD_WINDOW = 512
        const val VAD_THRESHOLD = 0.5f
        const val VAD_SILENCE_SECONDS = 0.1f
        const val VAD_SPEECH_SECONDS = 0.1f
        const val ENERGY_THRESHOLD = 0.008
    }
}
