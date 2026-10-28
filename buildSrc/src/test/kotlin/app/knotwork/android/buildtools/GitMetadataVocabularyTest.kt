package app.knotwork.android.buildtools

import app.knotwork.android.buildtools.ForbiddenVocabularyChecker.Family
import app.knotwork.android.buildtools.GitMetadataVocabulary.Commit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Unit coverage for [GitMetadataVocabulary]: the range it scans, how it reads
 * `git log`, and which texts fail — first on fixtures, then against a real
 * repository through the same commands the task runs.
 *
 * The fixtures carry the forbidden forms on purpose; `buildSrc/src/test` is
 * outside the file gate's scope for exactly this reason.
 */
class GitMetadataVocabularyTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `given no explicit range when resolved then everything on HEAD missing from the base remote`() {
        assertEquals("origin/main..HEAD", GitMetadataVocabulary.range(null, "main"))
        assertEquals("origin/phase/45..HEAD", GitMetadataVocabulary.range("  ", "phase/45"))
    }

    @Test
    fun `given an explicit range when resolved then it wins over the base`() {
        assertEquals("v0.10.1..v0.11.0", GitMetadataVocabulary.range("v0.10.1..v0.11.0", "main"))
    }

    @Test
    fun `given log output with multi-line messages when parsed then one commit per record`() {
        val field = Char(0x1F)
        val record = Char(0x1E)
        val output = "aaaa${field}fix: one\n\nBody line.\n\n$record\n" +
            "bbbb${field}Merge pull request #2 from o/b\n\nfeat: two\n$record\n"

        assertEquals(
            listOf(
                Commit("aaaa", "fix: one\n\nBody line."),
                Commit("bbbb", "Merge pull request #2 from o/b\n\nfeat: two"),
            ),
            GitMetadataVocabulary.parseLog(output),
        )
    }

    @Test
    fun `given empty log output when parsed then no commits`() {
        assertTrue(GitMetadataVocabulary.parseLog("").isEmpty())
    }

    @Test
    fun `given a numbered feature branch when scanned then the branch is flagged`() {
        val violations = GitMetadataVocabulary.scan("feature/phase45-3-signing", emptyList(), null, null)

        assertEquals(listOf("branch:1: internal planning number `phase45`"), violations.map { it.format() })
    }

    @Test
    fun `given the integration branch, main or a detached head when scanned then nothing is flagged`() {
        listOf("phase/45", "main", "HEAD", "fix/contributor-conventions").forEach { branch ->
            assertTrue(branch, GitMetadataVocabulary.scan(branch, emptyList(), null, null).isEmpty())
        }
    }

    @Test
    fun `given an integration merge titled the new way when scanned then it is clean`() {
        val merge = Commit(
            hash = "0123456789abcdef",
            message = "Merge pull request #480 from o/phase/45\n\nrelease: 0.12.0 — theme of the batch",
        )

        assertTrue(GitMetadataVocabulary.scan(null, listOf(merge), null, null).isEmpty())
    }

    @Test
    fun `given an integration merge titled the old way when scanned then its body line is flagged`() {
        val merge = Commit(
            hash = "fea18b3400000000",
            message = "Merge pull request #470 from o/phase/44\n\nphase(44): close the findings; release 0.11.0",
        )

        assertEquals(
            listOf("commit fea18b34:3: internal planning number `phase(44`"),
            GitMetadataVocabulary.scan(null, listOf(merge), null, null).map { it.format() },
        )
    }

    @Test
    fun `given two commits sharing a hash prefix when scanned then both messages are reported`() {
        val commits = listOf(
            Commit("0123456700000000", "feat(phase45-1): first"),
            Commit("0123456711111111", "feat(phase45-2): second"),
        )

        assertEquals(
            listOf("commit 01234567", "commit 0123456711111111"),
            GitMetadataVocabulary.scan(null, commits, null, null).map { it.file },
        )
    }

    @Test
    fun `given a title and body when scanned then each is reported under its own label`() {
        val violations = GitMetadataVocabulary.scan(
            branch = null,
            commits = emptyList(),
            title = "feat(phase45-2): a thing",
            body = "Intro.\n## Phase 45: the batch\n",
        )

        assertEquals(
            listOf("body:2: internal planning number `Phase 45`", "title:1: internal planning number `phase45`"),
            violations.map { it.format() },
        )
    }

    @Test
    fun `given the retired product name in a commit when scanned then it is flagged too`() {
        val commit = Commit("abcdef0123456789", "docs: rename\n\nwas Android AI Agent")

        assertEquals(
            listOf(Family.RETIRED_PRODUCT_NAME),
            GitMetadataVocabulary.scan(null, listOf(commit), null, null).map { it.family },
        )
    }

    @Test
    fun `given a real repository when the task's commands run then branch, commits and merges are read and scanned`() {
        val repository = temporaryFolder.newFolder("repository")
        git(repository, "init", "-q", "-b", "main")
        git(repository, "commit", "-q", "--allow-empty", "-m", "chore: base")
        git(repository, "update-ref", "refs/remotes/origin/main", "HEAD")
        git(repository, "checkout", "-q", "-b", "feature/phase45-2-x")
        git(repository, "commit", "-q", "--allow-empty", "-m", "feat(phase45-2): add\n\nA body line.\nTask 2/9 closes.")
        git(repository, "checkout", "-q", "-b", "fix/clean")
        git(repository, "commit", "-q", "--allow-empty", "-m", "fix(memory): clean one")
        git(repository, "checkout", "-q", "feature/phase45-2-x")
        git(repository, "merge", "-q", "--no-ff", "-m", "Merge branch 'fix/clean' into phase/45", "fix/clean")

        val branch = run(GitMetadataVocabulary.BRANCH_COMMAND, repository).trim()
        val commits = GitMetadataVocabulary.parseLog(
            run(GitMetadataVocabulary.logCommand(GitMetadataVocabulary.range(null, "main")), repository),
        )
        val tokens = GitMetadataVocabulary.scan(branch, commits, null, null).map { "${it.line} ${it.token}" }

        assertEquals("feature/phase45-2-x", branch)
        assertEquals(3, commits.size)
        assertEquals(listOf("1 phase45", "1 phase45", "4 Task 2/9"), tokens)
    }

    /** Runs git with fixed identity and no global or system configuration. */
    private fun git(directory: File, vararg arguments: String): String {
        val identity = listOf("-c", "user.name=Test", "-c", "user.email=test@example.invalid", "-c", "commit.gpgsign=false")
        return run(listOf("git") + identity + arguments, directory)
    }

    /** Runs [command] in [directory] and returns its standard output, failing on a non-zero exit. */
    private fun run(command: List<String>, directory: File): String {
        val process = ProcessBuilder(command)
            .directory(directory)
            .redirectErrorStream(true)
            .apply {
                environment()["GIT_CONFIG_GLOBAL"] = "/dev/null"
                environment()["GIT_CONFIG_NOSYSTEM"] = "1"
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(30, TimeUnit.SECONDS)) { "`${command.joinToString(" ")}` did not finish" }
        check(process.exitValue() == 0) { "`${command.joinToString(" ")}` failed: $output" }
        return output
    }
}
