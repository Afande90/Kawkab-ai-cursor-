package app.kawkab.android.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** Personal enrollment evidence is complete, gain invariant and separate from generic training features. */
class PersonalWakeTemplatesTest {
    @Test fun `all ten enrolled exemplars match their own templates`() {
        val clips = (0 until 10).map { word(180.0 + it * 12) }
        val templates = PersonalWakeTemplates(clips)
        assertEquals(10, templates.count)
        clips.forEach { assertTrue(templates.score(it) >= WakeAssessment.PERSONAL_THRESHOLD) }
    }

    @Test fun `quieter fresh clip and shifted silence preserve the personal word score`() {
        val clip = word(210.0)
        val templates = PersonalWakeTemplates(List(10) { clip })
        val quieter = FloatArray(4_000) + clip.map { it / 8 }.toFloatArray() + FloatArray(8_000)
        assertTrue(templates.score(quieter) >= WakeAssessment.PERSONAL_THRESHOLD)
    }

    @Test fun `partial corrupt or silent enrollment cannot match`() {
        val clip = word(210.0)
        listOf(
            List(9) { clip },
            List(11) { clip },
            List(10) { FloatArray(48_000) },
            List(9) { clip } + listOf(floatArrayOf(Float.NaN)),
        ).forEach {
            val templates = PersonalWakeTemplates(it)
            assertEquals(0, templates.count)
            assertEquals(0.0, templates.score(clip), 0.0)
        }
    }

    @Test fun `silence invalid samples and a different spectral sequence do not match`() {
        val templates = PersonalWakeTemplates(List(10) { word(210.0) })
        assertEquals(0.0, templates.score(FloatArray(48_000)), 0.0)
        assertEquals(0.0, templates.score(floatArrayOf(Float.NaN)), 0.0)
        assertTrue(templates.score(word(1_200.0)) < WakeAssessment.PERSONAL_THRESHOLD)
    }

    private fun word(frequency: Double): FloatArray = FloatArray(48_000) { i ->
        if (i in
            12_000 until 28_000
        ) {
            (0.2 * sin(2 * PI * (frequency + (i - 12_000) / 80.0) * i / 16_000)).toFloat()
        } else {
            0f
        }
    }
}
