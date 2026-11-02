package app.kawkab.android.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kawkab.android.domain.voice.WakeWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real JNI coexistence regression: both versioned ORT bindings must work in one app process. */
@RunWith(AndroidJUnit4::class)
class WakeAndSmartTurnInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun wakeThenSmartTurnThenWakeRemainUsable() {
        WakeSpotter(context.assets).use { wake ->
            silenceDoesNotWake(wake)
            inferTurn()
            wake.reset()
            silenceDoesNotWake(wake)
        }
    }

    @Test fun smartTurnThenWakeThenSmartTurnRemainUsable() {
        inferTurn()
        WakeSpotter(context.assets).use { silenceDoesNotWake(it) }
        inferTurn()
    }

    private fun inferTurn() {
        val model = SmartTurnCpu.forSession(context)
        try {
            // Functional load/inference check, not the separately tested 150 ms performance budget.
            val probability = model.probability(FloatArray(WakeWord.SAMPLE_RATE))
            assertTrue("real CPU turn inference", probability.isFinite() && probability in 0f..1f)
        } finally {
            model.cancel()
        }
    }

    private fun silenceDoesNotWake(wake: WakeSpotter) {
        var count = 0
        repeat(WakeWord.SAMPLE_RATE / WakeWord.FRAME_SAMPLES) {
            if (wake.feed(FloatArray(WakeWord.FRAME_SAMPLES))) count += 1
        }
        assertEquals("real sherpa VAD/KWS inference after both runtimes load", 0, count)
    }
}
