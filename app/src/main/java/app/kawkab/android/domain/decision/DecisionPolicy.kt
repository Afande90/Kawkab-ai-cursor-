package app.kawkab.android.domain.decision

import kotlin.math.exp

/** Measurements from disjoint calibration, threshold-selection and final-test partitions.
 * @property modelSha256 Exact GGUF hash covered by the final test.
 * @property temperature Fitted only on calibration data.
 * @property threshold Selected before final testing.
 * @property finalPrecision Correct auto-actions divided by all final-test auto-actions.
 * @property finalActedCount Number of auto-actions in the final partition.
 */
data class DecisionCalibration(
    val modelSha256: String,
    val temperature: Double,
    val threshold: Double,
    val finalPrecision: Double,
    val finalActedCount: Int,
)

/** The language model's speech is never parsed into an action decision. */
sealed interface DecisionResult {
    /** Only an allowlisted option can be selected. The normal tool gate still applies.
     * @property option Selected allowlisted label.
     * @property probability Calibrated softmax probability.
     */
    data class Chosen(val option: String, val probability: Double) : DecisionResult

    /** Ask one short spoken question; no tool executes.
     * @property question Short clarification read by the voice loop.
     */
    data class Ask(val question: String) : DecisionResult
}

/** Jev verdict-slot logit differences become calibrated typed choices, never generated JSON. */
object DecisionPolicy {
    private const val REQUIRED_PRECISION = 0.95

    /** Missing or failed calibration, malformed output and ambiguity all ask. */
    fun choose(options: List<String>, logits: DoubleArray, calibration: DecisionCalibration?): DecisionResult {
        val ask = DecisionResult.Ask("Which do you mean?")
        if (options.size < 2 ||
            options.distinct().size != options.size ||
            logits.size != options.size ||
            logits.any { !it.isFinite() } ||
            calibration == null
        ) {
            return ask
        }
        val validPrecision = calibration.finalPrecision.isFinite() &&
            calibration.finalPrecision in REQUIRED_PRECISION..1.0 &&
            calibration.finalActedCount > 0
        val validTemperature = calibration.temperature.isFinite() && calibration.temperature > 0
        val validThreshold = calibration.threshold.isFinite() && calibration.threshold in REQUIRED_PRECISION..1.0
        if (!validPrecision || !validTemperature || !validThreshold) return ask
        val maximum = logits.max()
        val weights = logits.map { exp((it - maximum) / calibration.temperature) }
        val index = weights.indices.maxBy { weights[it] }
        val probability = weights[index] / weights.sum()
        return if (probability >= calibration.threshold) DecisionResult.Chosen(options[index], probability) else ask
    }
}
