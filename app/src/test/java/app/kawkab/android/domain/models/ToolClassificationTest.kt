package app.kawkab.android.domain.models

import app.kawkab.android.domain.services.HttpRequestPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every registered local tool is in `safety/tool_classification.csv`, and every
 * outbound-capable tool is [ToolRisk.OUTBOUND]. The table is the committed
 * classification; [RegisteredTools] is what the gate reads.
 */
class ToolClassificationTest {

    @Test
    fun `every registered executor is classified and outbound tools are OUTBOUND`() {
        val csv = parseCsv(File("../safety/tool_classification.csv"))
        val registered = registeredExecutorNames()
        val descriptions = executorDescriptions()

        assertEquals(registered, csv.map { it.name }.toSet())
        assertEquals(RegisteredTools.all.map { it.name }.toSet(), registered)

        val outboundByName = Regex("(?i)(?:^|_)(send|sms|call|pay|share|post|submit|transfer|delete)(?:_|$)")
        registered.forEach { name ->
            val row = csv.first { it.name == name }
            val code = RegisteredTools.all.first { it.name == name }
            assertEquals(name, code.risk, row.risk)
            assertEquals(name, code.outboundCapable, row.outboundCapable)
            val capable = outboundByName.containsMatchIn(name) ||
                descriptions.getValue(name).contains("POST")
            assertEquals("$name outbound flag", capable, row.outboundCapable)
            if (row.outboundCapable) {
                assertEquals("$name must be OUTBOUND", ToolRisk.OUTBOUND, row.risk)
            }
        }
    }

    @Test
    fun `no approval policy and no quieter override can quiet OUTBOUND`() {
        ToolApprovalPolicy.entries.forEach { policy ->
            assertTrue("$policy quieted OUTBOUND", policy.requiresApproval(ToolRisk.OUTBOUND))
        }
        assertEquals(ToolRisk.OUTBOUND, ToolRisk.OUTBOUND.notQuietedBy(ToolRisk.READ_ONLY))
        assertEquals(ToolRisk.OUTBOUND, ToolRisk.OUTBOUND.notQuietedBy(null))
        assertEquals(ToolRisk.READ_ONLY, ToolRisk.SENSITIVE.notQuietedBy(ToolRisk.READ_ONLY))
    }

    @Test
    fun `http writes are outbound and a get stays sensitive`() {
        assertEquals(ToolRisk.SENSITIVE, HttpRequestPolicy.methodRisk("GET"))
        assertEquals(ToolRisk.OUTBOUND, HttpRequestPolicy.methodRisk("POST"))
        assertEquals(ToolRisk.OUTBOUND, HttpRequestPolicy.methodRisk("PUT"))
        assertEquals(ToolRisk.OUTBOUND, HttpRequestPolicy.methodRisk("DELETE"))
    }

    private fun parseCsv(file: File): List<RegisteredTool> {
        val lines = file.readLines().filter { it.isNotBlank() }
        assertEquals("tool_name,risk,outbound_capable", lines.first())
        return lines.drop(1).map { line ->
            val (name, risk, capable) = line.split(",")
            RegisteredTool(name, ToolRisk.valueOf(risk), capable.toBoolean())
        }
    }

    private fun registeredExecutorNames(): Set<String> {
        val module = File("src/main/java/app/kawkab/android/di/LocalToolsModule.kt").readText()
        val refs = Regex("""@StringKey\((\w+)\.TOOL_NAME\)""").findAll(module).map { it.groupValues[1] }.toList()
        assertTrue(refs.isNotEmpty())
        val names = refs.map { className ->
            val source = findMainSource(className).readText()
            Regex("""const val TOOL_NAME = "([^"]+)"""").find(source)?.groupValues?.get(1)
                ?: error("$className has no TOOL_NAME")
        }.toSet()
        File("src/main/java").walk()
            .filter { it.isFile && it.extension == "kt" && it.readText().contains(": LocalToolExecutor") }
            .forEach { file ->
                assertTrue(
                    "${file.name} implements LocalToolExecutor but is not registered",
                    module.contains(file.nameWithoutExtension),
                )
            }
        return names
    }

    private fun executorDescriptions(): Map<String, String> {
        val descriptions = mutableMapOf<String, String>()
        File("src/main/java").walk().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            val name = Regex("""const val TOOL_NAME = "([^"]+)"""").find(text)?.groupValues?.get(1)
                ?: return@forEach
            descriptions[name] = text
        }
        return descriptions
    }

    private fun findMainSource(className: String): File {
        val matches = File("src/main/java").walk().filter { it.name == "$className.kt" }.toList()
        return matches.singleOrNull() ?: error("expected one source for $className, found $matches")
    }
}
