package app.kawkab.android.domain.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Load errors, invalid scores and late native work cannot override the 700 ms fallback. */
class SmartTurnBudgetTest {
    @Test fun loadFailureFallsBackAt700MillisecondsWithoutRequiringAModel() {
        val failed = object : TurnCompletionModel {
            override fun probability(samples: FloatArray): Float = error("model failed to load")
        }
        SmartTurnBudget(failed).use { detector ->
            val result = detector.evaluate(floatArrayOf(0f))
            assertSame(TurnCompletion.Fallback, result)
            assertFalse(SmartTurnBudget.ends(result, 699))
            assertTrue(SmartTurnBudget.ends(result, 700))
        }
    }

    @Test fun anOverBudgetCompletedResultUsesSilenceInstead() {
        var now = 0L
        val late = object : TurnCompletionModel {
            override fun probability(samples: FloatArray): Float {
                now = TimeUnit.MILLISECONDS.toNanos(151)
                return 0.99f
            }
        }
        SmartTurnBudget(late) { now }.use { detector ->
            assertSame(TurnCompletion.Fallback, detector.evaluate(floatArrayOf(0f)))
        }
    }

    @Test fun aBlockedModelIsCancelledWithoutWaitingForItsEventualAnswer() {
        val release = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        val blocked = object : TurnCompletionModel {
            override fun probability(samples: FloatArray): Float {
                release.await()
                return 0.99f
            }
            override fun cancel() {
                cancelled.set(true)
                release.countDown()
            }
        }
        SmartTurnBudget(blocked).use { detector ->
            assertSame(TurnCompletion.Fallback, detector.evaluate(floatArrayOf(0f)))
            assertTrue(cancelled.get())
        }
    }

    @Test fun incompleteDoesNotEndTheTurnAtTheOldSilenceBoundary() {
        val model = object : TurnCompletionModel {
            override fun probability(samples: FloatArray): Float = 0.4f
        }
        SmartTurnBudget(model).use { detector ->
            val result = detector.evaluate(floatArrayOf(0f))
            assertSame(TurnCompletion.Incomplete, result)
            assertFalse(SmartTurnBudget.ends(result, 1000))
        }
    }

    @Test fun completeUsesTheShortSemanticPause() {
        val model = object : TurnCompletionModel {
            override fun probability(samples: FloatArray): Float = 0.9f
        }
        SmartTurnBudget(model).use { detector ->
            val result = detector.evaluate(floatArrayOf(0f))
            assertSame(TurnCompletion.Complete, result)
            assertFalse(SmartTurnBudget.ends(result, 199))
            assertTrue(SmartTurnBudget.ends(result, 200))
        }
    }

    @Test fun malformedScoresUseFallback() {
        val model = object : TurnCompletionModel {
            override fun probability(samples: FloatArray): Float = Float.NaN
        }
        SmartTurnBudget(model).use { detector ->
            assertSame(TurnCompletion.Fallback, detector.evaluate(floatArrayOf(0f)))
        }
    }
}
