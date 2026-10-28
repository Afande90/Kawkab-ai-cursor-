package app.kawkab.android.domain.engine.golden

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the one harness behaviour no golden trace can show: a run that does not finish fails
 * the scenario instead of hanging the test task.
 */
internal class GoldenTraceHarnessTest {

    @Test
    fun `given a run that outlives the virtual-time limit when run then the harness fails instead of hanging`() {
        // A run waiting for an answer: nobody approves within the 30 s window this scenario
        // lets pass, so a 600 ms limit binds mid-wait, exactly as it would on an answer that
        // never comes. (The engine itself waits nowhere else, so a plain run finishes at 0 ms.)
        val scenario = GoldenScenarios.all.first {
            it.source.fileStem == "tool-with-approval" && it.name == "parked-then-approved"
        }

        val error = assertThrows(IllegalStateException::class.java) {
            runTest { GoldenTraceHarness(scenario, attemptLimitMs = 600).run(this) }
        }

        assertTrue(error.message, error.message.orEmpty().contains("did not finish within 600 ms"))
    }
}
