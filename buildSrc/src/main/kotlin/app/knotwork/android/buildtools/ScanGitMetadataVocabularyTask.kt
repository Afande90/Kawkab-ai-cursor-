package app.knotwork.android.buildtools

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.VerificationException
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Fails when the branch name, the commits of a range or a pull request's title
 * and body carry the retired product name or internal planning numbering.
 *
 * The rules are [ForbiddenVocabularyChecker]'s; which texts to read and how is
 * [GitMetadataVocabulary]'s. This task only runs git and reports. It is not
 * wired into `check`: a gate that decides a build must be a function of the
 * tree, and this verdict is a function of git state. It runs before a pull
 * request is opened or retitled.
 *
 * @property repositoryRoot Directory git runs in.
 * @property gitBase Branch the work merges into (`-PgitBase`, default `main`);
 *   the default range is `origin/<base>..HEAD`.
 * @property gitRange Explicit revision range (`-PgitRange`), overriding [gitBase].
 * @property pullRequestTitle Pull request title to scan (`-Ptitle`).
 * @property pullRequestBodyFile Path of a file holding the pull request body
 *   (`-PbodyFile`), absolute or relative to [repositoryRoot].
 * @property execOperations Runs git.
 */
@UntrackedTask(because = "its verdict depends on git state - the branch and the commits of a range - not on files")
abstract class ScanGitMetadataVocabularyTask : DefaultTask() {

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:Input
    abstract val gitBase: Property<String>

    @get:Input
    @get:Optional
    abstract val gitRange: Property<String>

    @get:Input
    @get:Optional
    abstract val pullRequestTitle: Property<String>

    @get:Input
    @get:Optional
    abstract val pullRequestBodyFile: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    /** Reads the branch, the range and the optional title and body, and fails on any hit. */
    @TaskAction
    fun scan() {
        val root = repositoryRoot.get().asFile
        val branch = git(root, GitMetadataVocabulary.BRANCH_COMMAND).trim()
        val range = GitMetadataVocabulary.range(gitRange.orNull, gitBase.get())
        val commits = GitMetadataVocabulary.parseLog(git(root, GitMetadataVocabulary.logCommand(range)))
        val body = pullRequestBodyFile.orNull?.let { path ->
            File(path).let { if (it.isAbsolute) it else root.resolve(path) }.readText()
        }
        val violations = GitMetadataVocabulary.scan(branch, commits, pullRequestTitle.orNull, body)
        if (violations.isNotEmpty()) {
            throw VerificationException(
                "Forbidden vocabulary in git metadata (${violations.size} hit(s), range `$range`):\n" +
                    violations.joinToString("\n") { "  ${it.format()}" } + "\n\n" +
                    "Branch names, commit messages and pull request titles are public. Name the area of the " +
                    "code, not the planning phase or task; the integration branch path is the one exception. " +
                    "See `CONTRIBUTING.md` and `docs/static-analysis.md`.",
            )
        }
        logger.lifecycle(
            "Git metadata clean: branch `$branch`, ${commits.size} commit(s) in `$range`" +
                (if (pullRequestTitle.isPresent) ", title" else "") +
                (if (body != null) ", body" else "") + ".",
        )
    }

    /** Runs [command] in [directory] and returns its standard output. */
    private fun git(directory: File, command: List<String>): String {
        val output = ByteArrayOutputStream()
        execOperations.exec { spec ->
            spec.commandLine(command)
            spec.workingDir = directory
            spec.standardOutput = output
        }
        return output.toString(Charsets.UTF_8)
    }
}
