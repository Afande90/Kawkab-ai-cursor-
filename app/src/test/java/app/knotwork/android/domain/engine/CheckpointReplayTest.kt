package app.knotwork.android.domain.engine

import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.ResumeContext
import app.knotwork.android.domain.models.RunTraceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit coverage for [CheckpointReplay].
 *
 * A replay that consumed a record for the wrong node, or skipped one, would
 * re-derive a resumed run's branches from someone else's output — so the cursor
 * is pinned here record by record: what advances it, what does not, and what
 * stops the resume.
 */
class CheckpointReplayTest {

    private fun node(id: String, type: NodeType = NodeType.LITE_RT) = NodeModel(id = id, type = type, x = 0f, y = 0f)

    private fun record(nodeId: String, seq: Long, visit: Int? = null, failure: String? = null) = RunTraceRecord.NodeIo(
        runId = "run-1",
        sessionId = "s",
        seq = seq,
        timestamp = 0L,
        nodeId = nodeId,
        nodeType = "LITE_RT",
        inputText = "in $nodeId",
        outputText = "out $nodeId",
        durationMs = 40L + seq,
        tokenCount = 7,
        conditionResult = true,
        routingKey = "billing",
        resolvedToolName = "search_tool",
        visit = visit,
        failure = failure,
    )

    private fun resumed(vararg nodeIds: String) = CheckpointReplay(
        ResumeContext(
            records = nodeIds.mapIndexed { i, id ->
                record(id, i.toLong())
            },
            memorySnapshot = null,
            nextSeq = 9L,
        ),
    )

    @Test
    fun `given a fresh run then every node runs live`() {
        val replay = CheckpointReplay(null)

        assertFalse(replay.resuming)
        assertEquals(ReplayStep.Live, replay.take(node("a"), 0))
    }

    @Test
    fun `given recorded nodes then each is replayed from its record in order, then the walk goes live`() {
        val replay = resumed("a", "b")

        val a = replay.take(node("a"), 0) as ReplayStep.Replayed
        val b = replay.take(node("b"), 0) as ReplayStep.Replayed

        assertTrue(replay.resuming)
        assertEquals("out a", a.result.outputText)
        assertEquals(true, a.result.conditionResult)
        assertEquals("billing", a.result.routingKey)
        assertEquals(7, a.result.tokenCount)
        assertEquals("search_tool", a.result.resolvedToolName)
        assertEquals("in a", a.input)
        assertEquals(40L, a.durationMs)
        assertEquals("out b", b.result.outputText)
        assertEquals(ReplayStep.Live, replay.take(node("c"), 0))
    }

    @Test
    fun `given INPUT or OUTPUT mid-replay then they run live and keep the next record for the next node`() {
        val replay = resumed("a")

        assertEquals(ReplayStep.Live, replay.take(node("in", NodeType.INPUT), 0))
        assertEquals(ReplayStep.Live, replay.take(node("out", NodeType.OUTPUT), 0))
        assertEquals("out a", (replay.take(node("a"), 0) as ReplayStep.Replayed).result.outputText)
    }

    @Test
    fun `given a record for another node then the resume has diverged and nothing is consumed`() {
        val replay = resumed("a")

        assertEquals(ReplayStep.Diverged, replay.take(node("x"), 0))
        assertEquals("out a", (replay.take(node("a"), 0) as ReplayStep.Replayed).result.outputText)
    }

    private fun resumedWith(vararg records: RunTraceRecord.NodeIo) = CheckpointReplay(
        ResumeContext(records = records.toList(), memorySnapshot = null, nextSeq = 9L),
    )

    @Test
    fun `given a queue node's records name their visits when each visit is reached then each gets its own`() {
        val replay = resumedWith(record("q", 0L, visit = 0), record("q", 1L, visit = 1))

        // Each record's duration is 40 ms plus its seq, which tells the two apart.
        assertEquals(40L, (replay.take(node("q"), 0) as ReplayStep.Replayed).durationMs)
        assertEquals(41L, (replay.take(node("q"), 1) as ReplayStep.Replayed).durationMs)
        assertEquals(ReplayStep.Live, replay.take(node("q"), 2))
    }

    @Test
    fun `given the next record is a later visit when an earlier visit is reached then the resume has diverged`() {
        // Visit 1 left no record: on a trace written before failed queue items were
        // recorded, the item that failed. Taking visit 2's record for it handed the
        // failed item the next item's output, and ran the next item again.
        val replay = resumedWith(record("q", 0L, visit = 0), record("q", 1L, visit = 2))

        assertTrue(replay.take(node("q"), 0) is ReplayStep.Replayed)
        assertEquals(ReplayStep.Diverged, replay.take(node("q"), 1))
    }

    @Test
    fun `given a recorded failure when its visit is reached then it replays as that failure`() {
        val failed = record("q", 1L, visit = 1, failure = "Rate limit reached").copy(outputText = "")
        val replay = resumedWith(record("q", 0L, visit = 0), failed)

        replay.take(node("q"), 0)
        val step = replay.take(node("q"), 1) as ReplayStep.Replayed

        assertEquals("Rate limit reached", step.result.error)
        assertEquals("", step.result.outputText)
        assertEquals("in q", step.input)
    }

    @Test
    fun `given a completed record when replayed then it carries no error`() {
        val step = resumedWith(record("q", 0L, visit = 0)).take(node("q"), 0) as ReplayStep.Replayed

        assertEquals(null, step.result.error)
    }

    @Test
    fun `given records written before visits were recorded when replayed then they match by node alone`() {
        val replay = resumedWith(record("q", 0L), record("q", 1L))

        assertTrue(replay.take(node("q"), 0) is ReplayStep.Replayed)
        assertTrue(replay.take(node("q"), 5) is ReplayStep.Replayed)
    }
}
