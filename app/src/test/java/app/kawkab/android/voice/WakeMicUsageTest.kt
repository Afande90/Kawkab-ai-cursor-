package app.kawkab.android.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

/** Daily on-device aggregates survive a service restart and are visible across store instances. */
@RunWith(RobolectricTestRunner::class)
class WakeMicUsageTest {
    @Test fun `flushing preserves each date and restarting does not reset or double count`() {
        val context = RuntimeEnvironment.getApplication<android.app.Application>()
        val day = LocalDate.parse("2026-11-02")
        val store = WakeMicUsage(context)
        store.record(mapOf(day to 60_000L, day.plusDays(1) to 30_000L))
        store.flush()
        store.flush()
        assertEquals(mapOf(day to 60_000L, day.plusDays(1) to 30_000L), WakeMicUsage(context).dailyMillis())
        val restarted = WakeMicUsage(context)
        restarted.record(mapOf(day to 30_000L))
        restarted.flush()
        assertEquals(90_000L, store.dailyMillis().getValue(day))
        assertFalse(java.io.File(context.filesDir, "wake_mic_usage").exists())
    }
}
