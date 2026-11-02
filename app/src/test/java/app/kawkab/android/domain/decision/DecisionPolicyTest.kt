package app.kawkab.android.domain.decision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

/** The owner explicitly selected the 0.85 confidence boundary, without a certificate gate. */
class DecisionPolicyTest {
    @Test fun confidenceAtOrAboveTheOwnerBoundaryActs() {
        for (confidence in listOf(0.85, 0.8501, 0.99)) {
            val result = DecisionPolicy.choose(listOf("act", "ask"), doubleArrayOf(ln(confidence), ln(1 - confidence)))
            assertEquals("act", (result as DecisionResult.Chosen).option)
            assertEquals(confidence, result.probability, 0.0000001)
        }
    }

    @Test fun belowBoundaryAndUncertaintyAsk() {
        for (confidence in listOf(0.5, 0.75, 0.8499)) {
            assertTrue(
                DecisionPolicy.choose(listOf("act", "ask"), doubleArrayOf(ln(confidence), ln(1 - confidence))) is DecisionResult.Ask,
            )
        }
    }

    @Test fun malformedOrNonfiniteVerdictsCannotSelectAnAction() {
        for (logits in listOf(doubleArrayOf(Double.NaN, 0.0), doubleArrayOf(Double.POSITIVE_INFINITY, 0.0), doubleArrayOf(1.0))) {
            assertTrue(DecisionPolicy.choose(listOf("act", "ask"), logits) is DecisionResult.Ask)
        }
        assertTrue(DecisionPolicy.choose(listOf("same", "same"), doubleArrayOf(10.0, 0.0)) is DecisionResult.Ask)
    }

    @Test fun largeLogitsStayFiniteAndCannotInventAnOption() {
        val result = DecisionPolicy.choose(listOf("first", "second"), doubleArrayOf(10000.0, 9990.0)) as DecisionResult.Chosen
        assertEquals("first", result.option)
        assertTrue(result.probability.isFinite())
    }
}
