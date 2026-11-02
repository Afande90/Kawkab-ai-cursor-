package app.kawkab.android.presentation.ui.pocket

import android.app.TimePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.kawkab.android.domain.voice.WakeListeningConfig
import app.kawkab.android.domain.voice.WakeListeningMode
import app.kawkab.android.voice.WakeMicUsage
import app.kawkab.android.voice.WakeWordSwitch
import app.kawkab.design.device.DeviceKey
import app.kawkab.design.device.LcdLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

/** Voice-only listening controls and local daily capture totals, inside the shared LCD. */
@Composable
fun PocketListeningSettings(enrolled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(WakeWordSwitch.config(context)) }
    var expanded by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(emptyMap<LocalDate, Long>()) }
    val usage = remember { WakeMicUsage(context) }
    fun update(value: WakeListeningConfig) {
        config = value
        // A sleep edit before enrollment must not enable capture.
        WakeWordSwitch.configure(context, value, activate = enrolled || value.mode == WakeListeningMode.OFF)
    }
    LifecycleResumeEffect(Unit) {
        config = WakeWordSwitch.config(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(usage) {
        while (true) {
            days = withContext(Dispatchers.IO) { usage.dailyMillis() }
            delay(REFRESH_MS)
        }
    }
    DeviceKey("LISTENING: ${config.mode.label}", onClick = { expanded = !expanded })
    if (expanded) {
        WakeListeningMode.entries.forEach { mode ->
            DeviceKey(mode.label, onClick = {
                if (enrolled || mode == WakeListeningMode.OFF) update(config.copy(mode = mode))
            }, pressed = config.mode == mode)
        }
        LcdLine("SMART: POCKET / FACE DOWN / LOW BATTERY")
        LcdLine("ALWAYS OVERRIDES SMART PAUSES")
        LcdLine("CALLS AND SLEEP HOURS STILL PAUSE")
    }
    DeviceKey("SLEEP HOURS: ${if (config.sleepEnabled) "ON" else "OFF"}", onClick = {
        update(config.copy(sleepEnabled = !config.sleepEnabled))
    })
    if (config.sleepEnabled) {
        DeviceKey("FROM ${clock(config.sleepStartMinute)}", onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    update(
                        config.copy(
                            sleepStartMinute =
                            hour * MINUTES_PER_HOUR + minute,
                        ),
                    )
                },
                config.sleepStartMinute / MINUTES_PER_HOUR,
                config.sleepStartMinute % MINUTES_PER_HOUR,
                true,
            ).show()
        })
        DeviceKey("UNTIL ${clock(config.sleepEndMinute)}", onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    update(
                        config.copy(
                            sleepEndMinute =
                            hour * MINUTES_PER_HOUR + minute,
                        ),
                    )
                },
                config.sleepEndMinute / MINUTES_PER_HOUR,
                config.sleepEndMinute % MINUTES_PER_HOUR,
                true,
            ).show()
        })
    }
    LcdLine("MIC TODAY: ${minutes(days[LocalDate.now()] ?: 0)} MIN")
    DeviceKey("DAILY MIC LOG", onClick = {
        showLog = !showLog
        scope.launch { days = withContext(Dispatchers.IO) { usage.dailyMillis() } }
    })
    if (showLog) {
        if (days.isEmpty()) LcdLine("NO MICROPHONE TIME RECORDED YET")
        days.toSortedMap(compareByDescending { it }).forEach { (date, millis) ->
            LcdLine("$date  ${minutes(millis)} MIN")
        }
        LcdLine("COUNTS MIC OPEN, INCLUDING SILENCE")
    }
}

private const val MINUTES_PER_HOUR = 60
private const val REFRESH_MS = 60_000L
private const val MS_PER_MINUTE = 60_000.0
private fun clock(minute: Int): String = String.format(
    Locale.ROOT,
    "%02d:%02d",
    minute / MINUTES_PER_HOUR,
    minute % MINUTES_PER_HOUR,
)
private fun minutes(millis: Long): String = String.format(Locale.ROOT, "%.1f", millis / MS_PER_MINUTE)
