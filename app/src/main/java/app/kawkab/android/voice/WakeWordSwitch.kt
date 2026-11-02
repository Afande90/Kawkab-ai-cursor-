package app.kawkab.android.voice

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.BatteryManager
import androidx.core.content.edit
import app.kawkab.android.domain.voice.WakeListeningConfig
import app.kawkab.android.domain.voice.WakeListeningMode
import app.kawkab.android.domain.voice.WakeStats

/**
 * The wake word's on/off setting and its counters (task 27). Kept on this phone only, outside
 * backups of the chat data: it describes this phone's microphone, not the owner's content.
 */
object WakeWordSwitch {
    private const val FILE = "wake_word"
    private const val ENABLED = "enabled"
    private const val STARTED_AT = "started_at"
    private const val START_BATTERY = "start_battery"
    private const val WAKES = "wakes"
    private const val CHARGED = "charged"
    private const val FAILURE = "failure"
    private const val MODE = "listening_mode"
    private const val SLEEP = "sleep_enabled"
    private const val SLEEP_START = "sleep_start"
    private const val SLEEP_END = "sleep_end"
    private const val LAST_MINUTE_OF_DAY = 24 * 60 - 1

    /** SMART is the default; setup must still enroll the owner before enabling capture. */
    fun config(context: Context): WakeListeningConfig {
        val p = prefs(context)
        val fallback = if (p.contains(ENABLED) && !p.getBoolean(ENABLED, false)) {
            WakeListeningMode.OFF
        } else {
            WakeListeningMode.SMART
        }
        val mode = runCatching { WakeListeningMode.valueOf(p.getString(MODE, null).orEmpty()) }.getOrDefault(fallback)
        val defaults = WakeListeningConfig()
        return WakeListeningConfig(
            mode,
            p.getBoolean(SLEEP, false),
            p.getInt(SLEEP_START, defaults.sleepStartMinute).coerceIn(0, LAST_MINUTE_OF_DAY),
            p.getInt(SLEEP_END, defaults.sleepEndMinute).coerceIn(0, LAST_MINUTE_OF_DAY),
        )
    }

    /** Persist on the app process and send a complete snapshot to the isolated wake process. */
    fun configure(context: Context, config: WakeListeningConfig, activate: Boolean = true) {
        prefs(context).edit {
            putString(MODE, config.mode.name)
            putBoolean(SLEEP, config.sleepEnabled)
            putInt(SLEEP_START, config.sleepStartMinute)
            putInt(SLEEP_END, config.sleepEndMinute)
        }
        if (activate) set(context, config.mode != WakeListeningMode.OFF)
    }

    /** Preferences are cached per process; startup intents carry current settings explicitly. */
    internal fun attachConfig(context: Context, intent: Intent): Intent = config(context).let { value ->
        intent.putExtra(MODE, value.mode.name).putExtra(SLEEP, value.sleepEnabled)
            .putExtra(SLEEP_START, value.sleepStartMinute).putExtra(SLEEP_END, value.sleepEndMinute)
    }

    /** Reads the app process snapshot, without relying on stale cross-process preferences. */
    internal fun readConfig(context: Context, intent: Intent?): WakeListeningConfig {
        if (intent == null || !intent.hasExtra(MODE)) return config(context)
        val fallback = WakeListeningConfig()
        val mode = runCatching { WakeListeningMode.valueOf(intent.getStringExtra(MODE).orEmpty()) }
            .getOrDefault(WakeListeningMode.SMART)
        return WakeListeningConfig(
            mode,
            intent.getBooleanExtra(SLEEP, false),
            intent.getIntExtra(SLEEP_START, fallback.sleepStartMinute).coerceIn(0, LAST_MINUTE_OF_DAY),
            intent.getIntExtra(SLEEP_END, fallback.sleepEndMinute).coerceIn(0, LAST_MINUTE_OF_DAY),
        )
    }

    /** @return Whether the owner turned the wake word on. */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(ENABLED, false)

    /**
     * Turns the wake word on or off and starts or stops the listening service. Turning it on
     * restarts the counters.
     *
     * @param context Any context; the service starts only while the app is in front.
     * @param on The new setting.
     */
    fun set(context: Context, on: Boolean) {
        val battery = batteryPercent(context)
        val wasEnabled = isEnabled(context)
        prefs(context).edit {
            putBoolean(ENABLED, on)
            if (!on) putString(MODE, WakeListeningMode.OFF.name)
            if (on && config(context).mode == WakeListeningMode.OFF) putString(MODE, WakeListeningMode.SMART.name)
            if (on) remove(FAILURE)
            if (on && !wasEnabled) {
                putLong(STARTED_AT, System.currentTimeMillis())
                putInt(START_BATTERY, battery)
                putInt(WAKES, 0)
                putBoolean(CHARGED, false)
            }
        }
        if (on) WakeWordService.start(context) else WakeWordService.stop(context)
    }

    /**
     * The wake word could not start: it is turned off and the reason is kept for the notice (task 32).
     *
     * @param reason A short cause, such as the error's class name.
     */
    fun failed(context: Context, reason: String) {
        prefs(context).edit {
            putBoolean(ENABLED, false)
            putString(MODE, WakeListeningMode.OFF.name)
            putString(FAILURE, reason)
        }
        WakeWordService.stop(context)
    }

    /** @return Why the wake word last failed to start, or `null`. Cleared when it is turned on again. */
    fun failure(context: Context): String? = prefs(context).getString(FAILURE, null)

    /** Counts one wake. */
    fun countWake(context: Context) {
        val p = prefs(context)
        p.edit { putInt(WAKES, p.getInt(WAKES, 0) + 1) }
    }

    /** Notes that the phone charged, so battery use can no longer be measured since start. */
    fun noteCharging(context: Context) {
        prefs(context).edit { putBoolean(CHARGED, true) }
    }

    /** @return Whether the phone charged since listening started. */
    fun charged(context: Context): Boolean = prefs(context).getBoolean(CHARGED, false)

    /** @return The counters, or `null` while the wake word is off. */
    fun stats(context: Context): WakeStats? {
        val p = prefs(context)
        if (!p.getBoolean(ENABLED, false)) return null
        return WakeStats(p.getLong(STARTED_AT, 0L), p.getInt(START_BATTERY, 0), p.getInt(WAKES, 0))
    }

    /** @return The battery level in percent. */
    fun batteryPercent(context: Context): Int = context.getSystemService(BatteryManager::class.java)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0

    /** @return Whether the phone is charging now. */
    fun isCharging(context: Context): Boolean = context.getSystemService(BatteryManager::class.java)?.isCharging == true

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
