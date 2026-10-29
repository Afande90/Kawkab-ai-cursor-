package app.kawkab.android.domain.assistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Unloads the model a few seconds after the assistant panel hides.
 *
 * A chat that is on screen keeps the model. The two-minute idle unload is a separate path.
 *
 * @param scope Where the delayed unload runs. It must outlive the panel.
 * @param chatOpen Whether a chat session is visible.
 * @param unload Releases the loaded model.
 * @param wait How the delay is applied. Tests pass a stand-in.
 */
class AssistantModelRelease(
    private val scope: CoroutineScope,
    private val chatOpen: () -> Boolean,
    private val unload: suspend () -> Unit,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private var job: Job? = null

    /**
     * Cancels a release that has not run yet. Called when the panel is shown again.
     */
    fun shown() {
        job?.cancel()
        job = null
    }

    /**
     * Releases the model [RELEASE_AFTER_HIDE_MS] after the panel hides, unless a chat is open.
     */
    fun hidden() {
        job?.cancel()
        job = scope.launch {
            wait(RELEASE_AFTER_HIDE_MS)
            if (!chatOpen()) unload()
        }
    }

    /**
     * How long a hidden panel waits before the model is released.
     */
    companion object {
        /**
         * How long the model stays loaded after the assistant panel hides.
         */
        const val RELEASE_AFTER_HIDE_MS: Long = 5_000L
    }
}
