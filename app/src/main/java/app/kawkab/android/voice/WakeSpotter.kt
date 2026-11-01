package app.kawkab.android.voice

import android.content.res.AssetManager
import app.kawkab.android.domain.voice.SpeechWindow
import app.kawkab.android.domain.voice.WakeWord
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * Silero VAD in front of the sherpa-onnx keyword spotter (task 27). The models ship in
 * `assets/wake/`, so nothing is downloaded and no audio leaves the phone. Audio is held only in
 * the pre-roll of [SpeechWindow] and the spotter's own frames; nothing is written anywhere.
 *
 * Not thread-safe: one thread feeds it.
 *
 * @param assets The app's assets.
 */
class WakeSpotter(assets: AssetManager) : AutoCloseable {
    private val vad = Vad(
        assets,
        VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "$DIR/silero_vad.onnx",
                threshold = VAD_THRESHOLD,
                minSilenceDuration = VAD_MIN_SILENCE_S,
                minSpeechDuration = VAD_MIN_SPEECH_S,
                windowSize = WakeWord.FRAME_SAMPLES,
            ),
            sampleRate = WakeWord.SAMPLE_RATE,
            numThreads = 1,
        ),
    )
    private val spotter = KeywordSpotter(
        assets,
        KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = WakeWord.SAMPLE_RATE, featureDim = FEATURE_DIM),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$DIR/encoder.int8.onnx",
                    decoder = "$DIR/decoder.int8.onnx",
                    joiner = "$DIR/joiner.int8.onnx",
                ),
                tokens = "$DIR/tokens.txt",
                numThreads = 1,
            ),
            keywordsFile = "$DIR/keywords.txt",
            keywordsScore = WakeWord.BOOST,
            keywordsThreshold = WakeWord.THRESHOLD,
            numTrailingBlanks = 1,
        ),
    )
    private var stream: OnlineStream = spotter.createStream("")
    private val window = SpeechWindow()

    /**
     * @param frame [WakeWord.FRAME_SAMPLES] samples in -1..1.
     * @return `true` when the frame completes "Kawkab".
     */
    fun feed(frame: FloatArray): Boolean {
        vad.acceptWaveform(frame)
        val speech = vad.isSpeechDetected()
        while (!vad.empty()) vad.pop()
        var heard = false
        for (chunk in window.feed(frame, speech)) {
            stream.acceptWaveform(chunk, WakeWord.SAMPLE_RATE)
            while (spotter.isReady(stream)) {
                spotter.decode(stream)
                if (spotter.getResult(stream).keyword.isNotEmpty()) {
                    spotter.reset(stream)
                    heard = true
                }
            }
        }
        return heard
    }

    /** Forgets the audio so far, for example after a wake. */
    fun reset() {
        window.reset()
        vad.reset()
        stream.release()
        stream = spotter.createStream("")
    }

    override fun close() {
        stream.release()
        spotter.release()
        vad.release()
    }

    private companion object {
        const val DIR = "wake"
        const val FEATURE_DIM = 80
        const val VAD_THRESHOLD = 0.5f
        const val VAD_MIN_SILENCE_S = 0.25f
        const val VAD_MIN_SPEECH_S = 0.1f
    }
}
