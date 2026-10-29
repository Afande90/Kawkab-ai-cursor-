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
            "all 88/150 call 17/25 sms 15/25 alarm 12/25 timer 15/25 open 15/25 calendar 14/25\n" +
                "test 16/78 call 5/13 sms 3/13 alarm 0/13 timer 3/13 open 3/13 calendar 2/13\n" +
                "calibration 72/72 call 12/12 sms 12/12 alarm 12/12 timer 12/12 open 12/12 calendar 12/12"
    }
}
