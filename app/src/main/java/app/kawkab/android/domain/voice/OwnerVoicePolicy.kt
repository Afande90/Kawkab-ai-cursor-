package app.kawkab.android.domain.voice

import kotlin.math.sqrt

/** Speaker verification policy, independent of Android and model-generated confidence. */
object OwnerVoicePolicy {
    /** Wake-word recordings required before enrollment is complete. */
    const val WAKE_REPETITIONS = 10

    /** Longer recordings required to capture the owner beyond the wake word. */
    const val SENTENCES = 2
    private const val MIN_SAMPLES = 16_000
    private const val MIN_RMS = 0.003
    private const val SIMILARITY = 0.65

    /** No profile, a failed extractor or a non-finite vector always refuses the utterance. */
    fun matches(candidate: FloatArray, owner: FloatArray?): Boolean {
        if (owner == null || owner.isEmpty() || candidate.size != owner.size) return false
        if (candidate.any { !it.isFinite() } || owner.any { !it.isFinite() }) return false
        val dot = candidate.indices.sumOf { candidate[it].toDouble() * owner[it] }
        val left = sqrt(candidate.sumOf { it.toDouble() * it })
        val right = sqrt(owner.sumOf { it.toDouble() * it })
        return left > 0 && right > 0 && dot / (left * right) >= SIMILARITY
    }

    /** Twelve accepted recordings are required, not just the wake-word repetitions. */
    fun enrollmentComplete(count: Int): Boolean = count == WAKE_REPETITIONS + SENTENCES

    /** Refuses silence and clips too short to verify. */
    fun usable(samples: FloatArray): Boolean = samples.size >= MIN_SAMPLES &&
        samples.all { it.isFinite() } &&
        sqrt(samples.sumOf { it.toDouble() * it } / samples.size) >= MIN_RMS
}

/** Short-lived evidence issued only after raw audio passed the owner speaker check. */
object SpokenOwnerAnswer {
    private const val FRESH_MS = 3_000L
    private var answer: Pair<String, Long>? = null

    /** Called by the verified audio path, never by typed input or model output. */
    @Synchronized fun record(text: String, nowMs: Long = System.currentTimeMillis()) {
        answer = text to nowMs
    }

    /** A new deletion question invalidates every earlier answer, even within the freshness window. */
    @Synchronized fun clear() {
        answer = null
    }

    /** Consumes an explicit spoken yes once; a stale yes cannot approve a later deletion. */
    @Synchronized fun consumeYes(nowMs: Long = System.currentTimeMillis()): Boolean {
        val previous = answer.also { answer = null } ?: return false
        return nowMs - previous.second in 0..FRESH_MS &&
            VoiceAnswers.answer(previous.first) == VoiceAnswers.Answer.YES
    }
}
