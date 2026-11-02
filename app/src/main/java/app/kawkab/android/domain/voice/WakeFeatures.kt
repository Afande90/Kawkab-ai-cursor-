package app.kawkab.android.domain.voice

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** Duration-normalized Goertzel log-spectral features; identical to the local CPU training pipeline. */
object WakeFeatures {
    private const val RATE = 16_000
    private const val STEPS = 32
    private const val BANDS = 16
    private const val FRAME = 320
    private const val FIRST_HZ = 100.0
    private const val BAND_RATIO = 1.27
    private const val SILENCE = 0.01
    private const val EPSILON = 1e-12

    /** A fixed-size representation of the spoken keyword, with silence trimmed. */
    fun encode(audio: FloatArray): DoubleArray {
        val result = DoubleArray(STEPS * BANDS)
        val first = audio.indexOfFirst { abs(it) > SILENCE }
        val last = audio.indexOfLast { abs(it) > SILENCE }
        if (first < 0 || audio.any { !it.isFinite() }) return result
        for (step in 0 until STEPS) {
            val start = (first + (last - first).toDouble() * step / (STEPS - 1)).toInt() - FRAME / 2
            for (band in 0 until BANDS) {
                val coefficient = 2 * cos(2 * Math.PI * FIRST_HZ * BAND_RATIO.pow(band) / RATE)
                var s1 = 0.0
                var s2 = 0.0
                for (sample in start until start + FRAME) {
                    val s0 = audio.getOrElse(sample) { 0f } + coefficient * s1 - s2
                    s2 = s1
                    s1 = s0
                }
                result[step * BANDS + band] = ln1p(max(0.0, s1 * s1 + s2 * s2 - coefficient * s1 * s2) / FRAME)
            }
        }
        val mean = result.average()
        for (i in result.indices) result[i] -= mean
        val norm = max(sqrt(result.sumOf { it * it }), EPSILON)
        for (i in result.indices) result[i] /= norm
        return result
    }

    /** Cosine similarity; a zero or invalid feature vector can never match. */
    fun similarity(left: DoubleArray, right: DoubleArray): Double {
        if (left.size != right.size || left.any { !it.isFinite() } || right.any { !it.isFinite() }) return 0.0
        val norm = sqrt(left.sumOf { it * it } * right.sumOf { it * it })
        return if (norm <= EPSILON) 0.0 else left.indices.sumOf { left[it] * right[it] } / norm
    }
}
