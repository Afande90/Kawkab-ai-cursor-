package app.kawkab.android.hands

import app.kawkab.android.domain.models.RegisteredTools
import app.kawkab.android.domain.models.ToolRisk
import app.kawkab.android.domain.safety.ActivityLog
import app.kawkab.android.domain.safety.ActivityLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A local action that just ran and can still be undone.
 *
 * @property tool The local tool.
 * @property args Its arguments.
 * @property atMs When it ran.
 */
data class UndoOffer(val tool: String, val args: Map<String, String>, val atMs: Long)

/**
 * The Undo chip after a local action (task 23i): an alarm, a timer, an opened app or a calendar
 * event ran without asking, so the owner can take it back for [WINDOW_MS]. Undoing is recorded
 * in the activity log.
 */
@Singleton
class LocalUndo @Inject constructor(private val hands: PhoneHands, private val log: ActivityLog) {
    private val offer = MutableStateFlow<UndoOffer?>(null)

    /** The action that can still be undone, or `null`. Drives the chip. */
    val state: StateFlow<UndoOffer?> = offer.asStateFlow()

    /**
     * Offers Undo for [tool] when it is a local tool.
     *
     * @param tool The tool that ran.
     * @param args Its arguments.
     * @param nowMs When it ran.
     */
    fun offer(tool: String, args: Map<String, String>, nowMs: Long) {
        if (RegisteredTools.all.any { it.name == tool && it.risk == ToolRisk.LOCAL }) {
            offer.value = UndoOffer(tool, args, nowMs)
        }
    }

    /**
     * Drops [expired] once its window has passed, unless a newer offer replaced it.
     *
     * @param expired The offer whose chip timed out.
     */
    fun expire(expired: UndoOffer) {
        offer.compareAndSet(expired, null)
    }

    /**
     * Undoes the offered action.
     *
     * @param nowMs The time of the tap.
     * @return What happened, or `null` when nothing can be undone any more.
     */
    fun undo(nowMs: Long): String? {
        val current = offer.value ?: return null
        offer.value = null
        if (nowMs - current.atMs > WINDOW_MS) return null
        val result = hands.undo(current.tool, current.args)
        log.record(
            ActivityLogEntry(
                asked = "undo",
                ran = current.tool,
                entryPoint = "undo",
                result = result,
                confirmation = "tap",
                atEpochMs = nowMs,
            ),
        )
        return result
    }

    /** How long Undo stays. */
    companion object {
        /** Ten seconds. */
        const val WINDOW_MS: Long = 10_000L

        /** Appended to a local action's reply: it ran without a card because it stays on the phone. */
        const val NO_ASK_NOTE: String = "Stays on the phone, so I didn't ask."
    }
}
