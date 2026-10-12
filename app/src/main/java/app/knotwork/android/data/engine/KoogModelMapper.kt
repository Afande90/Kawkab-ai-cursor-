package app.knotwork.android.data.engine

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel
import app.knotwork.android.domain.models.CloudProvider

/**
 * The models the app offers for each hosted provider Koog lists, and each one's default — Koog's
 * own [LLModel] constants, each written once.
 *
 * It is prohibited to instantiate [LLModel] for these providers: a constant carries the
 * capabilities Koog's client routes by (OpenAI's Responses-only models, for one). The id a user
 * saved maps back to its constant through [offeredModels]; [KoogCloudModelCatalog] decides what
 * a saved id that is not offered falls back to.
 */
object KoogModelMapper {

    /**
     * A provider's offer.
     *
     * @property default The model used when nothing is chosen, or the choice is not offered.
     *   Always one of [offered].
     * @property offered The models the provider's list shows, in that order.
     */
    private class Offer(val default: LLModel, val offered: List<LLModel>)

    private val OPENAI = Offer(
        default = OpenAIModels.Chat.GPT5_4,
        offered = listOf(
            OpenAIModels.Chat.GPT4o,
            OpenAIModels.Chat.GPT4oMini,
            OpenAIModels.Chat.GPT4_1,
            OpenAIModels.Chat.GPT4_1Nano,
            OpenAIModels.Chat.GPT4_1Mini,
            OpenAIModels.Chat.O1,
            OpenAIModels.Chat.O3,
            OpenAIModels.Chat.O3Mini,
            OpenAIModels.Chat.O4Mini,
            OpenAIModels.Chat.GPT5,
            OpenAIModels.Chat.GPT5Mini,
            OpenAIModels.Chat.GPT5Nano,
            OpenAIModels.Chat.GPT5Codex,
            OpenAIModels.Chat.GPT5_1,
            OpenAIModels.Chat.GPT5_1Codex,
            OpenAIModels.Chat.GPT5_1CodexMax,
            OpenAIModels.Chat.GPT5_2,
            OpenAIModels.Chat.GPT5_2Pro,
            OpenAIModels.Chat.GPT5_2Codex,
            OpenAIModels.Chat.GPT5_3Codex,
            OpenAIModels.Chat.GPT5_4,
            OpenAIModels.Chat.GPT5_4Pro,
        ),
    )

    private val ANTHROPIC = Offer(
        default = AnthropicModels.Sonnet_4_5,
        offered = listOf(
            AnthropicModels.Haiku_4_5,
            AnthropicModels.Sonnet_4,
            AnthropicModels.Sonnet_4_5,
            AnthropicModels.Opus_4,
            AnthropicModels.Opus_4_1,
            AnthropicModels.Opus_4_5,
            AnthropicModels.Opus_4_6,
        ),
    )

    private val GOOGLE = Offer(
        default = GoogleModels.Gemini3_Flash_Preview,
        offered = listOf(
            GoogleModels.Gemini2_5Pro,
            GoogleModels.Gemini2_5Flash,
            GoogleModels.Gemini2_5FlashLite,
            GoogleModels.Gemini3_Flash_Preview,
            GoogleModels.Gemini3_1Pro_Preview,
        ),
    )

    private val DEEPSEEK = Offer(
        default = DeepSeekModels.DeepSeekV4Flash,
        offered = listOf(
            DeepSeekModels.DeepSeekV4Flash,
            DeepSeekModels.DeepSeekV4Pro,
        ),
    )

    /** The offer of [provider], or `null` for a provider whose model is typed. */
    private fun offerOf(provider: CloudProvider): Offer? = when (provider) {
        CloudProvider.OPENAI -> OPENAI
        CloudProvider.ANTHROPIC -> ANTHROPIC
        CloudProvider.GOOGLE -> GOOGLE
        CloudProvider.DEEPSEEK -> DEEPSEEK
        CloudProvider.OPENROUTER, CloudProvider.GROQ, CloudProvider.OLLAMA, CloudProvider.OPENAI_COMPATIBLE -> null
    }

    /**
     * The models the app offers for [provider], in the order its list shows them.
     *
     * @param provider The provider.
     * @return Koog's constants, or `null` for a provider whose model is typed.
     */
    fun offeredModels(provider: CloudProvider): List<LLModel>? = offerOf(provider)?.offered

    /**
     * The model [provider] uses when nothing is chosen or the choice is not offered.
     *
     * @param provider The provider.
     * @return Koog's constant, or `null` for a provider whose model is typed.
     */
    fun defaultModel(provider: CloudProvider): LLModel? = offerOf(provider)?.default
}
