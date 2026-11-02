package app.kawkab.android.domain.voice

import java.time.LocalTime

/** Owner-selected wake service modes; SMART is the battery-saving default.
 * @property label English label on the Voice settings keys.
 */
enum class WakeListeningMode(val label: String) {
    /** Explicit override of proximity, orientation and low-battery heuristics. */
    ALWAYS("ALWAYS"),

    /** Apply all automatic battery and sensor pauses. */
    SMART("SMART"),

    /** Require charging as well as the smart conditions. */
    CHARGING("ONLY WHILE CHARGING"),

    /** Stop the wake service and release its microphone. */
    OFF("OFF"),
}

/** Optional local sleep schedule. Equal endpoints mean no scheduled pause.
 * @property mode Owner-selected listening mode.
 * @property sleepEnabled Whether the local sleep window pauses wake capture.
 * @property sleepStartMinute Inclusive window start, in minutes since midnight.
 * @property sleepEndMinute Exclusive window end, in minutes since midnight.
 */
data class WakeListeningConfig(
    val mode: WakeListeningMode = WakeListeningMode.SMART,
    val sleepEnabled: Boolean = false,
    val sleepStartMinute: Int = DEFAULT_SLEEP_START,
    val sleepEndMinute: Int = DEFAULT_SLEEP_END,
) {
    init {
        require(sleepStartMinute in 0 until MINUTES_PER_DAY && sleepEndMinute in 0 until MINUTES_PER_DAY)
    }

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val DEFAULT_SLEEP_START = 22 * 60
        const val DEFAULT_SLEEP_END = 7 * 60
    }
}

/** Only device state is needed; no call identity or audio content is recorded.
 * @property covered Whether the proximity sensor reports a nearby cover.
 * @property faceDown Whether orientation points the screen downward.
 * @property batteryPercent Current battery percentage, from zero to one hundred.
 * @property charging Whether Android reports charging or full on external power.
 * @property inCall Whether the device audio mode is a call, ringtone or communication mode.
 * @property conversation Whether the foreground conversation owns capture instead.
 */
data class WakeListeningState(
    val covered: Boolean = false,
    val faceDown: Boolean = false,
    val batteryPercent: Int = 100,
    val charging: Boolean = false,
    val inCall: Boolean = false,
    val conversation: Boolean = false,
)

/** Visible reasons for releasing, rather than merely ignoring, microphone capture.
 * @property label English explanation in the foreground notification.
 */
enum class WakePauseReason(val label: String) {
    /** The owner disabled wake listening. */
    OFF("OFF"),

    /** A foreground conversation owns the microphone. */
    CONVERSATION("CONVERSATION"),

    /** The device is handling ringing or call audio. */
    CALL("CALL IN PROGRESS"),

    /** The current local time falls inside the optional sleep window. */
    SLEEP("SLEEP HOURS"),

    /** The charging-only mode is waiting for external power. */
    NOT_CHARGING("WAITING FOR CHARGING"),

    /** The proximity sensor is covered. */
    COVERED("PROXIMITY COVERED"),

    /** The screen points downward. */
    FACE_DOWN("FACE DOWN"),

    /** Battery is below twenty percent and is not charging. */
    LOW_BATTERY("BATTERY UNDER 20%"),
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
