package app.kawkab.android.domain.engine.stub

import app.kawkab.android.domain.models.LocalBackend
import app.kawkab.android.domain.models.Result
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 1 stub engine (spec §12.1). The reply is fixed and no model file is read.
 */
class StubLlmInferenceEngineTest {
    @Test
    fun `given any path when initialized then the stub replies without loading a model`() = runBlocking {
        val engine = StubLlmInferenceEngine()
        val initialized = engine.initialize("/not/a/real/model.litertlm")

        assertTrue(initialized is Result.Success)
        assertTrue(engine.isInitialized)
        assertEquals(LocalBackend.CPU, engine.activeBackend)
        assertEquals(StubLlmInferenceEngine.CONTEXT_LENGTH, engine.activeContextLength)
        assertEquals(
            listOf(StubLlmInferenceEngine.STUB_REPLY),
            engine.generateResponseStream("hello").toList(),
        )
    }

    @Test
    fun `given a loaded stub when unloaded then it reports nothing loaded`() = runBlocking {
        val engine = StubLlmInferenceEngine()
        engine.initialize("/m/unused.litertlm", enableVision = true, enableAudio = true)
        engine.unload()

        assertFalse(engine.isInitialized)
        assertNull(engine.currentModelPath)
        assertFalse(engine.isVisionEnabled)
        assertFalse(engine.isAudioEnabled)
        assertNull(engine.activeBackend)
    }
}
