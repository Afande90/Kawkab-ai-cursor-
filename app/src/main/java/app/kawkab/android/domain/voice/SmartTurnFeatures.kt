package app.kawkab.android.domain.voice

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Whisper-tiny's 80x800 Slaney log-mel input, matched to the frozen upstream NumPy reference. */
object SmartTurnFeatures {
    /** Input PCM sampling rate. */
    const val RATE = 16_000

    /** The published model uses the last eight seconds. */
    const val SECONDS = 8

    /** Fixed padded input sample count. */
    const val SAMPLES = RATE * SECONDS

    /** Whisper-tiny mel bands. */
    const val BANDS = 80

    /** Time frames after dropping the final centered-STFT frame. */
    const val FRAMES = 800
    private const val FFT = 400
    private const val HOP = 160
    private const val RADIX_FOUR = 4
    private const val RADIX_FIVE = 5
    private const val LINEAR_HZ = 1_000.0
    private const val LINEAR_MEL = 15.0
    private const val MEL_STEP = 200.0 / 3.0
    private const val LOG_BASE = 6.4
    private const val LOG_DIVISOR = 27.0
    private const val NORMALIZE_EPSILON = 1e-7
    private const val POWER_FLOOR = 1e-10
    private const val LOG_RANGE = 8.0
    private const val LOG_OFFSET = 4.0
    private val logStep = ln(LOG_BASE) / LOG_DIVISOR
    private val window = DoubleArray(FFT) { (1 - cos(2 * PI * it / FFT)) / 2 }
    private val cosine = DoubleArray(FFT) { cos(2 * PI * it / FFT) }
    private val sine = DoubleArray(FFT) { -sin(2 * PI * it / FFT) }
    private val filters = filters()

    /** Last eight seconds, zero-padded at the beginning, normalized before centered periodic-Hann STFT. */
    fun extract(samples: FloatArray): FloatArray {
        require(samples.all { it.isFinite() })
        val waveform = DoubleArray(SAMPLES)
        val count = min(samples.size, SAMPLES)
        for (i in 0 until count) waveform[SAMPLES - count + i] = samples[samples.size - count + i].toDouble()
        val mean = waveform.average()
        val variance = waveform.sumOf { (it - mean) * (it - mean) } / SAMPLES
        val deviation = sqrt(variance + NORMALIZE_EPSILON)
        for (i in waveform.indices) waveform[i] = (waveform[i] - mean) / deviation
        val input = DoubleArray(FFT)
        val fourier = Fourier(input)
        val output = DoubleArray(BANDS * FRAMES)
        for (frame in 0 until FRAMES) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Turn feature extraction cancelled")
            for (n in input.indices) {
                val position = frame * HOP + n - FFT / 2
                val reflected = when {
                    position < 0 -> -position
                    position >= SAMPLES -> 2 * SAMPLES - 2 - position
                    else -> position
                }
                input[n] = waveform[reflected] * window[n]
            }
            val power = fourier.power()
            for (band in filters.indices) {
                var energy = 0.0
                for ((bin, weight) in filters[band]) energy += power[bin] * weight
                output[band * FRAMES + frame] = log10(max(POWER_FLOOR, energy))
            }
        }
        val floor = output.max() - LOG_RANGE
        return FloatArray(output.size) { ((max(output[it], floor) + LOG_OFFSET) / LOG_OFFSET).toFloat() }
    }

    private fun filters(): List<List<Pair<Int, Double>>> {
        val maxMel = LINEAR_MEL + ln((RATE / 2) / LINEAR_HZ) / logStep
        val points = DoubleArray(BANDS + 2) { index ->
            val mel = maxMel * index / (BANDS + 1)
            if (mel < LINEAR_MEL) MEL_STEP * mel else LINEAR_HZ * exp(logStep * (mel - LINEAR_MEL))
        }
        return List(BANDS) { band ->
            (0..FFT / 2).mapNotNull { bin ->
                val hz = bin.toDouble() * RATE / FFT
                val rising = (hz - points[band]) / (points[band + 1] - points[band])
                val falling = (points[band + 2] - hz) / (points[band + 2] - points[band + 1])
                val weight = max(0.0, min(rising, falling)) * 2 / (points[band + 2] - points[band])
                if (weight > 0) bin to weight else null
            }
        }
    }

    /** Mixed radix 4/5 FFT: 400 points exactly, without changing the upstream frequency grid. */
    private class Fourier(private val input: DoubleArray) {
        private val real = DoubleArray(FFT)
        private val imaginary = DoubleArray(FFT)
        private val scratchReal = DoubleArray(FFT)
        private val scratchImaginary = DoubleArray(FFT)
        private val power = DoubleArray(FFT / 2 + 1)

        fun power(): DoubleArray {
            transform(0, 1, FFT, 0)
            for (bin in power.indices) power[bin] = real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
            return power
        }

        private fun transform(offset: Int, stride: Int, size: Int, out: Int) {
            if (size == 1) {
                real[out] = input[offset]
                imaginary[out] = 0.0
                return
            }
            val radix = if (size % RADIX_FOUR == 0) RADIX_FOUR else RADIX_FIVE
            val child = size / radix
            for (r in 0 until radix) transform(offset + r * stride, stride * radix, child, out + r * child)
            for (k in 0 until size) {
                var sumReal = 0.0
                var sumImaginary = 0.0
                for (r in 0 until radix) {
                    val index = out + r * child + k % child
                    val angle = (r * k * (FFT / size)) % FFT
                    sumReal += real[index] * cosine[angle] - imaginary[index] * sine[angle]
                    sumImaginary += real[index] * sine[angle] + imaginary[index] * cosine[angle]
                }
                scratchReal[out + k] = sumReal
                scratchImaginary[out + k] = sumImaginary
            }
            scratchReal.copyInto(real, out, out, out + size)
            scratchImaginary.copyInto(imaginary, out, out, out + size)
        }
    }
}
