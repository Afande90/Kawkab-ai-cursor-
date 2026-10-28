package app.kawkab.android.domain.engine

import app.kawkab.android.domain.models.CloudProvider

/**
 * The cloud models the app offers, and the model a run uses for what the user saved.
 *
 * One answer for every surface that names a provider's model — the provider's screen, its row in
 * Settings, the node sheet — and for [CloudLlmModelResolver], which sends it. Read anywhere else, a
 * second copy of "what is used when nothing is chosen" would drift from the one a run applies: a
 * screen would name one model while the run called another.
 */
interface CloudModelCatalog {

    /**
     * The model ids the app offers for [provider], in the order its list shows them.
     *
     * @param provider The provider.
     * @return The built-in list, or `null` for a provider whose model is typed (or picked from
     *   the server's own list): OpenRouter, Groq, Ollama and a server the user runs.
     */
    fun offeredModels(provider: CloudProvider): List<String>?

    /**
     * The model a run of [provider] uses when the user saved [savedId].
     *
     * A provider with a built-in list uses its default when nothing is saved or when the saved id
     * is no longer offered; Ollama uses its default when nothing is saved; any other provider uses
     * exactly what was saved. A blank id counts as nothing saved.
     *
     * @param provider The provider.
     * @param savedId The id the user saved, or `null`.
     * @return The model in use, or `null` when the provider has no default and nothing is saved —
     *   such a provider is not usable until a model is chosen.
     */
    fun modelInUse(provider: CloudProvider, savedId: String?): CloudModelInUse?
}

/**
 * The model a run of a provider uses, and why it is not the saved one when it is not.
 *
 * @property id The model id sent to the provider.
 * @property fallback Why [id] stands in for the saved choice; `null` when it is the saved choice.
 */
data class CloudModelInUse(val id: String, val fallback: CloudModelFallback? = null)

/** Why a run uses a provider's default model instead of a saved one. */
sealed interface CloudModelFallback {

    /** Nothing is saved for the provider. */
    data object NotChosen : CloudModelFallback

    /**
     * The saved model is not among the models the app offers any more — retired by its provider,
     * or from a newer version of the app.
     *
     * @property savedId The id that was saved.
     */
    data class NoLongerOffered(val savedId: String) : CloudModelFallback
}
