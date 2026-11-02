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

    /** Looser bar used only while enrolling, to drop the odd noisy take instead of failing. */
    private const val ENROLL_SIMILARITY = 0.5

    /** Takes that may be dropped as outliers; at least ten of twelve must agree. */
    private const val MAX_OUTLIERS = 2
    private const val FRAME = 320
    private const val PAD_FRAMES = 10
    private const val VOICED_FRACTION = 0.15

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

    /**
     * Keeps only the spoken part of a clip, with a short pad, so a half-second "Kawkab" inside
     * three seconds of room noise is judged on the voice, not the noise. The result is never
     * shorter than [MIN_SAMPLES] when the source is long enough.
     */
    fun trimSpeech(samples: FloatArray): FloatArray {
        val frames = samples.size / FRAME
        if (frames == 0) return samples
        val energy = DoubleArray(frames) { f ->
            var sum = 0.0
            for (i in f * FRAME until (f + 1) * FRAME) sum += samples[i].toDouble() * samples[i]
            sqrt(sum / FRAME)
        }
        val bar = maxOf(MIN_RMS, (energy.maxOrNull() ?: 0.0) * VOICED_FRACTION)
        val first = energy.indexOfFirst { it >= bar }
        if (first < 0) return samples
        val last = energy.indexOfLast { it >= bar }
        var start = (first - PAD_FRAMES).coerceAtLeast(0) * FRAME
        var end = ((last + 1 + PAD_FRAMES) * FRAME).coerceAtMost(samples.size)
        if (end - start < MIN_SAMPLES && samples.size >= MIN_SAMPLES) {
            val middle = (start + end) / 2
            start = (middle - MIN_SAMPLES / 2).coerceIn(0, samples.size - MIN_SAMPLES)
            end = start + MIN_SAMPLES
        }
        return samples.copyOfRange(start, end)
    }

    /**
     * The agreed owner voice from all enrollment takes. A take counts when it sounds like more
     * than half of the other takes; up to [MAX_OUTLIERS] takes may fail that, otherwise the takes
     * do not come from one voice and the result is null.
     */
    fun enrollmentProfile(vectors: List<FloatArray>): FloatArray? {
        val size = vectors.firstOrNull()?.size ?: return null
        if (vectors.any { v -> v.size != size || v.any { !it.isFinite() } }) return null
        val others = vectors.size - 1
        val kept = vectors.filterIndexed { i, v ->
            val agreeing = vectors.indices.count { j ->
                j != i && similarity(v, vectors[j]) >= ENROLL_SIMILARITY
            }
            agreeing * 2 > others
        }
        if (kept.size < vectors.size - MAX_OUTLIERS) return null
        return centroid(kept)
    }

    private fun centroid(vectors: List<FloatArray>): FloatArray =
        FloatArray(vectors[0].size) { i -> vectors.map { it[i] }.average().toFloat() }

    private fun similarity(a: FloatArray, b: FloatArray): Double {
        val dot = a.indices.sumOf { a[it].toDouble() * b[it] }
        val left = sqrt(a.sumOf { it.toDouble() * it })
        val right = sqrt(b.sumOf { it.toDouble() * it })
        return if (left > 0 && right > 0) dot / (left * right) else 0.0
    }

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
