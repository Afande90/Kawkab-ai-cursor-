package app.kawkab.android.presentation.ui.models

import org.junit.Assert.assertEquals
import org.junit.Test

/** Task 23f: each model is labelled with the runtime that really runs it. */
class ModelRuntimeLabelTest {

    @Test
    fun `a GGUF brain is labelled llama cpp on the CPU`() {
        assertEquals("CPU · llama.cpp", runtimeLabel("/models/Qwen3-1.7B-Q4_K_M.gguf", backendLabel = "GPU"))
    }

    @Test
    fun `a LiteRT model is labelled with its backend`() {
        assertEquals("GPU · LiteRT", runtimeLabel("/models/gemma-4-E4B-it.litertlm", backendLabel = "GPU"))
        assertEquals("CPU · LiteRT", runtimeLabel("/models/gemma-4-E2B-it.litertlm", backendLabel = "CPU"))
    }

    @Test
    fun `below flagship RAM the E4B preset carries the warning and E2B does not`() {
        val state = ModelsUiState().toViewState(
            subtitleFormat = "%1\$d active · %2\$d on disk · %3\$s",
            verificationLabels = VerificationLabels(verified = "checked", notVerified = "not checked"),
            e4bWarning = "needs 16 GB RAM",
        )
        assertEquals("Gemma-4-E4B-it · needs 16 GB RAM", state.presets.first { it.name.startsWith("Gemma-4-E4B") }.name)
        assertEquals("Gemma-4-E2B-it", state.presets.first { it.name.startsWith("Gemma-4-E2B") }.name)
    }

    @Test
    fun `on a flagship phone no warning is shown`() {
        val state = ModelsUiState().toViewState(
            subtitleFormat = "%1\$d active · %2\$d on disk · %3\$s",
            verificationLabels = VerificationLabels(verified = "checked", notVerified = "not checked"),
        )
        assertEquals("Gemma-4-E4B-it", state.presets.first { it.name.startsWith("Gemma-4-E4B") }.name)
    }
}
