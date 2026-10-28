package app.kawkab.android.di

import app.kawkab.android.data.backup.Argon2idBackupKeyDerivation
import app.kawkab.android.data.backup.BackedUpSecrets
import app.kawkab.android.data.backup.BackupContentCollector
import app.kawkab.android.data.backup.BackupContentSource
import app.kawkab.android.data.backup.BackupExporterImpl
import app.kawkab.android.data.backup.BackupLocalDataCounter
import app.kawkab.android.data.backup.BackupRestorerImpl
import app.kawkab.android.data.backup.DatabaseSnapshotter
import app.kawkab.android.data.backup.SafBackupDocuments
import app.kawkab.android.data.backup.SqlCipherDatabaseSnapshotter
import app.kawkab.android.data.backup.SqlCipherStagedDatabasePreparer
import app.kawkab.android.data.backup.StagedDatabasePreparer
import app.kawkab.android.data.local.ApiKeyManager
import app.kawkab.android.data.local.settings.GenerationSettingsStore
import app.kawkab.android.data.local.settings.ToolSettingsStore
import app.kawkab.android.domain.backup.BackupDocuments
import app.kawkab.android.domain.backup.BackupExporter
import app.kawkab.android.domain.backup.BackupKeyDerivation
import app.kawkab.android.domain.backup.BackupRestorer
import app.kawkab.android.domain.backup.LocalDataCounter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Wires the encrypted backup: the exporter and the restorer, the key derivation, the database snapshot
 * and the staged database's preparation, the content it gathers, and every owner of encrypted-store
 * entries ([BackedUpSecrets]). The bound classes are
 * class-level singletons, so each binding here shares their one instance.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class BackupModule {

    /** The exporter. */
    @Binds
    abstract fun bindBackupExporter(impl: BackupExporterImpl): BackupExporter

    /** Argon2id on Bouncy Castle. */
    @Binds
    abstract fun bindBackupKeyDerivation(impl: Argon2idBackupKeyDerivation): BackupKeyDerivation

    /** The SQLCipher snapshot. */
    @Binds
    abstract fun bindDatabaseSnapshotter(impl: SqlCipherDatabaseSnapshotter): DatabaseSnapshotter

    /** The documents a backup is written to, through the system picker. */
    @Binds
    abstract fun bindBackupDocuments(impl: SafBackupDocuments): BackupDocuments

    /** The restore. */
    @Binds
    abstract fun bindBackupRestorer(impl: BackupRestorerImpl): BackupRestorer

    /** The staged database's check, re-key and migration on SQLCipher and Room. */
    @Binds
    abstract fun bindStagedDatabasePreparer(impl: SqlCipherStagedDatabasePreparer): StagedDatabasePreparer

    /** What the export gathers. */
    @Binds
    abstract fun bindBackupContentSource(impl: BackupContentCollector): BackupContentSource

    /** What a restore would replace, counted as a backup counts it. */
    @Binds
    abstract fun bindLocalDataCounter(impl: BackupLocalDataCounter): LocalDataCounter

    /** Cloud API keys, and each provider's model and server address. */
    @Binds
    @IntoSet
    abstract fun bindApiKeySecrets(impl: ApiKeyManager): BackedUpSecrets

    /** The Hugging Face token. */
    @Binds
    @IntoSet
    abstract fun bindGenerationSecrets(impl: GenerationSettingsStore): BackedUpSecrets

    /** MCP sign-ins and headers. */
    @Binds
    @IntoSet
    abstract fun bindToolSecrets(impl: ToolSettingsStore): BackedUpSecrets
}
