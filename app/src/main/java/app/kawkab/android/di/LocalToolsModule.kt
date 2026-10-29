package app.kawkab.android.di

import app.kawkab.android.data.tools.local.SearchTool
import app.kawkab.android.data.tools.local.executors.AddCalendarEventExecutor
import app.kawkab.android.data.tools.local.executors.AppendFileExecutor
import app.kawkab.android.data.tools.local.executors.CallPhoneExecutor
import app.kawkab.android.data.tools.local.executors.DelegateTaskExecutor
import app.kawkab.android.data.tools.local.executors.DeleteFileExecutor
import app.kawkab.android.data.tools.local.executors.DetectHabitsExecutor
import app.kawkab.android.data.tools.local.executors.EditFileExecutor
import app.kawkab.android.data.tools.local.executors.FindFilesExecutor
import app.kawkab.android.data.tools.local.executors.HttpRequestExecutor
import app.kawkab.android.data.tools.local.executors.ListFilesExecutor
import app.kawkab.android.data.tools.local.executors.MacroSendExecutor
import app.kawkab.android.data.tools.local.executors.OpenAppExecutor
import app.kawkab.android.data.tools.local.executors.ReadFileExecutor
import app.kawkab.android.data.tools.local.executors.ReadScreenExecutor
import app.kawkab.android.data.tools.local.executors.RecordMacroExecutor
import app.kawkab.android.data.tools.local.executors.ScheduleTaskExecutor
import app.kawkab.android.data.tools.local.executors.SearchToolExecutor
import app.kawkab.android.data.tools.local.executors.SendMessageExecutor
import app.kawkab.android.data.tools.local.executors.SendSmsExecutor
import app.kawkab.android.data.tools.local.executors.SetAlarmExecutor
import app.kawkab.android.data.tools.local.executors.SetTimerExecutor
import app.kawkab.android.data.tools.local.executors.SubmitTapExecutor
import app.kawkab.android.data.tools.local.executors.WriteFileExecutor
import app.kawkab.android.domain.repositories.LocalToolExecutor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

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

    @Binds
    @IntoMap
    @StringKey(CallPhoneExecutor.TOOL_NAME)
    abstract fun bindCallPhoneExecutor(executor: CallPhoneExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(SendSmsExecutor.TOOL_NAME)
    abstract fun bindSendSmsExecutor(executor: SendSmsExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(SendMessageExecutor.TOOL_NAME)
    abstract fun bindSendMessageExecutor(executor: SendMessageExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(SetAlarmExecutor.TOOL_NAME)
    abstract fun bindSetAlarmExecutor(executor: SetAlarmExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(SetTimerExecutor.TOOL_NAME)
    abstract fun bindSetTimerExecutor(executor: SetTimerExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(OpenAppExecutor.TOOL_NAME)
    abstract fun bindOpenAppExecutor(executor: OpenAppExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(AddCalendarEventExecutor.TOOL_NAME)
    abstract fun bindAddCalendarEventExecutor(executor: AddCalendarEventExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(ReadScreenExecutor.TOOL_NAME)
    abstract fun bindReadScreenExecutor(executor: ReadScreenExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(SubmitTapExecutor.TOOL_NAME)
    abstract fun bindSubmitTapExecutor(executor: SubmitTapExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(RecordMacroExecutor.TOOL_NAME)
    abstract fun bindRecordMacroExecutor(executor: RecordMacroExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(MacroSendExecutor.TOOL_NAME)
    abstract fun bindMacroSendExecutor(executor: MacroSendExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(DetectHabitsExecutor.TOOL_NAME)
    abstract fun bindDetectHabitsExecutor(executor: DetectHabitsExecutor): LocalToolExecutor
}
