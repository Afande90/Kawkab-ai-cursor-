package app.kawkab.android.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Mic accounting measures actual open intervals, including midnight and clock changes. */
class MicUsageMeterTest {
    private val zone = ZoneId.of("UTC")
    private val start = Instant.parse("2026-11-02T23:59:30Z").toEpochMilli()

    @Test fun `paused time never counts and checkpoints never count an interval twice`() {
        val meter = MicUsageMeter()
        assertTrue(meter.checkpoint(start, 0, zone).isEmpty())
        meter.open(start, 100)
        assertEquals(30_000L, meter.checkpoint(start + 30_000, 30_100, zone).values.sum())
        assertEquals(10_000L, meter.close(start + 40_000, 40_100, zone).values.sum())
        assertTrue(meter.close(start + 50_000, 50_100, zone).isEmpty())
        assertTrue(meter.checkpoint(start + 60_000, 60_100, zone).isEmpty())
    }

    @Test fun `an open minute spanning midnight is split between the two dates`() {
        val meter = MicUsageMeter()
        meter.open(start, 0)
        assertEquals(mapOf(LocalDate.parse("2026-11-02") to 30_000L, LocalDate.parse("2026-11-03") to 30_000L), meter.close(start + 60_000, 60_000, zone))
    }

    @Test fun `wall clock changes cannot inflate microphone minutes`() {
        val meter = MicUsageMeter()
        meter.open(start, 0)
        assertEquals(1_000L, meter.checkpoint(start - 3_600_000, 1_000, zone).values.sum())
        assertEquals(2_000L, meter.close(start + 86_400_000, 3_000, zone).values.sum())
    }

    @Test fun `daylight saving midnight uses the local calendar rather than fixed 24 hour days`() {
        val local = ZoneId.of("Europe/London")
        val beginning = Instant.parse("2026-03-29T00:00:00Z").toEpochMilli()
        val meter = MicUsageMeter()
        meter.open(beginning, 0)
        val day = 23 * 3_600_000L
        assertEquals(mapOf(LocalDate.parse("2026-03-29") to day, LocalDate.parse("2026-03-30") to 60_000L), meter.close(beginning + day + 60_000, day + 60_000, local))
    }
}
