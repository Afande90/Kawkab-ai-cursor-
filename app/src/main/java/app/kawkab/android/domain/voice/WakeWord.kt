package app.kawkab.android.domain.voice

/**
 * The wake word "Kawkab" / "كوكب" (task 27).
 *
 * The keyword spotter is sherpa-onnx's 3.3M-parameter English zipformer. "Kawkab" is not an
 * English word, so it is listed by the ways the model hears it, chosen on the calibration split
 * of `evals/wake-word/v1` only (see `DECISIONS.md`). The lines live in `assets/wake/keywords.txt`;
 * [KEYWORD_LINES] is the same list for the tests.
 */
object WakeWord {
    /** The tag every keyword line ends with. */
    const val TAG: String = "KAWKAB"

    /** The keyword lines, as `assets/wake/keywords.txt` holds them. */
    val KEYWORD_LINES: List<String> = listOf(
        "▁CO O K ▁UP",
        "▁CO CK A B",
        "▁CO CK ▁UP",
        "▁CO KE ▁UP",
        "▁CO O K A B",
        "▁K A W K A B",
        "▁CO CK ▁A B",
        "▁K O CK A B",
        "▁CO O K ▁A B",
    ).map { "$it @$TAG" }

    /** Spotter threshold, from the calibration sweep. Lower wakes more often. */
    const val THRESHOLD: Float = 0.15f

    /** Spotter boost for the keyword paths, from the calibration sweep. */
    const val BOOST: Float = 2.0f

    /** Audio rate of the microphone, the VAD and the spotter. */
    const val SAMPLE_RATE: Int = 16_000

    /** Samples per microphone read: 32 ms, the Silero VAD window. */
    const val FRAME_SAMPLES: Int = 512

    /** After a wake, further wakes are ignored for this long. */
    const val REFRACTORY_MS: Long = 2_000L
}

/**
 * Decides whether a spotter hit wakes Kawkab (task 27).
 *
 * @property refractoryMs Hits closer than this to the last wake are dropped.
 */
class WakeGate(private val refractoryMs: Long = WakeWord.REFRACTORY_MS) {
    private var lastWakeMs: Long? = null

    /**
     * @param nowMs The time of the hit.
     * @param busy `true` while a conversation already holds the microphone.
     * @return `true` when Kawkab should wake.
     */
    fun hit(nowMs: Long, busy: Boolean): Boolean {
        if (busy) return false
        val last = lastWakeMs
        if (last != null && nowMs - last < refractoryMs) return false
        lastWakeMs = nowMs
        return true
    }
}

/**
 * Passes audio to the spotter only around speech, so the spotter sleeps in silence (task 27).
 *
 * Silero VAD decides each frame. The frames just before speech are kept ([preRollFrames]) so
 * the start of "Kawkab" is not cut, and a few after it ([hangoverFrames]) so its end is not.
 *
 * @property preRollFrames Frames kept from before speech starts.
 * @property hangoverFrames Frames still passed after speech stops.
 */
class SpeechWindow(
    private val preRollFrames: Int = DEFAULT_PRE_ROLL,
    private val hangoverFrames: Int = DEFAULT_HANGOVER,
) {
    private val preRoll = ArrayDeque<FloatArray>()
    private var hangover = 0

    /**
     * @param frame One microphone frame.
     * @param speech Whether the VAD hears speech in it.
     * @return The frames to give the spotter now, oldest first. Empty in silence.
     */
    fun feed(frame: FloatArray, speech: Boolean): List<FloatArray> {
        if (speech) {
            val out = preRoll.toList() + listOf(frame)
            preRoll.clear()
            hangover = hangoverFrames
            return out
        }
        if (hangover > 0) {
            hangover -= 1
            return listOf(frame)
        }
        preRoll.addLast(frame)
        while (preRoll.size > preRollFrames) preRoll.removeFirst()
        return emptyList()
    }

    /** Forgets everything, for example after a wake. */
    fun reset() {
        preRoll.clear()
        hangover = 0
    }

    private companion object {
        /** About 0.5 s at 32 ms per frame. */
        const val DEFAULT_PRE_ROLL = 16

        /** About 0.6 s at 32 ms per frame. */
        const val DEFAULT_HANGOVER = 19
    }
}

/**
 * Wakes and battery since the wake word was turned on, for the Phone access card (task 27).
 *
 * @property startedAtMs When listening started.
 * @property startBatteryPercent The battery level then.
 * @property wakes How many times Kawkab woke since.
 */
data class WakeStats(val startedAtMs: Long, val startBatteryPercent: Int, val wakes: Int) {
    /**
     * @param nowMs The current time.
     * @return Hours of listening.
     */
    fun hours(nowMs: Long): Double = (nowMs - startedAtMs).coerceAtLeast(0L) / MS_PER_HOUR

    /**
     * @param nowMs The current time.
     * @return Wakes per hour, or `null` before [MIN_HOURS] of listening.
     */
    fun wakesPerHour(nowMs: Long): Double? = hours(nowMs).takeIf { it >= MIN_HOURS }?.let { wakes / it }

    /**
     * Battery used per hour. The whole phone's drain, not only Kawkab's: Android does not tell an
     * app its own share.
     *
     * @param nowMs The current time.
     * @param batteryPercent The battery level now.
     * @param charging Whether the phone charged in between; then nothing can be said.
     * @return Percent per hour, or `null` when it cannot be known yet.
     */
    fun batteryPerHour(nowMs: Long, batteryPercent: Int, charging: Boolean): Double? {
        val hours = hours(nowMs)
        if (charging || hours < MIN_HOURS || batteryPercent > startBatteryPercent) return null
        return (startBatteryPercent - batteryPercent) / hours
    }

    private companion object {
        const val MS_PER_HOUR = 3_600_000.0
        const val MIN_HOURS = 0.25
    }
}
