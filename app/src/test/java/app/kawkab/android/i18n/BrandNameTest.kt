package app.kawkab.android.i18n

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fails when a user-visible string or a preset pipeline name says Knotwork.
 * Resource ids and the About acknowledgment are not on this list.
 */
class BrandNameTest {
    @Test
    fun `given string resources and preset names when read then none say Knotwork`() {
        val hits = mutableListOf<String>()
        stringRoots().forEach { root ->
            if (!root.isDirectory) return@forEach
            root.walkTopDown().filter { file ->
                val parent = file.parentFile?.name ?: return@filter false
                file.isFile &&
                    parent.startsWith("values") &&
                    file.name.startsWith("strings") &&
                    file.extension == "xml"
            }.forEach { file ->
                bodies(file.readText()).forEach { body ->
                    if (body.contains("knotwork", ignoreCase = true)) {
                        hits += "${file.path}: ${body.trim()}"
                    }
                }
            }
        }
        presetDir().listFiles { file -> file.extension == "json" }?.sortedBy { it.name }?.forEach { file ->
            val name = JSONObject(file.readText()).optString("name")
            if (name.contains("knotwork", ignoreCase = true)) hits += "${file.path}: $name"
        }
        assertTrue(hits.joinToString("\n"), hits.isEmpty())
    }

    private fun stringRoots(): List<File> = listOf(
        File("src/main/res"),
        File("../catalog/src/main/res"),
        File("../tools-probe/src/main/res"),
        File("../testapps/stubmessenger/src/main/res"),
    )

    private fun presetDir(): File = File("src/main/assets/presets/pipelines")

    private fun bodies(xml: String): List<String> {
        val stripped = COMMENT.replace(xml, "")
        return BODY.findAll(stripped).map { it.groupValues[1] }.toList()
    }

    private companion object {
        val COMMENT: Regex = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
        val BODY: Regex = Regex("""<(?:string|item)\b[^>]*>(.*?)</(?:string|item)>""", RegexOption.DOT_MATCHES_ALL)
    }
}
