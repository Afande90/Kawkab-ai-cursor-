package app.knotwork.android.buildtools

import app.knotwork.android.buildtools.ForbiddenVocabularyChecker.Family
import app.knotwork.android.buildtools.ForbiddenVocabularyChecker.Violation

/**
 * The pure half of the git-metadata vocabulary scan: which commands to run, how
 * to read their output, and what to hand to [ForbiddenVocabularyChecker].
 *
 * `verifyForbiddenVocabulary` reads files, and it has to: a gate that decides a
 * build must be a function of the tree. But the first thing a visitor to the
 * repository reads is not a file. Branch names, commit messages and pull request
 * titles carried internal planning numbers for months while the file gate stayed
 * green: in the range between two releases, 142 of 143 commits did. This scan
 * reads that layer with the same rules — there is no second list of patterns —
 * and minus one family, [Family.INTEGRATION_BRANCH_PATH], whose number has a
 * public referent in `CONTRIBUTING.md`.
 *
 * It stays out of `check` because its verdict depends on git state, not on the
 * tree. [ScanGitMetadataVocabularyTask] runs it on demand, before a pull request
 * is opened or retitled.
 */
object GitMetadataVocabulary {

    /** Families reported for git metadata: all but the integration branch path. */
    val FAMILIES: Set<Family> = Family.entries.toSet() - Family.INTEGRATION_BRANCH_PATH

    /** Prints the checked-out branch name, or `HEAD` when the head is detached. */
    val BRANCH_COMMAND: List<String> = listOf("git", "rev-parse", "--abbrev-ref", "HEAD")

    /** Separates a commit's hash from its message in [logCommand] output. */
    private val FIELD_SEPARATOR = Char(0x1F)

    /** Ends each commit in [logCommand] output: a control character commit messages do not use. */
    private val RECORD_SEPARATOR = Char(0x1E)

    /** Characters of a commit hash used to label its message in a violation. */
    private const val HASH_LABEL_LENGTH = 8

    /** Label of the branch name in the scanned texts. */
    const val BRANCH_LABEL = "branch"

    /** Label of the pull request title in the scanned texts. */
    const val TITLE_LABEL = "title"

    /** Label of the pull request body in the scanned texts. */
    const val BODY_LABEL = "body"

    /**
     * One commit of the scanned range.
     *
     * @property hash Full object name.
     * @property message Raw message — subject, blank line and body — as git stores it.
     */
    data class Commit(val hash: String, val message: String)

    /**
     * Resolves the revision range to scan.
     *
     * @param explicitRange A range given by the caller (`-PgitRange`), used as is.
     * @param base Branch the work will merge into; the default range is everything
     *   on `HEAD` that its remote-tracking copy does not have.
     * @return [explicitRange] when set, otherwise `origin/<base>..HEAD`.
     */
    fun range(explicitRange: String?, base: String): String =
        explicitRange?.takeIf { it.isNotBlank() } ?: "origin/$base..HEAD"

    /**
     * Lists every commit of [range] with its full message, merges included: a
     * merge commit's subject names the branch that was merged.
     *
     * @param range Revision range, as returned by [range].
     * @return The command line; its output is read by [parseLog].
     */
    fun logCommand(range: String): List<String> =
        listOf("git", "log", "--no-color", "--format=%H%x1f%B%x1e", range)

    /**
     * Reads the output of [logCommand].
     *
     * @param output Standard output of [logCommand].
     * @return One [Commit] per record, newest first as git prints them; trailing
     *   blank lines of each message are dropped.
     */
    fun parseLog(output: String): List<Commit> =
        output.split(RECORD_SEPARATOR)
            .map { it.trimStart('\n') }
            .filter { it.isNotEmpty() }
            .map { record ->
                val hash = record.substringBefore(FIELD_SEPARATOR)
                val message = record.substringAfter(FIELD_SEPARATOR, missingDelimiterValue = "")
                Commit(hash = hash, message = message.trimEnd())
            }

    /**
     * Scans one pull request's git metadata.
     *
     * @param branch Output of [BRANCH_COMMAND]; a detached head (`HEAD`) or `null`
     *   has no name to scan.
     * @param commits Commits of the range, from [parseLog].
     * @param title Pull request title, when the caller has one.
     * @param body Pull request body, when the caller has one.
     * @return Every violation, labelled [BRANCH_LABEL], `commit <hash prefix>`,
     *   [TITLE_LABEL] or [BODY_LABEL], with the line inside that text. A commit
     *   whose prefix is already taken by another is labelled with its full hash,
     *   so a shared prefix never drops a message from the scan.
     */
    fun scan(branch: String?, commits: List<Commit>, title: String?, body: String?): List<Violation> {
        val texts = buildMap {
            branch?.trim()?.takeIf { it.isNotEmpty() && it != "HEAD" }?.let { put(BRANCH_LABEL, it) }
            commits.forEach { commit ->
                val short = "commit ${commit.hash.take(HASH_LABEL_LENGTH)}"
                put(if (short in this) "commit ${commit.hash}" else short, commit.message)
            }
            title?.let { put(TITLE_LABEL, it) }
            body?.let { put(BODY_LABEL, it) }
        }
        return ForbiddenVocabularyChecker.scan(texts, FAMILIES)
    }
}
