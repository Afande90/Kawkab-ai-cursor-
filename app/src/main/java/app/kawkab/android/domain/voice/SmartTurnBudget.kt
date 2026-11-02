package app.kawkab.android.domain.voice

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** The CPU runtime may cancel a native run when its wall-clock budget is exhausted. */
interface TurnCompletionModel {
    /** The model completion probability; preprocessing is included in the deadline. */
    fun probability(samples: FloatArray): Float

    /** Terminate any in-flight native run belonging to this session. */
    fun cancel() = Unit
}

/** A decision that distinguishes a model's incomplete answer from the silence fallback. */
sealed interface TurnCompletion {
    /** The model has identified an endpoint. */
    data object Complete : TurnCompletion

    /** The owner appears to be continuing. */
    data object Incomplete : TurnCompletion

    /** The model failed or exceeded the budget. */
    data object Fallback : TurnCompletion
}

/** Bounds preprocessing plus inference, without ever blocking the recorder or UI thread. */
class SmartTurnBudget(private val model: TurnCompletionModel, private val clock: () -> Long = System::nanoTime) :
    AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor { job -> Thread(job, "turn-cpu").apply { isDaemon = true } }
    private var fallback = false

    /** One failure/slow result degrades the current listening session to the 700 ms silence rule. */
    fun evaluate(samples: FloatArray): TurnCompletion {
        if (fallback) return TurnCompletion.Fallback
        val started = clock()
        val future = runCatching { worker.submit<Float> { model.probability(samples) } }.getOrElse { return degrade() }
        return try {
            val probability = future.get(MAX_MS, TimeUnit.MILLISECONDS)
            if (clock() - started > MAX_MS * NANOS_PER_MS || !probability.isFinite() || probability !in 0f..1f) {
                degrade()
            } else if (probability > COMPLETION_THRESHOLD) {
                TurnCompletion.Complete
            } else {
                TurnCompletion.Incomplete
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            degrade()
        } catch (_: Exception) {
            degrade()
        } finally {
            if (fallback) {
                model.cancel()
                future.cancel(true)
            }
        }
    }

    private fun degrade(): TurnCompletion {
        fallback = true
        return TurnCompletion.Fallback
    }

    override fun close() {
        model.cancel()
        worker.shutdownNow()
    }

    /** Fixed model budget and fallback policy. */
    companion object {
        /** Maximum combined feature/inference latency. */
        const val MAX_MS = 150L

        /** Automatic degraded-mode silence duration. */
        const val SILENCE_MS = 700L

        /** Short VAD pause before asking the semantic model. */
        const val MIN_SILENCE_MS = 200L
        private const val NANOS_PER_MS = 1_000_000L
        private const val COMPLETION_THRESHOLD = 0.5f

        /** A model that says incomplete never ends a turn just because 700 ms passed. */
        fun ends(completion: TurnCompletion, silenceMs: Long): Boolean = when (completion) {
            TurnCompletion.Complete -> silenceMs >= MIN_SILENCE_MS
            TurnCompletion.Incomplete -> false
            TurnCompletion.Fallback -> silenceMs >= SILENCE_MS
        }
    }
}
