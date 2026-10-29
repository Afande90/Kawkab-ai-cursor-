package app.kawkab.android.hands

import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory macros. A failed row keeps the run from being saved.
 * Replay is a separate tool and still stops for a tap.
 */
@Singleton
class MacroStore @Inject constructor() {
    private val pending = mutableListOf<HistoryRow>()
    private val saved = mutableMapOf<String, Macro>()

    /** Appends one finished row to the run being recorded. */
    fun add(row: HistoryRow) {
        pending += row
    }

    /**
     * Saves the pending run under [name] when every row succeeded.
     * Returns null when there is nothing successful to save.
     */
    fun save(name: String): Macro? {
        val steps = MacroRecording.stepsFromCompleted(pending) ?: return null
        pending.clear()
        val macro = Macro(id = name, name = name, steps = steps)
        saved[name] = macro
        return macro
    }

    /** The macro named [name], or null. */
    fun find(name: String): Macro? = saved[name]
}
