package app.kawkab.android.domain.voice

import kotlin.math.abs

/** Ten enrollment exemplars with speech trimming and gain normalization for a quiet distant voice. */
class PersonalWakeTemplates(recordings: List<FloatArray>) {
    private val templates = recordings.map(::features).filter { vector -> vector.any { it != 0.0 } }

    /** A partial or corrupt enrollment cannot authorize a wake. */
    val count: Int get() = templates.size.takeIf { recordingsComplete } ?: 0
    private val recordingsComplete = recordings.size == OwnerVoicePolicy.WAKE_REPETITIONS &&
        templates.size == OwnerVoicePolicy.WAKE_REPETITIONS

    /** Best normalized exemplar score, with no change to the frozen generic model representation. */
    fun score(samples: FloatArray): Double {
        if (count != OwnerVoicePolicy.WAKE_REPETITIONS) return 0.0
        val candidate = features(samples)
        return templates.maxOf { WakeFeatures.similarity(candidate, it) }.coerceIn(0.0, 1.0)
    }

    private fun features(raw: FloatArray): DoubleArray {
        if (raw.isEmpty() || raw.any { !it.isFinite() }) return WakeFeatures.encode(floatArrayOf())
        val speech = OwnerVoicePolicy.trimSpeech(raw)
        val peak = speech.maxOfOrNull { abs(it) } ?: 0f
        if (peak < MIN_PEAK) return WakeFeatures.encode(floatArrayOf())
        return WakeFeatures.encode(FloatArray(speech.size) { speech[it] * NORMAL_PEAK / peak })
    }

    private companion object {
        const val MIN_PEAK = 0.003f
        const val NORMAL_PEAK = 0.5f
    }
}
