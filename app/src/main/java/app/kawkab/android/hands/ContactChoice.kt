package app.kawkab.android.hands

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A call or text waiting for the owner to pick one of several contacts (task 23j).
 *
 * @property tool The hands tool that asked, such as `call_phone`.
 * @property args Its other arguments, such as the message body.
 * @property choices The contacts to pick from, at most [ContactMatcher.MAX_CHOICES].
 * @property atMs When it was asked.
 */
data class PendingContact(
    val tool: String,
    val args: Map<String, String>,
    val choices: List<ContactMatch>,
    val atMs: Long,
)

/**
 * What a line typed while a choice is pending means.
 */
sealed interface ContactAnswer {
    /** The owner picked [contact]; run [tool] with [args], whose `contact` is now its number. */
    data class Picked(val tool: String, val args: Map<String, String>, val contact: ContactMatch) : ContactAnswer

    /** The owner cancelled. */
    data object Cancelled : ContactAnswer

    /** The line did not pick anyone; ask again instead of chatting. */
    data object Unclear : ContactAnswer

    /** Nothing is pending, or it expired. */
    data object NotPending : ContactAnswer
}

/**
 * Holds the pending choice and the last contact a command resolved to.
 *
 * The pending call or text stays until the owner picks, cancels, or [TIMEOUT_MS] passes. A
 * follow-up such as "the mubadala", "the second one", or a chip tap completes it.
 */
@Singleton
class ContactChoice @Inject constructor(private val aliases: ContactAliasStore) {
    /** Without remembered names, for tests and previews. */
    constructor() : this(ContactAliasStore.NONE)

    private val pending = MutableStateFlow<PendingContact?>(null)

    /** The choice waiting for the owner, or `null`. Drives the chips. */
    val state: StateFlow<PendingContact?> = pending.asStateFlow()

    /** The contact the last command resolved to, for "him" and "her". */
    @Volatile
    var lastResolved: ContactMatch? = null

    /**
     * Keeps [tool] waiting for a pick among [choices].
     *
     * @param tool The hands tool.
     * @param args Its arguments.
     * @param choices The contacts.
     * @param nowMs The time.
     */
    fun offer(tool: String, args: Map<String, String>, choices: List<ContactMatch>, nowMs: Long) {
        pending.value = PendingContact(tool, args, choices.take(ContactMatcher.MAX_CHOICES), nowMs)
    }

    /**
     * @param spoken A name the owner said.
     * @return The contact he picked for it before, or `null`.
     */
    fun remembered(spoken: String): ContactMatch? = aliases.get(spoken)

    /** Drops the pending choice. */
    fun clear() {
        pending.value = null
    }

    /**
     * Reads [text] as an answer to the pending choice.
     *
     * @param text What the owner typed or tapped.
     * @param nowMs The time.
     * @return What it means.
     */
    fun answer(text: String, nowMs: Long): ContactAnswer {
        val current = pending.value ?: return ContactAnswer.NotPending
        if (nowMs - current.atMs > TIMEOUT_MS) {
            clear()
            return ContactAnswer.NotPending
        }
        val words = ContactMatcher.tokens(text)
        if (words.any { it in cancelWords }) {
            clear()
            return ContactAnswer.Cancelled
        }
        val picked = byOrdinal(words, current.choices)
            ?: byDigits(text, current.choices)
            ?: byName(text, current.choices)
            ?: return ContactAnswer.Unclear
        clear()
        lastResolved = picked
        // Never ask again for this name (task 34b, vision rule 2).
        current.args[SPOKEN]?.let { spoken -> aliases.remember(spoken, picked) }
        return ContactAnswer.Picked(current.tool, current.args + ("contact" to picked.phone), picked)
    }

    private fun byOrdinal(words: List<String>, choices: List<ContactMatch>): ContactMatch? {
        val index = words.firstNotNullOfOrNull { ordinals[it] } ?: return null
        return choices.getOrNull(index)
    }

    private fun byDigits(text: String, choices: List<ContactMatch>): ContactMatch? {
        val groups = digitGroup.findAll(text).map { it.value.takeLast(DIGITS) }.toList()
        if (groups.isEmpty()) return null
        return choices.filter { ContactMatcher.lastDigits(it.phone) in groups }.singleOrNull()
    }

    private fun byName(text: String, choices: List<ContactMatch>): ContactMatch? {
        val entries = choices.map { ContactEntry(it.name, it.phone, it.label) }
        val matches = ContactMatcher.match(text, entries)
        return matches.singleOrNull()?.let { found -> choices.first { it.phone == found.phone } }
    }

    /** Timing and the words that cancel or count. */
    companion object {
        /** The argument that carries the name the owner said, kept so a pick can be remembered. */
        const val SPOKEN: String = "spoken"

        /** A pending choice expires after two minutes. */
        const val TIMEOUT_MS: Long = 120_000L

        private const val DIGITS = 4

        private val digitGroup = Regex("\\d{4,}")

        /**
         * The sentence shown with the chips and read aloud in a conversation: the names as a short
         * numbered list, so the owner can answer "the second one" or a name (task 29). It never lists
         * phone numbers.
         *
         * @param spoken The name the owner said.
         * @param count How many contacts matched.
         * @param names The names offered, in chip order.
         * @return The question.
         */
        fun question(spoken: String, count: Int, names: List<String> = emptyList()): String {
            val list = names.mapIndexed { index, name -> "${index + 1}, $name." }.joinToString(" ")
            val head = "$count contacts match '$spoken'. Which one?"
            return if (list.isEmpty()) {
                "$head Say the name or say cancel."
            } else {
                "$head $list Say the number or the name. I will remember. Say cancel to stop."
            }
        }

        private val cancelWords = setOf("cancel", "stop", "never", "nevermind", "الغاء", "الغي", "خلاص", "بلاش")

        private val ordinals = mapOf(
            "first" to 0, "1" to 0, "one" to 0, "الاول" to 0, "اول" to 0, "واحد" to 0,
            "second" to 1, "2" to 1, "two" to 1, "الثاني" to 1, "التاني" to 1, "تاني" to 1, "اتنين" to 1,
            "third" to 2, "3" to 2, "three" to 2, "الثالث" to 2, "التالت" to 2, "تالت" to 2,
            "fourth" to 3, "4" to 3, "four" to 3, "الرابع" to 3, "رابع" to 3,
            "fifth" to 4, "5" to 4, "five" to 4, "الخامس" to 4, "خامس" to 4,
        )
    }
}
