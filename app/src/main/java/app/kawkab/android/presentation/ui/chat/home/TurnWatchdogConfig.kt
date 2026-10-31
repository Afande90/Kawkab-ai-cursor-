package app.kawkab.android.presentation.ui.chat.home

import javax.inject.Inject

/**
 * How long a chat turn may show no progress before it ends with an error (tasks 23h and 23j).
 *
 * Injected so the app uses a minute and a test can switch it off.
 *
 * @property timeoutMs The limit, or 0 to switch the watchdog off.
 */
open class TurnWatchdogConfig(val timeoutMs: Long) {
    /** The app's limit: every turn ends within a minute in a reply, a card, or an error. */
    class Default @Inject constructor() : TurnWatchdogConfig(DEFAULT_TIMEOUT_MS)

    /** No watchdog. */
    data object Disabled : TurnWatchdogConfig(0L)

    /** The limits. */
    companion object {
        /** One minute. */
        const val DEFAULT_TIMEOUT_MS: Long = 60_000L
    }
}
