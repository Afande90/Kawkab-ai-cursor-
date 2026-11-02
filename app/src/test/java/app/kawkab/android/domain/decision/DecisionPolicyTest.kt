package app.kawkab.android.domain.decision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A confident number alone is never a certificate of action precision. */
class DecisionPolicyTest {
    @Test fun `uncertified and failed final tests always ask`() {
        assertTrue(DecisionPolicy.choose(listOf("act", "ask"), doubleArrayOf(20.0, 0.0), null) is DecisionResult.Ask)
        val failed = DecisionCalibration("model", 1.0, 0.9, 0.949, 100)
        assertTrue(DecisionPolicy.choose(listOf("act", "ask"), doubleArrayOf(20.0, 0.0), failed) is DecisionResult.Ask)
    }
    @Test fun `finite calibrated confident choices act while ambiguous choices ask`() {
        val calibrated = DecisionCalibration("model", 1.0, 0.95, 0.96, 100)
        assertEquals("act", (DecisionPolicy.choose(listOf("act", "ask"), doubleArrayOf(9.0, 0.0), calibrated)
            as DecisionResult.Chosen).option)
        assertTrue(DecisionPolicy.choose(listOf("act", "ask"), doubleArrayOf(0.0, 0.0), calibrated) is DecisionResult.Ask)
        assertTrue(DecisionPolicy.choose(listOf("act", "ask"), doubleArrayOf(Double.NaN, 0.0), calibrated) is DecisionResult.Ask)
    }
}
