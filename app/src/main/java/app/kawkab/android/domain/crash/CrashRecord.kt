package app.kawkab.android.domain.crash

/**
 * One crash in the on-device crash log (task 33). It holds the exception classes and stack frames
 * only: exception messages can quote what the owner typed or said, so they are dropped.
 *
 * @property atEpochMs When it happened.
 * @property kind "java", "native" or "anr".
 * @property trace The class names and frames, one per line.
 */
data class CrashRecord(val atEpochMs: Long, val kind: String, val trace: String)

/** Pure rules of the crash log and the safe-mode start (tasks 32 and 33). */
object CrashRules {
    /** How many crashes are kept. */
    const val KEPT: Int = 5

    /** A death this soon after a start means the start itself is broken. */
    const val EARLY_DEATH_MS: Long = 10_000L

    /** Frames kept per trace, enough to see the cause without dumping a whole tombstone. */
    const val MAX_FRAMES: Int = 40

    /**
     * A throwable as class names and frames, without messages, including its causes.
     *
     * @param error The uncaught throwable.
     * @return The trace text.
     */
    fun trace(error: Throwable): String = buildString {
        var current: Throwable? = error
        var depth = 0
        var frames = 0
        while (current != null && depth < MAX_CAUSES) {
            appendLine(if (depth == 0) current.javaClass.name else "Caused by: ${current.javaClass.name}")
            for (frame in current.stackTrace) {
                if (frames >= MAX_FRAMES) break
                appendLine("    at $frame")
                frames += 1
            }
            current = current.cause
            depth += 1
        }
    }.trimEnd()

    /**
     * A native tombstone or ANR trace cut to its backtrace lines, without memory maps or registers.
     *
     * @param raw The text Android kept.
     * @return At most [MAX_FRAMES] lines that name a frame, an abort message or a signal.
     */
    fun nativeTrace(raw: String): String = raw.lineSequence()
        .map { it.trim() }
        .filter { line -> line.startsWith("#") || line.startsWith("signal") || line.startsWith("Abort message") }
        .take(MAX_FRAMES)
        .joinToString("\n")

    /**
     * @param log The crashes so far, newest first.
     * @param record A new crash.
     * @return The log with [record] first, at most [KEPT] long.
     */
    fun add(log: List<CrashRecord>, record: CrashRecord): List<CrashRecord> = (listOf(record) + log).take(KEPT)

    /**
     * Whether this start should be a safe one: the previous run died, by a crash, within
     * [EARLY_DEATH_MS] of starting.
     *
     * @param lastStartMs When the previous run started, or `null` on the first run.
     * @param lastCrashMs When the previous run's crash was recorded, or `null` if it did not crash.
     * @return `true` for a safe-mode start.
     */
    fun safeMode(lastStartMs: Long?, lastCrashMs: Long?): Boolean = lastStartMs != null &&
        lastCrashMs != null &&
        lastCrashMs >= lastStartMs &&
        lastCrashMs - lastStartMs <= EARLY_DEATH_MS

    /**
     * The text "Copy crash report" puts on the clipboard.
     *
     * @param version The app version and commit.
     * @param device The Android version and phone model.
     * @param log The crashes, newest first.
     * @return Plain text the owner can paste.
     */
    fun report(version: String, device: String, log: List<CrashRecord>): String = buildString {
        appendLine("Kawkab crash report")
        appendLine("App: $version")
        appendLine("Device: $device")
        if (log.isEmpty()) appendLine("No crashes recorded.")
        log.forEachIndexed { index, record ->
            appendLine()
            appendLine("#${index + 1} ${record.kind} at ${record.atEpochMs}")
            appendLine(record.trace)
        }
    }.trimEnd()

    private const val MAX_CAUSES = 5
}
