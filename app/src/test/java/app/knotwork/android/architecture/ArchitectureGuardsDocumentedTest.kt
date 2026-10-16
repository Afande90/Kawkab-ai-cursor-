package app.knotwork.android.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the Konsist table of `docs/static-analysis.md` to the suite it describes.
 *
 * **Why.** The table is where a reader learns what each architecture guard enforces,
 * and it is prose nobody regenerates. Seventeen guards were once missing from it at
 * the same time — added over two stretches of work, each one found by hand when
 * somebody went looking.
 *
 * **What counts.** A guard is a file of this package with at least one `@Test`; the
 * helpers the guards share (`ProductionSources`, `ArchitectureScope`, …) have none. It
 * is documented when the guide names it in backticks as the first cell of a row of
 * the table, or in a `##` heading — a guard with a section of its own. The other
 * direction holds too: every row names a test class under `src/test`, so a guard
 * renamed or deleted cannot leave its row behind. The guide is a declared input of
 * the test task in `app/build.gradle.kts`.
 *
 * **What it cannot see.** Whether a row describes its test truly. That stays with
 * review: a row is written from the test's assertions and the call sites they pin,
 * not from its KDoc.
 */
class ArchitectureGuardsDocumentedTest {

    @Test
    fun `every guard of the suite is named in the static-analysis guide`() {
        val documented = tableRows().toSet() + sectionHeadingNames()

        assertEquals(
            "these architecture guards are missing from docs/static-analysis.md — add a row to the Konsist " +
                "table saying what the test enforces, written from its assertions",
            emptyList<String>(),
            suiteGuards().filterNot { it in documented },
        )
    }

    @Test
    fun `every row of the table names a test class that exists`() {
        val testClasses = File(ProductionSources.moduleDirectory(), "src/test").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.nameWithoutExtension }
            .toSet()

        assertEquals(
            "rows of the Konsist table in docs/static-analysis.md name no test class — rename or remove them",
            emptyList<String>(),
            tableRows().filterNot { it in testClasses },
        )
    }

    @Test
    fun `the census reads the suite and the guide it claims to`() {
        // Keeps the rules above from passing vacuously if a path or a pattern goes stale.
        val guards = suiteGuards()
        assertTrue("guards found: $guards", "LayerDependencyKonsistTest" in guards && this::class.simpleName in guards)
        assertTrue("a helper without @Test counted as a guard: $guards", "ProductionSources" !in guards)
        assertTrue("table rows found: ${tableRows()}", "LayerDependencyKonsistTest" in tableRows())
        assertTrue("section headings found: ${sectionHeadingNames()}", "TopBarInsetGuardTest" in sectionHeadingNames())
    }

    /** Names of the files of this package that declare at least one test, sorted. */
    private fun suiteGuards(): List<String> {
        val directory = File(
            ProductionSources.moduleDirectory(),
            "src/test/java/" + ArchitectureGuardsDocumentedTest::class.java.packageName.replace('.', '/'),
        )
        check(directory.isDirectory) { "suite not found at $directory" }
        return directory.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "kt" && TEST_ANNOTATION.containsMatchIn(it.readText()) }
            .map { it.nameWithoutExtension }
            .sorted()
    }

    /** The class named in the first cell of each row of the Konsist table, in table order. */
    private fun tableRows(): List<String> {
        val lines = guide().lines()
        val header = lines.indexOfFirst { it.startsWith(TABLE_HEADER) }
        check(header >= 0) { "the Konsist table header '$TABLE_HEADER' is not in the guide" }
        return lines.drop(header + 1)
            .takeWhile { it.startsWith("|") }
            .mapNotNull { ROW_NAME.find(it)?.groupValues?.get(1) }
    }

    /** Every identifier the guide names in backticks inside a `##` heading. */
    private fun sectionHeadingNames(): Set<String> = guide().lines()
        .filter { it.startsWith("## ") }
        .flatMap { heading -> BACKTICKED_NAME.findAll(heading).map { it.groupValues[1] }.toList() }
        .toSet()

    private fun guide(): String = File(ProductionSources.moduleDirectory().absoluteFile.parentFile, GUIDE).readText()

    private companion object {
        const val GUIDE = "docs/static-analysis.md"

        const val TABLE_HEADER = "| Rule (test class)"

        val TEST_ANNOTATION = Regex("""^\s*@Test\b""", RegexOption.MULTILINE)

        /** The backticked class name opening a table row: `| \`Name\` …`. */
        val ROW_NAME = Regex("""^\|\s*`(\w+)`""")

        val BACKTICKED_NAME = Regex("""`(\w+)`""")
    }
}
