package app.kawkab.android.domain.harness

/**
 * Puts the owner's name and identity at the top of a system prompt, then any stated facts.
 */
object HarnessPrompt {
    /**
     * [rest] with the owner line, then each fact, above it.
     * A blank [ownerName] uses the person who uses this phone.
     */
    fun top(ownerName: String, facts: List<String>, rest: String): String {
        val who = ownerName.trim().ifBlank { DEFAULT_OWNER }
        val stated = facts.map { it.trim() }.filter { it.isNotEmpty() }
        val factBlock = if (stated.isEmpty()) "" else stated.joinToString(separator = "\n") { "Fact: $it" } + "\n"
        return "Owner: $who.\n$factBlock$rest"
    }

    private const val DEFAULT_OWNER: String = "the person who uses this phone"
}
