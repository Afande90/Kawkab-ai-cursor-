package app.kawkab.android.data.engine

import app.kawkab.android.domain.engine.CloudModelCatalog
import app.kawkab.android.domain.engine.CloudModelFallback
import app.kawkab.android.domain.engine.CloudModelInUse
import app.kawkab.android.domain.models.CloudProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data-layer implementation of [CloudModelCatalog]: the built-in lists and defaults of
 * [KoogModelMapper], and Ollama's default.
 */
@Singleton
class KoogCloudModelCatalog @Inject constructor() : CloudModelCatalog {

    override fun offeredModels(provider: CloudProvider): List<String>? =
        KoogModelMapper.offeredModels(provider)?.map { it.id }

    override fun modelInUse(provider: CloudProvider, savedId: String?): CloudModelInUse? {
        val saved = savedId?.takeIf { it.isNotBlank() }
        val offered = KoogModelMapper.offeredModels(provider)
        val default = KoogModelMapper.defaultModel(provider)
        return when {
            offered != null && default != null -> when {
                saved == null -> CloudModelInUse(default.id, CloudModelFallback.NotChosen)
                offered.any { it.id == saved } -> CloudModelInUse(saved)
                else -> CloudModelInUse(default.id, CloudModelFallback.NoLongerOffered(saved))
            }
            saved != null -> CloudModelInUse(saved)
            provider == CloudProvider.OLLAMA -> CloudModelInUse(OLLAMA_DEFAULT_MODEL, CloudModelFallback.NotChosen)
            else -> null
        }
    }

    private companion object {
        /**
         * The Ollama model asked for when the user typed none. Kept as it shipped rather than
         * taken from Koog: an Ollama model is whatever tag the user pulled, Koog's Ollama
         * constants are a sample rather than a list of what is installed, and `llama3` is a tag
         * of Ollama's own library. Changing it would move users who never chose a model onto
         * another one.
         */
        const val OLLAMA_DEFAULT_MODEL = "llama3"
    }
}
