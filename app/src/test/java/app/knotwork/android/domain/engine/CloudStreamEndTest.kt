package app.knotwork.android.domain.engine

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import app.knotwork.android.domain.models.CloudProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * [CloudStreamEnd]: which providers' missing finish reason means a cut answer — by measurement, per
 * provider and, on OpenAI, per endpoint — and that a finish reason always means a whole one.
 */
class CloudStreamEndTest {

    @Test
    fun `given a stream without a finish reason when checked then only the measured providers call it cut off`() {
        val cut = CloudProvider.entries.filter { CloudStreamEnd.cutOff(it, CHAT_MODEL, finishReason = null) }.toSet()

        assertEquals(setOf(CloudProvider.OPENAI, CloudProvider.DEEPSEEK, CloudProvider.GOOGLE), cut)
    }

    @Test
    fun `given a stream with a finish reason when checked then no provider calls it cut off`() {
        CloudProvider.entries.forEach { provider ->
            assertFalse(provider.id, CloudStreamEnd.cutOff(provider, CHAT_MODEL, finishReason = "stop"))
        }
    }

    @Test
    fun `given a Responses-only OpenAI model when its stream ends without a finish reason then it is not cut off`() {
        // A Responses stream ends every healthy answer without one; Koog fails a cut one itself.
        assertFalse(CloudStreamEnd.cutOff(CloudProvider.OPENAI, RESPONSES_ONLY_MODEL, finishReason = null))
    }

    @Test
    fun `given a provider when the cut is told then the message names it and says the answer was discarded`() {
        assertEquals(
            "The response from 'deepseek' was cut off before it finished — the connection " +
                "ended without a completion signal. The partial answer was discarded; try again.",
            CloudStreamEnd.truncatedMessage("deepseek"),
        )
        assertEquals(CloudStreamEnd.truncatedMessage("google"), CloudStreamCutOffException("google").message)
    }

    private companion object {
        /** A model that supports Chat Completions, as every model but the Responses-only lines does. */
        val CHAT_MODEL = LLModel(
            LLMProvider.OpenAI,
            "gpt-chat",
            listOf(LLMCapability.OpenAIEndpoint.Completions, LLMCapability.OpenAIEndpoint.Responses),
        )

        /** A model Koog can stream only through the Responses API, like the Codex and Pro lines. */
        val RESPONSES_ONLY_MODEL = LLModel(
            LLMProvider.OpenAI,
            "gpt-codex",
            listOf(LLMCapability.OpenAIEndpoint.Responses),
        )
    }
}
