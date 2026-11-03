package app.kawkab.android.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The production and test paths share cadence, onset feedback and score selection without silent work. */
class ScoredWakeSessionTest {
    private val accepted = WakeAssessment(personal = 0.95, speaker = 0.8, templates = 10)

    @Test fun `silence never allocates audio or evaluates and does not flicker`() {
        val session =
            ScoredWakeSession(evaluate = { _, _ -> error("silent inference") }, onSpeech = { error("silent flicker") })
        repeat(1_000) { assertFalse(session.feed(false, true) { error("silent audio") }) }
        assertNull(session.best)
    }

    @Test fun `speech onset flickers once and accepted wake is logged once`() {
        var onsets = 0
        val results = mutableListOf<WakeAssessment>()
        val session = ScoredWakeSession({ _, _ -> accepted }, { onsets++ }, results::add)
        repeat(16) { session.feed(true, false) { floatArrayOf(1f) } }
        session.finish()
        assertEquals(1, onsets)
        assertEquals(listOf(accepted), results)
    }

    @Test fun `rejected speech logs one peak at segment end and sherpa is evaluated immediately`() {
        var evaluations = 0
        val results = mutableListOf<WakeAssessment>()
        val miss = accepted.copy(speaker = 0.2)
        val session =
            ScoredWakeSession({ _, sherpa ->
                evaluations++
                miss.copy(sherpa = sherpa)
            }, onResult = results::add)
        repeat(7) { assertFalse(session.feed(true, false) { floatArrayOf(1f) }) }
        assertEquals(0, evaluations)
        assertFalse(session.feed(true, true) { floatArrayOf(1f) })
        assertEquals(1, evaluations)
        assertTrue(results.isEmpty())
        session.feed(false, false) { error("silent audio") }
        assertEquals(1, results.size)
        assertEquals("SPEAKER_REJECTED", results.single().outcome)
    }

    @Test fun `pause clears prior scores and a new segment can wake again`() {
        val session = ScoredWakeSession({ _, _ -> accepted })
        assertTrue(session.feed(true, true) { floatArrayOf(1f) })
        session.reset()
        assertNull(session.best)
        assertTrue(session.feed(true, true) { floatArrayOf(1f) })
    }
}
