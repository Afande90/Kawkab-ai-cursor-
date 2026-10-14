package app.knotwork.android.di

import app.knotwork.android.data.backup.Argon2idBackupKeyDerivation
import app.knotwork.android.data.backup.BackedUpSecrets
import app.knotwork.android.data.backup.BackupContentCollector
import app.knotwork.android.data.backup.BackupContentSource
import app.knotwork.android.data.backup.BackupExporterImpl
import app.knotwork.android.data.backup.BackupRestorerImpl
import app.knotwork.android.data.backup.DatabaseSnapshotter
import app.knotwork.android.data.backup.SafBackupDocuments
import app.knotwork.android.data.backup.SqlCipherDatabaseSnapshotter
import app.knotwork.android.data.backup.SqlCipherStagedDatabasePreparer
import app.knotwork.android.data.backup.StagedDatabasePreparer
import app.knotwork.android.data.local.ApiKeyManager
import app.knotwork.android.data.local.settings.GenerationSettingsStore
import app.knotwork.android.data.local.settings.ToolSettingsStore
import app.knotwork.android.domain.backup.BackupDocuments
import app.knotwork.android.domain.backup.BackupExporter
import app.knotwork.android.domain.backup.BackupKeyDerivation
import app.knotwork.android.domain.backup.BackupRestorer
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
