package app.knotwork.android.presentation.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide request to restart the app, set once a restore is committed: the next start applies the restore,
 * and until then the running process must not carry on as though nothing had changed.
 *
 * State, not an event, and held above any screen: the activity restarts the process whenever it is started with
 * the request set. A rotation, a switch to another tab or the app going to the background while the commit
 * finishes therefore cannot lose it — the screen that asked need not be on screen when it is answered. A new
 * process starts without a request.
 */
@Singleton
class AppRestartRequest @Inject constructor() {

    private val _requested = MutableStateFlow(false)

    /** Whether the app must restart: `true` from [request] until the process ends. */
    val requested: StateFlow<Boolean> = _requested.asStateFlow()

    /** Asks for the restart. Asking again changes nothing. */
    fun request() {
        _requested.value = true
    }
}
