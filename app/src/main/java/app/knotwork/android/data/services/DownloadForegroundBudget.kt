package app.knotwork.android.data.services

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps model downloads under Android's daily limit for `dataSync` foreground
 * services.
 *
 * Android 15 lets an app's `dataSync` services run for six hours in a 24-hour
 * window and resets the count whenever the app is on screen. When the limit is
 * reached the system asks the service to stop and gives it a few seconds; if it
 * is still in the foreground by then, the system crashes the app. WorkManager
 * does handle that request for [ModelDownloadWorker], and the crash still
 * happened in the field, so the download stops itself first: once it has spent
 * [BUDGET_MS] in the foreground, it pauses and asks the user to continue.
 *
 * The count is app-wide, like Android's, and survives process death, because
 * WorkManager re-runs a stopped download in a fresh process. It starts over only
 * when the user starts or re-attaches to a download ([reset]). The app is on
 * screen at that moment, so Android has reset its own count too. Android also
 * resets on every other visit to the app, and this budget does not, so it can
 * only run out earlier than Android's, never later.
 *
 * @property preferences Supplies the store the spent time is kept in.
 * @property clock Monotonic milliseconds used to measure foreground time.
 */
@Singleton
class DownloadForegroundBudget internal constructor(
    private val preferences: () -> SharedPreferences,
    private val clock: () -> Long,
) {

    /**
     * Hilt-visible constructor wiring the production store and clock.
     * `elapsedRealtime` keeps counting through deep sleep, which is when a
     * background download spends most of its time. Kept secondary because Hilt
     * rejects a class with two `@Inject` constructors.
     *
     * @param context Application context owning the preferences file.
     */
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        preferences = { context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE) },
        clock = SystemClock::elapsedRealtime,
    )

    /**
     * Starts the budget over. Call it only where the user acts on a visible
     * screen — that is what makes Android's own count start over at the same
     * moment; `DownloadStartCallSiteGuardTest` pins those places.
     *
     * A reset while a download is running does not extend that run: it pauses
     * at the deadline it started with, and the run after it gets the full
     * budget. That errs towards pausing early, never towards Android's limit.
     */
    @Synchronized
    fun reset() {
        preferences().edit { putLong(KEY_SPENT_MS, 0L) }
    }

    /**
     * Foreground time left before the download has to pause.
     *
     * @return Milliseconds left; `0` once the budget is spent.
     */
    @Synchronized
    fun remainingMs(): Long = (BUDGET_MS - spentMs()).coerceAtLeast(0L)

    /**
     * Whether the budget is spent.
     *
     * @return `true` when a download must pause instead of entering the
     *   foreground.
     */
    fun isExhausted(): Boolean = remainingMs() == 0L

    /**
     * Marks the budget spent. Used when Android stopped the download for its
     * time limit anyway, meaning this count fell short of Android's; any further
     * foreground start would be refused until the user returns.
     */
    @Synchronized
    fun exhaust() {
        preferences().edit { putLong(KEY_SPENT_MS, BUDGET_MS) }
    }

    /**
     * Starts measuring one stretch of foreground time.
     *
     * @return A meter that adds the time elapsed since its previous reading to
     *   the budget on every [Meter.record].
     */
    fun startMeter(): Meter = Meter(clock())

    /**
     * Measures one stretch of foreground time and books it against the budget.
     * Recording in small steps bounds what a killed process can lose to one
     * step.
     *
     * @param startedAt Clock reading the stretch started at.
     */
    inner class Meter internal constructor(startedAt: Long) {
        private var lastReading = startedAt

        /** Books the time elapsed since the previous reading. */
        fun record() {
            val now = clock()
            add((now - lastReading).coerceAtLeast(0L))
            lastReading = now
        }
    }

    @Synchronized
    private fun add(elapsedMs: Long) {
        if (elapsedMs == 0L) return
        val spent = (spentMs() + elapsedMs).coerceAtMost(BUDGET_MS)
        preferences().edit { putLong(KEY_SPENT_MS, spent) }
    }

    private fun spentMs(): Long = preferences().getLong(KEY_SPENT_MS, 0L)

    /** Constants of the budget. */
    companion object {
        /**
         * Foreground time a download may use before it pauses: five hours, one
         * below Android's six. The hour covers time the count cannot see — a
         * process killed between two readings, or WorkManager keeping the
         * `dataSync` type on its service while another worker holds the
         * notification after the download has finished.
         */
        const val BUDGET_MS: Long = 5L * 60L * 60L * 1_000L

        /** Preferences file holding the spent time. */
        internal const val PREFERENCES_NAME = "model_download_foreground_budget"

        /** Key of the spent foreground time, in milliseconds. */
        private const val KEY_SPENT_MS = "spent_ms"
    }
}
