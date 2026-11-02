package app.kawkab.android.domain.voice

import java.time.LocalTime

/** Owner-selected wake service modes; SMART is the battery-saving default. */
enum class WakeListeningMode(val label: String) {
    ALWAYS("ALWAYS"), SMART("SMART"), CHARGING("ONLY WHILE CHARGING"), OFF("OFF"),
}

/** Optional local sleep schedule. Equal endpoints mean no scheduled pause. */
data class WakeListeningConfig(
    val mode: WakeListeningMode = WakeListeningMode.SMART,
    val sleepEnabled: Boolean = false,
    val sleepStartMinute: Int = 22 * 60,
    val sleepEndMinute: Int = 7 * 60,
) {
    init {
        require(sleepStartMinute in 0 until MINUTES_PER_DAY && sleepEndMinute in 0 until MINUTES_PER_DAY)
    }

    private companion object { const val MINUTES_PER_DAY = 24 * 60 }
}

/** Only device state is needed; no call identity or audio content is recorded. */
data class WakeListeningState(
    val covered: Boolean = false,
    val faceDown: Boolean = false,
    val batteryPercent: Int = 100,
    val charging: Boolean = false,
    val inCall: Boolean = false,
    val conversation: Boolean = false,
)

/** Visible reasons for releasing, rather than merely ignoring, microphone capture. */
enum class WakePauseReason(val label: String) {
    OFF("OFF"), CONVERSATION("CONVERSATION"), CALL("CALL IN PROGRESS"), SLEEP("SLEEP HOURS"),
    NOT_CHARGING("WAITING FOR CHARGING"), COVERED("PROXIMITY COVERED"), FACE_DOWN("FACE DOWN"), LOW_BATTERY("BATTERY UNDER 20%"),
}

/** Pure policy shared by device monitoring and deterministic tests. */
object WakeListeningPolicy {
    private const val LOW_BATTERY_PERCENT = 20
    private const val MINUTES_PER_HOUR = 60

    /** Calls, conversations and an enabled sleep schedule take precedence over every enabled mode. */
    fun pauseReason(config: WakeListeningConfig, state: WakeListeningState, time: LocalTime): WakePauseReason? = when {
        config.mode == WakeListeningMode.OFF -> WakePauseReason.OFF
        state.conversation -> WakePauseReason.CONVERSATION
        state.inCall -> WakePauseReason.CALL
        sleeping(config, time) -> WakePauseReason.SLEEP
        config.mode == WakeListeningMode.CHARGING && !state.charging -> WakePauseReason.NOT_CHARGING
        config.mode == WakeListeningMode.ALWAYS -> null
        state.covered -> WakePauseReason.COVERED
        state.faceDown -> WakePauseReason.FACE_DOWN
        state.batteryPercent < LOW_BATTERY_PERCENT && !state.charging -> WakePauseReason.LOW_BATTERY
        else -> null
    }

    private fun sleeping(config: WakeListeningConfig, time: LocalTime): Boolean {
        if (!config.sleepEnabled || config.sleepStartMinute == config.sleepEndMinute) return false
        val minute = time.hour * MINUTES_PER_HOUR + time.minute
        return if (config.sleepStartMinute < config.sleepEndMinute) {
            minute >= config.sleepStartMinute && minute < config.sleepEndMinute
        } else {
            minute >= config.sleepStartMinute || minute < config.sleepEndMinute
        }
    }
}
