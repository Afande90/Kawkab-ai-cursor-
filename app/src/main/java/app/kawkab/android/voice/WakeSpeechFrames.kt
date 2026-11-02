package app.kawkab.android.voice

import android.content.Context
import android.content.res.AssetManager
import app.kawkab.android.domain.voice.SpeechWakeGate
import app.kawkab.android.domain.voice.WakeWord

/** One bounded speech buffer; personal KWS and speaker inference sleep whenever Silero hears silence. */
internal class WakeSpeechFrames(assets: AssetManager, context: Context) : AutoCloseable {
    private val spotter = WakeSpotter(assets)
    private val owner = OwnerVoice(context)
    private val personal = PersonalWakeDetector(context)
    private val gate = SpeechWakeGate(personal::hit) { owner.enrolled() && owner.accepts(it) }
    private val recent = FloatArray(OWNER_WINDOW_SAMPLES)
    private val pcm = ShortArray(WakeWord.FRAME_SAMPLES)
    private val frame = FloatArray(WakeWord.FRAME_SAMPLES)
    private var cursor = 0

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
        return gate.feed(spotter.speechSegment, keyword) {
            FloatArray(recent.size) { recent[(cursor + it) % recent.size] }
        }
    }

    /** No speech from before a pause can participate in a later keyword or speaker match. */
    fun reset() {
        recent.fill(0f)
        cursor = 0
        spotter.reset()
        gate.feed(false, false) { error("Silence cannot request samples") }
    }

    override fun close() = spotter.close()

    private companion object {
        const val OWNER_WINDOW_SAMPLES = 48_000
        const val PCM_SCALE = 32_768f
    }
}
