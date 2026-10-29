package app.kawkab.android.domain.assistant

/**
 * Decides when text from the assistant API may be read.
 */
object ScreenTextPolicy {
    /**
     * Screen text is read only when the owner opted in and asked on this turn.
     *
     * @param ownerAllows The stored "use text from screen" switch.
     * @param requested Whether this turn asked to read the screen.
     * @return `true` only when both are true.
     */
    fun allow(ownerAllows: Boolean, requested: Boolean): Boolean = ownerAllows && requested
}
