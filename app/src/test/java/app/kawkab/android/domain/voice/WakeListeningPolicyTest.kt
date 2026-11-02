package app.kawkab.android.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/** Each automatic pause and each listening mode is held to the owner's battery rules. */
class WakeListeningPolicyTest {
    private val smart = WakeListeningConfig()
    private val ready = WakeListeningState(batteryPercent = 80)
    private val noon = LocalTime.NOON

    @Test fun `smart is the default and an uncovered charged-enough idle phone listens`() {
        assertEquals(WakeListeningMode.SMART, smart.mode)
        assertNull(WakeListeningPolicy.pauseReason(smart, ready, noon))
    }

    @Test fun `covered proximity pauses and uncovering resumes`() {
        assertEquals(WakePauseReason.COVERED, reason(ready.copy(covered = true)))
        assertNull(reason(ready.copy(covered = false)))
    }

    @Test fun `face down pauses and face up resumes`() {
        assertEquals(WakePauseReason.FACE_DOWN, reason(ready.copy(faceDown = true)))
        assertNull(reason(ready.copy(faceDown = false)))
    }

    @Test fun `under twenty percent pauses only while unplugged`() {
        assertEquals(WakePauseReason.LOW_BATTERY, reason(ready.copy(batteryPercent = 19)))
        assertNull(reason(ready.copy(batteryPercent = 20)))
        assertNull(reason(ready.copy(batteryPercent = 1, charging = true)))
    }

    @Test fun `calls pause every enabled mode and ending the call resumes`() {
        WakeListeningMode.entries.filter { it != WakeListeningMode.OFF }.forEach { mode ->
            val config = smart.copy(mode = mode)
            assertEquals(
                WakePauseReason.CALL,
                WakeListeningPolicy.pauseReason(config, ready.copy(inCall = true, charging = true), noon),
            )
            assertNull(WakeListeningPolicy.pauseReason(config, ready.copy(charging = true), noon))
        }
    }

    @Test fun `sleep hours span midnight with inclusive start and exclusive end`() {
        val config = smart.copy(sleepEnabled = true, sleepStartMinute = 22 * 60, sleepEndMinute = 7 * 60)
        for (hour in listOf(22, 23, 0, 6)) {
            assertEquals(WakePauseReason.SLEEP, WakeListeningPolicy.pauseReason(config, ready, LocalTime.of(hour, 0)))
        }
        assertNull(WakeListeningPolicy.pauseReason(config, ready, LocalTime.of(7, 0)))
        assertNull(WakeListeningPolicy.pauseReason(config, ready, LocalTime.of(21, 59)))
        assertNull(WakeListeningPolicy.pauseReason(config.copy(sleepEnabled = false), ready, LocalTime.MIDNIGHT))
    }

    @Test fun `daytime sleep window and empty window are handled without an all-day pause`() {
        val config = smart.copy(sleepEnabled = true, sleepStartMinute = 12 * 60, sleepEndMinute = 14 * 60)
        assertEquals(WakePauseReason.SLEEP, WakeListeningPolicy.pauseReason(config, ready, noon))
        assertNull(WakeListeningPolicy.pauseReason(config, ready, LocalTime.of(14, 0)))
        assertNull(WakeListeningPolicy.pauseReason(config.copy(sleepEndMinute = 12 * 60), ready, noon))
    }

    @Test fun `charging-only pauses when power is removed even with a full battery`() {
        val config = smart.copy(mode = WakeListeningMode.CHARGING)
        assertEquals(WakePauseReason.NOT_CHARGING, WakeListeningPolicy.pauseReason(config, ready, noon))
        assertNull(WakeListeningPolicy.pauseReason(config, ready.copy(charging = true), noon))
    }

    @Test fun `off always closes listening and conversations keep every other mode paused`() {
        assertEquals(
            WakePauseReason.OFF,
            WakeListeningPolicy.pauseReason(smart.copy(mode = WakeListeningMode.OFF), ready, noon),
        )
        assertEquals(WakePauseReason.CONVERSATION, reason(ready.copy(conversation = true)))
    }

    @Test fun `always overrides smart heuristics but respects optional sleep`() {
        val config = smart.copy(mode = WakeListeningMode.ALWAYS)
        assertNull(
            WakeListeningPolicy.pauseReason(
                config,
                ready.copy(covered = true, faceDown = true, batteryPercent = 1),
                noon,
            ),
        )
        assertEquals(
            WakePauseReason.SLEEP,
            WakeListeningPolicy.pauseReason(config.copy(sleepEnabled = true), ready, LocalTime.MIDNIGHT),
        )
    }

    private fun reason(state: WakeListeningState) = WakeListeningPolicy.pauseReason(smart, state, noon)
}
