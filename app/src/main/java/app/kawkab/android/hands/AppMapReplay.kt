package app.kawkab.android.hands

/**
 * What a stored-macro replay did. No model is consulted.
 */
sealed interface MapReplay {
    /**
     * Every step ran, and each screen matched.
     *
     * @property steps How many steps ran.
     */
    data class Ran(val steps: Int) : MapReplay

    /**
     * An outbound step is next, and the owner has not tapped.
     *
     * @property atStep Index of the step that is waiting.
     * @property tool The outbound tool.
     */
    data class NeedsTap(val atStep: Int, val tool: String) : MapReplay

    /**
     * The screen after a step was not the stored screen. The caller should use tier 3.
     *
     * @property atStep Index of the step that did not match.
     * @property saw The text that was on screen.
     */
    data class TierThree(val atStep: Int, val saw: String) : MapReplay
}

/**
 * Replays a stored macro and checks the screen after every step.
 */
object AppMapReplay {
    /**
     * Plays [macro] against [map].
     *
     * [readScreen] is called after each step that ran. [run] performs the step.
     * An outbound step is not passed to [run] until [confirmed] is true.
     * A screen that does not show one of the stored markers stops the replay.
     *
     * @param map The app's stored screens.
     * @param macro The task to replay.
     * @param confirmed Whether the owner has tapped for an outbound step.
     * @param readScreen Current screen text.
     * @param run Performs one step. The caller still puts outbound work through the tap gate.
     */
    fun play(
        map: StoredAppMap,
        macro: StoredMacro,
        confirmed: Boolean,
        readScreen: () -> String,
        run: (AppMapStep) -> Unit,
    ): MapReplay {
        macro.steps.forEachIndexed { index, step ->
            if (step.outbound && !confirmed) return MapReplay.NeedsTap(index, step.tool)
            run(step)
            val saw = readScreen()
            val screen = map.screens.find { it.id == step.screenAfter }
            val matched = screen?.markers?.any { marker -> saw.contains(marker) } == true
            if (!matched) return MapReplay.TierThree(index, saw)
        }
        return MapReplay.Ran(macro.steps.size)
    }
}
