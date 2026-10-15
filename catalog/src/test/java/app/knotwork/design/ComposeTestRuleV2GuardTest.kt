package app.knotwork.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the design system's tests on the v2 Compose test APIs.
 *
 * The v1 factories (`junit4.createComposeRule`, `runComposeUiTest` and the rest) are deprecated.
 * They run effects on an `UnconfinedTestDispatcher`; the v2 factories in the `v2` packages use a
 * `StandardTestDispatcher`, as `runTest` does. Every screenshot test moved to v2 at once, with no
 * baseline changing. This fails the next one written against v1. The app's instrumented tests have
 * the same guard in `:app`. The scanned sources compile with this module, so an edit re-runs it.
 */
class ComposeTestRuleV2GuardTest {

    @Test
    fun `given a catalog test then it builds its Compose rule with the v2 factories`() {
        val files = File(TEST_SOURCES).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(
            "found no v2 Compose rule in $TEST_SOURCES — the scan no longer matches the code",
            files.any { V2_FACTORY.containsMatchIn(it.readText()) },
        )

        val v1 = files.flatMap { file ->
            V1_API.findAll(file.readText()).map { "${file.name}: ${it.value}" }.toList()
        }

        assertEquals("use the v2 factory (the `v2` package) instead", emptyList<String>(), v1)
    }

    private companion object {
        const val TEST_SOURCES = "src/test/java"

        /** A deprecated v1 factory or environment, imported or written out. */
        val V1_API = Regex(
            """androidx\.compose\.ui\.test\.(junit4\.(createComposeRule|createAndroidComposeRule|""" +
                """createEmptyComposeRule|AndroidComposeTestRule)|runComposeUiTest|runAndroidComposeUiTest|""" +
                """runEmptyComposeUiTest|AndroidComposeUiTestEnvironment)\b""",
        )

        /** A v2 factory, for the check that the scan reads what it claims to. */
        val V2_FACTORY = Regex("""androidx\.compose\.ui\.test\.junit4\.v2\.create\w*ComposeRule\b""")
    }
}
