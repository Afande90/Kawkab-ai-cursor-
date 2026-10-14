package app.knotwork.android.presentation.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide request to restart the app, set once a restore is committed: the next start applies the restore.
 *
 * State, not an event, and held above any screen: `MainActivity` restarts the process whenever it is started with
 * the request set. A rotation, a switch to another tab or the app going to the background while the commit
 * finishes therefore cannot lose it — the screen that asked need not be on screen when it is answered. A new
 * process starts without a request.
 *
 * Only that activity reads it. An entry point that runs without it — the share target — does not: with the app
 * sent to the background during the commit, what it starts before the app is next opened is replaced by the
 * restore.
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
