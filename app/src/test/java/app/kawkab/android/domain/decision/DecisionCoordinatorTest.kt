package app.kawkab.android.domain.decision

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classification and permission to act are separate structured decisions. */
class DecisionCoordinatorTest {
    @Test fun `conversation never asks the model to authorize a phone action`() = runTest {
        var calls = 0
        val coordinator = DecisionCoordinator(TypedJudge { _, _, _ ->
            calls += 1
            DecisionResult.Chosen("chat", 0.99)
        })
        assertEquals("chat", (coordinator.route("tell me a story") as DecisionResult.Chosen).option)
        assertEquals(1, calls)
    }
    @Test fun `a command with low act confidence asks instead of executing`() = runTest {
        var calls = 0
        val coordinator = DecisionCoordinator(TypedJudge { _, _, _ ->
            calls += 1
            if (calls == 1) DecisionResult.Chosen("command", 0.99) else DecisionResult.Ask("uncertain")
        })
        assertTrue(coordinator.route("handle that reminder") is DecisionResult.Ask)
        assertEquals(2, calls)
    }
}
