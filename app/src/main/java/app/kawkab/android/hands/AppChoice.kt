package app.kawkab.android.hands

import kotlinx.coroutines.flow.StateFlow

/** An installed app option.
 * @property label Owner-facing application name.
 * @property packageName Android's exact dispatch identity, never a generated executable.
 */
data class AppOption(val label: String, val packageName: String)

/** One spoken app name awaiting a choice.
 * @property spoken The name to remember once the owner chooses.
 * @property options Installed applications only.
 */
data class PendingApp(val spoken: String, val options: List<AppOption>)

/** App ambiguity uses the same act-or-ask rule as contact ambiguity. */
interface AppChoice {
    /** A short question is pending; no app opens yet. */
    val state: StateFlow<PendingApp?>

    /** Every owner choice is visible and removable on Growth. */
    val learned: StateFlow<Map<String, AppOption>>

    /** Known choices are reused; unknown ambiguities consult the calibrated judge or ask. */
    suspend fun resolve(name: String, utterance: String): String?

    /** Reads a spoken answer and remembers the owner's choice. */
    fun answer(text: String): String?

    /** A new command or cancellation retires the old question. */
    fun clear()

    /** Forgetting a learned name restores ambiguity. The shell first obtains spoken consent. */
    fun forget(name: String)
}

/** Shared, deterministic answer interpretation; model confidence never invents package names. */
object AppChoiceRules {
    /** Ambiguous names and invalid ordinals return null. */
    fun pick(text: String, options: List<AppOption>): AppOption? {
        val words = ContactMatcher.tokens(text)
        val index = when {
            words.any { it in setOf("first", "one", "1", "الاول", "اول", "واحد") } -> 0
            words.any { it in setOf("second", "two", "2", "الثاني", "التاني", "اتنين") } -> 1
            words.any { it in setOf("third", "three", "3", "الثالث", "تلاتة") } -> 2
            else -> null
        }
        if (index != null) return options.getOrNull(index)
        val meaningful = words.filterNot { it == "the" }
        if (meaningful.isEmpty()) return null
        return options.filter { option ->
            val label = ContactMatcher.tokens(option.label)
            meaningful.all { word -> label.any { it.startsWith(word) } }
        }.singleOrNull()
    }

    /** No package identifiers or engine terms are spoken. */
    fun question(pending: PendingApp): String = "Which ${pending.spoken}? " +
        pending.options.mapIndexed { i, app -> "${i + 1}, ${app.label}." }.joinToString(" ") +
        " Say the number or name. I will remember."
}
