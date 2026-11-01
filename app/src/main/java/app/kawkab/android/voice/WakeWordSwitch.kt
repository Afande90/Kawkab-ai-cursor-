package app.kawkab.android.voice

import android.content.Context
import android.content.SharedPreferences
import android.os.BatteryManager
import androidx.core.content.edit
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
        prefs(context).edit {
            putBoolean(ENABLED, on)
            if (on) {
                putLong(STARTED_AT, System.currentTimeMillis())
                putInt(START_BATTERY, battery)
                putInt(WAKES, 0)
                putBoolean(CHARGED, false)
            }
        }
        if (on) WakeWordService.start(context) else WakeWordService.stop(context)
    }

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
