package app.kawkab.android.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Silence must never trigger feature extraction or speaker verification. */
class SpeechWakeGateTest {
    @Test fun `silence does no personal or speaker work even after a stale keyword hit`() {
        var work = 0
        val gate = SpeechWakeGate(personal = { work++; true }, speaker = { work++; true })
        repeat(1_000) { assertFalse(gate.feed(false, true) { work++; floatArrayOf(1f) }) }
        assertEquals(0, work)
    }

    @Test fun `personal checks are throttled within speech and only a keyword checks the speaker`() {
        var personal = 0
        var speakers = 0
        val gate = SpeechWakeGate(personal = { personal++; false }, speaker = { speakers++; true })
        repeat(16) { assertFalse(gate.feed(true, false) { floatArrayOf(1f) }) }
        assertEquals(2, personal)
        assertEquals(0, speakers)
        assertTrue(gate.feed(true, true) { floatArrayOf(1f) })
        assertEquals(2, personal)
        assertEquals(1, speakers)
    }

    @Test fun `a detected personal keyword still ignores another speaker`() {
        val gate = SpeechWakeGate(personal = { true }, speaker = { false })
        repeat(8) { assertFalse(gate.feed(true, false) { floatArrayOf(1f) }) }
    }
}
