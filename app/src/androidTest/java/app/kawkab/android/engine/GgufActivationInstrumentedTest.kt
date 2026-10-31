package app.kawkab.android.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kawkab.android.data.testing.ModelSwitchTestEntryPoint
import app.kawkab.android.domain.models.LocalModel
import app.kawkab.android.domain.models.Result
import app.kawkab.android.domain.usecases.ActivateModelUseCase
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.security.MessageDigest

/**
 * Task 23e on the emulator: a GGUF brain is downloaded and checked against its SHA-256, activated
 * only after it loads, and answers "hello". SmolLM2 135M (Apache-2.0, 105 MB) is the smallest
 * GGUF that llama.cpp answers with; it is a test fixture, never shipped.
 */
@RunWith(AndroidJUnit4::class)
class GgufActivationInstrumentedTest {

    @Test
    fun aDownloadedGgufActivatesAndAnswersHello() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ModelSwitchTestEntryPoint::class.java,
        )
        val file = File(context.cacheDir, FILE_NAME)
        if (!file.isFile || sha256(file) != SHA256) {
            URL(URL_STRING).openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        assertEquals("the download must match its published SHA-256", SHA256, sha256(file))

        val repository = entry.localModelRepository()
        val id = repository.insertModel(
            LocalModel(name = FILE_NAME, path = file.absolutePath, size = file.length(), isActive = false),
        )
        val engine = entry.llmInferenceEngine()
        try {
            val activated = ActivateModelUseCase(repository, engine)(id)
            assertTrue("not activated: $activated", activated is Result.Success)
            assertEquals(file.absolutePath, repository.getActiveModel()?.path)

            val reply = engine.generateResponseStream("USER: hello\nAGENT: ").toList().joinToString("")
            assertTrue("empty reply", reply.isNotBlank())
        } finally {
            engine.unload()
            repository.deleteModelById(id)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val FILE_NAME = "SmolLM2-135M-Instruct-Q4_K_M.gguf"
        const val URL_STRING = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/$FILE_NAME"
        const val SHA256 = "2e8040ceae7815abe0dcb3540b9995eaa1fa0d2ca9e797d0a635ae4433c68c2d"
        const val BUFFER = 1 shl 16
    }
}
