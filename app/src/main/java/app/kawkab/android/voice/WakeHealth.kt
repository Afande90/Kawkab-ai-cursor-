package app.kawkab.android.voice

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context

/**
 * Whether the wake process died in native code since the wake word was last turned on (M1.8).
 * Read from Android's own record of how processes ended, which both processes can see.
 */
object WakeHealth {
    /** The reason shown when the native library crashed. */
    const val NATIVE_CRASH: String = "its speech library crashed on this phone"

    /** @return `true` when the `:wake` process ended in a native crash after it was turned on. */
    fun nativeCrashedSinceEnabled(context: Context): Boolean {
        val since = WakeWordSwitch.stats(context)?.startedAtMs ?: return false
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        val process = context.packageName + PROCESS_SUFFIX
        return runCatching { manager.getHistoricalProcessExitReasons(context.packageName, 0, EXITS_READ) }
            .getOrDefault(emptyList())
            .any {
                it.processName == process &&
                    it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE &&
                    it.timestamp >= since
            }
    }

    /** The wake service's process name after the package name. */
    const val PROCESS_SUFFIX: String = ":wake"

    private const val EXITS_READ = 16
}
