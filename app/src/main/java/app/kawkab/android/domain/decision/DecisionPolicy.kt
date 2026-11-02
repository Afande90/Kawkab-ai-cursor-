package app.kawkab.android.domain.decision

import kotlin.math.exp

/** The language model's speech is never parsed into an action decision. */
sealed interface DecisionResult {
    /** An allowlisted option and its softmax confidence; the normal action gate still applies.
     * @property option Selected allowlisted option.
     * @property probability Softmax confidence, without claiming measured accuracy.
     */
    data class Chosen(val option: String, val probability: Double) : DecisionResult

    /** One short spoken question; no tool executes.
     * @property question Short clarification read by the voice loop.
     */
    data class Ask(val question: String) : DecisionResult
}

/** Owner policy: confidence at least 0.85 acts; lower or malformed verdicts ask. */
object DecisionPolicy {
    /** Fixed by the owner for this round; no final-test certificate is required. */
    const val CONFIDENCE_THRESHOLD = 0.85

    /** Stable option probabilities from actual verdict logits, without a precision-certificate gate. */
    fun probabilities(logits: DoubleArray): DoubleArray? {
        if (logits.size < 2 || logits.any { !it.isFinite() }) return null
        val maximum = logits.max()
        val weights = logits.map { exp(it - maximum) }
        val total = weights.sum()
        return weights.map { it / total }.toDoubleArray()
    }

    /** Confidence is not a measured accuracy claim. Outbound choices still wait three seconds. */
    fun choose(options: List<String>, logits: DoubleArray): DecisionResult {
        val ask = DecisionResult.Ask("Which do you mean?")
        if (options.size < 2 ||
            options.distinct().size != options.size ||
            options.any { it.isBlank() } ||
            logits.size != options.size
        ) {
            return ask
        }
        val probabilities = probabilities(logits) ?: return ask
        val index = probabilities.indices.maxBy { probabilities[it] }
        return if (probabilities[index] >= CONFIDENCE_THRESHOLD) {
            DecisionResult.Chosen(options[index], probabilities[index])
        } else {
            ask
        }
    }
}
