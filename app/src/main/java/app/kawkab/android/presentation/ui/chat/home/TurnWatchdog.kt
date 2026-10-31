package app.kawkab.android.presentation.ui.chat.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ends a chat turn that has shown no progress for [timeoutMs] (tasks 23h and 23j).
 *
 * A turn is active while the chat is generating and no card waits for the owner; a waiting tap
 * card is not a hang. [onStuck] stops the turn and shows an error with Retry.
 *
 * @property scope Where the watchdog runs.
 * @property timeoutMs The limit; 0 switches the watchdog off.
 * @property activeTurn Whether a turn is running with nothing waiting for the owner.
 * @property lastProgressMs When the turn last showed progress.
 * @property onStuck Called once when the limit passes.
 */
class TurnWatchdog(
    private val scope: CoroutineScope,
    private val timeoutMs: Long,
    private val activeTurn: Flow<Boolean>,
    private val lastProgressMs: () -> Long,
    private val onStuck: () -> Unit,
) {
    /** Starts watching, unless [timeoutMs] is 0. */
    fun start() {
        if (timeoutMs <= 0L) return
        scope.launch {
            activeTurn.distinctUntilChanged().collectLatest { active ->
                if (!active) return@collectLatest
                val startedAt = System.currentTimeMillis()
                while (isActive) {
                    delay(TICK_MS)
                    val since = maxOf(startedAt, lastProgressMs())
                    if (System.currentTimeMillis() - since >= timeoutMs) {
                        onStuck()
                        break
                    }
                }
            }
        }
    }

    private companion object {
        const val TICK_MS: Long = 1_000L
    }
}
