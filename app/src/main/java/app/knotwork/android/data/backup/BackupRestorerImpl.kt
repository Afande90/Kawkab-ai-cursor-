package app.knotwork.android.data.backup

import android.content.Context
import android.system.ErrnoException
import android.system.OsConstants
import app.knotwork.android.di.IoDispatcher
import app.knotwork.android.domain.backup.BackupArchiveInfo
import app.knotwork.android.domain.backup.BackupFormatException
import app.knotwork.android.domain.backup.BackupRestoreException
import app.knotwork.android.domain.backup.BackupRestorer
import app.knotwork.android.domain.backup.RestoreFailure
import app.knotwork.android.domain.backup.RestoreProgress
import app.knotwork.android.domain.backup.RestoreReport
import app.knotwork.android.domain.backup.StagedRestore
import app.knotwork.android.domain.repositories.TriggerRepository
import app.knotwork.android.domain.services.TaskScheduler
import app.knotwork.android.domain.services.TriggerScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BackupRestorer] over the app's own storage: [RestoreStager] checks and stages, the start applies the
 * files ([PendingRestoreApplier]) and [RestoreFinisher] completes the rest.
 *
 * Committing writes the marker first — from then on the restore always completes — and then stops what
 * would otherwise run against the restored data: every queued background run, and the watch of every
 * active trigger of this phone's database (the trigger sync cannot cancel a watch whose trigger is gone).
 * Downloads and maintenance keep going. When that fails, or the process ends first, the marker says so
 * and the start cancels the queued runs instead.
 *
 * Staging, committing and finishing are serialised: only one restore is staged at a time.
 *
 * @param layout Where the restore is staged.
 * @param stager Reads and stages archives.
 * @param finisher Completes a committed restore.
 * @param taskScheduler Cancels queued background runs.
 * @param triggerRepository Lists this phone's active triggers.
 * @param triggerScheduler Stops their watches.
 * @param ioDispatcher Runs the file and database work.
 */
@Singleton
internal class BackupRestorerImpl(
    private val layout: RestoreLayout,
    private val stager: RestoreStager,
    private val finisher: RestoreFinisher,
    private val taskScheduler: TaskScheduler,
    private val triggerRepository: TriggerRepository,
    private val triggerScheduler: TriggerScheduler,
    private val ioDispatcher: CoroutineDispatcher,
) : BackupRestorer {

    /** Restores into the app's own storage. */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        stager: RestoreStager,
        finisher: RestoreFinisher,
        taskScheduler: TaskScheduler,
        triggerRepository: TriggerRepository,
        triggerScheduler: TriggerScheduler,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        RestoreLayout.of(context),
        stager,
        finisher,
        taskScheduler,
        triggerRepository,
        triggerScheduler,
        ioDispatcher,
    )

    private val mutex = Mutex()

    override suspend fun inspect(source: InputStream): BackupArchiveInfo = withContext(ioDispatcher) {
        source.use { stager.inspect(it) }
    }

    override suspend fun stage(
        source: InputStream,
        password: CharArray,
        onProgress: (RestoreProgress) -> Unit,
    ): StagedRestore = mutex.withLock {
        withContext(ioDispatcher) {
            source.use {
                check(!layout.isCommitted) { "a committed restore waits for the restart" }
                clearStaging()
                try {
                    stager.stage(it, password, onProgress)
                } catch (e: CancellationException) {
                    clearStaging()
                    throw e
                } catch (e: Exception) {
                    clearStaging()
                    throw failureOf(e)
                }
            }
        }
    }

    override suspend fun commit(applySecrets: Boolean): Unit = mutex.withLock {
        withContext(ioDispatcher) {
            check(layout.stagedManifest.isFile && !layout.isCommitted) { "no staged restore to commit" }
            layout.writeCommit(RestoreLayout.CommitMarker(applySecrets, backgroundStopped = false))
            try {
                taskScheduler.cancelAllBackgroundRuns()
                triggerScheduler.stopWatching(triggerRepository.observeActiveTriggers().first().map { it.id })
                layout.writeCommit(RestoreLayout.CommitMarker(applySecrets, backgroundStopped = true))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Committed all the same: the start cancels the queued runs, and a watch whose trigger is gone
                // ends itself on its next wake.
                Timber.e(e, "Background work could not all be stopped before a restore; the start retries")
            }
        }
    }

    override suspend fun discard(): Boolean = mutex.withLock {
        withContext(ioDispatcher) {
            if (layout.isCommitted) return@withContext false
            clearStaging()
            !layout.staging.exists()
        }
    }

    override suspend fun erase(): Boolean = mutex.withLock {
        withContext(ioDispatcher) {
            clearStaging()
            layout.report.delete()
            !layout.staging.exists() && !layout.report.exists()
        }
    }

    override suspend fun finishPending(): Unit = mutex.withLock {
        withContext(ioDispatcher) {
            val marker = layout.readCommit()
            when {
                marker == null -> if (layout.staging.exists()) clearStaging()
                // The start could not move every file; it tries again, and so does this.
                !layout.isSwapped -> Timber.w("A committed restore's files are not all in place yet")
                else -> finisher.finish(marker)
            }
        }
    }

    override suspend fun report(): RestoreReport? = withContext(ioDispatcher) { RestoreReportJson.read(layout.report) }

    override suspend fun dismissReport(): Unit = withContext(ioDispatcher) {
        if (!layout.report.delete() && layout.report.exists()) Timber.w("The restore report could not be deleted")
    }

    private fun clearStaging() {
        finisher.clear()
    }

    /**
     * What the caller is told about [e]: an archive that cannot be read, or one that cannot be restored here,
     * as it is; the phone running out of space as such; anything else as a failed read — never a raw
     * exception.
     */
    private fun failureOf(e: Exception): Exception = when {
        e is BackupFormatException || e is BackupRestoreException -> e
        generateSequence<Throwable>(e) { it.cause }.any { it is ErrnoException && it.errno == OsConstants.ENOSPC } ->
            BackupRestoreException(RestoreFailure.NO_SPACE, "the phone ran out of space while staging", e)
        else -> {
            Timber.w("A restore could not be staged: %s", e.javaClass.simpleName)
            BackupRestoreException(RestoreFailure.READ_FAILED, "the archive could not be staged", e)
        }
    }
}
