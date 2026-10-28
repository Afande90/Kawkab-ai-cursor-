package app.kawkab.android.tier1

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kawkab.android.domain.engine.stub.StubLlmInferenceEngine
import app.kawkab.android.domain.models.Result
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tier 1 emulator harness (spec §12.1). Real inference stays stubbed: this test
 * never opens a model file and never calls LiteRT. Later tiers add tool dispatch,
 * the outbound gate, the denylist and the activity log on top of the same engine.
 */
@RunWith(AndroidJUnit4::class)
class Tier1StubEngineTest {
    @Test
    fun stubEngine_answersWithoutAModelFile() = runBlocking {
        val engine = StubLlmInferenceEngine()
        val initialized = engine.initialize("/not/a/real/model.litertlm")

        assertTrue(initialized is Result.Success)
        assertEquals(
            listOf(StubLlmInferenceEngine.STUB_REPLY),
            engine.generateResponseStream("اقرأ الشاشة").toList(),
        )
        assertEquals(
            listOf(StubLlmInferenceEngine.STUB_TRANSCRIPT),
            engine.transcribe("/not/audio.wav", "transcribe").toList(),
        )
    }
}
