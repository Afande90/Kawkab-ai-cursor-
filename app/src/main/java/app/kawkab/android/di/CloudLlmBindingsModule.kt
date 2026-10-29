package app.kawkab.android.di

import app.kawkab.android.data.engine.KoogClientFactory
import app.kawkab.android.data.engine.KoogCloudLlmModelResolver
import app.kawkab.android.data.engine.KoogCloudModelCatalog
import app.kawkab.android.data.engine.KoogStructuredInferenceClientFactory
import app.kawkab.android.domain.engine.CloudLlmClientFactory
import app.kawkab.android.domain.engine.CloudLlmModelResolver
import app.kawkab.android.domain.engine.CloudModelCatalog
import app.kawkab.android.domain.engine.structured.CloudStructuredInferenceClientFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Domain bindings for the cloud model clients. Split out of the tool map so the
 * hands tools can register beside the original built-ins.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CloudLlmBindingsModule {
    /**
     * Domain-level binding for the cloud client factory.
     */
    @Binds
    @Singleton
    abstract fun bindCloudLlmClientFactory(impl: KoogClientFactory): CloudLlmClientFactory

    /**
     * Domain-level binding for the cloud model resolver.
     */
    @Binds
    @Singleton
    abstract fun bindCloudLlmModelResolver(impl: KoogCloudLlmModelResolver): CloudLlmModelResolver

    /**
     * Domain-level binding for the cloud model catalog.
     */
    @Binds
    @Singleton
    abstract fun bindCloudModelCatalog(impl: KoogCloudModelCatalog): CloudModelCatalog

    /**
     * Domain-level binding for the cloud structured-inference client factory.
     */
    @Binds
    @Singleton
    abstract fun bindCloudStructuredInferenceClientFactory(
        impl: KoogStructuredInferenceClientFactory,
    ): CloudStructuredInferenceClientFactory
}
