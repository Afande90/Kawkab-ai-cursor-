package app.kawkab.android.voice

import app.kawkab.android.domain.voice.WakeListeningConfig
import app.kawkab.android.domain.voice.WakeListeningMode
import app.kawkab.android.domain.voice.WakeListeningPolicy
import app.kawkab.android.domain.voice.WakeListeningState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId

/** Every pause releases the capture resource, rather than leaving an ignored microphone open. */
class WakeMicSessionTest {
    @Test fun `each automatic pause closes the microphone and resume opens a fresh resource`() {
        val config = WakeListeningConfig(sleepEnabled = true)
        val ready = WakeListeningState(batteryPercent = 80)
        val pauses = listOf(
            ready.copy(covered = true) to LocalTime.NOON,
            ready.copy(faceDown = true) to LocalTime.NOON,
            ready.copy(batteryPercent = 19) to LocalTime.NOON,
            ready.copy(inCall = true) to LocalTime.NOON,
            ready to LocalTime.MIDNIGHT,
            ready.copy(conversation = true) to LocalTime.NOON,
        )
        pauses.forEach { (state, time) ->
            var releases = 0
            val session = WakeMicSession({ FakeCapture { releases++ } }, {}, { 0L }, { 0L }, { ZoneId.of("UTC") })
            session.setAllowed(true)
            val original = session.acquireOrWait(1)
            assertTrue(session.isCurrent(requireNotNull(original)))
            session.setAllowed(WakeListeningPolicy.pauseReason(config, state, time) == null)
            assertEquals(1, releases)
            assertFalse(session.isCurrent(original))
            assertNull(session.acquireOrWait(1))
            session.setAllowed(true)
            assertNotSame(original, session.acquireOrWait(1))
            session.close()
            assertEquals(2, releases)
        }
    }

    @Test fun `off charging-only and shutdown cannot reopen capture`() {
        var opens = 0
        val session = WakeMicSession({ opens++; FakeCapture {} }, {}, { 0L }, { 0L }, { ZoneId.of("UTC") })
        for (mode in listOf(WakeListeningMode.OFF, WakeListeningMode.CHARGING)) {
            session.setAllowed(WakeListeningPolicy.pauseReason(WakeListeningConfig(mode), WakeListeningState(), LocalTime.NOON) == null)
            assertNull(session.acquireOrWait(1))
        }
        session.close()
        session.setAllowed(true)
        assertNull(session.acquireOrWait(1))
        assertEquals(0, opens)
    }

    @Test fun `successful capture time is counted exactly once on release`() {
        var elapsed = 0L
        var recorded = 0L
        val session = WakeMicSession({ FakeCapture {} }, { recorded += it.values.sum() }, { elapsed }, { elapsed }, { ZoneId.of("UTC") })
        session.setAllowed(true)
        session.acquireOrWait(1)
        elapsed = 60_000
        session.setAllowed(false)
        session.close()
        assertEquals(60_000L, recorded)
    }

    private class FakeCapture(private val onClose: () -> Unit) : WakeCapture {
        override fun read(pcm: ShortArray): Int = pcm.size
        override fun close() = onClose()
    }
}
