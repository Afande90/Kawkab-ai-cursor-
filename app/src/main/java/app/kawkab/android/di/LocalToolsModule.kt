package app.kawkab.android.di

import app.kawkab.android.data.engine.KoogClientFactory
import app.kawkab.android.data.engine.KoogCloudLlmModelResolver
import app.kawkab.android.data.engine.KoogCloudModelCatalog
import app.kawkab.android.data.engine.KoogStructuredInferenceClientFactory
import app.kawkab.android.data.tools.local.SearchTool
import app.kawkab.android.data.tools.local.executors.AppendFileExecutor
import app.kawkab.android.data.tools.local.executors.DelegateTaskExecutor
import app.kawkab.android.data.tools.local.executors.DeleteFileExecutor
import app.kawkab.android.data.tools.local.executors.EditFileExecutor
import app.kawkab.android.data.tools.local.executors.FindFilesExecutor
import app.kawkab.android.data.tools.local.executors.HttpRequestExecutor
import app.kawkab.android.data.tools.local.executors.ListFilesExecutor
import app.kawkab.android.data.tools.local.executors.ReadFileExecutor
import app.kawkab.android.data.tools.local.executors.ScheduleTaskExecutor
import app.kawkab.android.data.tools.local.executors.SearchToolExecutor
import app.kawkab.android.data.tools.local.executors.WriteFileExecutor
import app.kawkab.android.domain.engine.CloudLlmClientFactory
import app.kawkab.android.domain.engine.CloudLlmModelResolver
import app.kawkab.android.domain.engine.CloudModelCatalog
import app.kawkab.android.domain.engine.structured.CloudStructuredInferenceClientFactory
import app.kawkab.android.domain.repositories.LocalToolExecutor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton

/**
 * DI module wiring the [LocalToolExecutor] multibinding map and the cloud LLM
 * abstractions used by `CloudLlmNodeExecutor` (`CloudLlmClientFactory`,
 * `CloudLlmModelResolver`).
 *
 * Each built-in tool registers its implementation under its tool name; consumers inject
 * `Map<String, LocalToolExecutor>` and dispatch by name. New built-ins are added by
 * appending another `@Binds @IntoMap @StringKey(...)` line — no edits to
 * `ToolRepositoryImpl`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocalToolsModule {

    @Binds
    @IntoMap
    @StringKey(ScheduleTaskExecutor.TOOL_NAME)
    abstract fun bindScheduleTaskExecutor(executor: ScheduleTaskExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(DelegateTaskExecutor.TOOL_NAME)
    abstract fun bindDelegateTaskExecutor(executor: DelegateTaskExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(SearchTool.TOOL_NAME)
    abstract fun bindSearchToolExecutor(executor: SearchToolExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(ReadFileExecutor.TOOL_NAME)
    abstract fun bindReadFileExecutor(executor: ReadFileExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(ListFilesExecutor.TOOL_NAME)
    abstract fun bindListFilesExecutor(executor: ListFilesExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(FindFilesExecutor.TOOL_NAME)
    abstract fun bindFindFilesExecutor(executor: FindFilesExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(WriteFileExecutor.TOOL_NAME)
    abstract fun bindWriteFileExecutor(executor: WriteFileExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(EditFileExecutor.TOOL_NAME)
    abstract fun bindEditFileExecutor(executor: EditFileExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(AppendFileExecutor.TOOL_NAME)
    abstract fun bindAppendFileExecutor(executor: AppendFileExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(DeleteFileExecutor.TOOL_NAME)
    abstract fun bindDeleteFileExecutor(executor: DeleteFileExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(HttpRequestExecutor.TOOL_NAME)
    abstract fun bindHttpRequestExecutor(executor: HttpRequestExecutor): LocalToolExecutor

    /**
     * Domain-level binding for the cloud client factory. The data-layer impl
     * [KoogClientFactory] keeps its public per-provider helpers for `DelegateTaskTool`
     * while exposing the provider-keyed [CloudLlmClientFactory.createClient] entry-point
     * to domain consumers.
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
     * Domain-level binding for the cloud model catalog: the models the app offers and the model a
     * run uses — read by the resolver and by every screen that names a provider's model.
     */
    @Binds
    @Singleton
    abstract fun bindCloudModelCatalog(impl: KoogCloudModelCatalog): CloudModelCatalog

    /**
     * Domain-level binding for the cloud structured-inference client factory.
     * The data-layer impl [KoogStructuredInferenceClientFactory] backs the
     * structured-output gate's cloud path for structured node types whose
     * `cloudProvider` is set.
     */
    @Binds
    @Singleton
    abstract fun bindCloudStructuredInferenceClientFactory(
        impl: KoogStructuredInferenceClientFactory,
    ): CloudStructuredInferenceClientFactory
}
