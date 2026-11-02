package app.kawkab.android.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Reset rule 5b: locale overrides must preserve the English interface, including plural forms. */
class ArabicStringKeysTest {
    @Test
    fun `every locale uses English interface resources`() {
        listOf(File("src/main/res"), File("../catalog/src/main/res")).forEach { root ->
            val defaults = File(root, "values").listFiles().orEmpty()
                .filter { it.name.startsWith("strings") && it.extension == "xml" }
                .flatMap { entries(it).entries }.associate { it.toPair() }
            assertTrue(defaults.isNotEmpty())
            root.listFiles().orEmpty().filter { it.name.startsWith("values-") }.forEach { locale ->
                locale.listFiles().orEmpty().filter { it.name.startsWith("strings") && it.extension == "xml" }
                    .forEach { file ->
                        entries(file).forEach { (key, text) ->
                            val fallback = key.substringBeforeLast('/') + "/other"
                            assertEquals("${file.path}#$key", defaults[key] ?: defaults[fallback], text)
                        }
                    }
            }
        }
    }

    private fun entries(file: File): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val children = factory.newDocumentBuilder().parse(file).documentElement.childNodes
        return buildMap {
            for (i in 0 until children.length) {
                val node = children.item(i) as? Element ?: continue
                val name = node.getAttribute("name")
                if (node.tagName == "string") put(name, node.textContent.trim())
                if (node.tagName == "plurals") {
                    val items = node.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val item = items.item(j) as Element
                        put("$name/${item.getAttribute("quantity")}", item.textContent.trim())
                    }
                }
            }
        }
    }
}
