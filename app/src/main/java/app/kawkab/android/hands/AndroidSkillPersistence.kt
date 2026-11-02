package app.kawkab.android.hands

import android.content.Context
import android.util.AtomicFile
import app.kawkab.android.domain.harness.CachedSkill
import app.kawkab.android.domain.harness.SkillPersistence
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

/** Successful local plans are private, atomic and excluded from device backup. */
class AndroidSkillPersistence @Inject constructor(@ApplicationContext context: Context) : SkillPersistence {
    private val file = AtomicFile(File(context.noBackupFilesDir, "learned-plans.json"))

    @Synchronized override fun load(): List<CachedSkill> = runCatching {
        val rows = JSONArray(file.readFully().toString(Charsets.UTF_8))
        List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val args = row.getJSONObject("args")
            CachedSkill(
                row.getString("key"),
                row.getString("tool"),
                args.keys().asSequence().associateWith { args.getString(it) },
                row.getInt("successes"),
            )
        }
    }.getOrDefault(emptyList())

    @Synchronized override fun save(skills: List<CachedSkill>) {
        val rows = JSONArray(
            skills.map { skill ->
                JSONObject().put("key", skill.key).put("tool", skill.tool)
                    .put("args", JSONObject(skill.args)).put("successes", skill.successes)
            },
        )
        val stream = file.startWrite()
        try {
            stream.write(rows.toString().toByteArray())
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
