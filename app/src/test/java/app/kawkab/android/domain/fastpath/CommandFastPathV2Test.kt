package app.kawkab.android.domain.fastpath

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Scores the frozen v2 command set. The v1 gate of 90 percent does not apply here.
 */
class CommandFastPathV2Test {
    @Test
    fun `the frozen v2 set is scored without raising the v1 gate`() {
        val rows = load()
        assertEquals(150, rows.size)
        val report = buildString {
            appendLine(scoreLine("all", rows))
            appendLine(scoreLine("test", rows.filter { it.split == "test" }))
            append(scoreLine("calibration", rows.filter { it.split == "calibration" }))
        }
        assertEquals(report, HONEST_SCORE, report)
    }

    private fun scoreLine(label: String, rows: List<Row>): String {
        val byType = rows.groupBy { it.type }
        val parts = listOf("call", "sms", "alarm", "timer", "open", "calendar").map { type ->
            val items = byType[type].orEmpty()
            val hits = items.count { row -> matches(CommandFastPath.parse(row.text), row) }
            "$type $hits/${items.size}"
        }
        val hits = rows.count { row -> matches(CommandFastPath.parse(row.text), row) }
        return "$label $hits/${rows.size} " + parts.joinToString(" ")
    }

    private fun matches(planned: PlannedCommand?, row: Row): Boolean =
        planned != null && planned.tool == row.tool && planned.args == row.args

    private fun load(): List<Row> {
        val file = File("../evals/commands/v2/commands.jsonl")
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
        const val HONEST_SCORE: String =
            "all 25/150 call 5/25 sms 4/25 alarm 3/25 timer 3/25 open 5/25 calendar 5/25\n" +
                "test 10/78 call 3/13 sms 1/13 alarm 0/13 timer 2/13 open 2/13 calendar 2/13\n" +
                "calibration 15/72 call 2/12 sms 3/12 alarm 3/12 timer 1/12 open 3/12 calendar 3/12"
    }
}
