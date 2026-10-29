package app.kawkab.android.domain.safety

import org.json.JSONObject
import java.io.File

/**
 * One on-device record of something Kawkab did or refused.
 *
 * @property asked What the owner said, when there was an utterance.
 * @property ran The tool or step that ran.
 * @property entryPoint tool, skill, macro, routine, or tap.
 * @property result What happened, including a block.
 * @property confirmation tap, none, or the denylist layer that refused it.
 * @property atEpochMs When it was written, unix millis.
 */
data class ActivityLogEntry(
    val asked: String,
    val ran: String,
    val entryPoint: String,
    val result: String,
    val confirmation: String,
    val atEpochMs: Long,
)

/**
 * The on-device activity log. Entries can be exported as text. Nothing here uploads them.
 */
interface ActivityLog {
    /** Appends one entry. */
    fun record(entry: ActivityLogEntry)

    /** Every entry, oldest first. */
    fun entries(): List<ActivityLogEntry>

    /** The export the owner can share themselves. The app does not send it. */
    fun export(): String
}

/**
 * A JSON-lines file under the app's private files directory.
 */
class FileActivityLog(private val file: File) : ActivityLog {
    /** The file name under the app files directory. */
    companion object {
        /** Private JSON-lines log. Not a directory. */
        const val FILE_NAME: String = "activity-log.jsonl"
    }

    override fun record(entry: ActivityLogEntry) {
        file.parentFile?.mkdirs()
        file.appendText(encode(entry) + "\n")
    }

    override fun entries(): List<ActivityLogEntry> {
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.isNotBlank() }.map { decode(it) }
    }

    override fun export(): String = if (file.exists()) file.readText() else ""

    private fun encode(entry: ActivityLogEntry): String = JSONObject()
        .put("asked", entry.asked)
        .put("ran", entry.ran)
        .put("entryPoint", entry.entryPoint)
        .put("result", entry.result)
        .put("confirmation", entry.confirmation)
        .put("atEpochMs", entry.atEpochMs)
        .toString()

    private fun decode(line: String): ActivityLogEntry {
        val json = JSONObject(line)
        return ActivityLogEntry(
            asked = json.optString("asked"),
            ran = json.optString("ran"),
            entryPoint = json.optString("entryPoint"),
            result = json.optString("result"),
            confirmation = json.optString("confirmation"),
            atEpochMs = json.optLong("atEpochMs"),
        )
    }
}
