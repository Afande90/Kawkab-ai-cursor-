package app.kawkab.android.data.testing

import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.repositories.LocalModelRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hands the real inference engine to the device-only model switch test (task 23f).
 *
 * Declared in `main` for the same reason as [AppFunctionsE2ETestEntryPoint]: the production
 * application is already `@HiltAndroidApp`, so an entry point reads the real graph without a
 * separate test component.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ModelSwitchTestEntryPoint {
    /** The engine chat uses. */
    fun llmInferenceEngine(): LlmInferenceEngine

    /** The installed models. */
    fun localModelRepository(): LocalModelRepository
}
