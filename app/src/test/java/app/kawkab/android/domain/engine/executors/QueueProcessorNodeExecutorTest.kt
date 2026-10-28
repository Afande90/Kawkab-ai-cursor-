package app.kawkab.android.domain.engine.executors

import app.kawkab.android.domain.models.NodeExecutionResult
import app.kawkab.android.domain.models.NodeModel
import app.kawkab.android.domain.models.NodeType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueProcessorNodeExecutorTest {
    @Test
    fun `execute returns input text`() = runTest {
        val executor = QueueProcessorNodeExecutor()
        val node = NodeModel("1", NodeType.QUEUE_PROCESSOR, 0f, 0f)

        val results = executor.execute(node, "queue input", "session-1", "prompt").toList().unwrap()

        assertEquals(1, results.size)
        val executionResult = results[0] as NodeExecutionResult
        assertEquals("queue input", executionResult.outputText)
    }
}
