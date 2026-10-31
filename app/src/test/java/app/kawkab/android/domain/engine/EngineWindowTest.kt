package app.kawkab.android.domain.engine

import app.kawkab.android.data.tools.local.handAgentTools
import app.kawkab.android.domain.constants.SettingsDefaults
import app.kawkab.android.domain.models.LocalModel
import app.kawkab.android.domain.usecases.FallbackBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Task 23f: the engine window must hold the Kawkab prompt, the phone tools, the injected
 * context and the reply.
 */
class EngineWindowTest {

    private val assistantPrompt: String by lazy {
        val json = File("src/main/assets/presets/pipelines/kawkab_assistant.json").readText()
        Regex("\"systemPrompt\": \"((?:[^\"\\\\]|\\\\.)*)\"").find(json)!!.groupValues[1]
    }

    private val toolSchemas: String by lazy {
        handAgentTools().joinToString("\n") { "${it.name} — ${it.description}\n${it.parameters}" }
    }

    @Test
    fun `the default window holds the prompt, the tool schemas, the injected budget and the reply`() {
        val required = EngineWindow.requiredTokens(
            systemPromptTokens = EngineWindow.estimateTokens(assistantPrompt) + OWNER_AND_FACTS_ALLOWANCE,
            toolSchemaTokens = EngineWindow.estimateTokens(toolSchemas),
        )
        assertTrue(
            "needs $required tokens, window is ${SettingsDefaults.MAX_CONTEXT_LENGTH_DEFAULT}",
            required <= SettingsDefaults.MAX_CONTEXT_LENGTH_DEFAULT,
        )
        assertTrue("the floor must hold it too: $required", required <= EngineWindow.MIN_ENGINE_TOKENS)
        assertTrue(ReplyBudget.TOOL_OR_JSON_MAX_OUTPUT_TOKENS <= EngineWindow.REPLY_RESERVE_TOKENS)
    }

    @Test
    fun `task 15's 1,500 window cannot hold the same turn`() {
        val required = EngineWindow.requiredTokens(
            systemPromptTokens = EngineWindow.estimateTokens(assistantPrompt),
            toolSchemaTokens = EngineWindow.estimateTokens(toolSchemas),
        )
        assertTrue(required > SettingsDefaults.LEGACY_TASK15_CONTEXT_LENGTH)
    }

    @Test
    fun `a stored 1,500 migrates to 4,096 and other values stay`() {
        assertEquals(4_096, EngineWindow.migrated(1_500))
        assertEquals(4_096, EngineWindow.migrated(null))
        assertEquals(6_144, EngineWindow.migrated(6_144))
        assertEquals(3_584, EngineWindow.migrated(3_584))
    }

    @Test
    fun `the engine is never opened below the floor`() {
        assertEquals(EngineWindow.MIN_ENGINE_TOKENS, EngineWindow.engineTokens(512))
        assertEquals(EngineWindow.MIN_ENGINE_TOKENS, EngineWindow.engineTokens(1_500))
        assertEquals(8_192, EngineWindow.engineTokens(8_192))
    }

    @Test
    fun `allocation failures are recognised in the message and in the cause chain`() {
        val raw = "Status Code 13 … llm_litert_compiled_model_executor.cc:743 Failed to invoke the compiled model"
        assertTrue(EngineWindow.isAllocationFailure(raw))
        assertTrue(EngineWindow.isAllocationFailure("Failed to allocate tensors"))
        assertTrue(
            EngineWindow.isAllocationFailure(
                message = "wrapped",
                throwable = IllegalStateException("outer", RuntimeException("Failed to allocate tensors")),
            ),
        )
        assertFalse(EngineWindow.isAllocationFailure("Model file not found"))
        assertFalse(EngineWindow.isAllocationFailure(null, null))
    }

    @Test
    fun `the fallback prefers E2B, skips the failed model and never picks a GGUF`() {
        val e4b = model(1, "gemma-4-E4B-it.litertlm")
        val e2b = model(2, "gemma-4-E2B-it.litertlm")
        val qwen = model(3, "Qwen3-1.7B-Q4_K_M.gguf")
        val other = model(4, "other.litertlm")

        assertEquals(listOf(e2b, other), FallbackBrain.order(listOf(e4b, qwen, other, e2b), failedPath = e4b.path))
        assertEquals(listOf(e2b, e4b), FallbackBrain.order(listOf(qwen, e4b, e2b), failedPath = qwen.path))
        assertEquals(emptyList<LocalModel>(), FallbackBrain.order(listOf(e2b, qwen), failedPath = e2b.path))
    }

    private fun model(id: Long, name: String) =
        LocalModel(id = id, name = name, path = "/models/$name", size = 1L, isActive = false)

    private companion object {
        /** The owner line and stated facts added at the top of every prompt. */
        const val OWNER_AND_FACTS_ALLOWANCE = 200
    }
}
