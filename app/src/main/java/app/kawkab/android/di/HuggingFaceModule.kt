package app.kawkab.android.di

import app.kawkab.android.data.network.huggingface.HuggingFaceBaseUrl
import app.kawkab.android.domain.constants.ModelDiscoveryConstants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the Hugging Face Hub base URL consumed by the
 * model-discovery client. Bound as a qualified [String] so production resolves
 * the public Hub host from [ModelDiscoveryConstants] while tests can supply a
 * mock-server URL.
 */
@Module
@InstallIn(SingletonComponent::class)
object HuggingFaceModule {

    /** Public Hugging Face Hub host (no trailing slash). */
    @Provides
    @Singleton
    @HuggingFaceBaseUrl
    fun provideHuggingFaceBaseUrl(): String = ModelDiscoveryConstants.HF_BASE_URL
}
