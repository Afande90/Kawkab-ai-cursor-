package app.kawkab.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.kawkab.android.data.local.dao.BackgroundPromptDao
import app.kawkab.android.data.local.dao.ChatDao
import app.kawkab.android.data.local.dao.ChatHistorySummaryDao
import app.kawkab.android.data.local.dao.ExternalAutomationJournalDao
import app.kawkab.android.data.local.dao.LocalModelDao
import app.kawkab.android.data.local.dao.MemoryDao
import app.kawkab.android.data.local.dao.MemoryHistoryDao
import app.kawkab.android.data.local.dao.ModelPerformanceDao
import app.kawkab.android.data.local.dao.PendingInteractionDao
import app.kawkab.android.data.local.dao.PipelineDao
import app.kawkab.android.data.local.dao.PipelinePresetDao
import app.kawkab.android.data.local.dao.PipelineRunDao
import app.kawkab.android.data.local.dao.PromptPresetDao
import app.kawkab.android.data.local.dao.PromptTemplateDao
import app.kawkab.android.data.local.dao.SkillDao
import app.kawkab.android.data.local.dao.TraceStepDao
import app.kawkab.android.data.local.dao.TriggerDao
import app.kawkab.android.data.local.dao.TriggerJournalDao
import app.kawkab.android.data.local.dao.UsageTelemetryDao
import app.kawkab.android.data.local.models.BackgroundPromptEntity
import app.kawkab.android.data.local.models.ChatHistorySummaryEntity
import app.kawkab.android.data.local.models.ChatMessageEntity
import app.kawkab.android.data.local.models.ChatSessionEntity
import app.kawkab.android.data.local.models.ConnectionEntity
import app.kawkab.android.data.local.models.ExternalAutomationRequestEntity
import app.kawkab.android.data.local.models.LocalModelEntity
import app.kawkab.android.data.local.models.MemoryChunkEntity
import app.kawkab.android.data.local.models.MemoryChunkVersionEntity
import app.kawkab.android.data.local.models.MemoryPendingUpdateEntity
import app.kawkab.android.data.local.models.ModelCallEntity
import app.kawkab.android.data.local.models.ModelPerformanceSampleEntity
import app.kawkab.android.data.local.models.NodeEntity
import app.kawkab.android.data.local.models.OnboardingMilestoneEntity
import app.kawkab.android.data.local.models.PendingInteractionEntity
import app.kawkab.android.data.local.models.PipelineEntity
import app.kawkab.android.data.local.models.PipelinePresetEntity
import app.kawkab.android.data.local.models.PipelineRunEntity
import app.kawkab.android.data.local.models.PromptPresetEntity
import app.kawkab.android.data.local.models.PromptTemplateEntity
import app.kawkab.android.data.local.models.SkillEntity
import app.kawkab.android.data.local.models.TraceStepEntity
import app.kawkab.android.data.local.models.TriggerEntity
import app.kawkab.android.data.local.models.TriggerEvaluationEntity
import app.kawkab.android.data.local.models.UsageActiveDayEntity
import app.kawkab.android.data.local.models.UsageCounterEntity
import app.kawkab.android.data.local.models.UsagePipelineDayEntity

/**
 * Main Room Database for Knotwork.
 *
 * Future entities (e.g., PromptTemplates) will be registered here.
 *
 * **Versioning & migrations.** The current schema [version] is the durability baseline:
 * every bump from here on must add a matching `MIGRATION_<old>_<new>` constant in its own
 * `data/local/migrations/Migration<old>To<new>.kt` file and append it to
 * [app.kawkab.android.data.local.migrations.ALL_MIGRATIONS], the list
 * [app.kawkab.android.di.AppModule] registers. There is no destructive fallback on upgrade, so
 * an unsupplied migration fails fast in development rather than silently dropping user data. The
 * exported `app/schemas/<package>/<version>.json` snapshots back the `MigrationTestHelper`
 * regression suite.
 */
@Database(
    entities = [
        LocalModelEntity::class,
        ChatMessageEntity::class,
        ChatSessionEntity::class,
        MemoryChunkEntity::class,
        MemoryChunkVersionEntity::class,
        MemoryPendingUpdateEntity::class,
        PipelineEntity::class,
        NodeEntity::class,
        ConnectionEntity::class,
        PromptTemplateEntity::class,
        TraceStepEntity::class,
        ModelCallEntity::class,
        PipelinePresetEntity::class,
        PromptPresetEntity::class,
        PipelineRunEntity::class,
        PendingInteractionEntity::class,
        SkillEntity::class,
        ChatHistorySummaryEntity::class,
        ModelPerformanceSampleEntity::class,
        TriggerEntity::class,
        TriggerEvaluationEntity::class,
        ExternalAutomationRequestEntity::class,
        UsageCounterEntity::class,
        UsageActiveDayEntity::class,
        UsagePipelineDayEntity::class,
        OnboardingMilestoneEntity::class,
        BackgroundPromptEntity::class,
    ],
    version = AppDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    /**
     * Provides access to the LocalModelDao.
     *
     * @return The [LocalModelDao] instance.
     */
    abstract fun localModelDao(): LocalModelDao

    /**
     * Provides access to the ChatDao.
     *
     * @return The [ChatDao] instance.
     */
    abstract fun chatDao(): ChatDao

    /**
     * Provides access to the MemoryDao.
     *
     * @return The [MemoryDao] instance.
     */
    abstract fun memoryDao(): MemoryDao

    /**
     * Provides access to the MemoryHistoryDao.
     *
     * @return The [MemoryHistoryDao] instance.
     */
    abstract fun memoryHistoryDao(): MemoryHistoryDao

    /**
     * Provides access to the PipelineDao.
     *
     * @return The [PipelineDao] instance.
     */
    abstract fun pipelineDao(): PipelineDao

    /**
     * Provides access to the PromptTemplateDao.
     *
     * @return The [PromptTemplateDao] instance.
     */
    abstract fun promptTemplateDao(): PromptTemplateDao

    /**
     * Provides access to the TraceStepDao.
     *
     * @return The [TraceStepDao] instance.
     */
    abstract fun traceStepDao(): TraceStepDao

    /**
     * Provides access to the [PipelinePresetDao] backing the user-saved
     * pipeline-preset catalogue.
     *
     * @return The [PipelinePresetDao] instance.
     */
    abstract fun pipelinePresetDao(): PipelinePresetDao

    /**
     * Provides access to the [PromptPresetDao] backing the user-saved
     * prompt-preset catalogue.
     *
     * @return The [PromptPresetDao] instance.
     */
    abstract fun promptPresetDao(): PromptPresetDao

    /**
     * Provides access to the [PipelineRunDao] backing the persistent
     * pipeline-run records.
     *
     * @return The [PipelineRunDao] instance.
     */
    abstract fun pipelineRunDao(): PipelineRunDao

    /**
     * Provides access to the [PendingInteractionDao] backing the parked
     * HITL interaction records of the two-phase waiting protocol.
     *
     * @return The [PendingInteractionDao] instance.
     */
    abstract fun pendingInteractionDao(): PendingInteractionDao

    /**
     * Provides access to the [SkillDao] backing the skill catalogue (bundled +
     * user skills).
     *
     * @return The [SkillDao] instance.
     */
    abstract fun skillDao(): SkillDao

    /**
     * Provides access to the [ChatHistorySummaryDao] backing the per-session
     * compressed-history summaries.
     *
     * @return The [ChatHistorySummaryDao] instance.
     */
    abstract fun chatHistorySummaryDao(): ChatHistorySummaryDao

    /**
     * Provides access to the [ModelPerformanceDao] backing the per-model
     * inference performance samples (TTFT / decode speed / peak native memory)
     * shown on the model screen.
     *
     * @return The [ModelPerformanceDao] instance.
     */
    abstract fun modelPerformanceDao(): ModelPerformanceDao

    /**
     * Provides access to the [TriggerDao] backing user-defined automation
     * triggers (the `triggers` table).
     *
     * @return The [TriggerDao] instance.
     */
    abstract fun triggerDao(): TriggerDao

    /**
     * Provides access to the [TriggerJournalDao] backing the trigger-evaluation
     * journal (the `trigger_evaluations` table) — the durable, on-device log of
     * every trigger evaluation and the fate of the runs it started.
     *
     * @return The [TriggerJournalDao] instance.
     */
    abstract fun triggerJournalDao(): TriggerJournalDao

    /**
     * Provides access to the [UsageTelemetryDao] backing the privacy-preserving
     * local usage statistics (the `usage_counter` and `usage_active_day`
     * tables). Every figure stays on-device.
     *
     * @return The [UsageTelemetryDao] instance.
     */
    abstract fun usageTelemetryDao(): UsageTelemetryDao

    /**
     * Provides access to the [ExternalAutomationJournalDao] backing the
     * external-automation request journal (the `external_automation_requests`
     * table) — the durable log of every request a third-party app sent to this
     * one, admitted or refused.
     *
     * @return The [ExternalAutomationJournalDao] instance.
     */
    abstract fun externalAutomationJournalDao(): ExternalAutomationJournalDao

    /**
     * Provides access to the [BackgroundPromptDao] backing the prompts of queued
     * background runs (the `background_prompts` table).
     *
     * @return The [BackgroundPromptDao] instance.
     */
    abstract fun backgroundPromptDao(): BackgroundPromptDao

    companion object {
        /**
         * Canonical on-disk file name of the encrypted Room database. Single
         * source of truth shared by the Hilt provider, the passphrase
         * provider's "does the database already exist" invariant check, and
         * the explicit wipe path — so the three can never drift apart.
         */
        const val DATABASE_NAME: String = "agent_database.db"

        /**
         * The schema version this build's database is at. A restore refuses a database of a later version:
         * Room would recreate it empty rather than downgrade it.
         */
        const val SCHEMA_VERSION: Int = 73
    }
}
