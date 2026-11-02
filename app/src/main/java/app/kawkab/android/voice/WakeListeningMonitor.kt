package app.kawkab.android.voice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import app.kawkab.android.domain.voice.WakeListeningConfig
import app.kawkab.android.domain.voice.WakeListeningMode
import app.kawkab.android.domain.voice.WakeListeningPolicy
import app.kawkab.android.domain.voice.WakeListeningState
import app.kawkab.android.domain.voice.WakePauseReason
import java.time.LocalTime
import java.time.ZonedDateTime

/** Low-rate sensors and system events control capture; no polling, phone-state permission or wake lock. */
class WakeListeningMonitor(
    private val context: Context,
    private val conversation: () -> Boolean,
    private val changed: (WakePauseReason?) -> Unit,
) : SensorEventListener,
    AutoCloseable {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private var state = WakeListeningState()
    private var config = WakeListeningConfig()
    private var registered = false
    private var previous: WakePauseReason? = null
    private var delivered = false
    private val modeListener = AudioManager.OnModeChangedListener { mode ->
        state = state.copy(inCall = mode != AudioManager.MODE_NORMAL)
        refresh()
    }
    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) battery(intent)
            refresh()
            if (intent.action in
                setOf(ACTION_SLEEP_BOUNDARY, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
            ) {
                scheduleBoundary()
            }
        }
    }

    /** Called on the main thread after the service has become foreground. */
    fun configure(value: WakeListeningConfig) {
        config = value
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_SLEEP_BOUNDARY)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            ContextCompat.registerReceiver(context, events, filter, ContextCompat.RECEIVER_NOT_EXPORTED)?.let(::battery)
            audio?.addOnModeChangedListener(context.mainExecutor, modeListener)
            state = state.copy(inCall = audio != null && audio.mode != AudioManager.MODE_NORMAL)
            registered = true
        }
        sensors?.unregisterListener(this)
        state = state.copy(covered = false, faceDown = false)
        if (value.mode != WakeListeningMode.ALWAYS && value.mode != WakeListeningMode.OFF) {
            (
                sensors?.getDefaultSensor(
                    Sensor.TYPE_PROXIMITY,
                    true,
                ) ?: sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
                )?.let {
                sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            val gravity =
                sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            gravity?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, SENSOR_BATCH_US) }
        }
        refresh(force = true)
        scheduleBoundary()
    }

    /** Refresh on conversation handoff or a system time/power event. */
    fun refresh(force: Boolean = false) {
        val reason = WakeListeningPolicy.pauseReason(config, state.copy(conversation = conversation()), LocalTime.now())
        if (force || !delivered || reason != previous) {
            delivered = true
            previous = reason
            changed(reason)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        state = when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> state.copy(
                covered =
                event.values[0] < minOf(PROXIMITY_CM, event.sensor.maximumRange),
            )
            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> state.copy(
                faceDown =
                if (state.faceDown) event.values[2] < FACE_UP_Z else event.values[2] < FACE_DOWN_Z,
            )
            else -> state
        }
        refresh()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun close() {
        sensors?.unregisterListener(this)
        cancelBoundary()
        if (registered) {
            context.unregisterReceiver(events)
            audio?.removeOnModeChangedListener(modeListener)
            registered = false
        }
    }

    private fun scheduleBoundary() {
        cancelBoundary()
        if (!config.sleepEnabled || config.sleepStartMinute == config.sleepEndMinute) return
        val now = ZonedDateTime.now()
        val next = listOf(config.sleepStartMinute, config.sleepEndMinute).flatMap { minute ->
            listOf(now.toLocalDate(), now.toLocalDate().plusDays(1)).map { date ->
                date.atTime(minute / MINUTES_PER_HOUR, minute % MINUTES_PER_HOUR).atZone(now.zone)
            }
        }.filter { it.isAfter(now) }.minBy { it.toInstant() }
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_SLEEP_BOUNDARY).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            intent,
        )
    }

    private fun cancelBoundary() {
        val intent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_SLEEP_BOUNDARY).setPackage(context.packageName),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(intent)
    }

    private fun battery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        state = state.copy(
            batteryPercent = (level.toLong() * 100 / scale).toInt().coerceIn(0, 100),
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }

    private companion object {
        const val ACTION_SLEEP_BOUNDARY = "app.kawkab.android.WAKE_SLEEP_BOUNDARY"
        const val MINUTES_PER_HOUR = 60
        const val PROXIMITY_CM = 5f
        const val FACE_DOWN_Z = -7f
        const val FACE_UP_Z = -5f
        const val SENSOR_BATCH_US = 500_000
    }
}
