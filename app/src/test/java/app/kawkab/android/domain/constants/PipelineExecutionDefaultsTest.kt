package app.kawkab.android.domain.constants

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the engine-side log constant exposed by [PipelineExecutionDefaults]. It
 * sets logcat verbosity, so a silent regression here would be hard to spot in
 * code review.
 */
class PipelineExecutionDefaultsTest {

    @Test
    fun `given node io log char limit when read then matches documented value`() {
        assertEquals(1_000, PipelineExecutionDefaults.NODE_IO_LOG_CHAR_LIMIT)
    }
}
