package app.kawkab.android.domain.fastpath

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Scores the frozen command set. The gate is 90 percent per core type on the test split.
 * A parser that always answers with one call fails that gate.
 */
class CommandFastPathTest {
    @Test
    fun `the frozen test split clears 90 percent per core type`() {
        val rows = load().filter { it.split == "test" }
        val misses = mutableListOf<String>()
        rows.groupBy { it.type }.forEach { (type, items) ->
            val hits = items.count { row -> matches(CommandFastPath.parse(row.text), row) }
            if (hits * PERCENT < items.size * GATE_PERCENT) {
                misses += "$type $hits/${items.size}"
            }
            val missed = items.filterNot { row -> matches(CommandFastPath.parse(row.text), row) }
            missed.forEach { row -> misses += "${row.id} ${row.text}" }
            assertEquals("$type matched $hits of ${items.size}", items.size, hits)
        }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

    @Test
    fun `a constant call parser fails the per-type gate`() {
        val constant = PlannedCommand(tool = "call_phone", args = mapOf("contact" to "x"))
        val rows = load().filter { it.split == "test" }
        rows.groupBy { it.type }.forEach { (type, items) ->
            val hits = items.count { row -> matches(constant, row) }
            assertTrue(
                "$type constant parser scored $hits/${items.size}",
                hits * PERCENT < items.size * GATE_PERCENT,
            )
        }
    }

    private fun matches(planned: PlannedCommand?, row: Row): Boolean =
        planned != null && planned.tool == row.tool && planned.args == row.args

    private fun load(): List<Row> {
        val file = File("../evals/commands/v1/commands.jsonl")
        return file.readLines().filter { it.isNotBlank() }.map { line ->
            val json = JSONObject(line)
            Row(
                id = json.getString("id"),
                split = json.getString("split"),
                type = json.getString("type"),
                text = json.getString("text"),
                tool = json.getString("tool"),
                args = stringArgs(json.getJSONObject("args")),
            )
        }
    }

    private fun stringArgs(json: JSONObject): Map<String, String> {
        val args = linkedMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            args[key] = json.getString(key)
        }
        return args
    }

    private data class Row(
        val id: String,
        val split: String,
        val type: String,
        val text: String,
        val tool: String,
        val args: Map<String, String>,
    )

    private companion object {
        const val PERCENT = 100
        const val GATE_PERCENT = 90
    }
}
