package app.kawkab.android.voice

import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** A throttled package-only hint for a dynamically registered foreground LCD; never opens the app. */
internal class WakeHeardSignal(private val context: Context) {
    private var last = -MIN_INTERVAL_MS

    /** Speech onset only, not every audio frame; no manifest receiver listens for this action. */
    fun heard() {
        val now = SystemClock.elapsedRealtime()
        if (now - last < MIN_INTERVAL_MS) return
        last = now
        context.sendBroadcast(Intent(ACTION).setPackage(context.packageName))
    }

    companion object {
        /** Only an already resumed Pocket LCD registers for this non-exported signal. */
        const val ACTION = "app.kawkab.android.WAKE_HEARD_SOMETHING"
        private const val MIN_INTERVAL_MS = 1_800L
    }
}
