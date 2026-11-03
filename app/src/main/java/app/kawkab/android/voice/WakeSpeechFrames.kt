package app.kawkab.android.voice

import android.content.Context
import android.content.res.AssetManager
import app.kawkab.android.domain.voice.ScoredWakeSession
import app.kawkab.android.domain.voice.WakeAssessment
import app.kawkab.android.domain.voice.WakeWord

/** One bounded speech buffer; personal KWS and speaker inference sleep whenever Silero hears silence. */
internal class WakeSpeechFrames(
    assets: AssetManager,
    context: Context,
    onSpeech: () -> Unit = {},
    onResult: ((WakeAssessment) -> Unit)? = null,
) : AutoCloseable {
    private val spotter = WakeSpotter(assets)
    private val owner = OwnerVoice(context)
    private val personal = PersonalWakeDetector(context)
    private val journal = WakeScoreLog(context)
    private val gate = ScoredWakeSession(
        evaluate = { speech, sherpa ->
            val keyword = personal.score(speech, sherpa)
            if (keyword.keyword) keyword.copy(speaker = owner.score(speech)) else keyword
        },
        onSpeech = onSpeech,
        onResult = onResult ?: { journal.record(WakeScoreLog.Source.BACKGROUND, it) },
    )

    /** The test reads the same scored decisions as the service, without dispatching a wake. */
    val best: WakeAssessment? get() = gate.best
    private val recent = FloatArray(OWNER_WINDOW_SAMPLES)
    private val pcm = ShortArray(WakeWord.FRAME_SAMPLES)
    private val frame = FloatArray(WakeWord.FRAME_SAMPLES)
    private var cursor = 0
    private var wasSpeech = false

    /** Only the cheap VAD sees every frame. No speech sample allocation occurs during silence. */
    fun read(mic: WakeCapture, active: () -> Boolean): Boolean {
        val count = mic.read(pcm)
        if (!active()) return false
        check(count == pcm.size) { "Microphone read failed" }
        for (index in pcm.indices) {
            frame[index] = pcm[index] / PCM_SCALE
            recent[cursor] = frame[index]
            cursor = (cursor + 1) % recent.size
        }
        val keyword = spotter.feed(frame)
        if (spotter.speechSegment && !wasSpeech) {
            // Keep the onset pre-roll, not an unrelated sentence from the previous three seconds.
            for (index in 0 until recent.size - PRE_ROLL_SAMPLES) recent[(cursor + index) % recent.size] = 0f
        }
        wasSpeech = spotter.speechSegment
        return gate.feed(spotter.speechSegment, keyword) {
            FloatArray(recent.size) { recent[(cursor + it) % recent.size] }
        }
    }

    /** No speech from before a pause can participate in a later keyword or speaker match. */
    fun reset() {
        recent.fill(0f)
        cursor = 0
        wasSpeech = false
        spotter.reset()
        gate.reset()
    }

    override fun close() {
        gate.finish()
        spotter.close()
    }

    private companion object {
        const val OWNER_WINDOW_SAMPLES = 48_000
        const val PRE_ROLL_SAMPLES = WakeWord.FRAME_SAMPLES * 16
        const val PCM_SCALE = 32_768f
    }
}
