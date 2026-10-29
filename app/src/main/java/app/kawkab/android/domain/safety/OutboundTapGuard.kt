package app.kawkab.android.domain.safety

/**
 * Decides whether a tap target looks outbound from `safety/outbound_patterns.txt`.
 *
 * Latin needles match ignoring case. Arabic needles match as written, so both
 * `ارسال` and `إرسال` are listed. A blank target is not outbound.
 */
class OutboundTapGuard(patterns: List<String>) {

    private val needles: List<String> = patterns
        .map { it.substringBefore("#").trim() }
        .filter { it.isNotEmpty() }

    /**
     * True when [text], [contentDescription], or [viewId] contains a needle.
     */
    fun matches(text: String?, contentDescription: String?, viewId: String?): Boolean {
        val haystack = listOfNotNull(text, contentDescription, viewId)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        if (haystack.isBlank()) return false
        return needles.any { needle -> haystack.contains(needle, ignoreCase = true) }
    }

    /** Reads the pattern file. */
    companion object {
        /**
         * Builds a guard from the raw pattern file. Comment lines and blanks are dropped.
         */
        fun fromText(raw: String): OutboundTapGuard = OutboundTapGuard(raw.lines())
    }
}

/**
 * What a click is about to hit.
 *
 * @property text Visible label.
 * @property contentDescription Accessibility description.
 * @property viewId View id resource name, when the tree has one.
 */
data class TapTarget(val text: String?, val contentDescription: String?, val viewId: String?)

/**
 * Whether a tap may proceed.
 */
enum class TapDecision {
    /** The tap is not outbound, or the owner already tapped confirm. */
    ALLOW,

    /** The target matches the outbound list and there is no tap yet. */
    NEEDS_TAP,
}

/**
 * The generic tap gate. Every click, from a tool, a skill, a macro, or a routine, comes through here.
 */
object GuardedTap {
    /**
     * Outbound targets need [confirmed]. Anything else may proceed.
     */
    fun decision(guard: OutboundTapGuard, target: TapTarget, confirmed: Boolean): TapDecision {
        val outbound = guard.matches(target.text, target.contentDescription, target.viewId)
        if (outbound && !confirmed) return TapDecision.NEEDS_TAP
        return TapDecision.ALLOW
    }
}
