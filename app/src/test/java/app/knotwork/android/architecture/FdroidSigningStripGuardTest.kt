package app.knotwork.android.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the release signing assignment in `app/build.gradle.kts` to a shape F-Droid's
 * build server removes whole.
 *
 * **Why.** Before building, fdroidserver strips signing configuration from every
 * `build.gradle(.kts)` it finds, line by line (`remove_signing_keys` in
 * `fdroidserver/common.py`). F-Droid then verifies its unsigned build against the
 * published `foss` APK and ships that APK's signature. The stripper removed the
 * first line of a two-line
 * `signingConfig = signingConfigs.findByName("release")` /
 * `?: signingConfigs.getByName("debug")` statement and left the second, which
 * glued onto the preceding `proguardFiles(...)` call and compiled only by
 * accident. Written on one line with spaces, the assignment is not removed at all
 * — its rule accepts no space after the `=` — and F-Droid's release build would
 * come out debug-signed instead of unsigned.
 *
 * **How.** [FdroidSigningStripper] ports the stripper's rules (fdroidserver
 * master, measured 2026-09-27) and runs them over the build script. The first two
 * tests hold the real file; the fixtures below them prove the port reproduces the
 * failures it exists to catch.
 */
class FdroidSigningStripGuardTest {

    private val script: List<String> by lazy {
        File(ProductionSources.moduleDirectory(), "build.gradle.kts").readLines()
    }

    @Test
    fun `given the app build script when F-Droid strips signing then no signing assignment survives`() {
        // When
        val surviving = FdroidSigningStripper.strip(script).kept.filter { SIGNING_ASSIGNMENT.containsMatchIn(it) }

        // Then
        assertTrue(
            "the build script has no signing assignment to strip — this guard would pass vacuously",
            script.any { SIGNING_ASSIGNMENT.containsMatchIn(it) },
        )
        assertEquals("a signing assignment F-Droid's stripper leaves in place", emptyList<String>(), surviving)
    }

    @Test
    fun `given the app build script when F-Droid strips signing then nothing dangles after a removed line`() {
        // When
        val dangling = FdroidSigningStripper.strip(script).danglingContinuations()

        // Then
        assertEquals(
            "a line that continued a statement F-Droid's stripper removed — it now attaches to whatever precedes it",
            emptyList<String>(),
            dangling,
        )
    }

    @Test
    fun `given a two-line elvis assignment when stripped then the guard reports the dangling half`() {
        // Given — the shape the script had when 0.11.0 was submitted.
        val twoLines = listOf(
            "            proguardFiles(\"proguard-rules.pro\")",
            "            signingConfig = signingConfigs.findByName(\"release\")",
            "                ?: signingConfigs.getByName(\"debug\")",
        )

        // When
        val dangling = FdroidSigningStripper.strip(twoLines).danglingContinuations()

        // Then
        assertEquals(listOf("?: signingConfigs.getByName(\"debug\")"), dangling)
    }

    @Test
    fun `given a one-line elvis assignment when stripped then the assignment survives`() {
        // Given — spaces after the `=` put the line outside the stripper's rule.
        val oneLine = listOf(
            "signingConfig = signingConfigs.findByName(\"release\") ?: signingConfigs.getByName(\"debug\")",
        )

        // When
        val stripped = FdroidSigningStripper.strip(oneLine)

        // Then
        assertEquals(oneLine, stripped.kept)
    }

    @Test
    fun `given a signingConfigs block and a single-token assignment when stripped then both go and the rest stays`() {
        // Given
        val selection = "val releaseSigningConfig = " +
            "signingConfigs.findByName(\"release\") ?: signingConfigs.getByName(\"debug\")"
        val script = listOf(
            "    signingConfigs {",
            "        create(\"release\") {",
            "            storeFile = file(\"k\")",
            "        }",
            "    }",
            "    // a comment is always kept",
            selection,
            "signingConfig = releaseSigningConfig",
            "isMinifyEnabled = true",
        )

        // When
        val stripped = FdroidSigningStripper.strip(script)

        // Then
        assertEquals(
            listOf(
                "    // a comment is always kept",
                selection,
                "isMinifyEnabled = true",
            ),
            stripped.kept,
        )
        assertEquals(emptyList<String>(), stripped.danglingContinuations())
    }

    private companion object {
        /** A Kotlin DSL assignment of a build type's signing config. */
        val SIGNING_ASSIGNMENT = Regex("""^\s*signingConfig\s*=""")
    }
}

/**
 * A port of fdroidserver's `remove_signing_keys` for one build script: comments
 * are kept, a `signingConfigs {` block is dropped with everything up to its
 * closing brace, and a line matching any of [LINE_RULES] is dropped. Each rule is
 * applied the way Python's `re.match` applies it — anchored at the start of the
 * line, not required to reach its end.
 */
internal object FdroidSigningStripper {

    private val COMMENT = Regex("""[ ]*//""")
    private val SIGNING_CONFIGS_BLOCK = Regex("""^[\t ]*signingConfigs[ \t]*\{[ \t]*$""")
    private val LINE_RULES = listOf(
        Regex("""^[\t ]*signingConfig\s*[= ]\s*[^ ]*$"""),
        Regex(""".*android\.signingConfigs\.[^{]*$"""),
        Regex(""".*release\.signingConfig *= *"""),
    )

    /** Tokens that, opening a line, continue the expression of the line before. */
    private val CONTINUATIONS = listOf("?:", "?.", ".", "&&", "||", "+", "-", "*", "/", "%", "as ", "as?")

    /**
     * The result of a strip: every input line, in order, with whether it was kept.
     *
     * @property lines each input line paired with `true` when the stripper keeps it.
     */
    data class Result(val lines: List<Pair<String, Boolean>>) {

        /** The lines the stripper keeps, in order. */
        val kept: List<String> get() = lines.filter { it.second }.map { it.first }

        /**
         * Kept lines that open with a continuation token directly after a removed
         * line (comments and blank lines between them skipped), trimmed.
         */
        fun danglingContinuations(): List<String> {
            val found = mutableListOf<String>()
            var afterRemoved = false
            for ((line, kept) in lines) {
                val code = line.trim()
                when {
                    !kept -> afterRemoved = true
                    code.isEmpty() || code.startsWith("//") -> Unit
                    else -> {
                        if (afterRemoved && CONTINUATIONS.any { code.startsWith(it) }) found += code
                        afterRemoved = false
                    }
                }
            }
            return found
        }
    }

    /**
     * Strips [script] as fdroidserver would.
     *
     * @param script the build script, one element per line, without line terminators.
     * @return every line of [script] with whether it survives.
     */
    fun strip(script: List<String>): Result {
        var opened = 0
        val lines = script.map { line ->
            val keep = when {
                COMMENT.matchesPrefix(line) -> true
                opened > 0 -> {
                    opened += line.count { it == '{' } - line.count { it == '}' }
                    false
                }
                SIGNING_CONFIGS_BLOCK.matchesPrefix(line) -> {
                    opened += 1
                    false
                }
                LINE_RULES.any { it.matchesPrefix(line) } -> false
                else -> true
            }
            line to keep
        }
        return Result(lines)
    }

    /** Python's `re.match`: a match that starts at the first character. */
    private fun Regex.matchesPrefix(line: String): Boolean = toPattern().matcher(line).lookingAt()
}
