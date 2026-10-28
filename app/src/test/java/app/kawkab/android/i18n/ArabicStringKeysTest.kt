package app.kawkab.android.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fails when a default `strings.xml` key has no Arabic resource (spec §0.6).
 * Gradle runs this test with the working directory set to the `app` module.
 */
class ArabicStringKeysTest {
    @Test
    fun `given default string resources when arabic is required then every key exists`() {
        val roots = listOf(File("src/main/res"), File("../catalog/src/main/res"))
        val missing = mutableListOf<String>()
        roots.forEach { root ->
            val values = File(root, "values")
            val arabic = File(root, "values-ar")
            val files = values.listFiles { file ->
                file.isFile && file.name.startsWith("strings") && file.name.endsWith(".xml")
            }?.sortedBy { it.name }.orEmpty()
            assertTrue("No string files under ${values.path}", files.isNotEmpty())
            files.forEach { english ->
                val translated = File(arabic, english.name)
                if (!translated.isFile) {
                    missing += "${english.path} (no ${translated.path})"
                    return@forEach
                }
                val expected = resourceNames(english)
                val actual = resourceNames(translated)
                expected.forEach { name ->
                    if (name !in actual) missing += "${english.path}#$name"
                }
            }
        }
        assertTrue(
            "Arabic translations missing:\n" + missing.joinToString("\n"),
            missing.isEmpty(),
        )
    }

    private fun resourceNames(file: File): Set<String> {
        val names = linkedSetOf<String>()
        val pattern = Regex("""<(?:string|plurals)\b[^>]*\bname="([^"]+)"""")
        pattern.findAll(file.readText()).forEach { match -> names += match.groupValues[1] }
        return names
    }
}
