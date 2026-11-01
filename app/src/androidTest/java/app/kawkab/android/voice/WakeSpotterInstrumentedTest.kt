package app.kawkab.android.voice

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kawkab.android.domain.voice.WakeWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Task 27, Tier 1 with injected audio: the real VAD and spotter from `assets/wake/` on the
 * emulator. The clips are the first ten libritts positives and the first five hard negatives of
 * the frozen test split (`evals/wake-word/v1`), as 16 kHz 16-bit PCM. The reference runtime woke
 * on 6 of the 10 positives and 0 of the 5 negatives.
 */
@RunWith(AndroidJUnit4::class)
class WakeSpotterInstrumentedTest {
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val appAssets = InstrumentationRegistry.getInstrumentation().targetContext.assets

    @Test
    fun theSpotterWakesOnPositivesAndNotOnNegativesOrSilence() {
        WakeSpotter(appAssets).use { spotter ->
            val clips = testAssets.list(DIR)!!.sorted()
            val woke = clips.associateWith { name ->
                spotter.reset()
                wakes(spotter, pcm("$DIR/$name") + FloatArray(WakeWord.SAMPLE_RATE))
            }
            Log.i("KawkabWakeTest", "wakes per clip: $woke")
            val positives = woke.filterKeys { it.startsWith("pos_") }.values.count { it > 0 }
            val negatives = woke.filterKeys { it.startsWith("neg_") }.values.count { it > 0 }
            spotter.reset()
            val silence = wakes(spotter, FloatArray(WakeWord.SAMPLE_RATE * SILENCE_S))
            assertEquals("silence", 0, silence)
            assertEquals("hard negatives", 0, negatives)
            assertTrue("positives woke $positives of 10", positives >= MIN_POSITIVES)
        }
    }

    private fun wakes(spotter: WakeSpotter, audio: FloatArray): Int {
        var count = 0
        var i = 0
        while (i + WakeWord.FRAME_SAMPLES <= audio.size) {
            if (spotter.feed(audio.copyOfRange(i, i + WakeWord.FRAME_SAMPLES))) count += 1
            i += WakeWord.FRAME_SAMPLES
        }
        return count
    }

    private fun pcm(path: String): FloatArray {
        val bytes = testAssets.open(path).use { it.readBytes() }
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(shorts.remaining()) { shorts.get(it) / PCM_SCALE }
    }

    private companion object {
        const val DIR = "wake-clips"
        const val PCM_SCALE = 32_768f
        const val SILENCE_S = 10
        const val MIN_POSITIVES = 3
    }
}
