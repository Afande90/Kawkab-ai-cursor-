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
}
