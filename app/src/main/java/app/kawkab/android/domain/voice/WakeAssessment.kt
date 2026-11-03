package app.kawkab.android.domain.voice

/** Numeric evidence for one speech window, never a transcript or an audio recording.
 * @property personal Best cosine similarity across the ten owner keyword templates.
 * @property generic Learned generic head score; not a calibrated probability.
 * @property genericThreshold Frozen decision threshold shipped with that head.
 * @property sherpa Whether the independent keyword spotter detected Kawkab.
 * @property speaker Cosine similarity to the owner, or null when no keyword justified inference.
 * @property templates Number of valid enrolled keyword templates.
 */
data class WakeAssessment(
    val personal: Double = 0.0,
    val generic: Double = 0.0,
    val genericThreshold: Double = 1.0,
    val sherpa: Boolean = false,
    val speaker: Double? = null,
    val templates: Int = 0,
) {
    /** All ten templates are required, including when sherpa supplies the keyword opinion. */
    val keyword: Boolean get() = templates == OwnerVoicePolicy.WAKE_REPETITIONS &&
        (
            personal.isFinite() &&
                personal >= PERSONAL_THRESHOLD ||
                generic.isFinite() &&
                genericThreshold.isFinite() &&
                genericThreshold > 0 &&
                generic >= genericThreshold ||
                sherpa
            )

    /** Unknown or invalid speakers always fail closed, regardless of keyword score. */
    val accepted: Boolean get() = keyword &&
        speaker?.let { it.isFinite() && it >= OwnerVoicePolicy.MATCH_THRESHOLD } == true

    /** Stable private diagnostic labels; these never dispatch a command. */
    val outcome: String get() = when {
        templates != OwnerVoicePolicy.WAKE_REPETITIONS -> "NOT_ENROLLED"
        !keyword -> "KEYWORD_MISS"
        !accepted -> "SPEAKER_REJECTED"
        else -> "ACCEPTED"
    }

    /** Fixed decision constants; the phone test never changes these. */
    companion object {
        /** Existing personal threshold, unchanged by the arm's-length test. */
        const val PERSONAL_THRESHOLD = 0.90
    }
}

/** Speech-only evaluation with one result per segment; silence performs no feature or speaker work. */
class ScoredWakeSession(
    private val evaluate: (FloatArray, Boolean) -> WakeAssessment,
    private val onSpeech: () -> Unit = {},
    private val onResult: (WakeAssessment) -> Unit = {},
) {
    private var speaking = false
    private var tick = 0
    private var emitted = false
    private var segment: WakeAssessment? = null

    /** Strongest result in this bounded capture, available to the non-actioning wake test. */
    var best: WakeAssessment? = null
        private set

    /** Speaker inference belongs inside evaluate and is needed only for keyword candidates. */
    fun feed(speech: Boolean, keyword: Boolean, samples: () -> FloatArray): Boolean {
        if (!speech) {
            if (!emitted) segment?.let(onResult)
            speaking = false
            emitted = false
            tick = 0
            segment = null
            return false
        }
        if (!speaking) {
            speaking = true
            onSpeech()
        }
        if (emitted) return false
        tick = (tick + 1) % FRAME_INTERVAL
        if (!keyword && tick != 0) return false
        val result = evaluate(samples(), keyword)
        segment = stronger(segment, result)
        best = stronger(best, result)
        if (result.accepted) {
            onResult(result)
            emitted = true
        }
        return result.accepted
    }

    /** Flushes a last segment before closing a capture; never issues a second accepted event. */
    fun finish() {
        if (!emitted) segment?.let(onResult)
        emitted = true
    }

    /** A pause discards all pre-pause audio evidence, including diagnostic maxima. */
    fun reset() {
        speaking = false
        emitted = false
        tick = 0
        segment = null
        best = null
    }

    private fun stronger(old: WakeAssessment?, next: WakeAssessment): WakeAssessment = when {
        old == null -> next
        next.accepted && !old.accepted -> next
        old.accepted -> old
        next.keyword && !old.keyword -> next
        old.keyword && !next.keyword -> old
        next.personal > old.personal -> next
        else -> old
    }

    private companion object {
        const val FRAME_INTERVAL = 8
    }
}
