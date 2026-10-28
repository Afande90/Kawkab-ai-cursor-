package app.kawkab.android.domain.engine.stub

import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.models.AppError
import app.kawkab.android.domain.models.LocalBackend
import app.kawkab.android.domain.models.LocalSampling
import app.kawkab.android.domain.models.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * On-device stand-in for Tier 1. LiteRT-LM has no x86_64 library, so an emulator
 * gate must not load Gemma. This engine accepts any path, never reads it, and
 * returns a fixed reply. It lives in the debug source set, so release APKs do
 * not ship it.
 */
class StubLlmInferenceEngine : LlmInferenceEngine {
    private var loadedPath: String? = null
    private var vision: Boolean = false
    private var audio: Boolean = false

    override suspend fun initialize(
        modelPath: String,
        enableVision: Boolean,
        enableAudio: Boolean,
    ): Result<Unit, AppError> {
        loadedPath = modelPath
        vision = enableVision
        audio = enableAudio
        return Result.Success(Unit)
    }

    override val isInitialized: Boolean
        get() = loadedPath != null

    override val currentModelPath: String?
        get() = loadedPath

    override val isVisionEnabled: Boolean
        get() = vision && isInitialized

    override val isAudioEnabled: Boolean
        get() = audio && isInitialized

    override val activeBackend: LocalBackend?
        get() = if (isInitialized) LocalBackend.CPU else null

    override val activeContextLength: Int?
        get() = if (isInitialized) CONTEXT_LENGTH else null

    override fun transcribe(audioPath: String, prompt: String): Flow<String> = flow {
        emit(STUB_TRANSCRIPT)
    }

    override fun generateResponseStream(prompt: String, imagePath: String?, sampling: LocalSampling?): Flow<String> =
        flow {
            emit(STUB_REPLY)
        }

    override fun close() {
        loadedPath = null
        vision = false
        audio = false
    }

    override suspend fun unload() {
        close()
    }

    companion object {
        const val STUB_REPLY: String = "stub-engine"
        const val STUB_TRANSCRIPT: String = "stub-transcript"
        const val CONTEXT_LENGTH: Int = 1024
    }
}
