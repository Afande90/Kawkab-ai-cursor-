package app.kawkab.android.domain.harness

/**
 * One step kept with the goal.
 *
 * @property text What happened.
 * @property transientError True when a later success should drop this step.
 */
data class HarnessStep(val text: String, val transientError: Boolean)

/**
 * The goal plus the last few steps. Transient errors leave after a success.
 *
 * @property goal What the owner asked for. It is never dropped.
 */
class SlidingHistory(val goal: String) {
    private val steps = ArrayDeque<HarnessStep>()

    /**
     * Appends [step] and keeps only the newest steps.
     */
    fun add(step: HarnessStep) {
        steps.addLast(step)
        while (steps.size > KEPT) steps.removeFirst()
    }

    /**
     * Drops transient errors. The goal stays.
     */
    fun success() {
        val kept = steps.filterNot { it.transientError }
        steps.clear()
        kept.forEach { steps.addLast(it) }
    }

    /**
     * The goal and the steps still held, oldest first.
     */
    fun render(): String = buildString {
        append("Goal: ")
        append(goal)
        steps.forEach { step ->
            append("\n")
            append(step.text)
        }
    }

    private companion object {
        const val KEPT: Int = 9
    }
}
