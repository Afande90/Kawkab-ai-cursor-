package app.knotwork.android.data.services

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Covers the foreground-time budget that keeps a model download under
 * Android's daily `dataSync` limit: what it books, when it runs out, and that
 * the count outlives the process that measured it.
 */
@RunWith(RobolectricTestRunner::class)
class DownloadForegroundBudgetTest {

    private lateinit var context: Context
    private var now = 0L

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        now = START_MS
    }

    @Test
    fun `given a fresh budget when asked then all of it is left`() {
        val budget = budget()

        assertEquals(DownloadForegroundBudget.BUDGET_MS, budget.remainingMs())
        assertFalse(budget.isExhausted())
    }

    @Test
    fun `given foreground time when a meter records it then the time left shrinks by exactly that much`() {
        val budget = budget()
        val meter = budget.startMeter()

        now += HOUR_MS
        meter.record()
        now += HOUR_MS
        meter.record()

        // Two readings, two hours — each reading books only the stretch since
        // the previous one, never the whole time again.
        assertEquals(DownloadForegroundBudget.BUDGET_MS - 2 * HOUR_MS, budget.remainingMs())
    }

    @Test
    fun `given more time than the budget holds when recorded then it runs out and stops at zero`() {
        val budget = budget()
        val meter = budget.startMeter()

        now += DownloadForegroundBudget.BUDGET_MS + HOUR_MS
        meter.record()

        assertEquals(0L, budget.remainingMs())
        assertTrue(budget.isExhausted())
    }

    @Test
    fun `given a spent budget when the user starts a download then it starts over`() {
        val budget = budget().apply { exhaust() }

        budget.reset()

        assertEquals(DownloadForegroundBudget.BUDGET_MS, budget.remainingMs())
    }

    @Test
    fun `given Android stopped the download for its limit when exhausted then nothing is left`() {
        val budget = budget()

        budget.exhaust()

        assertTrue(budget.isExhausted())
    }

    @Test
    fun `given time booked in one process when a new one reads it then the count is still there`() {
        val meter = budget().startMeter()
        now += HOUR_MS
        meter.record()

        // WorkManager re-runs a stopped download in a fresh process; a count
        // that died with the old one would let the new run overshoot Android's.
        assertEquals(DownloadForegroundBudget.BUDGET_MS - HOUR_MS, budget().remainingMs())
    }

    @Test
    fun `given a clock that went backwards when recorded then no time is booked`() {
        val budget = budget()
        val meter = budget.startMeter()

        now -= HOUR_MS
        meter.record()

        assertEquals(DownloadForegroundBudget.BUDGET_MS, budget.remainingMs())
    }

    /** A budget over the shared test preferences, reading [now] as its clock. */
    private fun budget(): DownloadForegroundBudget = DownloadForegroundBudget(
        preferences = {
            context.getSharedPreferences(DownloadForegroundBudget.PREFERENCES_NAME, Context.MODE_PRIVATE)
        },
        clock = { now },
    )

    private companion object {
        /** One hour, in milliseconds. */
        const val HOUR_MS = 60L * 60L * 1_000L

        /** An arbitrary non-zero starting clock reading. */
        const val START_MS = 1_000_000L
    }
}
