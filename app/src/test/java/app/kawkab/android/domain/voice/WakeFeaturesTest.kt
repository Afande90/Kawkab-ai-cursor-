package app.kawkab.android.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** Portable wake features must handle silence and separate different sounds without a model. */
class WakeFeaturesTest {
    @Test fun silenceIsFinite() {
        val result = WakeFeatures.encode(FloatArray(16000))
        assertEquals(512, result.size)
        assertTrue(result.all { it.isFinite() && it == 0.0 })
    }
    @Test fun identicalUtterancesHaveIdenticalFeatures() {
        val tone = FloatArray(16000) { (sin(2 * PI * 330 * it / 16000) * 0.3).toFloat() }
        assertEquals(1.0, WakeFeatures.similarity(WakeFeatures.encode(tone), WakeFeatures.encode(tone)), 0.00001)
    }
    @Test fun differentSoundsDoNotMatchThePersonalTemplate() {
        fun tone(hz: Int) = FloatArray(16000) { (sin(2 * PI * hz * it / 16000) * 0.3).toFloat() }
        assertTrue(WakeFeatures.similarity(WakeFeatures.encode(tone(330)), WakeFeatures.encode(tone(2100))) < 0.8)
    }
}
