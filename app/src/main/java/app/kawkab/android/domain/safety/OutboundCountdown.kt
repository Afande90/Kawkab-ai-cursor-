package app.kawkab.android.domain.safety

import app.kawkab.android.domain.models.ToolRisk

/**
 * The owner's vision rule 1 (2 Nov 2026): a call or a message the owner asked for in person is
 * announced ("Calling Abdalla Mubadala") and runs after a 3-second cancel window. Saying "stop" or
 * tapping STOP cancels it; nothing needs a tap or a "yes".
 *
 * Rule 3: deleting the owner's data still needs an explicit spoken yes, remote network
 * actions are refused, and a run nobody is watching (a trigger, a background task) still asks,
 * because nobody would hear the announcement.
 */
object OutboundCountdown {
    /** The cancel window. */
    const val WINDOW_MS: Long = 3_000L

    /** Tools that run after the window: calls and messages. */
    val TOOLS: Set<String> = setOf("call_phone", "send_sms", "send_message", "macro_send", "submit_tap")

    /**
     * @param tool The tool about to run.
     * @param risk Its resolved risk.
     * @param interactive Whether the owner is in the chat or the panel that asked.
     * @param askedToConfirm Legacy node switch, ignored by the owner vision reset for interactive outbound actions.
     * @return `true` when the call runs after the window instead of waiting for a tap.
     */
    @Suppress("UNUSED_PARAMETER")
    fun applies(tool: String, risk: ToolRisk, interactive: Boolean, askedToConfirm: Boolean): Boolean =
        risk == ToolRisk.OUTBOUND && tool in TOOLS && interactive
}

/** Which sessions the owner is looking at right now (the open chat, or the assistant panel). */
fun interface InteractiveSessions {
    /**
     * @param sessionId A chat session.
     * @return `true` while the owner is in it.
     */
    fun isInteractive(sessionId: String): Boolean

    /** Defaults. */
    companion object {
        /** Nobody is watching: every outbound call asks. */
        val NONE: InteractiveSessions = InteractiveSessions { false }
    }
}
