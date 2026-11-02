package app.kawkab.android.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Every bin is compared to the independent frozen NumPy/Whisper reference, including padding and trimming. */
class SmartTurnFeaturesTest {
    @Test fun shortAudioMatchesLeadingPaddingAndWhisperFeatures() = compare("short", signal(15360))
    @Test fun longAudioMatchesLastEightSeconds() = compare("long", signal(144000))
    @Test fun silenceIsFiniteAndMatchesTheReference() = compare("silence", FloatArray(16000))

    private fun signal(size: Int): FloatArray = FloatArray(size) { index ->
        val t = index / 16000.0
        (0.1 * sin(2 * PI * (200 * t + 60 * t * t)) + 0.03 * cos(2 * PI * 1700 * t)).toFloat()
    }

    private fun compare(name: String, input: FloatArray) {
        val raw = requireNotNull(javaClass.getResourceAsStream("/turn/v1/$name.f32")).use { it.readBytes() }
        val expected = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val actual = SmartTurnFeatures.extract(input)
        assertEquals(80 * 800, actual.size)
        assertEquals(actual.size, expected.remaining())
        var maximumError = 0f
        for (value in actual) {
            assertTrue(value.isFinite())
            maximumError = maxOf(maximumError, abs(value - expected.get()))
        }
        assertTrue("$name maximum reference error: $maximumError", maximumError <= 0.0005f)
    }
}
