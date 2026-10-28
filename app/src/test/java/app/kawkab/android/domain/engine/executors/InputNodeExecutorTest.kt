package app.kawkab.android.domain.engine.executors

import app.kawkab.android.domain.models.NodeExecutionResult
import app.kawkab.android.domain.models.NodeModel
import app.kawkab.android.domain.models.NodeType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InputNodeExecutorTest {
    @Test
    fun `execute returns input text`() = runTest {
        val executor = InputNodeExecutor()
        val node = NodeModel("1", NodeType.INPUT, 0f, 0f)

        val results = executor.execute(node, "test input", "session-1", "prompt").toList().unwrap()

        assertEquals(1, results.size)
        val executionResult = results[0] as NodeExecutionResult
        assertEquals("test input", executionResult.outputText)
    }
}
