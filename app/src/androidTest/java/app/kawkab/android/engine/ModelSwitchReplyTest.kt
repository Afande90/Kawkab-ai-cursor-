package app.kawkab.android.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kawkab.android.data.testing.ModelSwitchTestEntryPoint
import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.models.LocalBackend
import app.kawkab.android.domain.models.Result
import app.kawkab.android.testing.DeviceOnlyInstrumentedTest
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Task 23f on a real phone: Gemma 4 E2B on CPU answers "hello", the smallest GGUF brain loads
 * and answers, and E2B answers again after the switch back.
 *
 * Push the models first:
 * `adb push gemma-4-E2B-it.litertlm /data/local/tmp/kawkab-models/` and any `.gguf` beside it.
 */
@RunWith(AndroidJUnit4::class)
@DeviceOnlyInstrumentedTest(
    reason = "Needs Gemma 4 E2B (about 2.6 GB) and a GGUF brain on the device, and enough RAM to run E2B on " +
        "the CPU. The CI emulator has 4 GB of RAM, no model files, and an x86_64 CPU, so it cannot load E2B. " +
        "Run on the owner's phone after pushing the models to /data/local/tmp/kawkab-models.",
)
class ModelSwitchReplyTest {

    private lateinit var engine: LlmInferenceEngine

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        engine = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ModelSwitchTestEntryPoint::class.java,
        ).llmInferenceEngine()
    }

    @Test
    fun e2bOnCpuAnswers_thenGguf_thenE2bAgain() = runBlocking {
        val e2b = File(MODEL_DIR, E2B_FILE)
        assertTrue("Push $E2B_FILE to $MODEL_DIR first", e2b.isFile)
        val gguf = File(MODEL_DIR).listFiles { file -> file.name.endsWith(".gguf") }
            ?.minByOrNull { it.length() }
        assertTrue("Push a .gguf brain to $MODEL_DIR first", gguf != null)

        assertReply(e2b.absolutePath)
        assertEquals(LocalBackend.CPU, engine.activeBackend)
        assertReply(gguf!!.absolutePath)
        assertReply(e2b.absolutePath)
        engine.unload()
    }

    private suspend fun assertReply(path: String) {
        val loaded = engine.initialize(path, enableVision = false, enableAudio = false)
        assertTrue("$path did not load: $loaded", loaded is Result.Success)
        val reply = engine.generateResponseStream("USER: hello\nAGENT: ").toList().joinToString("")
        assertTrue("$path gave an empty reply", reply.isNotBlank())
    }

    private companion object {
        const val MODEL_DIR = "/data/local/tmp/kawkab-models"
        const val E2B_FILE = "gemma-4-E2B-it.litertlm"
    }
}
