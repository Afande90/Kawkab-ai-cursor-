package app.kawkab.android.domain.harness

/**
 * Gecko 110M is a smaller embedding candidate. It has not been measured here.
 */
object EmbeddingChoice {
    /**
     * The score of Gecko against EmbeddingGemma on this machine.
     */
    fun geckoScore(): String = NOT_MEASURED

    /**
     * True while no measurement has beaten the embedding already in the app.
     */
    fun keepEmbeddingGemma(): Boolean = geckoScore() == NOT_MEASURED

    private const val NOT_MEASURED: String = "not measured"
}
