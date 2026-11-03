package app.kawkab.android.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keyword evidence can never authorize an unknown speaker, including at exact decision boundaries. */
class WakeAssessmentTest {
    private val match = WakeAssessment(personal = 0.90, speaker = 0.65, templates = 10)

    @Test fun `exact unchanged thresholds accept only a complete enrollment`() {
        assertTrue(match.accepted)
        assertFalse(match.copy(personal = 0.899999).accepted)
        assertFalse(match.copy(speaker = 0.649999).accepted)
        (0..9).forEach { assertFalse(match.copy(templates = it, sherpa = true).accepted) }
    }

    @Test fun `every keyword opinion still needs an owner match`() {
        listOf(
            match,
            match.copy(personal = 0.0, sherpa = true),
            match.copy(personal = 0.0, generic = 0.95, genericThreshold = 0.95),
        ).forEach { candidate ->
            assertTrue(candidate.accepted)
            listOf(null, Double.NaN, Double.POSITIVE_INFINITY, 0.2).forEach {
                assertFalse(candidate.copy(speaker = it).accepted)
                assertEquals("SPEAKER_REJECTED", candidate.copy(speaker = it).outcome)
            }
        }
    }

    @Test fun `invalid numeric keyword evidence cannot wake`() {
        assertFalse(match.copy(personal = Double.NaN, generic = Double.NaN).accepted)
        assertFalse(match.copy(personal = 0.0, generic = 0.9, genericThreshold = Double.NaN).accepted)
    }

    @Test fun `speaker score refuses invalid profiles and preserves cosine evidence`() {
        assertNull(OwnerVoicePolicy.score(floatArrayOf(1f), null))
        assertNull(OwnerVoicePolicy.score(floatArrayOf(1f), floatArrayOf(0f)))
        assertNull(OwnerVoicePolicy.score(floatArrayOf(Float.NaN), floatArrayOf(1f)))
        assertEquals(1.0, OwnerVoicePolicy.score(floatArrayOf(1f), floatArrayOf(1f))!!, 0.0)
    }
}
